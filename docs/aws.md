# Running CreditDesk on AWS — runbook

How the demo gets to `https://creditdesk.duckdns.org`, and how to operate it. Everything AWS-side runs in
**AWS CloudShell** (us-east-1), signed in as the IAM admin user — never the root user.

```
git push ──▶ images.yml ──▶ GHCR (amd64 + arm64)
                 └──▶ commit "deploy: images sha-…" to k8s/overlays/aws
                                   │
EC2 (k3s) ── Argo CD polls main ◀──┘──▶ applies k8s/overlays/aws + add-ons (k8s/argocd/apps)
                 External Secrets ◀── SSM Parameter Store (instance role)
                 cert-manager     ◀── Let's Encrypt (HTTP-01 on port 80)
```

## First deployment

1. **Merge the pull request.** `Images` (Actions tab) builds the five images and commits their tag to
   `k8s/overlays/aws/kustomization.yaml`. Wait for it to finish (~15 min the first time).
2. **Make the five packages public** (once): github.com/vanilds?tab=packages → each `creditdesk-*` package →
   *Package settings* → *Change visibility* → Public. The cluster pulls them without credentials; they contain
   no secrets (the repository is public too).
3. **Create the application secrets in SSM** (once; re-running never overwrites):
   ```bash
   curl -fsSLO https://raw.githubusercontent.com/vanilds/vanilds.github.io/main/infra/aws/create-secrets.sh
   bash create-secrets.sh
   ```
4. **Replace the server** (it was bootstrapped for Ingress NGINX; the new one uses Traefik and Argo CD):
   ```bash
   curl -fsSLO https://raw.githubusercontent.com/vanilds/vanilds.github.io/main/infra/aws/recreate-ec2.sh
   bash recreate-ec2.sh      # asks you to type the old instance id before terminating it
   ```
5. **Watch it come up** (~5 min bootstrap + ~10 min for Argo CD to install everything):
   ```bash
   ID=$(aws ec2 describe-instances --filters Name=tag:Project,Values=creditdesk Name=instance-state-name,Values=running \
        --query 'Reservations[0].Instances[0].InstanceId' --output text)
   aws ssm start-session --target "$ID"
   # inside the session:
   sudo k3s kubectl -n argocd get applications          # all Synced / Healthy at the end
   sudo k3s kubectl -n creditdesk get pods,externalsecret,certificate
   ```
6. **Test**: https://creditdesk.duckdns.org (valid certificate, login `ana` / see `api/README.md`), then the
   portfolio's *Try the demo* button at https://vanilds.github.io.

## Day to day

| I want to… | Do this |
|---|---|
| Deploy a change | Merge to `main`. `Images` publishes and commits the tag; Argo CD rolls the cluster within ~3 min. |
| Roll back | Revert the `deploy: images sha-…` commit. Argo CD goes back to the previous images. |
| Start / stop the server | Automatic on weekdays 08:00–20:00 (São Paulo). By hand: Actions → *EC2 start/stop* → *Run workflow*. |
| See what Argo CD sees | SSM session → `sudo k3s kubectl -n argocd get applications` (details: `... describe application creditdesk`). |
| Open the Argo CD UI | Needs the AWS CLI + Session Manager plugin on your computer. Session 1 (CloudShell or local): inside the instance run `sudo k3s kubectl -n argocd port-forward svc/argocd-server 8443:443`. Session 2 (local): `aws ssm start-session --target $ID --document-name AWS-StartPortForwardingSession --parameters portNumber=8443,localPortNumber=8443`, then open https://localhost:8443 (self-signed certificate warning is expected). The UI is deliberately not on the Internet. |
| Get the Argo CD admin password | Inside a session: `sudo k3s kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' \| base64 -d` — never paste it anywhere. |
| Change a secret | `aws ssm put-parameter --name /creditdesk/app/<KEY> --type SecureString --overwrite --value=…` (never the two `DOCUMENT_*` keys), then restart the pods that use it. |
| Use a language model for the opinions | Create `/creditdesk/app/LLM_API_KEY`, add it to `k8s/overlays/aws/secrets.yaml`, set `LLM_BASE_URL` / `LLM_MODEL` in the overlay. |
| Change the Keycloak realm | Edit `infra/keycloak/creditdesk-realm.json` and merge: the realm is re-imported at every start. The admin console is deliberately unreachable from the Internet (403). |
| Start over | `bash recreate-ec2.sh`. The cluster is rebuilt from git; SSM secrets and S3 documents are untouched; the database starts from the demo seed — then restore the latest backup ([runbook](incidents/README.md#restore-the-database-from-a-backup)). |
| Dashboards and alerts (Grafana Cloud), backups, follow a request | [operations.md](operations.md). |
| Something is broken | [incidents/README.md](incidents/README.md) — one runbook per alert. |

## Why it is built this way

- **GitOps.** The only thing applied by hand is one Argo CD Application (by the bootstrap script). Every other
  object is in git, and Argo CD reverts manual changes (`selfHeal`). The cluster can be thrown away and rebuilt.
- **No secrets in git, in CI or on the disk image.** SSM holds them (KMS-encrypted); External Secrets copies them
  into the cluster with the instance role. No AWS access keys exist anywhere.
- **Immutable releases.** Images are tagged `sha-<commit>`; the overlay pins all five to the same tag.
- **Fits in 4 GB.** Argo CD without Dex, notifications and ApplicationSet; External Secrets and KEDA without
  their validating webhooks; smaller memory requests for the apps. 2 GB of swap is the safety net.
- **Pods and the instance role.** Every pod can reach the metadata service (hop limit 2), so every pod could use
  the role. Acceptable here — one app, a narrow role (two buckets, `/creditdesk/*` in SSM). On EKS this would be
  IRSA / Pod Identity, one role per service account.
