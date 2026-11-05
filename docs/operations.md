# Operating CreditDesk (Phase 7)

How to see what the running system is doing, how its data is protected, and how to follow one request through
it. Incidents and drills are in [incidents/README.md](incidents/README.md); AWS setup is in [aws.md](aws.md).

## Turning Phase 7 on (once)

1. **CloudShell**, before merging: `curl -fsSLO https://raw.githubusercontent.com/vanilds/vanilds.github.io/<branch>/infra/aws/create-secrets.sh && bash create-secrets.sh`
   — creates `GRAFANA_ADMIN_PASSWORD`, keeps every existing parameter (it never overwrites).
2. Merge. `Images` rebuilds (the API now publishes latency histograms) and Argo CD installs the `monitoring`
   app and the two CronJobs within a few minutes: `sudo k3s kubectl -n argocd get applications`.
3. First backup and restore test now instead of waiting for 19:30 / Friday:
   `sudo k3s kubectl -n creditdesk create job --from=cronjob/postgres-backup backup-now`, wait until it completes,
   then the same with `--from=cronjob/postgres-restore-test restore-now` and read its log
   (`... logs job/restore-now --all-containers` ends with `RESTORE TEST PASSED`).
4. Open Grafana (below) and check that both dashboards have data.

## What runs where

```
monitoring namespace (priority -100: evicted first)       creditdesk namespace
┌───────────────────────────────────────────────┐          ┌──────────────────────────────────────────┐
│ Prometheus ── scrapes every 30 s ───────────────────────▶│ api /actuator/prometheus                 │
│   3 days, ≤ 1.5 GB, alert + recording rules   │          │ ai-worker /metrics (while it has pods)   │
│ kube-state-metrics (restarts, OOM, jobs, HPA) │          │ rabbitmq :15692 (queue depths, alarms)   │
│ node-exporter (memory, swap, disk, CPU)       │          │ CronJobs: postgres-backup, restore-test  │
│ Grafana ── reads Prometheus; dashboards = git │          └───────────────┬──────────────────────────┘
└───────────────────────────────────────────────┘                          │ instance role
                                                                           ▼
                                               s3://creditdesk-backups-vanilds/postgres/YYYY/MM/DD/HHMMSS/
```

Everything is installed by Argo CD from `k8s/argocd/apps/monitoring.yaml`. Values, rules and dashboards live in
`k8s/monitoring/`; a change there is a commit, never a click.

### Memory budget

The node has 4 GB and ran at ~700 MB available before Phase 7. Monitoring reserves ~420 MiB (requests) and is
limited to ~1 GiB in the worst case:

| Pod | Request | Limit | What was given up for it |
|---|---|---|---|
| Prometheus | 192 Mi | 448 Mi | 30 s scrapes, 3-day history, no API-server/kubelet jobs, cAdvisor allowlist |
| Grafana | 160 Mi | 384 Mi | no plugins, no sidecars: dashboards are files; a 1 Gi disk only so migrations run once |
| kube-state-metrics | 32 Mi | 96 Mi | only the 8 object kinds the alerts read |
| node-exporter | 24 Mi | 64 Mi | — |

All of them run at `PriorityClass creditdesk-observability` (value −100). Under memory pressure the kubelet
evicts the lowest priority first: losing graphs for an hour is a smaller incident than losing the demo.

## Opening Grafana and Prometheus

Neither is on the Internet. Same pattern as the Argo CD UI: a port forward inside the instance, carried to your
computer by SSM (needs the AWS CLI + Session Manager plugin locally).

1. Password (CloudShell): `aws ssm get-parameter --name /creditdesk/app/GRAFANA_ADMIN_PASSWORD --with-decryption --query Parameter.Value --output text`
2. Session 1, inside the instance: `sudo k3s kubectl -n monitoring port-forward svc/grafana 3000:80`
3. Session 2, on your computer: `aws ssm start-session --target $ID --document-name AWS-StartPortForwardingSession --parameters portNumber=3000,localPortNumber=3000`
4. Open http://localhost:3000, user `admin`. Dashboards → **CreditDesk** folder.

Prometheus itself: the same with `svc/prometheus-server 9090:80` (Status → Targets shows what is scraped,
Alerts shows every rule and its state).

| Dashboard | Answers |
|---|---|
| CreditDesk — application | Is the demo working? Request rate, 5xx %, p95/p99 latency per route, outbox backlog, queue depths, dead letters, worker throughput and pods, JVM heap, GC, DB pool |
| CreditDesk — platform | Is the node healthy? Memory + swap, CPU, memory per pod vs its limit, restarts, OOM kills, API replicas, disk, age of the last backup and restore test, Ingress 5xx |

