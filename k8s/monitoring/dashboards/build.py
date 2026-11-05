#!/usr/bin/env python3
"""Builds the Grafana dashboards (dashboards as code).

    python3 k8s/monitoring/dashboards/build.py      # rewrites the two JSON files next to this script

Why a script and not JSON exported from the UI: an exported dashboard is ~2,000 lines of noise where a reviewer
cannot see what changed. Here each panel is one line — title, query, unit — and the JSON is generated.
The JSON is imported into Grafana Cloud (infra/grafana-cloud/README.md).
"""
import json
from pathlib import Path

# The datasource is a dashboard variable, so the same JSON works in any Grafana: in Grafana Cloud it resolves to
# the stack's own Prometheus (grafanacloud-<stack>-prom).
DS = {"type": "prometheus", "uid": "${datasource}"}
HERE = Path(__file__).parent


class Board:
    def __init__(self, uid, title, description):
        self.uid, self.title, self.description = uid, title, description
        self.panels, self.y, self.x, self.row_h, self.next_id = [], 0, 0, 0, 1

    def _place(self, w, h):
        if self.x + w > 24:
            self.x, self.y, self.row_h = 0, self.y + self.row_h, 0
        pos = {"x": self.x, "y": self.y, "w": w, "h": h}
        self.x += w
        self.row_h = max(self.row_h, h)
        return pos

    def _add(self, panel, w, h):
        panel.update(id=self.next_id, gridPos=self._place(w, h), datasource=DS)
        self.next_id += 1
        self.panels.append(panel)

    def row(self, title):
        self.x, self.y = 0, self.y + self.row_h
        self.row_h = 0
        self.panels.append({"type": "row", "title": title, "id": self.next_id, "collapsed": False,
                            "gridPos": {"x": 0, "y": self.y, "w": 24, "h": 1}, "panels": []})
        self.next_id += 1
        self.y += 1

    def stat(self, title, expr, unit="short", thresholds=None, w=4, h=4, description=""):
        steps = [{"color": "green", "value": None}] + [
            {"color": c, "value": v} for v, c in (thresholds or [])]
        self._add({
            "type": "stat", "title": title, "description": description,
            "targets": [{"refId": "A", "expr": expr, "instant": True}],
            "fieldConfig": {"defaults": {"unit": unit, "thresholds": {"mode": "absolute", "steps": steps},
                                         "noValue": "–"}, "overrides": []},
            "options": {"reduceOptions": {"calcs": ["lastNotNull"]}, "colorMode": "background", "graphMode": "none"},
        }, w, h)

    def series(self, title, targets, unit="short", w=12, h=8, stack=False, description="", min0=True):
        self._add({
            "type": "timeseries", "title": title, "description": description,
            "targets": [{"refId": chr(65 + i), "expr": e, "legendFormat": legend} for i, (e, legend) in enumerate(targets)],
            "fieldConfig": {"defaults": {
                "unit": unit, **({"min": 0} if min0 else {}),
                "custom": {"lineWidth": 2, "fillOpacity": 15 if stack else 0, "showPoints": "never",
                           "stacking": {"mode": "normal" if stack else "none"}}}, "overrides": []},
            "options": {"legend": {"displayMode": "table", "placement": "bottom", "calcs": ["lastNotNull", "max"]},
                        "tooltip": {"mode": "multi", "sort": "desc"}},
        }, w, h)

    def table(self, title, expr, w=12, h=8, description=""):
        self._add({
            "type": "table", "title": title, "description": description,
            "targets": [{"refId": "A", "expr": expr, "instant": True, "format": "table"}],
            "transformations": [{"id": "organize", "options": {"excludeByName": {"Time": True}}}],
            "fieldConfig": {"defaults": {}, "overrides": []},
        }, w, h)

    def json(self):
        return {
            "uid": self.uid, "title": self.title, "description": self.description,
            "tags": ["creditdesk"], "timezone": "America/Sao_Paulo", "editable": False,
            "schemaVersion": 41, "version": 1, "refresh": "30s",
            "time": {"from": "now-6h", "to": "now"},
            "panels": self.panels, "annotations": {"list": []},
            "templating": {"list": [{
                "name": "datasource", "label": "Data source", "type": "datasource", "query": "prometheus",
                "current": {}, "hide": 0, "refresh": 1}]},
        }


