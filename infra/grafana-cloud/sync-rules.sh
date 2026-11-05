#!/bin/bash
# Upload the CreditDesk alert and recording rules to Grafana Cloud (run in AWS CloudShell).
#
#   bash sync-rules.sh            # show what would change
#   bash sync-rules.sh --apply    # change it
#
# Source of truth: k8s/monitoring/rules/creditdesk.yaml on main. Grafana Cloud's ruler (Mimir) evaluates them
# against the metrics Alloy ships, so alerts keep working without anything heavy on the node.
#
# Reads from git: the Prometheus endpoint and instance ID (k8s/monitoring/kustomization.yaml, not secret).
# Reads from SSM: the access-policy token (scopes metrics:write, rules:read, rules:write) — never printed.
set -euo pipefail
export AWS_DEFAULT_REGION="us-east-1"
RAW="https://raw.githubusercontent.com/vanilds/vanilds.github.io/main"
work=$(mktemp -d); trap 'rm -rf "$work"' EXIT

kustomization=$(curl -fsSL "$RAW/k8s/monitoring/kustomization.yaml")
url=$(sed -n 's/.*GRAFANA_CLOUD_PROM_URL=//p' <<<"$kustomization")
user=$(sed -n 's/.*GRAFANA_CLOUD_PROM_USER=//p' <<<"$kustomization")
[[ $url == https://* && $user =~ ^[0-9]+$ ]] || { echo "Set the Grafana Cloud endpoint and user in k8s/monitoring/kustomization.yaml first."; exit 1; }
address=$(sed -E 's#^(https://[^/]+).*#\1#' <<<"$url")     # the ruler lives on the same host, no path

token=$(aws ssm get-parameter --name /creditdesk/app/GRAFANA_CLOUD_TOKEN --with-decryption \
  --query Parameter.Value --output text)

# mimirtool (Grafana's CLI for Mimir, which Grafana Cloud metrics runs on), latest release, checked by checksum
release="https://github.com/grafana/mimir/releases/latest/download"
curl -fsSL -o "$work/mimirtool" "$release/mimirtool-linux-amd64"
curl -fsSL -o "$work/mimirtool.sha256" "$release/mimirtool-linux-amd64-sha-256"
(cd "$work" && echo "$(cut -d' ' -f1 mimirtool.sha256)  mimirtool" | sha256sum -c --quiet)
chmod +x "$work/mimirtool"

# The rule file in git is plain Prometheus (promtool checks it in CI); Mimir wants a namespace on top.
{ echo "namespace: creditdesk"; curl -fsSL "$RAW/k8s/monitoring/rules/creditdesk.yaml" | grep -v '^#'; } > "$work/creditdesk.yaml"

export MIMIR_ADDRESS="$address" MIMIR_TENANT_ID="$user" MIMIR_API_KEY="$token"
"$work/mimirtool" rules lint "$work/creditdesk.yaml" >/dev/null
echo "Rules in git vs Grafana Cloud ($address, instance $user):"
"$work/mimirtool" rules diff --namespaces creditdesk "$work/creditdesk.yaml" || true
if [[ ${1:-} == --apply ]]; then
  "$work/mimirtool" rules sync --namespaces creditdesk "$work/creditdesk.yaml"
  echo "Synced. Grafana Cloud → Alerting → Alert rules → data source grafanacloud-*-prom, folder creditdesk."
else
  echo "Nothing changed. Run again with --apply to upload."
fi
