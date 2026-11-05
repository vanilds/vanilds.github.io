#!/bin/bash
# CreditDesk — EC2 bootstrap (Ubuntu 24.04 LTS, arm64, t4g.medium)
# Paste this into "Advanced details → User data" when launching the instance.
# It runs once, on the first boot. Logs: /var/log/cloud-init-output.log
set -euxo pipefail

# ---- settings (edit if you changed names) ---------------------------------
DUCKDNS_SUBDOMAIN="creditdesk"            # creditdesk.duckdns.org
SSM_PREFIX="/creditdesk"                  # SSM Parameter Store prefix
REGION="us-east-1"

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

# ---- 4. k3s (single node). Traefik is disabled: we use Ingress NGINX -----------
curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--disable traefik --write-kubeconfig-mode 600" sh -

# ---- 5. cluster add-ons, installed by k3s' built-in Helm controller -----------
mkdir -p /var/lib/rancher/k3s/server/manifests

cat > /var/lib/rancher/k3s/server/manifests/ingress-nginx.yaml <<'EOF'
apiVersion: helm.cattle.io/v1
kind: HelmChart
metadata:
  name: ingress-nginx
  namespace: kube-system
spec:
  repo: https://kubernetes.github.io/ingress-nginx
  chart: ingress-nginx
  targetNamespace: ingress-nginx
  createNamespace: true
  valuesContent: |-
    controller:
      service:
        type: LoadBalancer        # k3s ServiceLB binds it to ports 80/443 on the node
      resources:
        requests: { cpu: 50m, memory: 96Mi }
        limits: { memory: 192Mi }
EOF

cat > /var/lib/rancher/k3s/server/manifests/cert-manager.yaml <<'EOF'
apiVersion: helm.cattle.io/v1
kind: HelmChart
metadata:
  name: cert-manager
  namespace: kube-system
spec:
  repo: https://charts.jetstack.io
  chart: cert-manager
  targetNamespace: cert-manager
  createNamespace: true
  valuesContent: |-
    crds:
      enabled: true
    resources:
      requests: { cpu: 10m, memory: 48Mi }
      limits: { memory: 128Mi }
EOF

echo "Bootstrap finished. Check with: sudo k3s kubectl get pods -A"
