#!/bin/bash
# CreditDesk — follow one request through the whole system by its correlation id (run in AWS CloudShell).
#
#   bash trace.sh 3f2a9c1e-…            # last 24 h
#   bash trace.sh 3f2a9c1e-… 72h
#
# Where the id comes from: every API response carries it in the X-Correlation-Id header (browser DevTools →
# Network → the request → Response headers), and every log line and audit record carries the same id.
# What it shows, in order: the API's log lines, the AI worker's log lines, the outbox event the request wrote,
# and the audit log rows — browser → API → Postgres → RabbitMQ → worker → API again.
#
# Read-only. It runs infra/aws/ops/trace-node.sh on the instance through SSM Run Command (no SSH, no open port).
set -euo pipefail
export AWS_DEFAULT_REGION="us-east-1"

id=${1:-}
since=${2:-24h}
# Strict input check: the id ends up in a remote command line, so only the characters an id can have
[[ $id =~ ^[A-Za-z0-9-]{8,64}$ ]] || { echo "usage: bash trace.sh <correlation-id> [since, e.g. 24h]"; exit 2; }
[[ $since =~ ^[0-9]{1,3}[smh]$ ]] || { echo "since must look like 30m, 24h or 72h"; exit 2; }

instance=$(aws ec2 describe-instances \
  --filters Name=tag:Project,Values=creditdesk Name=instance-state-name,Values=running \
  --query 'Reservations[].Instances[].InstanceId' --output text)
[[ -n $instance ]] || { echo "The EC2 is not running (it runs weekdays 08:00–20:00 São Paulo)."; exit 1; }

script="https://raw.githubusercontent.com/vanilds/vanilds.github.io/main/infra/aws/ops/trace-node.sh"
command_id=$(aws ssm send-command --instance-ids "$instance" --document-name AWS-RunShellScript \
  --comment "creditdesk trace $id" \
  --parameters "commands=[\"curl -fsSL $script | bash -s -- $id $since\"]" \
  --query Command.CommandId --output text)

# Wait for the command to finish, then print what the instance printed
for _ in $(seq 1 30); do
  status=$(aws ssm get-command-invocation --command-id "$command_id" --instance-id "$instance" \
    --query Status --output text 2>/dev/null || echo Pending)
  [[ $status == Pending || $status == InProgress || $status == Delayed ]] || break
  sleep 2
done
aws ssm get-command-invocation --command-id "$command_id" --instance-id "$instance" \
  --query StandardOutputContent --output text
