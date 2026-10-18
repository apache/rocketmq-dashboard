#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Controlled regression tests for dlq_check.sh's resend verdict: only a
# complete SUCCESS may proceed, and every verdict must still EXIT-clean.
# No Broker or remote host is touched; ssh/scp/python are stubbed.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
CHECK="$SCRIPT_DIR/dlq_check.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

PYTHON_BIN="$(command -v python3 || true)"
if [ -z "$PYTHON_BIN" ] || ! "$PYTHON_BIN" -c 'import sys' >/dev/null 2>&1; then
  PYTHON_BIN="$(command -v python || true)"
fi
if [ -z "$PYTHON_BIN" ]; then
  echo "FAIL: python is required" >&2
  exit 1
fi

BIN="$WORK/bin"
mkdir -p "$BIN"

# Executes the heredoc snippet exactly as _studio_client.py would, with the
# authenticated session replaced by fixture-driven responses.
cat > "$WORK/fake_studio_client.py" <<'PY'
import os
import sys

fixture = __import__("json").load(open(os.environ["FAKE_RESEND_FIXTURE"], encoding="utf-8"))
# The snippets are UTF-8 regardless of the platform's stdin locale.
code = sys.stdin.buffer.read().decode("utf-8")


class Response:
    def __init__(self, payload):
        self._payload = payload

    def json(self):
        return self._payload


def post(url, json=None, timeout=None):
    if url.endswith("/api/dlq/resend"):
        return Response(fixture)
    if url.endswith("/api/topics/send"):
        return Response({"code": 200, "data": {"msgId": "msg-1"}})
    return Response({"code": 200})


def get(url, params=None, timeout=None):
    return Response({"code": 200, "data": {"items": [{
        "groupName": os.environ["GROUP"],
        "messageCount": 3,
        "retryCount": 2,
        "status": "DLQ",
    }]}})


class Session:
    post = staticmethod(post)
    get = staticmethod(get)


exec(compile(code, "<dlq_check>", "exec"), {
    "__name__": "__main__",
    "os": os,
    "time": __import__("time"),
    "session": Session(),
    "BASE": "http://studio.test",
    "INSTANCE": "instance-1",
})
PY

cat > "$BIN/python3" <<'SH'
#!/usr/bin/env bash
exec "$FAKE_PYTHON_BIN" "$FAKE_PYTHON_SCRIPT" "$@"
SH

cat > "$BIN/ssh" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$FAKE_SSH_LOG"
if [[ "$*" == *"grep -c fail-consume"* ]]; then
  if [ "${FAKE_CONSUMER_GROWS:-1}" = "1" ]; then
    count=$(( $(cat "$FAKE_STATE_DIR/fail_count" 2>/dev/null || echo 0) + 1 ))
    printf '%s\n' "$count" > "$FAKE_STATE_DIR/fail_count"
    printf '%s\n' "$count"
  else
    printf '0\n'
  fi
fi
exit 0
SH

printf '#!/usr/bin/env bash\nexit 0\n' > "$BIN/scp"
printf '#!/usr/bin/env bash\nexit 0\n' > "$BIN/sleep"
chmod +x "$BIN"/*

run_case() {
  name="$1"
  expected_status="$2"
  fixture="$3"
  expected_text="$4"
  grows="${5:-1}"
  case_dir="$WORK/$name"
  mkdir -p "$case_dir/e2e" "$case_dir/state"
  : > "$case_dir/e2e/.env"
  printf '%s' "$fixture" > "$case_dir/fixture.json"

  set +e
  PATH="$BIN:$PATH" \
    PYTHONIOENCODING=utf-8 \
    E2E_DIR="$case_dir/e2e" \
    FAKE_PYTHON_BIN="$PYTHON_BIN" \
    FAKE_PYTHON_SCRIPT="$WORK/fake_studio_client.py" \
    FAKE_RESEND_FIXTURE="$case_dir/fixture.json" \
    FAKE_STATE_DIR="$case_dir/state" \
    FAKE_SSH_LOG="$case_dir/ssh.log" \
    FAKE_CONSUMER_GROWS="$grows" \
    bash "$CHECK" > "$case_dir/output" 2>&1
  status=$?
  set -e

  if [ "$status" -ne "$expected_status" ]; then
    echo "FAIL [$name]: expected exit $expected_status, got $status"
    cat "$case_dir/output"
    return 1
  fi
  if ! grep -qF "docker rm -f" "$case_dir/ssh.log"; then
    echo "FAIL [$name]: EXIT cleanup did not run"
    cat "$case_dir/ssh.log"
    return 1
  fi
  if ! grep -qF "$expected_text" "$case_dir/output"; then
    echo "FAIL [$name]: output is missing: $expected_text"
    cat "$case_dir/output"
    return 1
  fi
  echo "PASS [$name]"
}

complete='{"code":200,"data":{"matched":3,"resent":3,"failed":0,"outcome":"SUCCESS","scanIncomplete":false,"failedQueueCount":0}}'
partial='{"code":200,"data":{"matched":3,"resent":1,"failed":2,"outcome":"PARTIAL","scanIncomplete":false,"failedQueueCount":0}}'
scan_incomplete='{"code":200,"data":{"matched":3,"resent":3,"failed":0,"outcome":"PARTIAL","scanIncomplete":true,"failedQueueCount":1}}'
short_success='{"code":200,"data":{"matched":2,"resent":2,"failed":0,"outcome":"SUCCESS","scanIncomplete":false,"failedQueueCount":0}}'

failed=0
run_case complete-success 0 "$complete" "PASS 死信链路" || failed=1
run_case partial 1 "$partial" "FAIL 重投未完整成功" || failed=1
run_case scan-incomplete 1 "$scan_incomplete" "FAIL 重投未完整成功" || failed=1
run_case short-success 1 "$short_success" "FAIL 重投未完整成功" || failed=1
run_case consumer-still-missing 1 "$complete" "FAIL 重投后消费者没有再次收到" 0 || failed=1

if [ "$failed" -ne 0 ]; then
  echo "dlq_check regression: FAIL" >&2
  exit 1
fi
echo "dlq_check regression: PASS"
