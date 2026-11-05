# Incidents: runbooks and drills

Each alert in `k8s/monitoring/prometheus-values.yaml` links to a section here. A **runbook** says what to look
at and what to do; a **drill** breaks the thing on purpose, so the runbook is known to work before it is needed.
Drill results go in the table at the end — date, what was seen, what was learned.

All commands run inside an SSM session on the instance (`aws ssm start-session --target $ID`, then
`sudo -i`); `k` below means `k3s kubectl -n creditdesk`:

```
alias k='k3s kubectl -n creditdesk'
```

Ground rules: the cluster is GitOps. A change made with `kubectl` that is also in git (replicas of a
StatefulSet, an image, a limit) is reverted by Argo CD within minutes — that is the point. Drills use that:
break, watch, let git heal it. Real fixes are commits.

---

## API down

**Alert** `ApiDown`. **Users see** the portfolio's demo button lead to "Demo offline", or 502/503 from Traefik.

1. `k get pods -l app=api` — Running but not Ready? CrashLoopBackOff? Pending?
2. Not Ready: `k describe pod <pod>` (events at the bottom), `k logs <pod> --tail 100`. The readiness probe checks
   Postgres; a Pending/CrashLooping `postgres-0` is the real incident.
3. CrashLoop: `k logs <pod> --previous` shows why the last one died. `OOMKilled` in `describe` → see [OOM](#oom).
4. Pending: `k describe pod` → "Insufficient memory" means the node is full → [OOM](#oom).

**Drill — kill the API under load.** Grafana → CreditDesk — application, then:
`k delete pod -l app=api --wait=false` while clicking around the demo.
Expected: Traefik 5xx for ~30–60 s (one replica: the new pod must pass its startup probe; Flyway + Spring on
2 vCPUs), `ApiDown` goes *pending* but not *firing* (3 min). Lesson to check: with `minReplicas: 1` a lost pod is
downtime; two replicas would cost ~380 MB the node does not have.

## API errors

**Alert** `ApiErrorRateHigh`. Dashboard panel *Errors by route* says which route and which status.

- 5xx on every route → a dependency (Postgres, Keycloak JWKS). `k logs deploy/api | grep -E "ERROR|WARN" | tail`.
- 5xx on one route → a bug. Take one failing request's `X-Correlation-Id` and run `bash trace.sh <id>`
  (CloudShell, see [operations.md](../operations.md#following-one-request-correlation-id)).
- 4xx are not ours by default: 412 = a stale ETag (two analysts editing), 403 = role, 422 = validation.

## RabbitMQ down

**Alert** `OutboxBacklog`. **Users see** nothing at first: proposals are accepted and stored with their event in
the same transaction (outbox). Only the AI opinion stops arriving.

1. `k get pod rabbitmq-0`, `k logs rabbitmq-0 --tail 50`.
2. Memory/disk alarm (`RabbitMqMemoryAlarm`)? `k exec rabbitmq-0 -- rabbitmq-diagnostics alarms`.
3. When the broker is back, the relay publishes the backlog by itself (oldest first, publisher confirms). Nothing
   to replay by hand: watch *Outbox backlog* return to 0.

**Drill — broker gone for 5 minutes.**
1. `k scale statefulset rabbitmq --replicas=0`
2. In the demo, create and submit two proposals (as `ana`). They are saved, status SUBMITTED, no opinion.
3. Watch *Outbox pending* rise to 2. Argo CD notices the drift (git says `replicas: 1`) and scales it back,
   usually within 3 minutes — if not, `k scale statefulset rabbitmq --replicas=1`.
4. Expected: when `rabbitmq-0` is Ready, outbox → 0, KEDA starts a worker, both opinions appear, proposals move
   to IN_ANALYSIS. Zero lost events. Time it: from broker Ready to opinion visible.

## Worker stuck

**Alert** `OpinionQueueBacklog`. Messages wait in `ai-worker.proposal-submitted` and no worker takes them.

1. *Worker pods (KEDA)* panel: 0 pods with messages waiting → KEDA. `k3s kubectl -n keda logs deploy/keda-operator --tail 50`,
   `k get scaledobject ai-worker -o yaml | grep -A5 conditions`. KEDA reads the queue with the RabbitMQ password;
   a changed password in SSM needs the operator to re-read the secret.
2. Pods but no progress → `k logs -l app=ai-worker --tail 50`. Repeated failures go to retry, then to the DLQ.

## Dead letters

**Alert** `DeadLetters`. A message failed every retry (5 attempts, 10 s apart) and was parked. Nothing retries it.

1. Which queue? The alert says (`ai-worker.proposal-submitted.dlq` or `api.opinion-drafted.dlq`).
2. Read one without removing it (management API, inside the instance):
   ```
   k port-forward svc/rabbitmq 15672:15672 &
   U=$(k get secret creditdesk-secrets -o jsonpath='{.data.RABBITMQ_USER}' | base64 -d)
   P=$(k get secret creditdesk-secrets -o jsonpath='{.data.RABBITMQ_PASSWORD}' | base64 -d)
   curl -s -u "$U:$P" -H 'content-type: application/json' -X POST localhost:15672/api/queues/%2F/ai-worker.proposal-submitted.dlq/get \
     -d '{"count":1,"ackmode":"ack_requeue_true","encoding":"auto"}' | head -c 2000
   ```
   The `x-death` header says how often and why; `x-correlation-id` feeds `trace.sh`.
3. Bug fixed and deployed → move the messages back (management UI: Queues → the DLQ → *Move messages* to the
   main queue; needs the shovel plugin) or resubmit the proposal. Poison (bad payload) → purge:
   `k exec rabbitmq-0 -- rabbitmqctl purge_queue ai-worker.proposal-submitted.dlq`.

**Drill — poison message.** With the port-forward and `$U`/`$P` from step 2:
```
curl -s -u "$U:$P" -H 'content-type: application/json' -X POST localhost:15672/api/exchanges/%2F/amq.default/publish \
  -d '{"properties":{"headers":{"x-correlation-id":"drill-poison-0001"}},"routing_key":"ai-worker.proposal-submitted","payload":"this is not json","payload_encoding":"string"}'
```
Expected: KEDA starts a worker, the worker rejects it (unparseable payloads are dead-lettered at once, not
retried), *Dead letters* = 1, `DeadLetters` fires after 1 min, `creditdesk_worker_messages_total{outcome="dead_lettered"}`
goes up. Then read it (step 2) and purge it. Lesson: one bad message does not block the queue behind it.

## OOM

**Alerts** `ContainerOomKilled`, `NodeMemoryLow`, `NodeSwapHeavy`. The scarce resource on this node is memory.

1. Platform dashboard → *Memory vs limit*: who is near 1.0? *Memory by pod*: who grew?
2. A container killed at its limit (OOMKilled, exit 137) → its limit is too low for its real working set, or it
   leaks. JVMs size their heap from the limit (`MaxRAMPercentage`), so raising the limit raises the heap too.
   Fix = a commit to `k8s/overlays/aws/kustomization.yaml`.
3. Node short of memory → the kubelet evicts pods, monitoring first (priority −100). Grafana gone = a symptom.
   Options, cheapest first: lower Prometheus retention, drop a dashboard nobody reads, move Keycloak to a smaller
   heap, or pay for a t4g.large (8 GB, ~2× the EC2 cost).

**Drill — a container that eats memory.**
```
k3s kubectl -n monitoring run oom-drill --image=public.ecr.aws/docker/library/busybox:1.37 --restart=Always \
  --overrides='{"spec":{"containers":[{"name":"oom-drill","image":"public.ecr.aws/docker/library/busybox:1.37","command":["sh","-c","tail /dev/zero"],"resources":{"limits":{"memory":"32Mi"}}}]}}'
```
Expected: OOMKilled in seconds, CrashLoopBackOff, *OOM kills* panel shows it, `ContainerOomKilled` and later
`PodCrashLooping` fire. The kernel killed only that container: no other pod moved. Clean up:
`k3s kubectl -n monitoring delete pod oom-drill` (it is not in git, so Argo CD leaves it alone — delete it).

## Backup failed

**Alerts** `BackupJobFailed`, `BackupMissing`.

1. `k get jobs -l app=postgres-backup` (or `postgres-restore-test`), `k logs job/<name> --all-containers`.
2. Typical causes: Postgres not ready at 19:30 (the Job retries once); S3 `AccessDenied` (instance role policy,
   `infra/aws/iam/ec2-app-policy.json`); the node stopped at 20:00 in the middle (`activeDeadlineSeconds` 15 min,
   started 19:30 — should not happen; if it does, move the schedule earlier).
3. Fixed → run one now: `k create job --from=cronjob/postgres-backup backup-now`. Then delete the failed Job:
   that acknowledges the alert.
4. A failed **restore test** is the more serious one: the backups exist but may not be usable. Read the log —
   each check prints `ok`/`FAIL`. Do not delete old backups until one restores.

**Drill — prove the restore test catches a bad backup.** Done in the sandbox before Phase 7 shipped (corrupted
dump → checksum fails; row counts lower than recorded → `RESTORE TEST FAILED`, exit 1). On the cluster:
`k create job --from=cronjob/postgres-restore-test restore-now` and read `k logs job/restore-now -f --all-containers`.

## Restore the database from a backup

For real data loss (volume gone, bad migration, someone deleted data). **RPO**: last weekday 19:30. **RTO**: ~10 min.

1. **CloudShell** — choose the backup and get a link valid for 10 minutes (no credentials leave AWS; the
   instance downloads straight from S3):
   ```
   aws s3 ls s3://creditdesk-backups-vanilds/postgres/ --recursive | grep dump$ | tail -5
   aws s3 presign s3://creditdesk-backups-vanilds/postgres/<YYYY/MM/DD/HHMMSS>/creditdesk.dump --expires-in 600
   ```
2. **Instance** — a safety copy of what is there now (so the restore itself can be undone):
   `k create job --from=cronjob/postgres-backup before-restore && k wait --for=condition=complete job/before-restore --timeout=10m`
   (skip if Postgres is the thing that is broken).
3. Stop the only writer. The HPA does nothing while a Deployment is at 0, and Argo CD does not manage replicas:
   `k scale deploy/api --replicas=0`
4. Replace the database with the dump (one command; `--force` closes leftover connections):
   ```
   URL='<the presigned URL from step 1>'
   k exec -i postgres-0 -- sh -ec 'wget -qO- "$1" | { dropdb -U "$POSTGRES_USER" --force "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB" && pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --exit-on-error --no-owner --no-privileges; }' sh "$URL"
   ```
5. `k scale deploy/api --replicas=1`. Flyway validates the schema at start; the HPA takes over again.
6. Check: log in to the demo, open a proposal, and in Grafana the API is green. Events created after the backup
   are gone with their rows; documents in S3 uploaded after it remain but are no longer referenced.

Rehearsed in the sandbox against the real schema and seed data: database dropped and restored from a dump, the
API restarted and validated all migrations.

## Rebuild everything (EC2 lost)

`bash recreate-ec2.sh` rebuilds the cluster from git (docs/aws.md). Since Phase 7 the data comes back too:
after Argo CD shows `creditdesk` Healthy, run [Restore the database](#restore-the-database-from-a-backup) with
the latest backup. The demo seed (`R__Demo_data`) runs first on the empty database; the restore replaces it.

---

## Drill log

| Date | Drill | What happened | Time to recover | Learned / changed |
|---|---|---|---|---|
| 2026-11-05 | Restore test vs corrupted / incomplete dump (sandbox) | checksum failed; counts check failed, exit 1 | — | restore test fails loudly, as intended |
| 2026-11-05 | Restore into a live database (sandbox) | drop → recreate → `pg_restore`; API restarted, Flyway validated 8 migrations | < 1 min | runbook steps 3–5 confirmed |
| | RabbitMQ down 5 min | | | |
| | Kill the API pod | | | |
| | Poison message → DLQ | | | |
| | OOM drill | | | |
