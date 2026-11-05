# Keycloak realm `creditdesk`

Imported at startup by `docker compose` (`--import-realm`); in Kubernetes it will be a ConfigMap (Phase 5).

| User | Password | Role | Can do |
|---|---|---|---|
| `ana` | `ana-demo` | ANALYST | customers, own proposals (create, edit, submit, cancel) |
| `bruno` | `bruno-demo` | ANALYST | same — a second analyst, to show ownership rules |
| `marcos` | `marcos-demo` | MANAGER | analyst + start analysis, approve, reject, return proposals **of others** |
| `aline` | `aline-demo` | AUDITOR | read-only, audit trail, reveal a CPF with a reason |

**Local, synthetic data only.** These passwords are public on purpose; the public demo (Phase 6) gets its own
realm settings, login rate limiting and no password grant.

## Clients

- `creditdesk-web` — public client for the Angular apps: Authorization Code + **PKCE (S256)**, no secret
  (a browser cannot keep one), redirect only to `localhost:4200` and `vanilds.github.io`.
- `creditdesk-cli` — password grant ("direct access"), only so you can call the API from `api/requests.http`
  or curl while developing. OAuth 2.1 removes this grant; it never leaves local development.

Both add `creditdesk-api` to the token's `aud` (audience mapper). The API refuses tokens without it.

User ids are fixed (`a0000000-0000-4000-8000-00000000000N`) so the demo data (`R__Demo_data`) can say which
proposals belong to `ana` and `bruno`.

Get a token by hand:

```bash
curl -s http://localhost:8180/realms/creditdesk/protocol/openid-connect/token \
  -d grant_type=password -d client_id=creditdesk-cli -d username=ana -d password=ana-demo | jq -r .access_token
```
