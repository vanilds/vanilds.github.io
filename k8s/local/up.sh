#!/usr/bin/env bash
# Starts (or updates) the whole CreditDesk on a local Kubernetes cluster (kind), in Codespaces or in CI.
#
#   k8s/local/up.sh               create the cluster if needed, build the images, deploy, wait until ready
#   SKIP_BUILD=1 k8s/local/up.sh  deploy again without rebuilding (manifests changed, code did not)
#   k8s/local/smoke.sh            end-to-end check through the Ingress (login, upload, submit, AI opinion)
#   k8s/local/down.sh             delete the cluster (data included)
#
# Idempotent: running it again applies only what changed. Safe to re-run after an error.
set -euo pipefail

cd "$(dirname "$0")/../.."               # repository root
CLUSTER=creditdesk
NS=creditdesk
PORT=8088                                # must match k8s/local/kind-config.yaml
OVERLAY=k8s/overlays/local

# Cluster add-ons, pinned: an upgrade is a deliberate change in a commit, never a surprise on the next run.
TRAEFIK_CHART=42.3.0          # Traefik v3.7 — Ingress controller
KEDA_CHART=2.21.0             # KEDA — scales the AI worker on the RabbitMQ queue length
METRICS_SERVER_CHART=3.14.0   # metrics-server — CPU usage for the API's HPA (k3s has it built in)

step() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
need() { command -v "$1" >/dev/null || { echo "Missing '$1'. In Codespaces: rebuild the container (.devcontainer)." >&2; exit 1; }; }
for tool in docker kind kubectl helm openssl; do need "$tool"; done
echo "kind $(kind version -q 2>/dev/null || kind version) · $(kubectl version --client 2>/dev/null | head -1) · helm $(helm version --short)"

# ---------------------------------------------------------------------------------------------------------
step "Cluster"
if kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
  echo "kind cluster '$CLUSTER' already exists"
else
  kind create cluster --config k8s/local/kind-config.yaml --wait 120s
fi
kubectl config use-context "kind-$CLUSTER" >/dev/null

# ---------------------------------------------------------------------------------------------------------
step "Add-ons: Traefik, KEDA, metrics-server"
helm repo add traefik https://traefik.github.io/charts --force-update >/dev/null
helm repo add kedacore https://kedacore.github.io/charts --force-update >/dev/null
helm repo add metrics-server https://kubernetes-sigs.github.io/metrics-server --force-update >/dev/null
helm repo update traefik kedacore metrics-server >/dev/null

# Install one chart and wait for it. If it does not become ready, show WHY (pod status, events, logs of the
# previous run) instead of Helm's bare "context deadline exceeded".
addon() {
  local release=$1 namespace=$2; shift 2
  if ! helm upgrade --install "$release" "$@" --namespace "$namespace" --create-namespace --wait --timeout 5m; then
    echo "::group::$release did not become ready" >&2
    kubectl -n "$namespace" get pods -o wide >&2 || true
    kubectl -n "$namespace" describe pods >&2 || true
    kubectl -n "$namespace" get events --sort-by=.lastTimestamp >&2 || true
    kubectl -n "$namespace" logs -l "app.kubernetes.io/instance=$release" --all-containers --previous --tail 40 >&2 || true
    echo "::endgroup::" >&2
    exit 1
  fi
}

addon traefik traefik traefik/traefik --version "$TRAEFIK_CHART" -f k8s/local/traefik-values.yaml
addon keda keda kedacore/keda --version "$KEDA_CHART"
# kind's kubelets use self-signed certificates; trusting them is fine on a throwaway local node only
addon metrics-server kube-system metrics-server/metrics-server --version "$METRICS_SERVER_CHART" \
  --set 'args={--kubelet-insecure-tls}'

# ---------------------------------------------------------------------------------------------------------
IMAGES=(creditdesk-api:dev creditdesk-ai-worker:dev creditdesk-shell:dev creditdesk-mfe-customers:dev creditdesk-mfe-proposals:dev)
if [[ "${SKIP_BUILD:-0}" != 1 ]]; then
  step "Images (first build takes a few minutes; later ones reuse the cached layers)"
  export DOCKER_BUILDKIT=1
  docker build -t creditdesk-api:dev api
  docker build -t creditdesk-ai-worker:dev ai-worker
  for app in shell mfe-customers mfe-proposals; do
    docker build --target "$app" -t "creditdesk-$app:dev" web
  done
  # kind's node has its own image store: copy the images in (no registry needed). imagePullPolicy is
  # IfNotPresent, so the cluster never tries to download creditdesk-*:dev from the internet.
  kind load docker-image --name "$CLUSTER" "${IMAGES[@]}"
