#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/rocketmq-deploy-test.XXXXXX")"
BIN_DIR="$TEST_ROOT/bin"
STATE_DIR="$TEST_ROOT/state"
LOG_DIR="$TEST_ROOT/log"
mkdir -p "$BIN_DIR" "$STATE_DIR" "$LOG_DIR"
first_pid=""

cleanup() {
  if [[ -n "$first_pid" ]] && kill -0 "$first_pid" 2>/dev/null; then
    : > "$TEST_ROOT/hold.release"
    wait "$first_pid" || true
  fi
  rm -R "$TEST_ROOT"
}
trap cleanup EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

cat > "$BIN_DIR/ssh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

remote=""
command=""
while (($#)); do
  case "$1" in
    -o) shift 2 ;;
    *@*) remote="$1"; shift; command="$*"; break ;;
    *) shift ;;
  esac
done

lock_path="$(printf '%s' "$command" | sed -n "s/.*'\([^']*\.rocketmq-studio-deploy.lock\)'.*/\1/p")"
if [[ -n "$lock_path" ]]; then
  lock_key="$(printf '%s:%s' "$remote" "$lock_path" | tr '/:@' '____')"
  lock_dir="$STUB_STATE_DIR/$lock_key"
  case "$command" in
    mkdir\ *) mkdir "$lock_dir" ;;
    *"rmdir "*) rm -f "$lock_dir/pid"; rmdir "$lock_dir" ;;
    *"printf "*) printf '%s\n' owner > "$lock_dir/pid" ;;
  esac
  exit
fi

if [[ "$command" == *"mkdir -p"* && -n "${STUB_HOLD_FILE:-}" ]]; then
  : > "$STUB_HOLD_FILE.ready"
  while [[ ! -f "$STUB_HOLD_FILE.release" ]]; do /bin/sleep 0.02; done
fi
if [[ "$command" == *"actuator/health"* ]]; then
  printf '%s\n' '{"status":"UP"}'
fi
EOF

cat > "$BIN_DIR/tar" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
archive="$2"
printf '%s\n' "$archive" >> "$STUB_LOG_DIR/tar-paths"
: > "$archive"
EOF

cat > "$BIN_DIR/scp" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
[[ "${STUB_FAIL_SCP:-0}" == "0" ]] || exit 42
EOF

cat > "$BIN_DIR/curl" <<'EOF'
#!/usr/bin/env bash
printf 200
EOF

chmod +x "$BIN_DIR/ssh" "$BIN_DIR/tar" "$BIN_DIR/scp" "$BIN_DIR/curl"

run_deploy() {
  env PATH="$BIN_DIR:$PATH" \
    STUB_STATE_DIR="$STATE_DIR" STUB_LOG_DIR="$LOG_DIR" \
    REMOTE_HOST="$1" REMOTE_PATH="$2" \
    "$SCRIPT_DIR/deploy.sh" server
}

hold_file="$TEST_ROOT/hold"
env PATH="$BIN_DIR:$PATH" \
  STUB_STATE_DIR="$STATE_DIR" STUB_LOG_DIR="$LOG_DIR" STUB_HOLD_FILE="$hold_file" \
  REMOTE_HOST=host-a REMOTE_PATH=/opt/studio \
  "$SCRIPT_DIR/deploy.sh" server > "$LOG_DIR/first" 2>&1 &
first_pid=$!

for _ in $(seq 1 500); do
  [[ -f "$hold_file.ready" ]] && break
  /bin/sleep 0.02
done
[[ -f "$hold_file.ready" ]] || fail "first deployment did not reach the protected section"

if run_deploy host-a /opt/studio > "$LOG_DIR/same-target" 2>&1; then
  fail "a concurrent deployment to the same remote target was not rejected"
fi
if ! grep -q "已有部署正在操作" "$LOG_DIR/same-target"; then
  sed -n '1,120p' "$LOG_DIR/same-target" >&2
  fail "lock contention did not report a useful error"
fi

if ! run_deploy host-a /opt/studio-b > "$LOG_DIR/different-target" 2>&1; then
  sed -n '1,160p' "$LOG_DIR/different-target" >&2
  fail "a different remote path was incorrectly blocked"
fi

: > "$hold_file.release"
wait "$first_pid" || fail "first deployment did not finish successfully"

if STUB_FAIL_SCP=1 run_deploy host-a /opt/failing > "$LOG_DIR/failing" 2>&1; then
  fail "stubbed upload failure unexpectedly succeeded"
fi
run_deploy host-a /opt/failing > "$LOG_DIR/after-failure" 2>&1 \
  || fail "the remote lock was not released after a failure"

[[ "$(sort -u "$LOG_DIR/tar-paths" | wc -l | tr -d ' ')" == "4" ]] \
  || fail "deployments did not use unique source archives"
while IFS= read -r archive; do
  [[ ! -e "$archive" ]] || fail "temporary archive was not removed: $archive"
done < "$LOG_DIR/tar-paths"

[[ -z "$(find "$STATE_DIR" -mindepth 1 -print -quit)" ]] \
  || fail "a remote lock remained after deployment"

echo "PASS: deploy concurrency and cleanup"
