"""Prometheus metrics, served on /metrics (scraped in Phase 7)."""

from prometheus_client import Counter, Histogram

MESSAGES = Counter("creditdesk_worker_messages_total", "Messages handled, by outcome", ["outcome"])
PROCESSING = Histogram("creditdesk_worker_processing_seconds", "Time to draft and publish one opinion",
                       buckets=(0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10, 20, 40))
OPINIONS = Counter("creditdesk_worker_opinions_total", "Opinions published, by recommendation and text source",
                   ["recommendation", "source"])
