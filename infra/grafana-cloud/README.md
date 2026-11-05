# Grafana Cloud

CreditDesk's metrics are stored, graphed and alerted on in Grafana Cloud (free tier). The node only runs
Grafana Alloy, which ships them (`k8s/monitoring/alloy/config.alloy`).

| What | Where it lives | How it gets to Grafana Cloud |
|---|---|---|
| Metrics | the cluster | Alloy `remote_write`, continuously |
| Alert + recording rules | `k8s/monitoring/rules/creditdesk.yaml` | `bash sync-rules.sh` (diff), `bash sync-rules.sh --apply` |
| Dashboards | `k8s/monitoring/dashboards/*.json` (built by `build.py`) | Dashboards → New → Import (same uid → overwrite) |
| Endpoint + instance ID | `k8s/monitoring/kustomization.yaml` | not secret |
| Token (metrics:write, rules:read, rules:write) | SSM `/creditdesk/app/GRAFANA_CLOUD_TOKEN` | External Secrets → Secret `grafana-cloud-token` |

First-time setup, step by step: [docs/operations.md](../../docs/operations.md#turning-monitoring-on-once).
