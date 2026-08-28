#!/usr/bin/env bash

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/rocketmq-deploy-test.XXXXXX")"
PROJECT_ROOT="$TEST_ROOT/project"
BIN_DIR="$TEST_ROOT/bin"
STATE_DIR="$TEST_ROOT/state"
LOG_DIR="$TEST_ROOT/log"
first_pid=""
mkdir -p "$PROJECT_ROOT/deploy" "$BIN_DIR" "$STATE_DIR" "$LOG_DIR"
cp "$SCRIPT_DIR/deploy.sh" "$PROJECT_ROOT/deploy/deploy.sh"

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

cat > "$PROJECT_ROOT/deploy/.env" <<'EOF'
REMOTE_HOST=${TEST_REMOTE_HOST:?}
REMOTE_USER=deployer
REMOTE_PATH=${TEST_REMOTE_PATH:?}
PUBLIC_PORT=${TEST_PUBLIC_PORT:-18080}
MAVEN_CACHE_DIR=${TEST_MAVEN_CACHE_DIR-}
MAVEN_IMAGE=maven:test
EOF

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
printf '%s\t%s\n' "$remote" "$command" >> "$STUB_LOG_DIR/ssh-commands"

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

if [[ "$command" == *"rm -rf server web deploy"* && -n "${STUB_HOLD_FILE:-}" ]]; then
  : > "$STUB_HOLD_FILE.ready"
  while [[ ! -f "$STUB_HOLD_FILE.release" ]]; do /bin/sleep 0.02; done
fi
if [[ "$command" == *"actuator/health"* ]]; then
  printf '%s\n' '{"status":"UP"}'
fi
if [[ "$command" == 'printf %s "$HOME/.m2"' ]]; then
  printf '%s' '/home/deployer/.m2'
fi
EOF

cat > "$BIN_DIR/tar" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
archive="$2"
printf '%s\n' "$archive" >> "$STUB_LOG_DIR/tar-paths"
printf '%s\n' "$*" >> "$STUB_LOG_DIR/tar-commands"
: > "$archive"
EOF

cat > "$BIN_DIR/scp" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$STUB_LOG_DIR/scp-commands"
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
    TEST_REMOTE_HOST=host-a TEST_REMOTE_PATH="$1" \
    TEST_MAVEN_CACHE_DIR="${2-/srv/maven cache}" TEST_PUBLIC_PORT=18080 \
    "$PROJECT_ROOT/deploy/deploy.sh" all
}

hold_file="$TEST_ROOT/hold"
env PATH="$BIN_DIR:$PATH" \
  STUB_STATE_DIR="$STATE_DIR" STUB_LOG_DIR="$LOG_DIR" STUB_HOLD_FILE="$hold_file" \
  TEST_REMOTE_HOST=host-a TEST_REMOTE_PATH=/opt/studio \
  TEST_MAVEN_CACHE_DIR="/srv/maven cache" TEST_PUBLIC_PORT=18080 \
  "$PROJECT_ROOT/deploy/deploy.sh" all > "$LOG_DIR/first" 2>&1 &
first_pid=$!

for _ in $(seq 1 500); do
  [[ -f "$hold_file.ready" ]] && break
  /bin/sleep 0.02
done
[[ -f "$hold_file.ready" ]] || fail "first deployment did not reach the protected section"

if run_deploy /opt/studio > "$LOG_DIR/same-target" 2>&1; then
  fail "a concurrent deployment to the same remote target was not rejected"
fi
grep -q "已有部署正在操作" "$LOG_DIR/same-target" \
  || fail "lock contention did not report a useful error"

if ! run_deploy /opt/studio-b > "$LOG_DIR/different-target" 2>&1; then
  sed -n '1,160p' "$LOG_DIR/different-target" >&2
  fail "a different remote path was incorrectly blocked"
fi

: > "$hold_file.release"
wait "$first_pid" || fail "first deployment did not finish successfully"
first_pid=""

if STUB_FAIL_SCP=1 run_deploy /opt/failing > "$LOG_DIR/failing" 2>&1; then
  fail "stubbed upload failure unexpectedly succeeded"
fi
run_deploy /opt/failing > "$LOG_DIR/after-failure" 2>&1 \
  || fail "the remote lock was not released after a failure"
run_deploy /opt/default-cache "" > "$LOG_DIR/default-cache" 2>&1 \
  || fail "the remote Maven cache default could not be resolved"

[[ "$(sort -u "$LOG_DIR/tar-paths" | wc -l | tr -d ' ')" == "5" ]] \
  || fail "deployments did not use unique source archives"
while IFS= read -r archive; do
  [[ ! -e "$archive" ]] || fail "temporary archive was not removed: $archive"
done < "$LOG_DIR/tar-paths"
[[ -z "$(find "$STATE_DIR" -mindepth 1 -print -quit)" ]] \
  || fail "a remote lock remained after deployment"

grep -Fq "test -f '/srv/maven cache/settings.xml'" "$LOG_DIR/ssh-commands" \
  || fail "configured Maven cache was not used for settings.xml lookup"
grep -Fq -- "-v '/srv/maven cache:/maven-cache'" "$LOG_DIR/ssh-commands" \
  || fail "configured Maven cache was not mounted into the build container"
grep -Fq -- "-s /maven-cache/settings.xml" "$LOG_DIR/ssh-commands" \
  || fail "configured Maven settings were not passed to Maven"
grep -Fq -- "-v '/home/deployer/.m2:/maven-cache'" "$LOG_DIR/ssh-commands" \
  || fail "default remote Maven cache was not mounted into the build container"
grep -Fq -- "--env-file deploy/.env" "$LOG_DIR/ssh-commands" \
  || fail "remote Compose did not read the uploaded deploy/.env"
if grep -Fq -- "--env-file .env" "$LOG_DIR/ssh-commands"; then
  fail "remote Compose still reads the obsolete root .env"
fi
if grep -Fq "test -f '/opt/studio/.env'" "$LOG_DIR/ssh-commands"; then
  fail "first deployment still requires an obsolete remote root .env"
fi
grep -Fq "server web deploy" "$LOG_DIR/tar-commands" \
  || fail "deployment package omitted the deploy directory"

command -v docker >/dev/null 2>&1 || fail "docker is required for Compose config validation"
docker compose version >/dev/null 2>&1 || fail "docker compose is required for config validation"
compose_config="$(PUBLIC_PORT=18080 docker compose -f "$SCRIPT_DIR/docker-compose.yml" config)"
grep -Fq 'published: "18080"' <<< "$compose_config" \
  || fail "PUBLIC_PORT did not control the rendered frontend port mapping"

echo "PASS: deploy environment, cache, port, lock, and cleanup contracts"
