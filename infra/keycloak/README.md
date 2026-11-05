# Keycloak realm `creditdesk`

Imported at startup by `docker compose` (`--import-realm`); in Kubernetes it is the ConfigMap `keycloak-realm`,
generated from this same file by `kustomization.yaml` next to it (one source of truth for both).

| User | Password | Role | Can do |
|---|---|---|---|
| `ana` | `ana-demo` | ANALYST | customers, own proposals (create, edit, submit, cancel) |
| `bruno` | `bruno-demo` | ANALYST | same — a second analyst, to show ownership rules |
| `marcos` | `marcos-demo` | MANAGER | analyst + start analysis, approve, reject, return proposals **of others** |
| `aline` | `aline-demo` | AUDITOR | read-only, audit trail, reveal a CPF with a reason |

**Synthetic data only.** These passwords are public on purpose, so anyone can try the demo. On the public demo
(`k8s/overlays/aws`): Keycloak's brute-force detection locks an account for a while after 5 failed logins, the
`creditdesk-cli` password grant is switched off, and the admin console (`/auth/admin`) answers 403.

## Clients

- `creditdesk-web` — public client for the Angular apps: Authorization Code + **PKCE (S256)**, no secret
  (a browser cannot keep one), redirect only to `localhost:4200-4202` (shell + each micro frontend run alone),
  `vanilds.github.io`, and the cluster's own address. That last one is `${CREDITDESK_PUBLIC_URL:http://localhost:8088}`:
  Keycloak replaces placeholders from environment variables when it imports a realm, so the Codespaces URL
  (different for every codespace) or `https://creditdesk.duckdns.org` comes from the pod, not from this file.
- `creditdesk-cli` — password grant ("direct access"), only so you can call the API from `api/requests.http`
  or curl while developing. OAuth 2.1 removes this grant; it never leaves local development: its
  `directAccessGrantsEnabled` is `${CREDITDESK_PASSWORD_GRANT:true}`, and the aws overlay sets that to `false`.

Both add `creditdesk-api` to the token's `aud` (audience mapper). The API refuses tokens without it.

User ids are fixed (`a0000000-0000-4000-8000-00000000000N`) so the demo data (`R__Demo_data`) can say which
proposals belong to `ana` and `bruno`.

Get a token by hand:

```bash
curl -s http://localhost:8180/realms/creditdesk/protocol/openid-connect/token \
  -d grant_type=password -d client_id=creditdesk-cli -d username=ana -d password=ana-demo | jq -r .access_token
```
