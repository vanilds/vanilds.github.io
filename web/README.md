# web — portfolio shell + CreditDesk micro frontends

One Angular 22 workspace, three apps and one shared library:

| Project | Port | What it is | Deployed to |
|---|---|---|---|
| `shell` | 4200 | Portfolio, resume and the demo frame (header, login, navigation) | GitHub Pages — https://vanilds.github.io |
| `mfe-customers` | 4201 | Customers: search, create, edit, delete, auditor CPF reveal | Cluster (Phase 6), `/mfe/customers/` |
| `mfe-proposals` | 4202 | Proposals: filters, draft form with installment preview, workflow, status history, AI opinion (polled after submit), documents (presigned S3 upload) | Cluster (Phase 6), `/mfe/proposals/` |
| `shared` | — | `@creditdesk/shared`: login, API client, i18n, problem banner, pager, status badge | Inside each app, loaded once |

## Run it (Codespaces)

```bash
docker compose up --build        # repository root: API :8080, Keycloak :8180, PostgreSQL
cd web && npm run start:all      # shell :4200 + the two micro frontends :4201 / :4202
```

Open **http://localhost:4200** → "Open the CreditDesk demo" → log in as `ana` / `ana-demo` (see the table of demo
users in `infra/keycloak/README.md`).

> Open the codespace in **VS Code Desktop** (Codespaces menu → "Open in VS Code Desktop"). The machine is still in
> the cloud; VS Code only forwards the ports to your own `localhost`, which is what Keycloak's redirect and the
> API's CORS list expect. In the browser-only editor the ports get `*.app.github.dev` addresses instead, and the
> login would be refused.

Other scripts:

| Command | What it does |
|---|---|
| `npm start` / `npm run start:customers` / `npm run start:proposals` | One app at a time. A micro frontend alone logs in by itself (redirects allowed for 4201/4202) |
| `npm test` | Unit tests (Vitest) of all projects |
| `npm run build` | Production builds into `dist/` |
| `npm run api:types` | Regenerates `projects/shared/src/lib/api/schema.ts` from `../api/openapi.yaml` |

## How it fits together

```
 browser ──► shell (GitHub Pages)
               │  /, /resume                      always available
               │  /demo  ── guard: Keycloak up? ── no ──► /demo-offline
               │             │ yes: login (Authorization Code + PKCE)
               │             ▼
               │  /demo/customers ── loadRemoteModule(remoteEntry.json of mfe-customers) ──► its routes
               │  /demo/proposals ── loadRemoteModule(remoteEntry.json of mfe-proposals) ──► its routes
               ▼
            API (Bearer token added by the shared interceptor, only for the API's URL)
```

## Decisions

| Decision | Why |
|---|---|
| **Native Federation**, remotes registered lazily (`loadRemoteModule` with the `remoteEntry` URL, no manifest at startup) | The portfolio never waits for the demo cluster. When the cluster is off (nights, weekends), only `/demo` notices |
| Remotes expose **routes**, not components | Each team owns its URLs below `/demo/<area>`; the shell only knows the mount point |
| Micro frontends talk **by URL**, never by import (`/demo/proposals?customerId=…`) | They can be built and deployed separately; a link is the whole contract |
| `@creditdesk/shared` is a **tsconfig path shared as a singleton** (`sharedMappings`) | One `Session`, one `I18n`, one `HttpClient` setup for every app on screen — switching language or signing out affects all of them |
| Types **generated from `api/openapi.yaml`** (`openapi-typescript`), checked in CI | A field renamed in the API breaks the front's build, not production. Responses narrowed with `Required<…>` because the contract cannot say the API always fills them |
| `ETag` kept next to each record (`Versioned<T>`), sent back as `If-Match` | Optimistic locking from Phase 2 reaches the screen: a 412 shows "someone saved this after you opened it — reload" instead of overwriting a colleague |
| Errors shown as the API's **RFC 9457** problem, with the `X-Correlation-Id` | Field errors go under each input; the id lets anyone find that exact request in the logs and audit trail |
| OIDC with `angular-auth-oidc-client`: code + PKCE, refresh token, `secureRoutes` = API only | The password is typed only on Keycloak's page; the token is never sent to any other host |
| No auth check at app startup; `demoGuard` first asks Keycloak's discovery document (8 s timeout) | With the server off, the OIDC library would just say "not signed in" and send the user to a login page that does not exist |
| Buttons hidden by role and workflow state (`workflow.ts` mirrors `ProposalAction.java`) | Only to be helpful: the API still decides. If the mirror drifts, the user sees a 403/422 problem, never a wrong state |
| Installment preview in the browser, official value from the API | A float in JavaScript is fine for "about R$ 792"; money that counts is computed with `BigDecimal` on the server |
| Dates filtered as **São Paulo business dates** | 23:30 on the 4th in São Paulo is already the 5th in UTC; the filter must match what the analyst sees |
| EN/PT texts next to each component as `{ en, pt }` + a language signal | Two languages without a build per locale; the resume prints in the language on screen |
| Plain CSS custom properties (`projects/shared/styles`), no component library | Same look as the README diagrams; small bundles (shell ≈ 45 kB gzipped at first load) |
| GitHub Pages serves `index.html` also as `404.html` | Pages has no server routing; deep links (`/resume`) and the login callback (`/demo?code=…`) must reach Angular |
