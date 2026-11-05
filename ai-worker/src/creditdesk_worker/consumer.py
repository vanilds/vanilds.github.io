"""Consumes proposal.submitted, publishes opinion.drafted.

Delivery guarantees, end to end:

* **Ack only after the opinion is confirmed.** The channel uses publisher confirms and `mandatory`: `publish`
  returns only once the broker stored the opinion in a queue. Only then is the submission acknowledged. A crash in
  between means the submission is delivered again — at least once, never lost.
* **The same submission always produces the same opinion id** (UUID v5 of the submission's message id). If it is
  processed twice, the API's inbox recognizes the second opinion as a duplicate.
* **Failures:** an unreadable message goes straight to the dead-letter queue (retrying cannot fix it). Anything else
  is rejected into the retry queue, which hands it back after a delay; after `max_attempts` it is parked in the DLQ.
"""

import asyncio
import contextvars
import logging
import time
import uuid

import aio_pika
from aio_pika.abc import AbstractChannel, AbstractExchange, AbstractIncomingMessage, AbstractRobustConnection
from pydantic import ValidationError

from . import metrics, topology
from .config import Settings
from .graph import draft_opinion
from .messages import ProposalSubmitted

log = logging.getLogger(__name__)

# The browser request's id, carried by the message: it goes into every log line (see main.CorrelationFilter)
correlation_id: contextvars.ContextVar[str] = contextvars.ContextVar("correlation_id", default="-")

OPINION_NAMESPACE = uuid.UUID("6f1d2a8e-4c1b-5e7a-9d3f-0b8c7e6a5d41")


def opinion_id(submission_message_id: str) -> uuid.UUID:
    """Deterministic: re-processing a submission yields the same opinion id, so the API can deduplicate."""
    return uuid.uuid5(OPINION_NAMESPACE, "opinion:" + submission_message_id)


