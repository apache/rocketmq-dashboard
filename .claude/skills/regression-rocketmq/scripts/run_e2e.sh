#!/usr/bin/env bash
# 跑 studio-docs 的 e2e 套件（默认排除 cloud 用例）。先清遗留资源，避免上一轮的
# 告警规则/ topic / group 让本轮创建类用例 409。用法：run_e2e.sh [pytest 参数...]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="${E2E_DIR:-$SCRIPT_DIR/../../../../../../project/rocketmq-studio-docs/e2e-tests}"

cd "$E2E_DIR"
[[ -f .env ]] || { echo "FAIL 缺 $E2E_DIR/.env：cp env.example .env 并填值后重跑"; exit 1; }
set -a; source .env; set +a
export E2E_DIR

python3 "$SCRIPT_DIR/clean_leftovers.py"

if [[ $# -gt 0 ]]; then
  python3 -m pytest "$@" -q 2>&1 | tail -25
else
  python3 -m pytest -m "not cloud" -q 2>&1 | tail -25
fi
