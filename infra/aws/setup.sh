#!/bin/bash
# CreditDesk — one-time AWS setup (run in AWS CloudShell, region us-east-1).
# Safe to re-run: every step checks whether the resource already exists.
#
# usage:
#   ALERT_EMAIL=you@example.com bash setup.sh
#
# Creates: budget with alerts, 2 private S3 buckets, EC2 IAM role + instance profile,
# security group (80/443, no SSH), GitHub OIDC provider and the start/stop role.
set -euo pipefail

: "${ALERT_EMAIL:?set ALERT_EMAIL to the address that should receive budget alerts}"
REGION="us-east-1"
GITHUB_REPO="vanilds/vanilds.github.io"
DOCS_BUCKET="creditdesk-documents-vanilds"
BACKUPS_BUCKET="creditdesk-backups-vanilds"
EC2_ROLE="creditdesk-ec2-role"
GH_ROLE="creditdesk-github-actions"
SG_NAME="creditdesk-web"
RAW="https://raw.githubusercontent.com/${GITHUB_REPO}/main/infra/aws"

export AWS_DEFAULT_REGION="$REGION"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
WORK=$(mktemp -d)
echo "Account: $ACCOUNT_ID · Region: $REGION · Workdir: $WORK"

fetch() { curl -fsSL "$RAW/$1" | sed "s/ACCOUNT_ID/${ACCOUNT_ID}/g" > "$WORK/$(basename "$1")"; }
fetch iam/ec2-trust-policy.json
fetch iam/ec2-app-policy.json
fetch iam/github-oidc-trust-policy.json
fetch iam/github-ec2-startstop-policy.json
fetch s3/cors-documents.json
fetch s3/lifecycle-backups.json

# ---- 1. Budget ---------------------------------------------------------------
echo "== Budget"
if aws budgets describe-budget --account-id "$ACCOUNT_ID" --budget-name creditdesk-monthly >/dev/null 2>&1; then
  echo "budget already exists"
else
  cat > "$WORK/budget.json" <<EOF
{
  "BudgetName": "creditdesk-monthly",
  "BudgetLimit": { "Amount": "100", "Unit": "USD" },
  "TimeUnit": "MONTHLY",
  "BudgetType": "COST",
  "CostTypes": {
    "IncludeTax": true, "IncludeSubscription": true, "UseBlended": false,
    "IncludeRefund": false, "IncludeCredit": false, "IncludeUpfront": true,
    "IncludeRecurring": true, "IncludeOtherSubscription": true,
    "IncludeSupport": true, "IncludeDiscount": true, "UseAmortized": false
  }
}
EOF
  notif() { echo "{\"Notification\":{\"NotificationType\":\"ACTUAL\",\"ComparisonOperator\":\"GREATER_THAN\",\"Threshold\":$1,\"ThresholdType\":\"PERCENTAGE\"},\"Subscribers\":[{\"SubscriptionType\":\"EMAIL\",\"Address\":\"$ALERT_EMAIL\"}]}"; }
  echo "[$(notif 10),$(notif 50),$(notif 80)]" > "$WORK/notifications.json"
  aws budgets create-budget --account-id "$ACCOUNT_ID" \
    --budget "file://$WORK/budget.json" \
    --notifications-with-subscribers "file://$WORK/notifications.json"
  echo "budget created (credits excluded, alerts at 10/50/80%)"
fi

# ---- 2. S3 buckets -------------------------------------------------------------
echo "== S3"
make_bucket() {
  if aws s3api head-bucket --bucket "$1" >/dev/null 2>&1; then
    echo "bucket $1 already exists"
  else
    aws s3api create-bucket --bucket "$1" >/dev/null
    echo "bucket $1 created"
  fi
  aws s3api put-public-access-block --bucket "$1" --public-access-block-configuration \
    BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
  aws s3api put-bucket-encryption --bucket "$1" --server-side-encryption-configuration \
    '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
  aws s3api put-bucket-tagging --bucket "$1" --tagging 'TagSet=[{Key=Project,Value=creditdesk}]'
}
make_bucket "$DOCS_BUCKET"
aws s3api put-bucket-versioning --bucket "$DOCS_BUCKET" --versioning-configuration Status=Enabled
aws s3api put-bucket-cors --bucket "$DOCS_BUCKET" --cors-configuration "file://$WORK/cors-documents.json"
make_bucket "$BACKUPS_BUCKET"
aws s3api put-bucket-lifecycle-configuration --bucket "$BACKUPS_BUCKET" --lifecycle-configuration "file://$WORK/lifecycle-backups.json"

