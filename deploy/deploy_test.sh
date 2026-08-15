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

sleep() { :; }
ssh() {
  local command="${*: -1}"
  if [[ "$command" == *"podman ps"* ]]; then
    echo "rocketmq-server Up"
  elif [[ "$command" == *"actuator/health"* ]]; then
    return "${SERVER_HEALTH_EXIT:-0}"
  elif [[ "$command" == *"curl -sf -o"* ]]; then
    printf '%s' "${WEB_HTTP_CODE:-200}"
  fi
}

TARGET=server
SERVER_HEALTH_EXIT=0
verify >/dev/null

if (SERVER_HEALTH_EXIT=1; verify >/dev/null 2>&1); then
  echo "expected failed server health check to fail deployment verification" >&2
  exit 1
fi

TARGET=web
WEB_HTTP_CODE=200
verify >/dev/null

if (WEB_HTTP_CODE=503; verify >/dev/null 2>&1); then
  echo "expected non-200 web response to fail deployment verification" >&2
  exit 1
fi

echo "deploy configuration validation passed"
