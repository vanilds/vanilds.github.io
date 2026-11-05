# Operating CreditDesk (Phase 7)

How to see what the running system is doing, how its data is protected, and how to follow one request through
it. Incidents and drills are in [incidents/README.md](incidents/README.md); AWS setup is in [aws.md](aws.md).

## Turning monitoring on (once)

Grafana Cloud does the storing, querying, dashboards and alert evaluation; the node only runs Grafana Alloy and
kube-state-metrics. Why: [the 2026-11-05 incident](incidents/README.md#incident-2026-11-05--the-first-phase-7-deploy-took-the-node-down).

1. **grafana.com** (Free plan, no card): create a stack (region US East). Stack → Prometheus → *Details* gives the
   *Remote Write Endpoint* and the *Username / Instance ID*. Put both in `k8s/monitoring/kustomization.yaml`
   (`GRAFANA_CLOUD_PROM_URL`, `GRAFANA_CLOUD_PROM_USER`) — they are not secrets.
2. **Access Policies** → policy `creditdesk-alloy`, scopes `metrics:write`, `rules:read`, `rules:write` → *Add token*.
3. **CloudShell** — store the token in SSM without it ever showing on screen or in the shell history:
   ```
   read -rsp "Grafana Cloud token: " T && echo && aws ssm put-parameter --name /creditdesk/app/GRAFANA_CLOUD_TOKEN --type SecureString --value "$T" --tags Key=Project,Value=creditdesk >/dev/null && unset T && echo stored
   ```
   (to rotate it later: same command with `--overwrite` and without `--tags`).
4. Merge. Argo CD replaces nothing else; Alloy starts sending within a minute (Grafana Cloud → Explore →
   `up{cluster="creditdesk"}`).
5. Alert rules: `curl -fsSLO https://raw.githubusercontent.com/vanilds/vanilds.github.io/main/infra/grafana-cloud/sync-rules.sh && bash sync-rules.sh --apply`
6. Dashboards: Grafana Cloud → Dashboards → *New* → *Import* → upload `k8s/monitoring/dashboards/creditdesk-application.json`,
   then `creditdesk-platform.json`; pick the `grafanacloud-…-prom` data source.
7. First backup and restore test now, instead of waiting for 19:30 / Friday:
   `sudo k3s kubectl -n creditdesk create job --from=cronjob/postgres-backup backup-now`, wait until it completes,
   then the same with `--from=cronjob/postgres-restore-test restore-now`; its log ends with `RESTORE TEST PASSED`.

## What runs where

```
EC2 node (k3s)                                                              Grafana Cloud (free tier)
┌──────────────────────────────────────────────────────────────┐            ┌────────────────────────────┐
│ creditdesk: api · ai-worker · rabbitmq :15692 · traefik      │            │ metrics (14 days)          │
│          ▲ scrape every 30 s                                 │            │ recording + alert rules    │
│ monitoring (priority −100, evicted first):                   │ remote     │ dashboards                 │
│   Alloy ── pods, queues, cAdvisor, node /proc ─ allowlist ───┼──write───▶ │ alert notifications        │
│   kube-state-metrics (restarts, OOM, jobs, HPA)              │  (HTTPS)   └────────────────────────────┘
│ CronJobs: postgres-backup, postgres-restore-test ─────────────┼──▶ s3://creditdesk-backups-vanilds
└──────────────────────────────────────────────────────────────┘
```

Everything on the node is installed by Argo CD from `k8s/argocd/apps/monitoring.yaml`. The pipeline is
`k8s/monitoring/alloy/config.alloy`; rules (`k8s/monitoring/rules/`) and dashboards (`k8s/monitoring/dashboards/`)
are in git and uploaded to Grafana Cloud. A change is a commit, never a click.

### Memory budget

| Pod | Request | Limit | Measured |
|---|---|---|---|
| Alloy (+ config reloader) | 96 Mi + 16 Mi | 256 Mi + 48 Mi | ~40 Mi in a test with the same config |
| kube-state-metrics | 32 Mi | 96 Mi | ~20 Mi on the node |

Before (in-cluster Prometheus + Grafana): ~420 Mi requested, and a startup peak the node could not absorb.
Both pods run at `PriorityClass creditdesk-observability` (−100): under memory pressure they go first.

### What leaves the node, and the free tier

The free tier counts active series (10,000) and keeps metrics 14 days. Alloy forwards **only metric names that
a dashboard or a rule uses** (allowlist in `config.alloy`), and drops `/actuator` request series. CI
(`k8s/monitoring/check_allowlist.py`) fails if a panel or rule uses a name the allowlist would drop.

When the EC2 is stopped (nights, weekends) nothing is sent: graphs have gaps, and that is expected. Rules that
fire on missing data (`ApiDown`) only count while the node itself reports (`up{job="node"}`).

## Dashboards

| Dashboard | Answers |
|---|---|
| CreditDesk — application | Is the demo working? Request rate, 5xx %, p95/p99 latency per route, outbox backlog, queue depths, dead letters, worker throughput and pods, JVM heap, GC, DB pool |
| CreditDesk — platform | Is the node healthy? Memory + swap, CPU, memory per pod vs its limit, restarts, OOM kills, API replicas, disk, age of the last backup and restore test, memory used by monitoring itself, Ingress 5xx |

Dashboards are generated: edit `k8s/monitoring/dashboards/build.py`, run it, commit both files (CI fails if the
JSON is stale), then re-import them in Grafana Cloud (*Import* → same uid → *Overwrite*). The data source is a
dashboard variable, so the same JSON works in any Grafana.

## Alerts

Rules are in `k8s/monitoring/rules/creditdesk.yaml` (plain Prometheus format, `promtool` in CI) and uploaded with
`infra/grafana-cloud/sync-rules.sh` (shows the diff; `--apply` uploads). Grafana Cloud evaluates them; each one
links to its runbook in [incidents/README.md](incidents/README.md). To be notified: Grafana Cloud → Alerting →
Contact points → e-mail, and a notification policy for `severity=critical`.

| Alert | Fires when | Why that threshold |
|---|---|---|
| ApiDown | no API pod scraped for 3 min, while the node reports | longer than a rolling update; silent when the EC2 is off |
| ApiErrorRateHigh | > 5 % 5xx for 5 min (with real traffic) | one error in a quiet minute is not an incident |
| ApiSlow | p95 > 1 s for 10 min | the UI feels broken above ~1 s |
| OutboxBacklog | > 20 events waiting 5 min | RabbitMQ is unreachable; proposals still work, events wait safely |
| OpinionQueueBacklog | > 25 messages for 10 min | KEDA should have started workers long before |
| DeadLetters | any message in a `.dlq` | they are never retried by themselves |
| RabbitMqMemoryAlarm | broker blocks publishers | everything that publishes stalls |
| PodCrashLooping / ContainerOomKilled | ≥ 3 restarts in 15 min / last exit OOMKilled | |
| NodeMemoryLow / NodeSwapHeavy | < 10 % available / > 60 % of swap | the node is about to evict or crawl |
| NodeDiskFilling | < 15 % of / free | images, the Alloy WAL and the local-path volumes share it |
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
