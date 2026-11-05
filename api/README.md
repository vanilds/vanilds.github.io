# CreditDesk API

Spring Boot 3 service for customers (PF/PJ) and credit proposals, with their approval workflow.
Phases 1, 2 and 4 of the [roadmap](../README.md#9-roadmap): the CRUD core; security, workflow, audit and
encryption; then events to the AI worker and documents in S3.

## Run it

Everything runs in **GitHub Codespaces** (`Code → Codespaces → Create codespace`); nothing to install locally.

```bash
# Whole stack (PostgreSQL, Keycloak, RabbitMQ, local S3, AI worker, API with synthetic demo data)
docker compose up --build

# Or: infrastructure in Docker, API from Maven (faster edit → run loop)
docker compose up -d postgres keycloak rabbitmq seaweedfs ai-worker
cd api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

- API: http://localhost:8080 — Swagger UI at `/swagger-ui.html` (click **Authorize** and paste a token)
- Keycloak: http://localhost:8180 — demo users `ana`, `bruno` (analysts), `marcos` (manager), `aline` (auditor);
  passwords and how to get a token in [`infra/keycloak/README.md`](../infra/keycloak/README.md)
- RabbitMQ: http://localhost:15672 (`creditdesk` / `creditdesk`) — queues, retry and dead-letter queues
- Clickable requests, logins included: [`requests.http`](requests.http) (VS Code "REST Client" extension)
- Contract: [`openapi.yaml`](openapi.yaml), generated from the code by the tests

## Test it

```bash
cd api
./mvnw test     # unit tests only (seconds, no Docker)
./mvnw verify   # + integration tests against real PostgreSQL 16, RabbitMQ 4.1 and SeaweedFS (Testcontainers, needs Docker)
```

`verify` also rewrites `openapi.yaml`. CI fails if the committed file is different, so commit it with any API change.
Integration tests build JWTs with `spring-security-test`, so they never need a running Keycloak.

## Design

```
io.github.vanilds.creditdesk
├── customer/   Customer entity, CPF/CNPJ rules, search, soft delete, encrypted document, audited reveal
│   └── migration/   Java Flyway migrations that need the app's keys (encrypt old rows, dev seed)
├── proposal/   Proposal entity, Tabela Price, period filters, workflow (ProposalAction), status timeline,
│   │           proposal.submitted event
│   ├── opinion/     AI opinions: listener (opinion.drafted), idempotent recording, GET …/opinions
│   └── document/    Uploads and downloads through S3 presigned URLs
├── messaging/  RabbitMQ topology, transactional outbox + relay, inbox, retry / dead-letter handling
├── audit/      Append-only audit trail: who, what, when, from which request, why
└── shared/
    ├── crypto/      FieldCipher — AES-256-GCM + HMAC-SHA256
    ├── security/    Resource server config, Keycloak roles, Actor, 401/403 as problems
    ├── validation/  BrazilianDocuments — CPF/CNPJ check digits, alphanumeric CNPJ, masking
    └── web/         RFC 9457 problem handler, pagination, ETag/If-Match, correlation id
```

Packages are organised **by feature**, not by layer (no global `controllers/`, `services/`): everything about
proposals is in one place, and the dependency points one way only — `proposal → customer`. When the customer
module needs to know about proposals (to block a delete), it declares an interface (`CustomerDeletionGuard`) that
the proposal module implements.

### Phase 1 — CRUD

| Decision | Why |
|---|---|
| Flyway owns the schema, Hibernate only validates it (`ddl-auto: validate`) | Schema changes are reviewed SQL with history, never generated at startup |
| Partial unique index `WHERE deleted_at IS NULL` | Soft delete keeps history but frees the document; the database, not the code, guarantees uniqueness under concurrency |
| `pg_trgm` index on `lower(name)` | "Name contains" search stays indexed as the table grows |
| `@EntityGraph` on the proposal list | Customer names come in the same SQL join — no N+1 queries |
| Money as `BigDecimal`, `NUMERIC(15,2)` | `double` cannot represent 0.10 exactly |
| Dates filtered in São Paulo time | A proposal made at 23:30 on Nov 1 belongs to Nov 1, even though it is Nov 2 in UTC |
| Period capped at 90 days, page size 20/50/100 | Predictable query cost on a back-office grid |
| Errors as RFC 9457 `application/problem+json` with a stable `type` URI | Clients branch on `type`, not on message text |
| `Clock` injected, never `Instant.now()` | Time-dependent rules are testable |
| Real PostgreSQL in tests (Testcontainers), no H2 | Partial indexes, triggers, `jsonb` and time zones behave exactly as in production |

### Phase 2 — Security, workflow, audit, encryption

| Decision | Why |
|---|---|
| **Stateless resource server**: the API only validates JWTs signed by Keycloak | It never sees a password; any OIDC provider (Entra ID, Okta) can replace Keycloak with configuration only |
| Validate signature, expiry, **issuer and audience** (`aud` must contain `creditdesk-api`) | A valid token minted for another application of the same realm is still refused |
| Issuer and JWKS URL configured separately | The token says `localhost:8180` (what the browser sees); the API fetches keys at `keycloak:8080` (what the container can reach) |
| Roles from `realm_access.roles`, unknown roles ignored; `MANAGER > ANALYST` hierarchy | One place maps Keycloak to Spring; rules say "ANALYST" instead of "ANALYST or MANAGER" everywhere |
| `@PreAuthorize` for the role, the **entity** for owner and four-eyes rules | Role checks need no data; "is this your proposal?" does, and lives next to the state it protects |
| **Four-eyes**: whoever created a proposal cannot decide it, even as a manager | Segregation of duties — a standard bank control |
| Workflow as `POST /{id}/approve`-style commands, transitions in one enum (`ProposalAction`) | Each command has its own rules and audit action; the whole state machine reads in 6 lines |
| Errors in order 403 → 422 → 400 (who → when → what) | The client gets the most useful reason first |
| **`ETag` = `@Version`, `If-Match` required on every write** (428 if missing, 412 if stale) | Prevents lost updates between two people editing the same record; JPA's version check covers the race at commit |
| Status timeline (`proposal_status_history`) separate from the audit log | Different readers: the analyst sees *their* proposal's history; only managers and auditors read the audit trail |
| Audit written **in the same transaction** as the change (`Propagation.MANDATORY`), explicit calls | No change without its audit line and vice versa; the service knows the *intent* (approved, revealed + reason), Envers would only see rows |
| Audit log **append-only in the database** (trigger blocks UPDATE/DELETE/TRUNCATE) and no delete in the repository | History cannot be rewritten by a bug, a script or someone with the app's DB password |
| Personal data masked in the audit `changes` (CPF, e-mail) | The audit log is read by more people than the customer table |
| **CPF/CNPJ encrypted in the application** (AES-256-GCM, random IV, column name as associated data) | Disk encryption does not protect against `SELECT`; a leaked dump or backup shows only ciphertext |
| HMAC-SHA256 (separate key) for search and the unique index; masked form stored for display | Random IVs make ciphertexts unsearchable; a plain SHA-256 of ~10⁹ possible CPFs is brute-forced in minutes |
| Full CPF only through `POST …/document/reveal` (auditor, reason ≥ 10 chars, audited) | LGPD purpose limitation: access to the full value is exceptional and accountable |
| Plaintext column removed by a **Java Flyway migration** (V4 add → V5 encrypt → V6 drop) | SQL cannot encrypt without the key; one ordered migration run means no instance ever sees half-converted data |
| No default encryption keys outside `dev`/`test`; the app refuses to start without them | Fail fast instead of silently encrypting with a known key |
| CORS: explicit origin list, no cookies, `ETag` exposed | The Angular apps run on other origins (Pages, `localhost:4200-4202`); without `Access-Control-Expose-Headers: ETag` the browser hides the version and every edit would end in 428 |
| `X-Correlation-Id` in every log line, response and audit record | One id finds a request across logs, audit and the worker (it travels in a message header); validated to prevent log injection |

### Phase 4 — Events, AI opinion, documents

| Decision | Why |
|---|---|
| **Transactional outbox**: the event is a row written in the same transaction as the submit | "Commit then publish" loses events on a crash, "publish then commit" announces things that never happened; there is no transaction across Postgres and RabbitMQ (and XA is not worth it) |
| Relay with `FOR UPDATE SKIP LOCKED`, oldest first, stops at the first failure | Several API replicas share the work without publishing the same row twice; order kept; a broker outage only grows the backlog (`creditdesk_outbox_pending`) |
| **Publisher confirms + `mandatory`**, persistent messages | A row is marked published only when the broker stored *and routed* it; survives a broker restart |
| **At-least-once + idempotent consumers** (inbox table keyed by message id) | Exactly-once delivery does not exist across a network; exactly-once *effect* does, with deduplication in the same transaction as the work |
| Retry through a TTL queue back to the **same** queue; DLQ after N attempts; poison messages straight to DLQ | No tight requeue loop, no re-broadcast to other consumers, and a human sees what failed and why (`x-dead-letter-reason`) |
| `RabbitAdmin` ignores declaration errors | The retry delay is a queue argument; changing the setting must not stop the API on an existing broker (it logs and keeps the old queue) |
| The API declares the worker's main queue too | A topic exchange drops messages no queue is bound to: the first submissions would vanish if the worker had never started |
| Event payload without name, CPF/CNPJ or e-mail | Data minimization (LGPD): the worker and any LLM provider never receive personal data |
| `proposalVersion` in the event; the opinion moves the proposal only if it is still SUBMITTED at that version | A slow opinion never undoes a manager's manual start or a return-and-resubmit |
| The worker is an `Actor.system("ai-worker")` in history and audit | Processes are accountable like people; the `system:` prefix can never collide with a Keycloak id |
| Opinions are insert-only (`Persistable.isNew`) | A duplicate id fails loudly instead of being silently merged |
| **Presigned URLs**: the API signs, the browser talks to S3 | Files never load the API (memory, threads, timeouts); credentials never reach the browser |
| `Content-Type` and `Content-Length` are part of the upload signature; `confirm` re-checks with `HEAD` | S3 itself refuses another type or size; the API trusts nothing the browser says it uploaded |
| Storage key from ids only (`proposals/{id}/{documentId}`) | File names carry personal data and path tricks; keys end up in logs and access reports |
| Download link via `POST …/download`, audited, ~1 minute | Opening a document with someone's income is a read worth recording (LGPD accountability) |
| PDF/JPEG/PNG only, 10 MB, 10 per proposal | No HTML/SVG/Office files (scripts, macros); bounded storage per proposal |
| No S3 keys on AWS: default credentials chain → EC2 instance profile | Static keys leak; role credentials rotate by themselves (Phase 6) |

## Configuration

Same variable names as the Kubernetes ConfigMap/Secret in [`k8s/base/config.yaml`](../k8s/base/config.yaml):

| Variable | Purpose |
|---|---|
| `DB_HOST`, `DB_PORT` (5432), `DB_NAME`, `DB_USER`, `DB_PASSWORD` | PostgreSQL |
| `OIDC_ISSUER_URI` | Exact `iss` of the tokens, e.g. `http://localhost:8180/realms/creditdesk` |
| `OIDC_JWK_SET_URI` | Where to fetch Keycloak's public keys (internal address) |
| `CORS_ALLOWED_ORIGINS` | Browser origins allowed to call the API (comma-separated). Default: `localhost:4200-4202` + `https://vanilds.github.io` |
| `DOCUMENT_ENCRYPTION_KEY`, `DOCUMENT_HASH_KEY` | 32 random bytes each, base64 (`openssl rand -base64 32`), different from each other |
| `RABBITMQ_HOST`, `RABBITMQ_PORT` (5672), `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | RabbitMQ |
| `S3_BUCKET`, `S3_REGION` (us-east-1) | Documents bucket. On AWS that is all: credentials come from the instance profile |
| `S3_ENDPOINT`, `S3_PUBLIC_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY` | Only for an S3-compatible store (SeaweedFS locally). The public endpoint is the address the *browser* uses, written into presigned URLs |

Profile `dev` adds synthetic demo data (`customer/migration/R__Demo_data.java`), throwaway keys and the local S3
settings (creating the bucket if missing).

## Image

Multi-stage [`Dockerfile`](Dockerfile): compiles once on the build machine's architecture, then copies Spring Boot's
layers into a JRE-only image that runs as a non-root user. Heap is sized from the container memory limit.
