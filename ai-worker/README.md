# CreditDesk AI worker

Python 3.12 + LangGraph. For every submitted proposal it drafts an **advisory credit opinion**: risk level,
recommendation, risk flags, a plan that fits the customer's income, and a short summary in English and Portuguese.
The decision always stays with a manager.

```
RabbitMQ ── proposal.submitted ──▶ worker ── opinion.drafted ──▶ RabbitMQ ──▶ API
                                     │
                                     └── (optional) any OpenAI-compatible model, for the summary text only
```

## The one design rule: rules decide, the model writes

| Step (LangGraph node) | Who | What |
|---|---|---|
| `assess` | `policy.py`, plain Python | Installment / income, flags, risk level, recommendation, a longer term that fits |
| `facts` | Python | The only facts the text may mention |
| `write` | the language model, if configured | Two to four sentences in each language, as JSON |
| `write` → `write` | guardrails (`writer.check_summary`) | Refused drafts go back once **with the reasons**; at most 2 calls |
| `template` | Python | Deterministic text; used with no model, on model errors, or after two refusals |

So a hallucination can change the wording, never the outcome. The guardrails refuse a draft that contains
something like a CPF, CNPJ or e-mail, states a decision ("the proposal is approved"), mentions a percentage that is
not in the facts, or contradicts the recommendation. An auditor can read `policy.py` and get the same answer every
time — thresholds are illustrative for the demo, not any bank's real policy.

**No personal data comes in**: the API's event carries amounts, ratios and counts, never a name, CPF/CNPJ or
e-mail (LGPD data minimization). Nothing identifying can reach a model provider's logs.

## Delivery guarantees

- The input is acknowledged **only after** the opinion is confirmed by the broker (publisher confirms, `mandatory`).
  A crash in between means the submission comes again: at least once, never lost.
- The opinion id is a UUID v5 of the submission's message id. Processing the same submission twice yields the same
  id, and the API's inbox ignores the copy.
- Unreadable messages go straight to `ai-worker.proposal-submitted.dlq`. Other failures are rejected into
  `….retry`, which hands them back after `WORKER_RETRY_DELAY_SECONDS`; after `WORKER_MAX_ATTEMPTS` they are parked
  in the DLQ with an `x-dead-letter-reason` header.
- `connect_robust` reconnects and resumes consuming when RabbitMQ restarts. SIGTERM stops consuming and lets the
  messages in progress finish (Kubernetes graceful shutdown).
- The `x-correlation-id` header from the browser request is on every log line, so one id follows a click from the
  Angular app through the API, RabbitMQ and this worker.

## Run and test

```bash
docker compose up --build          # from the repository root: the whole stack, worker included

cd ai-worker
python3.12 -m venv .venv && . .venv/bin/activate
pip install -e ".[dev]"
pytest                             # unit tests + integration tests against a real RabbitMQ (needs Docker)
pytest -m "not integration"        # without Docker
ruff check .
```

`GET :8000/health` (200 when connected and consuming) and `GET :8000/metrics` (Prometheus:
`creditdesk_worker_messages_total{outcome}`, `creditdesk_worker_processing_seconds`,
`creditdesk_worker_opinions_total{recommendation,source}`).

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST` | `localhost`, `5672`, `creditdesk`, `creditdesk`, `/` | Broker |
| `WORKER_PREFETCH` | `4` | Unacknowledged messages at a time; small, because model calls are slow |
| `WORKER_RETRY_DELAY_SECONDS`, `WORKER_MAX_ATTEMPTS` | `10`, `5` | Retry policy |
| `WORKER_HTTP_PORT` | `8000` | Health and metrics |
| `LLM_BASE_URL`, `LLM_MODEL`, `LLM_API_KEY` | empty | Any OpenAI-compatible chat completions endpoint (a hosted free tier, or a local Ollama at `http://host:11434/v1`). Empty → rules template |
| `LLM_TIMEOUT_SECONDS` | `20` | Then the template is used |

The `rules-template-v1` model needs no key and costs nothing, which is what the public demo uses by default.
