# CreditDesk — Credit & Renegotiation Proposal Desk on Kubernetes (AWS)

> A full-stack portfolio project by a senior front-end engineer from the Brazilian banking sector: the kind of internal credit tool I've built screens for at large banks — now with the **backend, messaging, cloud and Kubernetes operations** built end to end.

This repository is two things at once:

1. **My portfolio** — a public site with my resume, projects and a "How it's built" page, live at **https://vanilds.github.io**.
2. **CreditDesk** — a working back-office app, opened from the portfolio's **"Try the demo"** button, where bank analysts register customers (individuals and companies), create **credit and debt-renegotiation proposals**, attach documents and move each proposal through an approval workflow. When a proposal is submitted, an **AI agent** drafts a credit opinion (summary, risk flags, suggested installment plan) to support — never replace — the analyst's decision. Every change is audited with LGPD-friendly data masking.

The demo runs on **Kubernetes (k3s) on an AWS EC2 instance**, with documents and backups in **Amazon S3**.

---

## 1. System overview

![System overview](docs/diagrams/system-overview.png)

<details><summary>Mermaid source</summary>

```mermaid
flowchart LR
    visitor([Recruiter / visitor])
    demo([Demo user<br/>analista.demo · gestor.demo])

    subgraph gh["GitHub — always on, free"]
        pages["GitHub Pages<br/><b>shell</b>: portfolio + resume<br/>(Angular, Native Federation host)"]
        repo[(Repository<br/>code · manifests · docs)]
    end

    subgraph aws["AWS us-east-1"]
        subgraph ec2["EC2 t4g.medium · k3s"]
            remotes["mfe-customers · mfe-proposals<br/>(remotes)"]
            backend["api · ai-worker · Keycloak<br/>Postgres · RabbitMQ · Redis"]
        end
        s3[("Amazon S3<br/>documents · DB backups")]
    end

    llm[(LLM API)]

    visitor --> pages
    demo --> pages
    pages -- "Try the demo:<br/>loads remotes at runtime" --> remotes
    remotes -- "/api (JWT)" --> backend
    backend --> s3
    backend --> llm
    repo -. "GitOps (Argo CD)" .-> ec2
```

</details>

**Design decision — graceful degradation.** The portfolio and resume are static and live on GitHub Pages, so they never go down and cost nothing. The demo's micro frontends are loaded at runtime from the EC2 cluster. When the instance is stopped (to save credits), the shell catches the failed remote load and shows *"Demo offline — watch the 2-minute video"* instead of breaking. Same fallback pattern I use for micro frontends in banking apps.

---

## 2. AWS infrastructure

![AWS infrastructure](docs/diagrams/aws-infrastructure.png)

<details><summary>Mermaid source</summary>

```mermaid
flowchart TB
    internet([Internet])

    subgraph aws["AWS account · us-east-1"]
        budgets["AWS Budgets<br/>alerts at $10 · $50 · $80"]

        subgraph vpc["VPC · public subnet"]
            sg{{"Security Group<br/>in: 80, 443<br/>SSH: closed (SSM Session Manager)"}}
            subgraph ec2["EC2 t4g.medium (arm64, 2 vCPU, 4 GB + 2 GB swap)"]
                subgraph k3s["k3s single-node cluster"]
                    ingress["Ingress NGINX<br/>+ cert-manager (Let's Encrypt)"]
                    apps["namespace creditdesk<br/>mfe-customers · mfe-proposals · api<br/>ai-worker · keycloak"]
                    data["Postgres · RabbitMQ · Redis<br/>(StatefulSets on local-path PVCs)"]
                    argo["Argo CD"]
                    obs["Prometheus · Grafana"]
                    cron["CronJob: pg_dump → S3 (daily)"]
                end
                ebs[("EBS gp3 30 GB")]
            end
        end

        role["IAM Role (instance profile)<br/>s3:GetObject / PutObject on creditdesk-* only<br/>— no access keys anywhere"]
        s3docs[("S3 creditdesk-documents<br/>private · SSE-S3 · versioning")]
        s3bkp[("S3 creditdesk-backups<br/>lifecycle: delete after 30 days")]
        ssm["SSM Parameter Store<br/>DB / RabbitMQ / LLM secrets"]
    end

    sched["GitHub Actions (cron)<br/>start EC2 08:00 · stop 20:00<br/>+ manual 'start demo' button"]

    internet --> dns["creditdesk.duckdns.org<br/>IP updated at every boot"] --> sg --> ingress
    ingress --> apps
    apps --> data
    data --- ebs
    apps -- "presigned URLs" --> s3docs
    cron --> s3bkp
    role -. "temporary credentials via IMDSv2" .-> apps
    ssm -. "read at deploy" .-> apps
    sched -. "ec2:StartInstances / StopInstances (OIDC role)" .-> ec2
```

