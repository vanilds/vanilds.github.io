#!/bin/bash
# CreditDesk — replace the demo server with a fresh one (run in AWS CloudShell, us-east-1).
#
# The server holds nothing that is not rebuilt from git or kept elsewhere: the cluster comes from Argo CD,
# documents are in S3, secrets in SSM. Only the PostgreSQL data on the instance's disk is lost (the demo seed is
# recreated at startup; real backups to S3 come in Phase 7). So the fix for a broken or outdated server is the
# same as the first install: terminate it and launch another with the current user-data.sh.
#
# Usage: curl -fsSLO https://raw.githubusercontent.com/vanilds/vanilds.github.io/main/infra/aws/recreate-ec2.sh
#        bash recreate-ec2.sh
set -euo pipefail
export AWS_DEFAULT_REGION="us-east-1"
RAW="https://raw.githubusercontent.com/vanilds/vanilds.github.io/main/infra/aws"

read -ra IDS <<<"$(aws ec2 describe-instances \
  --filters Name=tag:Project,Values=creditdesk Name=instance-state-name,Values=pending,running,stopping,stopped \
  --query 'Reservations[].Instances[].InstanceId' --output text)"

if [ "${#IDS[@]}" -gt 1 ]; then
  echo "More than one CreditDesk instance exists (${IDS[*]}). Terminate the extra ones in the console first."
  exit 1
fi

if [ "${#IDS[@]}" = 1 ]; then
  aws ec2 describe-instances --instance-ids "${IDS[0]}" \
    --query 'Reservations[].Instances[].{id:InstanceId,state:State.Name,launched:LaunchTime,type:InstanceType}' \
    --output table
  # Terminating cannot be undone: ask for the id itself, not a "y"
  read -rp "Type the instance id to terminate it (anything else cancels): " ANSWER
  if [ "$ANSWER" != "${IDS[0]}" ]; then echo "Cancelled. Nothing was changed."; exit 1; fi
  aws ec2 terminate-instances --instance-ids "${IDS[0]}" --output table
  echo "Waiting until it is terminated..."
  aws ec2 wait instance-terminated --instance-ids "${IDS[0]}"
fi

curl -fsSL "$RAW/launch-ec2.sh" -o /tmp/creditdesk-launch-ec2.sh
bash /tmp/creditdesk-launch-ec2.sh
