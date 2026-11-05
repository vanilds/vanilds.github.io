#!/usr/bin/env bash
# End-to-end check of the local cluster, only through the Ingress (exactly what a browser can reach):
#   front-ends served → Keycloak issues tokens with the public issuer → API accepts them → document uploaded to
#   S3 through a presigned URL → submit → outbox → RabbitMQ → KEDA wakes the worker from zero → AI opinion →
#   proposal moves to IN_ANALYSIS → an auditor downloads the document.
# Exit code 0 = everything works. Used by .github/workflows/k8s.yml and by hand after k8s/local/up.sh.
set -euo pipefail

cd "$(dirname "$0")/../.."
NS=creditdesk
BASE=http://localhost:8088                      # where this script connects (kind-config.yaml)
PUBLIC_URL=$(sed -n 's/^PUBLIC_URL=//p' k8s/overlays/local/local.env)
PUBLIC_HOST=${PUBLIC_URL#*://}
# In Codespaces the public address needs a GitHub login, so the script talks to localhost:8088 and sends the
# public Host header: for the cluster it is the same request the browser makes (and S3 signatures cover Host).
HOST_HEADER=(-H "Host: $PUBLIC_HOST")
[[ "$PUBLIC_URL" == https://* ]] && HOST_HEADER+=(-H "X-Forwarded-Proto: https")

pass() { printf '  \033[32m✓\033[0m %s\n' "$*"; }
fail() { printf '  \033[31m✗ %s\033[0m\n' "$*" >&2; exit 1; }
need() { command -v "$1" >/dev/null || fail "missing '$1'"; }
need curl; need jq; need kubectl

# curl through the Ingress. Prints the body; the status code goes to the file in $STATUS_FILE.
STATUS_FILE=$(mktemp); HEADERS_FILE=$(mktemp); trap 'rm -f "$STATUS_FILE" "$HEADERS_FILE" /tmp/creditdesk-smoke-*' EXIT
call() {
  local method=$1 path=$2; shift 2
  curl -sS -X "$method" "${HOST_HEADER[@]}" -D "$HEADERS_FILE" -o /tmp/creditdesk-smoke-body -w '%{http_code}' \
    "$BASE$path" "$@" > "$STATUS_FILE"
  cat /tmp/creditdesk-smoke-body
}
status() { cat "$STATUS_FILE"; }
etag() { sed -n 's/^[Ee][Tt]ag: *//p' "$HEADERS_FILE" | tr -d '\r'; }
expect() { [[ "$(status)" == "$1" ]] || fail "$2 (HTTP $(status): $(head -c 300 /tmp/creditdesk-smoke-body))"; }

token() {
  call POST /auth/realms/creditdesk/protocol/openid-connect/token \
    -d grant_type=password -d client_id=creditdesk-cli -d "username=$1" -d "password=$1-demo" | jq -r .access_token
}

# A valid CPF that is new on every run (9 random digits + the two check digits)
random_cpf() {
  local d=() i s r
  for i in $(seq 0 8); do d+=($((RANDOM % 10))); done
  for r in 10 11; do
    s=0; for i in $(seq 0 $((r - 2))); do s=$((s + d[i] * (r - i))); done
    s=$(((s * 10) % 11)); d+=($((s == 10 ? 0 : s)))
  done
  printf '%s' "${d[@]}"
}

echo "CreditDesk smoke test — public address $PUBLIC_URL"

echo "Front-ends"
BODY=$(call GET /); expect 200 "shell index.html"
grep -q '<cd-root' <<<"$BODY" || fail "shell index.html is not the Angular app"
pass "shell (portfolio) at /"
call GET /demo/proposals >/dev/null; expect 200 "Angular route served by the shell (SPA fallback)"
pass "deep link /demo/proposals answered by the shell"
for mfe in customers proposals; do
  [[ "$(call GET /mfe/$mfe/remoteEntry.json | jq -r .name)" == "mfe-$mfe" ]] || fail "remoteEntry of mfe-$mfe"
  pass "mfe-$mfe remoteEntry.json"
done
BODY=$(call GET /actuator/health)
grep -q '"status"' <<<"$BODY" && fail "/actuator must not be reachable from outside"
pass "/actuator is not exposed"

echo "Login (Keycloak under /auth)"
ISSUER=$(call GET /auth/realms/creditdesk/.well-known/openid-configuration | jq -r .issuer)
[[ "$ISSUER" == "$PUBLIC_URL/auth/realms/creditdesk" ]] || fail "issuer is '$ISSUER', expected the public address"
pass "issuer = $ISSUER"
ANA=$(token ana); MARCOS=$(token marcos); ALINE=$(token aline)
[[ "$ANA" != null && "$MARCOS" != null && "$ALINE" != null ]] || fail "password grant for the demo users"
pass "tokens for ana (analyst), marcos (manager), aline (auditor)"

echo "API (/api)"
call GET /api/customers >/dev/null; expect 401 "no token must be refused"
pass "no token → 401"
AUTH_ANA=(-H "Authorization: Bearer $ANA" -H "Content-Type: application/json")
CUSTOMER=$(call POST /api/customers "${AUTH_ANA[@]}" -d "{\"type\":\"PF\",\"document\":\"$(random_cpf)\",\
\"name\":\"Smoke Test\",\"email\":\"smoke@example.com\",\"monthlyRevenue\":12000}" | jq -r .id)
expect 201 "create customer"
pass "customer $CUSTOMER created"
PROPOSAL=$(call POST /api/proposals "${AUTH_ANA[@]}" -d "{\"customerId\":\"$CUSTOMER\",\"kind\":\"NEW_CREDIT\",\
\"amount\":15000,\"installments\":24,\"monthlyRate\":0.0199}" | jq -r .id)
expect 201 "create proposal"
pass "proposal $PROPOSAL created (DRAFT)"

echo "Document upload (presigned URL → S3 through the Ingress)"
FILE=api/samples/payslip-sample.pdf
SIZE=$(wc -c < "$FILE" | tr -d ' ')
UPLOAD=$(call POST "/api/proposals/$PROPOSAL/documents" "${AUTH_ANA[@]}" \
  -d "{\"category\":\"INCOME_PROOF\",\"fileName\":\"payslip.pdf\",\"contentType\":\"application/pdf\",\"sizeBytes\":$SIZE}")
expect 201 "request upload URL"
DOC=$(jq -r .document.id <<<"$UPLOAD")
URL=$(jq -r .upload.url <<<"$UPLOAD")
[[ "$URL" == "$PUBLIC_URL/creditdesk-documents/"* ]] || fail "presigned URL must use the public address: $URL"
pass "presigned URL points at the public address"
mapfile -t SIGNED_HEADERS < <(jq -r '.upload.headers // {} | to_entries[] | "\(.key): \(.value)"' <<<"$UPLOAD")
HEADER_ARGS=(); for h in "${SIGNED_HEADERS[@]}"; do [[ "${h,,}" == host:* ]] || HEADER_ARGS+=(-H "$h"); done
call PUT "${URL#"$PUBLIC_URL"}" "${HEADER_ARGS[@]}" --data-binary "@$FILE" >/dev/null
expect 200 "PUT to S3 with the presigned URL (signature covers path and Host)"
pass "file stored in S3 (SeaweedFS)"
call POST "/api/proposals/$PROPOSAL/documents/$DOC/confirm" "${AUTH_ANA[@]}" >/dev/null; expect 200 "confirm upload"
pass "upload confirmed (AVAILABLE)"

echo "Submit → RabbitMQ → KEDA → AI worker → opinion"
call GET "/api/proposals/$PROPOSAL" "${AUTH_ANA[@]}" >/dev/null; expect 200 "read proposal"
call POST "/api/proposals/$PROPOSAL/submit" "${AUTH_ANA[@]}" -H "If-Match: $(etag)" >/dev/null
expect 200 "submit"
pass "submitted"
STATUS=""
for _ in $(seq 1 60); do       # scale from zero = KEDA poll (5 s) + pod start; allow up to 3 minutes
  STATUS=$(call GET "/api/proposals/$PROPOSAL" "${AUTH_ANA[@]}" | jq -r .status)
  [[ "$STATUS" == IN_ANALYSIS ]] && break
  sleep 3
done
[[ "$STATUS" == IN_ANALYSIS ]] || {
  kubectl -n "$NS" get pods,scaledobject,hpa >&2 || true
  kubectl -n "$NS" logs deploy/ai-worker --tail 30 >&2 || true
  fail "proposal still $STATUS: no AI opinion arrived"
}
OPINION=$(call GET "/api/proposals/$PROPOSAL/opinions" "${AUTH_ANA[@]}" | jq -r '.[0] | "\(.recommendation) / risk \(.riskLevel)"')
pass "AI opinion: $OPINION — proposal moved to IN_ANALYSIS"
WORKERS=$(kubectl -n "$NS" get deployment ai-worker -o jsonpath='{.status.readyReplicas}')
pass "worker pods running now: ${WORKERS:-0} (KEDA returns them to 0 after 2 idle minutes)"

echo "Auditor download (audited presigned GET)"
DOWNLOAD=$(call POST "/api/proposals/$PROPOSAL/documents/$DOC/download" -H "Authorization: Bearer $ALINE" | jq -r .url)
expect 200 "download link"
call GET "${DOWNLOAD#"$PUBLIC_URL"}" > /tmp/creditdesk-smoke-download; expect 200 "GET with the presigned URL"
cmp -s "$FILE" /tmp/creditdesk-smoke-download || fail "downloaded file differs from the uploaded one"
pass "same bytes came back"

echo "Autoscaling objects"
kubectl -n "$NS" get hpa api >/dev/null && pass "HPA api (CPU) present"
kubectl -n "$NS" get scaledobject ai-worker -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' \
  | grep -q True && pass "KEDA ScaledObject ai-worker Ready"

printf '\n\033[1;32mAll checks passed.\033[0m\n'