</details>

**Cost plan.** On-demand t4g.medium is about $0.034/hour. Running it only ~10 hours on weekdays (plus on demand before interviews) keeps the whole project at roughly **$10/month**, including EBS, the public IP and S3 — so the $100 credit lasts for many months. There's deliberately **no Elastic IP**: AWS bills every public IPv4 address, and an Elastic IP keeps billing while the instance is stopped. Instead, a boot script updates the DuckDNS record with the new IP. A Budgets alert fires long before anything surprising happens.

**Security choices worth discussing in an interview:**
- No SSH port open — shell access only through **SSM Session Manager**.
- No AWS access keys in code, pods or GitHub — the EC2 uses an **IAM instance profile**, and GitHub Actions assumes a role through **OIDC**.
- Documents are never public: the API issues **short-lived presigned URLs**.
- IMDSv2 required, with hop limit 2 so pods can reach it.

---

## 3. Inside the cluster

![Inside the cluster](docs/diagrams/inside-the-cluster.png)

<details><summary>Mermaid source</summary>

```mermaid
flowchart LR
    user([Analyst / manager])

    subgraph k8s["k3s · namespace creditdesk"]
        ing[Ingress NGINX<br/>TLS]
        mfec["mfe-customers<br/>customer CRUD"]
        mfep["mfe-proposals<br/>proposals · workflow · AI opinion"]
        kc["Keycloak<br/>OIDC · roles"]
        api["api<br/>Spring Boot 3 / Java 21"]
        worker["ai-worker<br/>Python + LangGraph"]
        mq[("RabbitMQ<br/>exchange: proposals")]
        db[("PostgreSQL")]
        cache[("Redis<br/>rate limit · cache")]
    end

    shell["shell<br/>(GitHub Pages)"]
    s3[("Amazon S3")]
    llm[(LLM API)]

    user --> shell
    shell -. "import map" .-> mfec
    shell -. "import map" .-> mfep
    user --> ing
    ing -- "/mfe/customers" --> mfec
    ing -- "/mfe/proposals" --> mfep
    ing -- "/auth" --> kc
    ing -- "/api" --> api
    api -- "validate JWT" --> kc
    api --> db
    api --> cache
    api -- "presigned URLs" --> s3
    api -- "proposal.submitted<br/>audit.events" --> mq
    mq --> worker
    worker --> llm
    worker -- "POST /internal/proposals/{id}/opinion" --> api
```

</details>

---

## 4. Domain (the CRUD)

```mermaid
erDiagram
    CUSTOMER ||--o{ PROPOSAL : has
    PROPOSAL ||--o{ DOCUMENT : attaches
    PROPOSAL ||--o| AI_OPINION : receives
    PROPOSAL ||--o{ STATUS_HISTORY : tracks
    APP_USER ||--o{ AUDIT_LOG : generates

    CUSTOMER {
        uuid id PK
        enum type "PF | PJ"
        string document "CPF or CNPJ, stored encrypted, shown masked"
        string name
        string email
        decimal monthly_revenue
        timestamp created_at
    }
    PROPOSAL {
        uuid id PK
        uuid customer_id FK
        enum kind "NEW_CREDIT | RENEGOTIATION"
        decimal amount
        int installments
        decimal monthly_rate
        enum status
        string analyst_id
        int version "optimistic locking"
    }
    DOCUMENT {
        uuid id PK
        uuid proposal_id FK
        string type "contrato social, comprovante..."
        string s3_key
    }
    AI_OPINION {
        uuid proposal_id PK
        int risk_score
        text summary
        json flags
        json suggested_plan
    }
    STATUS_HISTORY {
        uuid id PK
        uuid proposal_id FK
        enum from_status
        enum to_status
        string changed_by
        string reason
    }
    AUDIT_LOG {
        uuid id PK
        string actor
        string action
        string entity
        string correlation_id
        timestamp at
    }
```

### Proposal workflow

```mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> SUBMITTED: analyst submits
    SUBMITTED --> IN_ANALYSIS: AI opinion ready, or a manager picks it up
    IN_ANALYSIS --> APPROVED: manager approves
    IN_ANALYSIS --> REJECTED: manager rejects (reason)
    IN_ANALYSIS --> DRAFT: returned for changes (reason)
    DRAFT --> CANCELLED: analyst cancels
    APPROVED --> [*]
    REJECTED --> [*]
    CANCELLED --> [*]
```

