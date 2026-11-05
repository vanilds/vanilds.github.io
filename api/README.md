# CreditDesk API

Spring Boot 3 service for customers (PF/PJ) and credit proposals. Phase 1 of the [roadmap](../README.md#9-roadmap):
the CRUD core, without authentication yet.

## Run it

Everything runs in **GitHub Codespaces** (`Code → Codespaces → Create codespace`); nothing to install locally.

```bash
# Whole stack (PostgreSQL + API with synthetic demo data), from the repository root
docker compose up --build

# Or: database in Docker, API from Maven (faster edit → run loop)
docker compose up -d postgres
cd api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

- API: http://localhost:8080 — Swagger UI at `/swagger-ui.html`
- Clickable requests: [`requests.http`](requests.http) (VS Code "REST Client" extension)
- Contract: [`openapi.yaml`](openapi.yaml), generated from the code by the tests

## Test it

```bash
cd api
./mvnw test     # unit tests only (seconds, no Docker)
./mvnw verify   # + integration tests against a real PostgreSQL 16 (Testcontainers, needs Docker)
```

`verify` also rewrites `openapi.yaml`. CI fails if the committed file is different, so commit it with any API change.

## Design

```
io.github.vanilds.creditdesk
├── customer/   Customer entity, CPF/CNPJ rules, search, soft delete
├── proposal/   Proposal entity, Tabela Price, period filters
└── shared/
    ├── validation/  BrazilianDocuments — CPF/CNPJ check digits, alphanumeric CNPJ, masking
    └── web/         RFC 9457 problem handler, pagination contract
```

Packages are organised **by feature**, not by layer (no global `controllers/`, `services/`): everything about
proposals is in one place, and the dependency points one way only — `proposal → customer`. When the customer
module needs to know about proposals (to block a delete), it declares an interface (`CustomerDeletionGuard`) that
the proposal module implements.

| Decision | Why |
|---|---|
| Flyway owns the schema, Hibernate only validates it (`ddl-auto: validate`) | Schema changes are reviewed SQL with history, never generated at startup |
| Partial unique index on `customer(document) WHERE deleted_at IS NULL` | Soft delete keeps history but frees the document; the database, not the code, guarantees uniqueness under concurrency |
| `pg_trgm` index on `lower(name)` | "Name contains" search stays indexed as the table grows |
| `@EntityGraph` on the proposal list | Customer names come in the same SQL join — no N+1 queries |
| Money as `BigDecimal`, `NUMERIC(15,2)` | `double` cannot represent 0.10 exactly |
| Dates filtered in São Paulo time | A proposal made at 23:30 on Nov 1 belongs to Nov 1, even though it is Nov 2 in UTC |
| Period capped at 90 days, page size 20/50/100 | Predictable query cost on a back-office grid |
| CPF masked in every response, CNPJ shown formatted | CPF is personal data under LGPD; CNPJ is public company data |
| Errors as RFC 9457 `application/problem+json` with a stable `type` URI | Clients branch on `type`, not on message text |
| `@Version` column on every entity | Optimistic locking; exposed as `ETag`/`If-Match` in Phase 2 |
| `Clock` injected, never `Instant.now()` | Time-dependent rules are testable |
| Real PostgreSQL in tests (Testcontainers), no H2 | Partial indexes, `pg_trgm` and time zones behave exactly as in production |

## Configuration

Same variable names as the Kubernetes ConfigMap/Secret in [`k8s/base/config.yaml`](../k8s/base/config.yaml):
`DB_HOST`, `DB_PORT` (5432), `DB_NAME`, `DB_USER`, `DB_PASSWORD`. Profile `dev` adds synthetic demo data
(`src/main/resources/db/seed`).

## Image

Multi-stage [`Dockerfile`](Dockerfile): compiles once on the build machine's architecture, then copies Spring Boot's
layers into a JRE-only image that runs as a non-root user. Heap is sized from the container memory limit.
