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
