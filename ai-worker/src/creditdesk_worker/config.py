"""Settings from environment variables, the same names as k8s/base/config.yaml and compose.yaml."""

import os
from dataclasses import dataclass


def _env(name: str, default: str = "") -> str:
    return os.environ.get(name, default).strip()


@dataclass(frozen=True)
class Settings:
    rabbitmq_host: str
    rabbitmq_port: int
    rabbitmq_user: str
    rabbitmq_password: str
    rabbitmq_vhost: str
    prefetch: int
    retry_delay_seconds: float
    max_attempts: int
    http_port: int
    llm_base_url: str
    llm_api_key: str
    llm_model: str
    llm_timeout_seconds: float

    @property
    def amqp_url(self) -> str:
        from urllib.parse import quote

        vhost = quote(self.rabbitmq_vhost, safe="")
        return (f"amqp://{quote(self.rabbitmq_user, safe='')}:{quote(self.rabbitmq_password, safe='')}"
                f"@{self.rabbitmq_host}:{self.rabbitmq_port}/{vhost}")

    @property
    def llm_enabled(self) -> bool:
        # Placeholders from the sample Secret count as "not configured": the template is used, no failing calls
        return bool(self.llm_base_url and self.llm_model and self.llm_api_key
                    and not self.llm_api_key.startswith(("set-me", "change-me")))

    @staticmethod
    def from_env() -> "Settings":
        return Settings(
            rabbitmq_host=_env("RABBITMQ_HOST", "localhost"),
            rabbitmq_port=int(_env("RABBITMQ_PORT", "5672")),
            rabbitmq_user=_env("RABBITMQ_USER", "creditdesk"),
            rabbitmq_password=_env("RABBITMQ_PASSWORD", "creditdesk"),
            rabbitmq_vhost=_env("RABBITMQ_VHOST", "/"),
            prefetch=int(_env("WORKER_PREFETCH", "4")),
            retry_delay_seconds=float(_env("WORKER_RETRY_DELAY_SECONDS", "10")),
            max_attempts=int(_env("WORKER_MAX_ATTEMPTS", "5")),
            http_port=int(_env("WORKER_HTTP_PORT", "8000")),
            llm_base_url=_env("LLM_BASE_URL"),
            llm_api_key=_env("LLM_API_KEY"),
            llm_model=_env("LLM_MODEL"),
            llm_timeout_seconds=float(_env("LLM_TIMEOUT_SECONDS", "20")),
        )