### Roles

| Role | Can do |
|---|---|
| `ANALYST` | CRUD customers, create/edit/submit/cancel **own** proposals, upload documents |
| `MANAGER` | Everything an analyst can, plus start analysis/approve/reject/return proposals **created by someone else** (four-eyes principle) |
| `AUDITOR` | Read-only access to customers, proposals and the audit trail; can reveal a full CPF by giving a reason, which is itself audited |

Every change writes, in the same database transaction, a line to the proposal's status timeline (read by the analyst) and to the append-only audit log (read by managers and auditors), with the user, the request's `X-Correlation-Id` and personal data masked. CPF/CNPJ are stored encrypted (AES-256-GCM) with an HMAC for search; the database never holds them in clear.

---

## 5. Key flows

### Submitting a proposal

```mermaid
sequenceDiagram
    autonumber
    actor A as Analyst
    participant F as mfe-proposals
    participant API as api (Spring Boot)
    participant DB as PostgreSQL
    participant Q as RabbitMQ
    participant W as ai-worker

    A->>F: Click "Submit proposal"
    F->>API: POST /api/proposals/{id}/submit<br/>(JWT, X-Correlation-Id, If-Match: version)
    API->>DB: UPDATE status=SUBMITTED + status_history + outbox row (one transaction)
    API-->>F: 202 Accepted
    API->>Q: outbox relay publishes proposal.submitted
    Q->>W: deliver (idempotent by proposal id)
    W->>API: GET /internal/proposals/{id} (masked data only)
    W->>W: LangGraph: summarize → risk flags → installment plan
    W->>API: POST /internal/proposals/{id}/opinion
    API->>DB: save AI_OPINION, status=IN_ANALYSIS
    F->>API: poll / SSE → opinion shown to analyst
```

### Uploading a document (presigned URL)

```mermaid
sequenceDiagram
    autonumber
    actor A as Analyst
    participant F as mfe-proposals
    participant API as api
    participant S3 as Amazon S3

    A->>F: Choose file (PDF, max 5 MB)
    F->>API: POST /api/proposals/{id}/documents/upload-url
    API->>API: check role, status, file type and size
    API-->>F: presigned PUT URL (valid 5 min) + document id
    F->>S3: PUT file directly (never passes through the API)
    F->>API: POST /api/proposals/{id}/documents/{docId}/confirm
    API->>S3: HEAD object (verify size and type)
    API-->>F: 201 Created
```

### Delivery pipeline

```mermaid
flowchart LR
    dev[git push] --> gha[GitHub Actions<br/>lint · test · build per app]
    gha --> img["Images on GHCR<br/>linux/arm64 + amd64"]
    gha --> pages[Deploy shell<br/>to GitHub Pages]
    gha --> bump[Bump image tags<br/>in k8s/overlays/aws]
    bump --> argo[Argo CD on EC2<br/>syncs from Git]
    argo --> cluster[(k3s)]
```

---

