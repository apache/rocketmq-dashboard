#!/usr/bin/env bash

# Lightweight lifecycle regression for native-alert-e2e.sh. It intentionally fails at the
# missing-jar preflight, after temporary artifacts and traps have been initialized.
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
SUBJECT="$SCRIPT_DIR/native-alert-e2e.sh"
TEST_TMP=$(mktemp -d "${TMPDIR:-/tmp}/native-alert-e2e-test.XXXXXX")
BIN_DIR="$TEST_TMP/bin"
mkdir "$BIN_DIR"

cleanup() {
  for command in curl jq mysql java; do
    [[ ! -e "$BIN_DIR/$command" ]] || unlink "$BIN_DIR/$command"
  done
  rmdir "$BIN_DIR" 2>/dev/null || true
  rmdir "$TEST_TMP" 2>/dev/null || true
}
trap cleanup EXIT

for command in curl jq mysql java; do
  printf '#!/usr/bin/env sh\nexit 0\n' >"$BIN_DIR/$command"
  chmod +x "$BIN_DIR/$command"
done

run_subject() {
  env \
    PATH="$BIN_DIR:/usr/bin:/bin" \
    TMPDIR="$TEST_TMP" \
    E2E_DB_JDBC_URL=jdbc:mysql://127.0.0.1/test \
    E2E_MYSQL_DATABASE=test \
    E2E_ADMIN_USERNAME=admin \
    E2E_ADMIN_PASSWORD=password \
    E2E_NAMESRV_ADDR=127.0.0.1:9876 \
    E2E_WEBHOOK_URL=http://receiver.invalid/hook \
    E2E_WEBHOOK_ASSERT_URL=http://receiver.invalid/assert \
    E2E_EMAIL_RECIPIENT=test@example.com \
    E2E_SMTP_HOST=127.0.0.1 \
    E2E_STUDIO_JAR="$TEST_TMP/missing.jar" \
    E2E_KEEP_ARTIFACTS="$1" \
    E2E_PORT="${2:-18083}" \
    bash "$SUBJECT" >/dev/null 2>&1
}

set +e
run_subject false 0
status=$?
set -e
[[ "$status" -eq 2 ]] || { echo "Expected invalid port exit 2, got $status" >&2; exit 1; }
if compgen -G "$TEST_TMP/rocketmq-studio-e2e.*" >/dev/null; then
  echo 'Argument validation created temporary artifacts' >&2
  exit 1
fi

set +e
run_subject false
status=$?
set -e
[[ "$status" -eq 2 ]] || { echo "Expected missing jar exit 2, got $status" >&2; exit 1; }
if compgen -G "$TEST_TMP/rocketmq-studio-e2e.*" >/dev/null; then
  echo 'Default cleanup left temporary artifacts behind' >&2
  exit 1
fi

set +e
run_subject true
status=$?
set -e
[[ "$status" -eq 2 ]] || { echo "Expected retained missing jar exit 2, got $status" >&2; exit 1; }
shopt -s nullglob
artifacts=("$TEST_TMP"/rocketmq-studio-e2e.*)
shopt -u nullglob
[[ "${#artifacts[@]}" -eq 2 ]] || { echo 'Artifact retention did not keep both paths' >&2; exit 1; }
for artifact in "${artifacts[@]}"; do
  if [[ -d "$artifact" ]]; then
    rmdir "$artifact"
  else
    unlink "$artifact"
  fi
done

echo 'native-alert-e2e lifecycle checks passed'
