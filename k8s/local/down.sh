#!/usr/bin/env bash
# Deletes the local cluster and everything in it (database, queues, documents). The generated secrets in
# k8s/overlays/local/secrets.env are kept, so the next up.sh reuses them.
set -euo pipefail
kind delete cluster --name creditdesk