fi

# ---------------------------------------------------------------------------------------------------------
step "Settings for this environment"
# The address the browser uses. In Codespaces, port 8088 is published as https://<codespace>-8088.app.github.dev
if [[ -n "${CODESPACE_NAME:-}" ]]; then
  PUBLIC_URL="https://${CODESPACE_NAME}-${PORT}.${GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN:-app.github.dev}"
else
  PUBLIC_URL="http://localhost:${PORT}"
fi
# Optional language model for the AI opinion: export LLM_BASE_URL, LLM_MODEL and LLM_API_KEY before running
# (ai-worker/README.md). Without them the worker writes the opinion from its rules template.
cat > "$OVERLAY/local.env" <<EOF
PUBLIC_URL=$PUBLIC_URL
LLM_BASE_URL=${LLM_BASE_URL:-}
LLM_MODEL=${LLM_MODEL:-}
EOF
echo "Public address: $PUBLIC_URL"

# Random secrets, created once and kept (git-ignored): re-running must not change the database password
# under a database that already uses it, nor the key that encrypted the CPFs already stored.
if [[ ! -f "$OVERLAY/secrets.env" ]]; then
  hex() { openssl rand -hex 16; }                 # letters and digits only (KEDA's rule for the AMQP password)
  cat > "$OVERLAY/secrets.env" <<EOF
DB_USER=creditdesk
DB_PASSWORD=$(hex)
RABBITMQ_USER=creditdesk
RABBITMQ_PASSWORD=$(hex)
KEYCLOAK_ADMIN_PASSWORD=$(hex)
DOCUMENT_ENCRYPTION_KEY=$(openssl rand -base64 32)
DOCUMENT_HASH_KEY=$(openssl rand -base64 32)
S3_ACCESS_KEY=creditdesk
S3_SECRET_KEY=$(hex)
LLM_API_KEY=
EOF
  chmod 600 "$OVERLAY/secrets.env"
  echo "Generated $OVERLAY/secrets.env (random, git-ignored)"
fi
if [[ -n "${LLM_API_KEY:-}" ]]; then
  sed -i "s|^LLM_API_KEY=.*|LLM_API_KEY=${LLM_API_KEY}|" "$OVERLAY/secrets.env"
fi

# ---------------------------------------------------------------------------------------------------------
step "Deploy"
FIRST_DEPLOY=1
kubectl get namespace "$NS" >/dev/null 2>&1 && FIRST_DEPLOY=0
kubectl apply -k "$OVERLAY"
# Same tag (:dev), new content: pods already running would keep the old image, so restart ours after a build.
if [[ "$FIRST_DEPLOY" == 0 && "${SKIP_BUILD:-0}" != 1 ]]; then
  kubectl -n "$NS" rollout restart deployment/api deployment/ai-worker deployment/shell \
    deployment/mfe-customers deployment/mfe-proposals
fi

step "Waiting until everything is ready (the first start of the API and Keycloak takes 1-3 min)"
for sts in postgres rabbitmq seaweedfs; do
  kubectl -n "$NS" rollout status "statefulset/$sts" --timeout 5m
done
for deploy in keycloak api shell mfe-customers mfe-proposals; do
  kubectl -n "$NS" rollout status "deployment/$deploy" --timeout 6m
done
# The AI worker is not waited for: with an empty queue KEDA keeps it at zero pods. That is the design.

kubectl -n "$NS" get pods,hpa,scaledobject
cat <<EOF

CreditDesk is up: $PUBLIC_URL
  Portfolio + demo   $PUBLIC_URL/demo        users: ana / ana-demo, marcos / marcos-demo (infra/keycloak/README.md)
  End-to-end check   k8s/local/smoke.sh
  Watch the worker   kubectl -n $NS get pods -l app=ai-worker -w   (0 pods until a proposal is submitted)
EOF