# ---- 3. EC2 role + instance profile ---------------------------------------------
echo "== IAM: EC2 role"
if ! aws iam get-role --role-name "$EC2_ROLE" >/dev/null 2>&1; then
  aws iam create-role --role-name "$EC2_ROLE" \
    --assume-role-policy-document "file://$WORK/ec2-trust-policy.json" \
    --tags Key=Project,Value=creditdesk >/dev/null
  echo "role $EC2_ROLE created"
fi
aws iam attach-role-policy --role-name "$EC2_ROLE" \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore
aws iam put-role-policy --role-name "$EC2_ROLE" --policy-name creditdesk-app-access \
  --policy-document "file://$WORK/ec2-app-policy.json"
if ! aws iam get-instance-profile --instance-profile-name "$EC2_ROLE" >/dev/null 2>&1; then
  aws iam create-instance-profile --instance-profile-name "$EC2_ROLE" >/dev/null
  aws iam add-role-to-instance-profile --instance-profile-name "$EC2_ROLE" --role-name "$EC2_ROLE"
  echo "instance profile $EC2_ROLE created"
fi

# ---- 4. Security group -----------------------------------------------------------
echo "== Security group"
VPC_ID=$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true --query 'Vpcs[0].VpcId' --output text)
if [ "$VPC_ID" = "None" ]; then
  echo "No default VPC in $REGION. Create one with: aws ec2 create-default-vpc" >&2
  exit 1
fi
SG_ID=$(aws ec2 describe-security-groups --filters Name=group-name,Values="$SG_NAME" Name=vpc-id,Values="$VPC_ID" \
  --query 'SecurityGroups[0].GroupId' --output text)
if [ "$SG_ID" = "None" ]; then
  SG_ID=$(aws ec2 create-security-group --group-name "$SG_NAME" --vpc-id "$VPC_ID" \
    --description "CreditDesk demo: HTTP and HTTPS only, no SSH" \
    --tag-specifications 'ResourceType=security-group,Tags=[{Key=Project,Value=creditdesk}]' \
    --query GroupId --output text)
  for port in 80 443; do
    aws ec2 authorize-security-group-ingress --group-id "$SG_ID" \
      --ip-permissions "IpProtocol=tcp,FromPort=$port,ToPort=$port,IpRanges=[{CidrIp=0.0.0.0/0}],Ipv6Ranges=[{CidrIpv6=::/0}]" >/dev/null
  done
  echo "security group $SG_ID created"
else
  echo "security group $SG_ID already exists"
fi

# ---- 5. GitHub OIDC provider + start/stop role ----------------------------------------
echo "== GitHub OIDC"
OIDC_ARN="arn:aws:iam::${ACCOUNT_ID}:oidc-provider/token.actions.githubusercontent.com"
if ! aws iam get-open-id-connect-provider --open-id-connect-provider-arn "$OIDC_ARN" >/dev/null 2>&1; then
  aws iam create-open-id-connect-provider \
    --url https://token.actions.githubusercontent.com \
    --client-id-list sts.amazonaws.com \
    --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1 >/dev/null
  echo "OIDC provider created"
fi
if ! aws iam get-role --role-name "$GH_ROLE" >/dev/null 2>&1; then
  aws iam create-role --role-name "$GH_ROLE" \
    --assume-role-policy-document "file://$WORK/github-oidc-trust-policy.json" \
    --tags Key=Project,Value=creditdesk >/dev/null
  echo "role $GH_ROLE created"
else
  aws iam update-assume-role-policy --role-name "$GH_ROLE" \
    --policy-document "file://$WORK/github-oidc-trust-policy.json"
fi
aws iam put-role-policy --role-name "$GH_ROLE" --policy-name ec2-start-stop \
  --policy-document "file://$WORK/github-ec2-startstop-policy.json"

GH_ROLE_ARN=$(aws iam get-role --role-name "$GH_ROLE" --query Role.Arn --output text)
echo
echo "================ DONE ================"
echo "Security group : $SG_ID"
echo "EC2 profile    : $EC2_ROLE"
echo "GitHub role    : $GH_ROLE_ARN   (repo variable AWS_ROLE_ARN)"
