#!/usr/bin/env bash
# 回归环境体检：studio 可达且能登录、docker 开源集群、k8s 开源集群（有证书才查）、e2e 依赖。
# 只读不改；缺失项只报告，修复动作见 SKILL.md。
set -uo pipefail

SSH_TARGET="${SSH_TARGET:-root@47.98.43.243}"
SSH="ssh -o BatchMode=yes -o ConnectTimeout=8 $SSH_TARGET"
STUDIO_URL="${STUDIO_URL:-http://47.98.43.243:6789}"
K8S_NS="${K8S_NS:-rocketmq1}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="${E2E_DIR:-$SCRIPT_DIR/../../../../../../project/rocketmq-studio-docs/e2e-tests}"

echo "== studio =="
echo "web HTTP $(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$STUDIO_URL/" 2>/dev/null || echo 000)"
if [[ -n "${STUDIO_USERNAME:-}" && -n "${STUDIO_PASSWORD:-}" ]]; then
  curl -s --max-time 10 -X POST "$STUDIO_URL/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$STUDIO_USERNAME\",\"password\":\"$STUDIO_PASSWORD\"}" \
    | head -c 120 | sed 's/^/login: /' ; echo
else
  echo "login: 未配 STUDIO_USERNAME/STUDIO_PASSWORD（从 e2e-tests/.env export 后再跑）"
fi

echo "== docker 开源集群（deploy-rocketmq 模式 B/C）=="
$SSH 'docker ps --format "{{.Names}}\t{{.Status}}" | grep "^rmq-" || echo "(无 rmq-* 容器：按 deploy-rocketmq 模式 C 起集群)"' 2>/dev/null

echo "== k8s 开源集群（本地有证书才查）=="
if command -v kubectl >/dev/null 2>&1 && kubectl config current-context >/dev/null 2>&1; then
  echo "context: $(kubectl config current-context)"
  kubectl -n "$K8S_NS" get pods 2>/dev/null \
    || echo "(namespace $K8S_NS 不存在：跑 studio-docs/deploy/rocketmq-k8s/deploy-rocketmq-k8s.sh 拉起)"
else
  echo "无 kubectl 或无可用 context：跳过 k8s 集群，本轮回归只覆盖 docker 集群"
fi

echo "== e2e 依赖 =="
echo "e2e dir: $E2E_DIR"
[[ -f "$E2E_DIR/.env" ]] && echo ".env: 存在" || echo ".env: 缺失（cp env.example .env 并填值）"
python3 -c 'import pytest, requests, dotenv; print("python deps: ok")' 2>/dev/null \
  || echo "python deps: 缺（pip install -r requirements.txt）"
