"""RabbitMQ names and arguments, the same as api/src/main/java/.../messaging/Topology.java.

RabbitMQ refuses to redeclare a queue with different arguments (PRECONDITION_FAILED), so these values must match
the API's exactly. The main queue has only fixed arguments, which is why both services can declare it; the retry
delay is the consumer's choice, so only the worker declares its own retry queue.
"""

EVENTS = "creditdesk.events"  # topic
RETRY = "creditdesk.retry"  # direct, keyed by queue name
DEAD_LETTER = "creditdesk.dead-letter"  # direct, keyed by queue name

PROPOSAL_SUBMITTED = "proposal.submitted"
OPINION_DRAFTED = "opinion.drafted"

QUEUE = "ai-worker.proposal-submitted"
RETRY_QUEUE = QUEUE + ".retry"
DEAD_LETTER_QUEUE = QUEUE + ".dlq"

CORRELATION_HEADER = "x-correlation-id"

MAIN_QUEUE_ARGUMENTS = {
    "x-dead-letter-exchange": RETRY,
    "x-dead-letter-routing-key": QUEUE,
}


def retry_queue_arguments(delay_ms: int) -> dict:
    # Wait for the TTL, then expire through the default exchange straight back to the main queue only
    return {
        "x-message-ttl": delay_ms,
        "x-dead-letter-exchange": "",
        "x-dead-letter-routing-key": QUEUE,
    }


def previous_failures(headers: dict | None, queue: str = QUEUE) -> int:
    """How many times `queue` already rejected this message, from the broker's x-death header."""
    for death in (headers or {}).get("x-death") or []:
        if death.get("queue") == queue and str(death.get("reason")) == "rejected":
            return int(death.get("count", 0))
    return 0