class Worker:
    def __init__(self, settings: Settings, graph):
        self.settings = settings
        self.graph = graph
        self.connection: AbstractRobustConnection | None = None
        self.channel: AbstractChannel | None = None
        self.events: AbstractExchange | None = None
        self.dead_letter: AbstractExchange | None = None
        self._consumer_tag: str | None = None
        self._queue = None
        self._in_flight: set[asyncio.Task] = set()

    @property
    def ready(self) -> bool:
        return self.connection is not None and not self.connection.is_closed and self._consumer_tag is not None

    async def start(self) -> None:
        # connect_robust reconnects (and re-declares, re-consumes) by itself when the broker restarts
        self.connection = await aio_pika.connect_robust(self.settings.amqp_url, client_properties={
            "connection_name": "creditdesk-ai-worker"})
        self.channel = await self.connection.channel(publisher_confirms=True, on_return_raises=True)
        await self._declare()
        # Prefetch: at most N unacknowledged messages at a time. Model calls are slow; a large prefetch would hoard
        # messages in one replica while another sits idle. (After _declare: it may have reopened the channel.)
        await self.channel.set_qos(prefetch_count=self.settings.prefetch)
        self._consumer_tag = await self._queue.consume(self._on_message)
        log.info("Consuming %s (prefetch %d, retry after %gs, %d attempts)", topology.QUEUE,
                 self.settings.prefetch, self.settings.retry_delay_seconds, self.settings.max_attempts)

    async def _declare(self) -> None:
        ch = self.channel
        self.events = await ch.declare_exchange(topology.EVENTS, aio_pika.ExchangeType.TOPIC, durable=True)
        retry = await ch.declare_exchange(topology.RETRY, aio_pika.ExchangeType.DIRECT, durable=True)
        self.dead_letter = await ch.declare_exchange(topology.DEAD_LETTER, aio_pika.ExchangeType.DIRECT, durable=True)

        # Retry first: declaring it may reopen the channel (see _declare_retry_queue)
        retry_queue = await self._declare_retry_queue(int(self.settings.retry_delay_seconds * 1000))
        await retry_queue.bind(retry, topology.QUEUE)
        dlq = await ch.declare_queue(topology.DEAD_LETTER_QUEUE, durable=True)
        await dlq.bind(self.dead_letter, topology.QUEUE)
        self._queue = await ch.declare_queue(topology.QUEUE, durable=True, arguments=topology.MAIN_QUEUE_ARGUMENTS)
        await self._queue.bind(self.events, topology.PROPOSAL_SUBMITTED)

    async def _declare_retry_queue(self, delay_ms: int):
        """The TTL is a queue argument, and RabbitMQ refuses to redeclare a queue with other arguments
        (PRECONDITION_FAILED closes the channel). So when the delay setting changes: an empty retry queue is
        recreated with the new delay; one still holding messages is kept as it is, so nothing waiting is lost."""
        arguments = topology.retry_queue_arguments(delay_ms)
        try:
            return await self.channel.declare_queue(topology.RETRY_QUEUE, durable=True, arguments=arguments)
        except aio_pika.exceptions.ChannelPreconditionFailed:
            await self.channel.reopen()
        try:
            await self.channel.queue_delete(topology.RETRY_QUEUE, if_empty=True)
            log.warning("Retry delay changed: recreated the empty %s with %d ms", topology.RETRY_QUEUE, delay_ms)
            return await self.channel.declare_queue(topology.RETRY_QUEUE, durable=True, arguments=arguments)
        except aio_pika.exceptions.ChannelPreconditionFailed:
            await self.channel.reopen()
            log.warning("Retry delay changed but %s still holds messages: keeping its old delay until it is "
                        "empty and the worker restarts", topology.RETRY_QUEUE)
            return await self.channel.declare_queue(topology.RETRY_QUEUE, passive=True)

    async def stop(self) -> None:
        """Graceful shutdown (SIGTERM from Kubernetes): stop taking messages, finish the ones in progress."""
        if self._queue is not None and self._consumer_tag is not None:
            await self._queue.cancel(self._consumer_tag)
            self._consumer_tag = None
        if self._in_flight:
            await asyncio.wait(self._in_flight, timeout=30)
        if self.connection is not None:
            await self.connection.close()

    async def _on_message(self, message: AbstractIncomingMessage) -> None:
        task = asyncio.current_task()
        if task is not None:
            self._in_flight.add(task)
        token = correlation_id.set(str((message.headers or {}).get(topology.CORRELATION_HEADER) or "-"))
        try:
            await self.handle(message)
        finally:
            correlation_id.reset(token)
            if task is not None:
                self._in_flight.discard(task)

    async def handle(self, message: AbstractIncomingMessage) -> None:
        started = time.perf_counter()
        try:
            if not message.message_id:
                raise ValueError("no message_id")
            submission = ProposalSubmitted.model_validate_json(message.body)
        except (ValidationError, ValueError) as poison:
            await self._dead_letter(message, f"unreadable message: {poison}")
            await message.ack()
            metrics.MESSAGES.labels("dead_lettered").inc()
            return

        try:
            opinion = await draft_opinion(self.graph, submission)
            headers = {topology.CORRELATION_HEADER: correlation_id.get()} if correlation_id.get() != "-" else {}
            await self.events.publish(aio_pika.Message(
                body=opinion.to_json(),
                message_id=str(opinion_id(message.message_id)),
                type=topology.OPINION_DRAFTED,
                content_type="application/json",
                delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                app_id="creditdesk-ai-worker",
                headers=headers,
            ), routing_key=topology.OPINION_DRAFTED, mandatory=True)
        except Exception as error:  # noqa: BLE001 — every failure is either retried or parked, never lost
            attempt = topology.previous_failures(message.headers) + 1
            if attempt >= self.settings.max_attempts:
                await self._dead_letter(message, f"gave up after {attempt} attempts: {type(error).__name__}: {error}")
                await message.ack()
                metrics.MESSAGES.labels("dead_lettered").inc()
            else:
                log.warning("Proposal %s failed (attempt %d/%d), retrying later: %s: %s", submission.proposal_id,
                            attempt, self.settings.max_attempts, type(error).__name__, error)
                await message.reject(requeue=False)  # → creditdesk.retry → .retry queue → back here after the TTL
                metrics.MESSAGES.labels("retried").inc()
            return

        await message.ack()
        metrics.MESSAGES.labels("published").inc()
        metrics.OPINIONS.labels(opinion.recommendation.value,
                                "template" if opinion.model.startswith("rules-template") else "llm").inc()
        metrics.PROCESSING.observe(time.perf_counter() - started)
        log.info("Opinion for proposal %s v%d: %s, risk %s (%s)", submission.proposal_id, submission.proposal_version,
                 opinion.recommendation.value, opinion.risk_level.value, opinion.model)

    async def _dead_letter(self, message: AbstractIncomingMessage, reason: str) -> None:
        headers = dict(message.headers or {})
        headers["x-dead-letter-reason"] = reason[:500]
        headers["x-original-queue"] = topology.QUEUE
        log.error("Message %s sent to the dead-letter queue: %s", message.message_id, reason)
        await self.dead_letter.publish(aio_pika.Message(
            body=message.body,
            message_id=message.message_id,
            type=message.type,
            content_type=message.content_type,
            delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
            headers=headers,
        ), routing_key=topology.QUEUE, mandatory=True)
