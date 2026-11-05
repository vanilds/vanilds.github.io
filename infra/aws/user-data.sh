#!/bin/bash
# CreditDesk — EC2 bootstrap (Ubuntu 24.04 LTS, arm64, t4g.medium). Passed as user data by launch-ec2.sh.
# It runs once, on the first boot, and only prepares the machine: swap, DuckDNS, k3s and Argo CD. Everything that
# runs IN the cluster comes from git through Argo CD (k8s/argocd). Logs: /var/log/cloud-init-output.log
set -euxo pipefail

# ---- settings (edit if you changed names) ---------------------------------
DUCKDNS_SUBDOMAIN="creditdesk"            # creditdesk.duckdns.org
SSM_PREFIX="/creditdesk"                  # SSM Parameter Store prefix
REGION="us-east-1"
REPO_RAW="https://raw.githubusercontent.com/vanilds/vanilds.github.io/main"
K3S_VERSION="v1.34.13+k3s1"

# ---- 1. swap: 4 GB of RAM is tight, 2 GB of swap is the safety net ----------
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
  sysctl -w vm.swappiness=10
  echo 'vm.swappiness=10' > /etc/sysctl.d/99-swappiness.conf
fi

# ---- 2. base tools ------------------------------------------------------------
apt-get update -y
apt-get install -y curl jq unzip
snap install aws-cli --classic

# ---- 3. DuckDNS: point the domain at this boot's public IP ---------------------
# The token lives in SSM (SecureString) and is read with the instance role — no keys on disk.
cat > /usr/local/bin/duckdns-update.sh <<EOF
#!/bin/bash
set -euo pipefail
IMDS_TOKEN=\$(curl -sX PUT http://169.254.169.254/latest/api/token -H "X-aws-ec2-metadata-token-ttl-seconds: 300")
PUBLIC_IP=\$(curl -s -H "X-aws-ec2-metadata-token: \$IMDS_TOKEN" http://169.254.169.254/latest/meta-data/public-ipv4)
TOKEN=\$(/snap/bin/aws ssm get-parameter --region ${REGION} --name ${SSM_PREFIX}/duckdns-token --with-decryption --query Parameter.Value --output text)
RESULT=\$(curl -s "https://www.duckdns.org/update?domains=${DUCKDNS_SUBDOMAIN}&token=\$TOKEN&ip=\$PUBLIC_IP")
echo "duckdns: \$PUBLIC_IP -> \$RESULT"
[ "\$RESULT" = "OK" ]
EOF
chmod 700 /usr/local/bin/duckdns-update.sh

cat > /etc/systemd/system/duckdns-update.service <<'EOF'
[Unit]
Description=Update DuckDNS with this instance's public IP
Wants=network-online.target
After=network-online.target

[Service]
Type=oneshot
ExecStart=/usr/local/bin/duckdns-update.sh
Restart=on-failure
RestartSec=15

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now duckdns-update.service

# ---- 4. k3s (single node), with its built-in Traefik as the Ingress controller -----------------------------
# Pinned to the Kubernetes minor the manifests are validated against (kubeconform and kind in CI use 1.34).
curl -sfL https://get.k3s.io | INSTALL_K3S_VERSION="${K3S_VERSION}" INSTALL_K3S_EXEC="--write-kubeconfig-mode 600" sh -

# ---- 5. Argo CD, then hand the cluster over to git ---------------------------------------------------------
# k3s installs Argo CD from this HelmChart file (its own Helm controller). After that, ONE Application ("root")
# points Argo CD at k8s/argocd/apps, and Argo CD installs everything else from git: cert-manager, External
# Secrets, KEDA, the Let's Encrypt issuers and CreditDesk. This script never needs to change for that.
# The installer returns as soon as the k3s service is started; the service creates this folder a moment later.
# Create it ourselves, or the download races k3s and fails ("curl: (23) Failure writing output").
mkdir -p /var/lib/rancher/k3s/server/manifests
curl -fsSL "${REPO_RAW}/infra/aws/k3s/argocd.yaml" -o /var/lib/rancher/k3s/server/manifests/argocd.yaml

for _ in $(seq 1 90); do
  k3s kubectl get crd applications.argoproj.io >/dev/null 2>&1 && break
  sleep 10
done
k3s kubectl wait --for condition=established --timeout 120s crd/applications.argoproj.io
k3s kubectl apply -f "${REPO_RAW}/k8s/argocd/root.yaml"

echo "Bootstrap finished. Argo CD is now installing the rest from git; follow it with:"
echo "  sudo k3s kubectl -n argocd get applications"
