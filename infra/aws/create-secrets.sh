#!/bin/bash
# CreditDesk — create the application secrets in SSM Parameter Store (run in AWS CloudShell, us-east-1).
#
# Random values, generated here and sent straight to SSM as SecureString (encrypted with the AWS-managed KMS key
# aws/ssm). They are never printed, never written to disk and never in git. In the cluster, External Secrets
# copies them into the Secret "creditdesk-secrets" (k8s/overlays/aws/secrets.yaml).
#
# Idempotent and safe to re-run: an existing parameter is NEVER overwritten. That matters most for the two
# document keys — CPF/CNPJ are stored encrypted with them, so a new key would make every stored document
# unreadable. Rotating a key is a migration, not a re-run of this script.
set -euo pipefail
export AWS_DEFAULT_REGION="us-east-1"
PREFIX="/creditdesk/app"

# Letters and digits only: KEDA reads the RabbitMQ password from the environment and refuses special characters,
# and the same rule keeps every value safe inside connection strings.
alnum() { openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 32; }

create() {
  local name="$PREFIX/$1" value=$2 error
  # put-parameter without --overwrite fails if it already exists: "create if missing" in one atomic call
  if error=$(aws ssm put-parameter --name "$name" --type SecureString --value="$value" \
      --tags Key=Project,Value=creditdesk 2>&1 >/dev/null); then
    echo "created  $name"
  elif grep -q ParameterAlreadyExists <<<"$error"; then
    echo "kept     $name (already exists)"
  else
    echo "$error" >&2
    exit 1
  fi
}

create DB_USER creditdesk
create DB_PASSWORD "$(alnum)"
create RABBITMQ_USER creditdesk
create RABBITMQ_PASSWORD "$(alnum)"
create KEYCLOAK_ADMIN_PASSWORD "$(alnum)"
create DOCUMENT_ENCRYPTION_KEY "$(openssl rand -base64 32)"   # AES-256-GCM key for CPF/CNPJ
create DOCUMENT_HASH_KEY "$(openssl rand -base64 32)"         # HMAC key for searching CPF/CNPJ

echo
echo "Parameters under $PREFIX (names only):"
aws ssm get-parameters-by-path --path "$PREFIX" --query 'Parameters[].Name' --output text | tr '\t' '\n'
