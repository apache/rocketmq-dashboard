#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

export REMOTE_HOST="example.test"
export SPRING_DATASOURCE_URL="jdbc:mysql://db.example.test:3306/rocketmq"
export SPRING_DATASOURCE_USERNAME="rocketmq"
export SPRING_DATASOURCE_PASSWORD="secret"
export STUDIO_ROCKETMQ_NAMESRV_ADDR="nameserver.example.test:9876"

# shellcheck source=deploy.sh
source "$SCRIPT_DIR/deploy.sh"

if (
  TARGET=server
  unset SPRING_DATASOURCE_URL
  validate_config
) >/dev/null 2>&1; then
  echo "expected server deployment validation to reject a missing datasource URL" >&2
  exit 1
fi

(
  TARGET=web
  unset SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD
  validate_config
)

capture_file="$(mktemp)"
trap 'rm -f "$capture_file"' EXIT

ssh() {
  printf '%s\n' "$*" >> "$capture_file"
}

TARGET=server
REMOTE="root@example.test"
REMOTE_PATH="/opt/rocketmq-studio"
NETWORK="rocketmq-studio"
deploy_remote >/dev/null

grep -F 'SPRING_PROFILES_ACTIVE="prod"' "$capture_file" >/dev/null
grep -F 'SPRING_DATASOURCE_URL="jdbc:mysql://db.example.test:3306/rocketmq"' "$capture_file" >/dev/null
grep -F 'SPRING_DATASOURCE_USERNAME="rocketmq"' "$capture_file" >/dev/null
grep -F 'SPRING_DATASOURCE_PASSWORD="secret"' "$capture_file" >/dev/null
grep -F 'STUDIO_ROCKETMQ_NAMESRV_ADDR="nameserver.example.test:9876"' "$capture_file" >/dev/null

echo "deploy.sh tests passed"