def application():
    b = Board("creditdesk-app", "CreditDesk — application",
              "Golden signals of the API, the event pipeline (outbox → RabbitMQ → AI worker) and the JVM.")
    b.row("Is the demo working? (last 5 minutes)")
    b.stat("Requests / s", "sum(creditdesk:http_requests:rate5m)", "reqps", w=4)
    b.stat("5xx errors", "creditdesk:http_errors:ratio_rate5m", "percentunit", [(0.01, "orange"), (0.05, "red")], w=4)
    b.stat("p95 latency", "creditdesk:http_latency:p95_5m", "s", [(0.5, "orange"), (1, "red")], w=4)
    b.stat("Outbox pending", "max(creditdesk_outbox_pending)", "short", [(5, "orange"), (20, "red")], w=4,
           description="Events written with the proposal, not yet confirmed by RabbitMQ.")
    b.stat("Waiting for AI opinion", 'sum(rabbitmq_detailed_queue_messages{queue="ai-worker.proposal-submitted"})',
           "short", [(5, "orange"), (25, "red")], w=4)
    b.stat("Dead letters", 'sum(rabbitmq_detailed_queue_messages{queue=~".*\\\\.dlq"})', "short", [(1, "red")], w=4,
           description="Messages that failed every retry. They stay until someone looks (docs/incidents).")

    b.row("API (Spring Boot)")
    b.series("Requests by status", [
        ('sum by (status) (creditdesk:http_requests:rate5m)', "{{status}}")], "reqps", stack=True)
    b.series("Latency p50 / p95 / p99", [
        ('histogram_quantile(0.50, sum by (le) (rate(http_server_requests_seconds_bucket{app="api", uri!~"/actuator.*"}[$__rate_interval])))', "p50"),
        ("creditdesk:http_latency:p95_5m", "p95"),
        ('histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{app="api", uri!~"/actuator.*"}[$__rate_interval])))', "p99")], "s")
    b.series("p95 latency by route", [
        ('topk(8, histogram_quantile(0.95, sum by (le, method, uri) (rate(http_server_requests_seconds_bucket{app="api", uri!~"/actuator.*"}[$__rate_interval]))))',
         "{{method}} {{uri}}")], "s")
    b.series("Errors by route", [
        ('sum by (method, uri, status) (rate(http_server_requests_seconds_count{app="api", status=~"[45]..", uri!~"/actuator.*"}[$__rate_interval]))',
         "{{status}} {{method}} {{uri}}")], "reqps",
        description="4xx are the client's (validation, 412 stale ETag, 403 role); 5xx are ours.")

    b.row("Events: outbox → RabbitMQ → AI worker")
    b.series("Outbox backlog", [("max(creditdesk_outbox_pending)", "pending")], "short",
             description="Grows only when RabbitMQ is down or slow; drains by itself when it is back.")
    b.series("Queue depth", [
        ('sum by (queue) (rabbitmq_detailed_queue_messages)', "{{queue}}")], "short")
    b.series("Worker messages by outcome", [
        ("sum by (outcome) (rate(creditdesk_worker_messages_total[$__rate_interval]))", "{{outcome}}")], "ops", stack=True,
        description="The worker scales to zero when the queue is empty: gaps here are normal.")
    b.series("Worker time per opinion (p95)", [
        ("histogram_quantile(0.95, sum by (le) (rate(creditdesk_worker_processing_seconds_bucket[$__rate_interval])))", "p95")], "s")
    b.series("Opinions by recommendation (per hour)", [
        ("sum by (recommendation) (increase(creditdesk_worker_opinions_total[1h]))", "{{recommendation}}")], "short", stack=True)
    b.series("Worker pods (KEDA)", [
        ('kube_deployment_status_replicas{namespace="creditdesk", deployment="ai-worker"}', "worker pods"),
        ('sum(rabbitmq_detailed_queue_consumers{queue="ai-worker.proposal-submitted"})', "consumers")], "short")

    b.row("JVM and database pool")
    b.series("Heap used vs max", [
        ('sum by (pod) (jvm_memory_used_bytes{app="api", area="heap"})', "used {{pod}}"),
        ('sum by (pod) (jvm_memory_max_bytes{app="api", area="heap"})', "max {{pod}}")], "bytes", w=8)
    b.series("GC pause time", [
        ('sum by (pod) (rate(jvm_gc_pause_seconds_sum{app="api"}[$__rate_interval]))', "{{pod}}")], "s", w=8,
        description="Seconds per second spent paused in GC. Above ~0.05 the heap is too small.")
    b.series("DB connections (Hikari)", [
        ('sum(hikaricp_connections_active{app="api"})', "active"),
        ('sum(hikaricp_connections_pending{app="api"})', "waiting for a connection"),
        ('sum(hikaricp_connections_max{app="api"})', "max")], "short", w=8)
    return b


