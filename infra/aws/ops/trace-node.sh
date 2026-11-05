#!/bin/bash
# Runs ON the EC2 instance (started by infra/aws/ops/trace.sh through SSM). Read-only.
#   trace-node.sh <correlation-id> <since>
set -uo pipefail
id=$1
since=$2
[[ $id =~ ^[A-Za-z0-9-]{8,64}$ && $since =~ ^[0-9]{1,3}[smh]$ ]] || { echo "bad arguments"; exit 2; }
k() { k3s kubectl -n creditdesk "$@"; }

section() { printf '\n== %s ==\n' "$1"; }

# Logs: --prefix shows which pod wrote each line. The worker scales to zero when its queue is empty and its pods
# (and their logs) go with it, so its lines are only here while a worker pod still exists — the audit log and
# the outbox below are the durable part of the trail.
for app in api ai-worker; do
  section "$app logs (last $since)"
  k logs -l "app=$app" --all-containers --prefix --since="$since" --tail=-1 2>/dev/null | grep -F -- "$id" \
    || { [[ $app == ai-worker ]] && echo "(no lines: the worker pod has probably scaled to zero since)" || echo "(no lines)"; }
done

# Database: the same id in the outbox (what was sent to RabbitMQ) and in the audit log (what changed, by whom).
# psql variables (:'id') quote the value: nothing typed here becomes SQL.
psql() { k exec -i postgres-0 -- sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -P pager=off "$@"' psql "$@"; }

section "outbox events"
psql -v id="$id" <<'SQL'
SELECT created_at AT TIME ZONE 'America/Sao_Paulo' AS created_sp,
       published_at AT TIME ZONE 'America/Sao_Paulo' AS published_sp,
       event_type, aggregate_id, attempts, last_error
FROM outbox_event WHERE correlation_id = :'id' ORDER BY created_at;
SQL

section "audit log"
psql -v id="$id" <<'SQL'
SELECT occurred_at AT TIME ZONE 'America/Sao_Paulo' AS occurred_sp,
       actor_name, action, entity_type, entity_id, reason
FROM audit_log WHERE correlation_id = :'id' ORDER BY occurred_at;
SQL
