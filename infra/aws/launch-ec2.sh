#!/bin/bash
# CreditDesk — launch the demo server (run in AWS CloudShell, us-east-1, after setup.sh).
# Ubuntu 24.04 arm64 on t4g.medium, IMDSv2 with hop limit 2, no key pair (access via SSM),
# bootstrapped by user-data.sh (swap, k3s, Ingress NGINX, cert-manager, DuckDNS updater).
set -euo pipefail
export AWS_DEFAULT_REGION="us-east-1"
RAW="https://raw.githubusercontent.com/vanilds/vanilds.github.io/main/infra/aws"

EXISTING=$(aws ec2 describe-instances \
  --filters Name=tag:Project,Values=creditdesk Name=instance-state-name,Values=pending,running,stopping,stopped \
  --query 'Reservations[].Instances[].InstanceId' --output text)
if [ -n "$EXISTING" ]; then
  echo "A CreditDesk instance already exists: $EXISTING (start it instead of launching a new one)"
  exit 0
fi

AMI=$(aws ssm get-parameter \
  --name /aws/service/canonical/ubuntu/server/24.04/stable/current/arm64/hvm/ebs-gp3/ami-id \
  --query Parameter.Value --output text)
SG_ID=$(aws ec2 describe-security-groups --filters Name=group-name,Values=creditdesk-web \
  --query 'SecurityGroups[0].GroupId' --output text)
curl -fsSL "$RAW/user-data.sh" -o /tmp/creditdesk-user-data.sh

INSTANCE_ID=$(aws ec2 run-instances \
  --image-id "$AMI" \
  --instance-type t4g.medium \
  --iam-instance-profile Name=creditdesk-ec2-role \
  --security-group-ids "$SG_ID" \
  --metadata-options HttpTokens=required,HttpPutResponseHopLimit=2,HttpEndpoint=enabled \
  --block-device-mappings '[{"DeviceName":"/dev/sda1","Ebs":{"VolumeSize":30,"VolumeType":"gp3","DeleteOnTermination":true}}]' \
  --user-data file:///tmp/creditdesk-user-data.sh \
  --tag-specifications \
    'ResourceType=instance,Tags=[{Key=Name,Value=creditdesk-demo},{Key=Project,Value=creditdesk}]' \
    'ResourceType=volume,Tags=[{Key=Name,Value=creditdesk-demo},{Key=Project,Value=creditdesk}]' \
  --query 'Instances[0].InstanceId' --output text)

echo "Launched $INSTANCE_ID (AMI $AMI). Waiting until it is running..."
aws ec2 wait instance-running --instance-ids "$INSTANCE_ID"
aws ec2 describe-instances --instance-ids "$INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].{id:InstanceId,state:State.Name,ip:PublicIpAddress,type:InstanceType}' --output table
echo "Repo variable EC2_INSTANCE_ID = $INSTANCE_ID"
echo "Bootstrap takes ~5 minutes. Then connect with: aws ssm start-session --target $INSTANCE_ID"