def platform():
    b = Board("creditdesk-platform", "CreditDesk — platform",
              "The one EC2 node: memory (the scarce resource), pods, restarts, backups and the Ingress.")
    b.row("Node (t4g.medium, 4 GB + 2 GB swap)")
    b.stat("Memory available", "node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes", "percentunit",
           [(0, "red"), (0.10, "orange"), (0.20, "green")], w=4)
    b.stat("Swap used", "node_memory_SwapTotal_bytes - node_memory_SwapFree_bytes", "bytes",
           [(512 * 2**20, "orange"), (1200 * 2**20, "red")], w=4)
    b.stat("Root disk free", 'node_filesystem_avail_bytes{mountpoint="/", fstype!="tmpfs"} / node_filesystem_size_bytes{mountpoint="/", fstype!="tmpfs"}',
           "percentunit", [(0, "red"), (0.15, "orange"), (0.30, "green")], w=4)
    b.stat("Last backup", 'time() - max(kube_cronjob_status_last_successful_time{namespace="creditdesk", cronjob="postgres-backup"})',
           "s", [(26 * 3600, "orange"), (72 * 3600, "red")], w=4,
           description="Age of the last successful pg_dump to S3. Weekdays at 19:30; the node is off on weekends.")
    b.stat("Last restore test", 'time() - max(kube_cronjob_status_last_successful_time{namespace="creditdesk", cronjob="postgres-restore-test"})',
           "s", [(8 * 86400, "orange"), (15 * 86400, "red")], w=4,
           description="A backup that was never restored is a hope, not a backup.")
    b.stat("Monitoring on the node", 'sum(container_memory_working_set_bytes{namespace="monitoring"})',
           "bytes", [(256 * 2**20, "orange"), (400 * 2**20, "red")], w=4,
           description="Alloy + kube-state-metrics. In-cluster Prometheus + Grafana took the node down (incident 2026-11-05).")
    b.series("Node memory", [
        ("node_memory_MemTotal_bytes - node_memory_MemAvailable_bytes", "used"),
        ("node_memory_SwapTotal_bytes - node_memory_SwapFree_bytes", "swap used"),
        ("node_memory_MemTotal_bytes", "total RAM")], "bytes")
    b.series("Node CPU (2 vCPU)", [
        ('sum by (mode) (rate(node_cpu_seconds_total{mode!~"idle|iowait|steal"}[$__rate_interval]))', "{{mode}}")], "short", stack=True)

    b.row("Pods")
    b.series("Memory by pod (working set)", [
        ('sum by (namespace, pod) (container_memory_working_set_bytes{namespace=~"creditdesk|monitoring|argocd"})', "{{namespace}}/{{pod}}")],
        "bytes", w=12, h=10, stack=True,
        description="Working set = what the kernel counts against the limit (the OOM killer looks at this).")
    b.table("Memory vs limit", """
        max by (namespace, pod, container) (container_memory_working_set_bytes{namespace=~"creditdesk|monitoring"})
        / on (namespace, pod, container) group_left
        max by (namespace, pod, container) (kube_pod_container_resource_limits{resource="memory", namespace=~"creditdesk|monitoring"})
    """.strip(), w=12, h=10, description="Above ~0.9 the container is next in line for an OOM kill.")
    b.series("Restarts (last hour)", [
        ('sum by (namespace, pod) (increase(kube_pod_container_status_restarts_total{namespace=~"creditdesk|monitoring"}[1h])) > 0', "{{namespace}}/{{pod}}")],
        "short", w=8)
    b.series("OOM kills", [
        ('sum by (namespace, pod) (increase(container_oom_events_total{namespace=~"creditdesk|monitoring"}[$__rate_interval])) > 0', "{{namespace}}/{{pod}}")],
        "short", w=8)
    b.series("API replicas (HPA on CPU)", [
        ('kube_horizontalpodautoscaler_status_current_replicas{namespace="creditdesk", horizontalpodautoscaler="api"}', "current"),
        ('kube_horizontalpodautoscaler_spec_max_replicas{namespace="creditdesk", horizontalpodautoscaler="api"}', "max")], "short", w=8)
    b.series("CPU by pod", [
        ('sum by (namespace, pod) (rate(container_cpu_usage_seconds_total{namespace=~"creditdesk|monitoring"}[$__rate_interval]))', "{{namespace}}/{{pod}}")],
        "short", w=24, stack=True)

    b.row("Ingress (Traefik)")
    b.series("Requests by service", [
        ('sum by (service) (rate(traefik_service_requests_total[$__rate_interval]))', "{{service}}")], "reqps", stack=True,
        description="Everything that reaches the cluster: shell, micro frontends, API, Keycloak.")
    b.series("5xx at the Ingress", [
        ('sum by (service) (rate(traefik_service_requests_total{code=~"5.."}[$__rate_interval]))', "{{service}}")], "reqps",
        description="502/503 here and not in the API = no pod was ready to take the request.")
    return b


if __name__ == "__main__":
    for board, name in [(application(), "creditdesk-application.json"), (platform(), "creditdesk-platform.json")]:
        (HERE / name).write_text(json.dumps(board.json(), indent=1) + "\n")
        print(f"wrote {name}: {sum(p['type'] != 'row' for p in board.panels)} panels")
