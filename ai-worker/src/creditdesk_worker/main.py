"""Entry point: the consumer plus a small HTTP server for Kubernetes probes and Prometheus."""

import asyncio
import logging
import signal

from aiohttp import web
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

from .config import Settings
from .consumer import Worker, correlation_id
from .graph import build_graph
from .writer import LlmWriter

log = logging.getLogger("creditdesk_worker")


class CorrelationFilter(logging.Filter):
    """Same log shape as the API: every line carries the request's correlation id."""

    def filter(self, record: logging.LogRecord) -> bool:
        record.correlation_id = correlation_id.get()
        return True


def configure_logging() -> None:
    handler = logging.StreamHandler()
    handler.addFilter(CorrelationFilter())
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)5s [%(correlation_id)s] %(name)s : %(message)s"))
    logging.basicConfig(level=logging.INFO, handlers=[handler])
    logging.getLogger("aio_pika").setLevel(logging.WARNING)
    logging.getLogger("aiormq").setLevel(logging.WARNING)
    logging.getLogger("httpx").setLevel(logging.WARNING)


def http_app(worker: Worker) -> web.Application:
    async def health(_: web.Request) -> web.Response:
        # Ready = connected to RabbitMQ and consuming. Kubernetes sends no traffic here, but a 503 shows up in
        # `kubectl get pods` and in alerts.
        if worker.ready:
            return web.json_response({"status": "UP"})
        return web.json_response({"status": "DOWN"}, status=503)

    async def prometheus(_: web.Request) -> web.Response:
        return web.Response(body=generate_latest(), headers={"Content-Type": CONTENT_TYPE_LATEST})

    app = web.Application()
    app.router.add_get("/health", health)
    app.router.add_get("/metrics", prometheus)
    return app


async def main() -> None:
    configure_logging()
    settings = Settings.from_env()
    writer = None
    if settings.llm_enabled:
        writer = LlmWriter(settings.llm_base_url, settings.llm_api_key, settings.llm_model,
                           settings.llm_timeout_seconds)
        log.info("Summaries written by %s, checked by guardrails (template as fallback)", settings.llm_model)
    else:
        log.info("No language model configured: summaries come from the rules template")

    worker = Worker(settings, build_graph(writer))
    runner = web.AppRunner(http_app(worker), access_log=None)
    await runner.setup()
    await web.TCPSite(runner, "0.0.0.0", settings.http_port).start()  # noqa: S104 — inside the pod network

    stop = asyncio.Event()
    loop = asyncio.get_running_loop()
    for sig in (signal.SIGTERM, signal.SIGINT):
        loop.add_signal_handler(sig, stop.set)

    while not stop.is_set():
        try:
            await worker.start()
            break
        except Exception as error:  # noqa: BLE001 — RabbitMQ may still be starting (compose, a fresh cluster)
            log.warning("RabbitMQ not reachable yet (%s); retrying in 5 s", error)
            try:
                await asyncio.wait_for(stop.wait(), timeout=5)
            except TimeoutError:
                pass

    await stop.wait()
    log.info("Shutting down: finishing messages in progress")
    await worker.stop()
    if writer is not None:
        await writer.close()
    await runner.cleanup()


def run() -> None:
    asyncio.run(main())


if __name__ == "__main__":
    run()
