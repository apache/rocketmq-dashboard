#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Provide the minimum target configuration needed to source the script.
REMOTE_HOST=test-host
# shellcheck disable=SC1091
source "$SCRIPT_DIR/deploy.sh"

if (unset SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD
    validate_server_config >/dev/null 2>&1); then
  echo "expected missing datasource configuration to fail" >&2
  exit 1
fi

SPRING_DATASOURCE_URL=jdbc:mysql://db:3306/rocketmq
SPRING_DATASOURCE_USERNAME=rocketmq
SPRING_DATASOURCE_PASSWORD=secret
validate_server_config

echo "deploy configuration validation passed"
