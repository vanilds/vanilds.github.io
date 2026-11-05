"""The worker against a real RabbitMQ (Docker): topology, confirms, retry and dead-lettering are broker features."""

import asyncio
import json
import uuid

import aio_pika
import pytest
from testcontainers.core.container import DockerContainer
from testcontainers.core.wait_strategies import LogMessageWaitStrategy

from creditdesk_worker import topology
from creditdesk_worker.config import Settings
from creditdesk_worker.consumer import Worker, opinion_id
from creditdesk_worker.graph import build_graph

from .conftest import make_submission

pytestmark = pytest.mark.integration

API_QUEUE = "api.opinion-drafted"  # declared here the way the API declares it


@pytest.fixture(scope="module")
def rabbit_url():
    container = (DockerContainer("public.ecr.aws/docker/library/rabbitmq:4.1-management-alpine")
                 .with_exposed_ports(5672)
                 .waiting_for(LogMessageWaitStrategy("Server startup complete").with_startup_timeout(120)))
    with container:
        yield f"amqp://guest:guest@{container.get_container_host_ip()}:{container.get_exposed_port(5672)}/"


def settings(url: str, **overrides) -> Settings:
    from urllib.parse import urlparse

    u = urlparse(url)
    base = dict(rabbitmq_host=u.hostname, rabbitmq_port=u.port, rabbitmq_user="guest", rabbitmq_password="guest",  # noqa: S106 — throwaway test broker
                rabbitmq_vhost="/", prefetch=4, retry_delay_seconds=0.2, max_attempts=3, http_port=0,
                llm_base_url="", llm_api_key="", llm_model="", llm_timeout_seconds=1)
    return Settings(**(base | overrides))


@pytest.fixture
async def channel(rabbit_url):
    connection = await aio_pika.connect_robust(rabbit_url)
    ch = await connection.channel()
    events = await ch.declare_exchange(topology.EVENTS, aio_pika.ExchangeType.TOPIC, durable=True)
    api_queue = await ch.declare_queue(API_QUEUE, durable=True)
    await api_queue.bind(events, topology.OPINION_DRAFTED)
    await api_queue.purge()
    yield ch, events, api_queue
    await connection.close()


async def next_message(queue, where=lambda m: True):
    async with asyncio.timeout(15):
        while True:
            message = await queue.get(no_ack=True, fail=False)
            if message is not None and where(message):
                return message
            await asyncio.sleep(0.1)


async def publish_submission(events, body: bytes, message_id: str | None, correlation: str | None = None):
    await events.publish(aio_pika.Message(body=body, message_id=message_id, content_type="application/json",
                                          headers={topology.CORRELATION_HEADER: correlation} if correlation else {}),
                         routing_key=topology.PROPOSAL_SUBMITTED)


async def test_submission_becomes_an_opinion_with_a_deterministic_id(rabbit_url, channel):
    _, events, api_queue = channel
    worker = Worker(settings(rabbit_url), build_graph(None))
    await worker.start()
    try:
        submission = make_submission()
        message_id = str(uuid.uuid4())
        await publish_submission(events, submission.model_dump_json(by_alias=True).encode(), message_id, "corr-123456")

        opinion = await next_message(api_queue)
        assert opinion.message_id == str(opinion_id(message_id))
        assert opinion.headers[topology.CORRELATION_HEADER] == "corr-123456"
        body = json.loads(opinion.body)
        assert body["proposalId"] == str(submission.proposal_id)
        assert body["recommendation"] == "FAVORABLE"
        assert body["model"] == "rules-template-v1"
        assert worker.ready
    finally:
        await worker.stop()


async def test_poison_goes_straight_to_the_dlq(rabbit_url, channel):
    ch, events, _ = channel
    worker = Worker(settings(rabbit_url), build_graph(None))
    await worker.start()
    try:
        dlq = await ch.declare_queue(topology.DEAD_LETTER_QUEUE, durable=True)
        bad = str(uuid.uuid4())
        await publish_submission(events, b'{"proposalId": "not a uuid"}', bad)

        dead = await next_message(dlq, where=lambda m: m.message_id == bad)
        assert "unreadable" in dead.headers["x-dead-letter-reason"]
        assert not dead.headers.get("x-death")  # never went through the retry queue
    finally:
        await worker.stop()


async def test_a_failure_is_retried_then_parked(rabbit_url, channel):
    ch, events, api_queue = channel

    class BrokenGraph:
        calls = 0

        async def ainvoke(self, state):
            BrokenGraph.calls += 1
            raise RuntimeError("simulated failure")

    worker = Worker(settings(rabbit_url), BrokenGraph())
    await worker.start()
    try:
        dlq = await ch.declare_queue(topology.DEAD_LETTER_QUEUE, durable=True)
        message_id = str(uuid.uuid4())
        await publish_submission(events, make_submission().model_dump_json(by_alias=True).encode(), message_id)

        dead = await next_message(dlq, where=lambda m: m.message_id == message_id)
        assert "gave up after 3 attempts" in dead.headers["x-dead-letter-reason"]
        assert BrokenGraph.calls == 3
        assert topology.previous_failures(dead.headers) == 2  # rejected twice into the retry queue
        assert await api_queue.get(no_ack=True, fail=False) is None
    finally:
        await worker.stop()


@pytest.mark.parametrize("waiting", [0, 1])
async def test_a_changed_retry_delay_does_not_stop_the_worker(rabbit_url, channel, waiting):
    """The retry queue exists with another TTL (the setting changed). Empty: recreated. With messages: kept."""
    ch, _, _ = channel
    await ch.queue_delete(topology.RETRY_QUEUE)
    old = await ch.declare_queue(topology.RETRY_QUEUE, durable=True, arguments=topology.retry_queue_arguments(60_000))
    for _ in range(waiting):
        await ch.default_exchange.publish(aio_pika.Message(b"{}"), routing_key=topology.RETRY_QUEUE)

    worker = Worker(settings(rabbit_url, retry_delay_seconds=0.2), build_graph(None))
    await worker.start()
    try:
        assert worker.ready
        # The message waiting in the old queue was not lost
        kept = await old.get(no_ack=True, fail=False)
        assert (kept is not None) == bool(waiting)
    finally:
        await worker.stop()
        await old.purge()
        await ch.queue_delete(topology.RETRY_QUEUE)