Dashboards are generated: edit `k8s/monitoring/dashboards/build.py`, run it, commit both files. CI fails if the
JSON is stale.

## Alerts

Rules are in `k8s/monitoring/prometheus-values.yaml` (`serverFiles.alerting_rules.yml`); each one links to its
runbook in [incidents/README.md](incidents/README.md). They are evaluated by Prometheus and listed in Grafana
under Alerting → Alert rules. There is **no notifier** on purpose: nobody is on call for a demo that is off at
night, and Alertmanager is one more pod on a full node. The next step would be a Grafana contact point (e-mail)
on the two `critical` rules.

| Alert | Fires when | Why that threshold |
|---|---|---|
| ApiDown | no API pod scraped for 3 min | longer than a rolling update |
| ApiErrorRateHigh | > 5 % 5xx for 5 min (with real traffic) | one error in a quiet minute is not an incident |
| ApiSlow | p95 > 1 s for 10 min | the UI feels broken above ~1 s |
| OutboxBacklog | > 20 events waiting 5 min | RabbitMQ is unreachable; proposals still work, events wait safely |
| OpinionQueueBacklog | > 25 messages for 10 min | KEDA should have started workers long before |
| DeadLetters | any message in a `.dlq` | they are never retried by themselves |
| RabbitMqMemoryAlarm | broker blocks publishers | everything that publishes stalls |
| PodCrashLooping / ContainerOomKilled | ≥ 3 restarts in 15 min / last exit OOMKilled | |
| NodeMemoryLow / NodeSwapHeavy | < 10 % available / > 60 % of swap | the node is about to evict or crawl |
| NodeDiskFilling | < 15 % of / free | images, Prometheus data and the local-path volumes share it |
| BackupMissing | last successful backup > 72 h | the node is off from Friday 20:00 to Monday 08:00 (~60 h) |
| BackupJobFailed | a backup or restore-test Job failed | stays until the failed Job is deleted (= acknowledged) |
| CertificateExpiringSoon | TLS certificate < 14 days | cert-manager renews at 30 days: this means renewal is stuck |

## Backups

| | |
|---|---|
| What | `pg_dump --format=custom` of the `creditdesk` database, plus a SHA-256 and the row counts taken just before |
| When | weekdays 19:30 São Paulo (`postgres-backup` CronJob, `k8s/overlays/aws/backup.yaml`) |
| Where | `s3://creditdesk-backups-vanilds/postgres/YYYY/MM/DD/HHMMSS/`; `postgres/latest.json` points at the newest complete one |
| Kept | 30 days (bucket lifecycle rule) |
| Restore tested | Fridays 19:45 (`postgres-restore-test`): latest dump → checksum → a throwaway Postgres inside the pod → `pg_restore --exit-on-error` → counts ≥ the backup's, schema version, foreign keys, audit-log triggers |
| Not backed up | Keycloak (realm and demo users are in git, `start-dev` keeps nothing else), RabbitMQ (messages are transient; the outbox in Postgres is the durable copy), S3 documents (the bucket is versioned) |
| RPO / RTO | up to one working day of data / ~10 minutes (see [restore](incidents/README.md#restore-the-database-from-a-backup)) |

Security: CPF/CNPJ are inside the dump only encrypted (AES-256-GCM). The keys are in SSM, never next to the
backup — a leaked dump does not leak documents. Losing the keys, on the other hand, makes every backup's
documents unreadable: that is why `create-secrets.sh` never overwrites them.

Run either job now: `sudo k3s kubectl -n creditdesk create job --from=cronjob/postgres-backup backup-now`
(or `postgres-restore-test`), then `... logs -f job/backup-now --all-containers`.

## Following one request (correlation id)

Every API response has an `X-Correlation-Id` header. The same id is in every API log line, travels in the
RabbitMQ message header to the AI worker (and in its log lines), and is stored in `outbox_event` and `audit_log`.

```
bash trace.sh <correlation-id> [24h]        # in CloudShell; read-only (infra/aws/ops/trace.sh)
```

prints, in order: API log lines → worker log lines → the outbox event → the audit rows. A support ticket with one
id finds everything.

Known gap: logs live only as long as their pod. The worker scales to zero, so its lines disappear minutes after
the queue empties; the database part of the trail is permanent. A log store (Loki) would close the gap but costs
~150 MB the node does not have — the honest trade-off for this budget. Distributed tracing with spans
(OpenTelemetry + Tempo) is the same story, one level up.