## 6. API

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/customers?document=&name=&page=&size=` | Paginated search, document returned masked |
| `POST` | `/api/customers` | CPF/CNPJ validation (check digits), duplicate check |
| `GET` / `PUT` / `DELETE` | `/api/customers/{id}` | `ETag` on read, `If-Match` on write; soft delete blocked while there are open proposals |
| `POST` | `/api/customers/{id}/document/reveal` | `AUDITOR` only, reason required, audited |
| `GET` | `/api/proposals?status=&from=&to=&customerId=&mine=` | Max 90-day window, 20/50/100 page sizes |
| `POST` | `/api/proposals` | Creates a `DRAFT` owned by the caller |
| `PUT` | `/api/proposals/{id}` | Owner only, only in `DRAFT`; `If-Match` |
| `POST` | `/api/proposals/{id}/submit` · `/cancel` · `/start-analysis` · `/approve` · `/reject` · `/return` | Workflow commands: role, owner and four-eyes checks; `If-Match` |
| `GET` | `/api/proposals/{id}/history` | Status timeline with who and why |
| `POST` | `/api/proposals/{id}/documents/upload-url` · `/{docId}/confirm` | Presigned S3 upload (Phase 4) |
| `GET` | `/api/audit?entityType=&entityId=&actorId=&action=&from=&to=` | `MANAGER` / `AUDITOR` only |

Every `/api` call needs a Keycloak access token (`Authorization: Bearer …`). Errors follow RFC 9457 (`application/problem+json`), including 401, 403, 412 and 428. The contract lives in `api/openapi.yaml` and the front-ends generate their clients from it.

How to run it, the demo users and the design decisions are in [`api/README.md`](api/README.md).

---

## 7. Tech stack

| Layer | Choice | Why it's here |
|---|---|---|
| Portfolio + shell | Angular 18+, Native Federation host, signals, GitHub Pages | Always-on showcase; loads the demo remotes at runtime with an offline fallback |
| Micro frontends | `mfe-customers`, `mfe-proposals` (Angular, Material) | The architecture I use daily at the bank, built from scratch |
| Auth | Keycloak (OIDC, PKCE, realm roles) | Same flow as corporate SSO / Entra ID |
| API | Java 21, Spring Boot 3, Spring Security, Spring Data JPA, Flyway, Bean Validation, springdoc, AWS SDK v2 | Layered design, validation, migrations, contracts, optimistic locking, transactional outbox |
| Async | RabbitMQ (topic exchange, retry + dead-letter queues) | AI opinion is slow; the UI must not wait for it |
| AI | Python 3.12, LangGraph | An agent that assists a human decision, using masked data only |
| Data | PostgreSQL 16, Redis 7 | Relational model; Redis for rate-limiting the public demo login |
| Cloud | AWS EC2 (t4g), S3, IAM roles, SSM, Budgets | Real AWS, least-privilege, no static keys |
| Orchestration | k3s; Deployments, StatefulSets, Services, Ingress, ConfigMaps, Secrets, probes, limits, CronJobs | The core learning goal; HPA demonstrated on the local kind cluster |
| CI/CD | GitHub Actions (OIDC to AWS), GHCR multi-arch images, Argo CD | Push-to-deploy, GitOps |
| Observability | Micrometer, OpenTelemetry, Prometheus, Grafana | Follow one `correlation-id` from the browser to the worker |
| Dev environment | GitHub Codespaces + kind | Everything runs in the browser — no powerful laptop needed |

---

## 8. Repository layout

```
vanilds.github.io/
├── frontend/
│   ├── shell/            # portfolio + resume + demo host → GitHub Pages
│   ├── mfe-customers/    # remote → EC2
│   └── mfe-proposals/    # remote → EC2
├── api/                  # Spring Boot service + openapi.yaml
├── ai-worker/            # Python LangGraph worker
├── k8s/
│   ├── base/             # Kustomize base manifests
│   └── overlays/
│       ├── local/        # kind in Codespaces (MinIO stands in for S3)
│       └── aws/          # k3s on EC2 (real S3, TLS, smaller limits)
├── infra/aws/            # EC2 bootstrap, IAM policies, budget, start/stop scripts
├── docs/diagrams/        # branded PNGs + their sources (render.mjs, specs.py)
├── .devcontainer/        # one-click Codespaces environment
├── compose.yaml          # local stack: docker compose up --build
└── .github/workflows/
```

---

## 9. Roadmap

- [x] **Phase 1 — CRUD backend**: customers and proposals in Spring Boot, Postgres + Flyway, CPF/CNPJ validation, pagination and filters, RFC 9457 errors, OpenAPI, Testcontainers, `docker compose up` in Codespaces.
- [x] **Phase 2 — Security & workflow**: Keycloak realm with `ANALYST`/`MANAGER`/`AUDITOR`, JWT resource server, workflow with ownership and four-eyes, optimistic locking with `ETag`/`If-Match`, append-only audit log, CPF/CNPJ encrypted at rest, data masking.
- [ ] **Phase 3 — Portfolio & micro frontends**: shell with portfolio + resume on GitHub Pages, `mfe-customers` + `mfe-proposals` with Native Federation, offline fallback, clients generated from OpenAPI.
- [ ] **Phase 4 — Events, AI & S3**: transactional outbox → RabbitMQ, retry/DLQ, idempotent LangGraph worker, presigned S3 uploads.
- [ ] **Phase 5 — Kubernetes locally**: kind in Codespaces with Ingress, probes, limits, HPA.
- [ ] **Phase 6 — AWS**: EC2 + k3s, IAM role, S3 buckets, SSM secrets, Budgets, TLS, GitHub Actions with OIDC, Argo CD, scheduled start/stop.
- [ ] **Phase 7 — Operations**: Prometheus/Grafana dashboards, tracing by correlation-id, daily backups to S3 and a tested restore, incident drills written up in `docs/`.

---

## Author

**Vani Santos** — Senior Front-end Engineer (Angular, micro frontends, banking) growing into backend, cloud and applied AI.

> All customer data in this repo is synthetic. CPF/CNPJ values are generated test numbers.
