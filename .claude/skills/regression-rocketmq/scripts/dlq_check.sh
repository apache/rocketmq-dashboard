#!/usr/bin/env bash
# 死信全链路回归：建 topic/group → Studio 发 3 条 → 必失败消费者重试进 DLQ →
# Studio DLQ 接口可见 → 重投 → 清理。命名带时间戳，trap 保证收尾清理。
# 依赖：目标机已有 deploy-rocketmq 模式 B/C 集群（容器网络 rocketmq_net + 镜像 apache-rocketmq:develop）。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SKILL_DIR="$(dirname "$SCRIPT_DIR")"
E2E_DIR="${E2E_DIR:-$SCRIPT_DIR/../../../../../../project/rocketmq-studio-docs/e2e-tests}"
SSH_TARGET="${SSH_TARGET:-root@47.98.43.243}"
SSH="ssh -o BatchMode=yes $SSH_TARGET"
NAMESRV="${NAMESRV:-rmq-nameserver:9876}"
RMQ_IMAGE="${RMQ_IMAGE:-apache-rocketmq:develop}"
REMOTE_DIR="${REMOTE_DIR:-/opt/e2e-dlq}"

cd "$E2E_DIR"
[[ -f .env ]] || { echo "FAIL 缺 $E2E_DIR/.env"; exit 1; }
set -a; source .env; set +a
export E2E_DIR

TS="$(date +%s)"
export TOPIC="E2EDlq_$TS" GROUP="E2EDlqGid_$TS" CONTAINER="rmq-e2e-failing-$TS"

api() {  # api：stdin 里的 python 片段可直接用 session / BASE / INSTANCE（已登录）
  python3 "$SCRIPT_DIR/_studio_client.py"
}

cleanup() {
  echo "-- 清理 --"
  $SSH "docker rm -f $CONTAINER >/dev/null 2>&1" || true
  api <<'PY' || true
import os
for path, key in (("/api/groups/delete", "name"), ("/api/topics/delete", "name")):
    name = os.environ["GROUP"] if "groups" in path else os.environ["TOPIC"]
    body = session.post(f"{BASE}{path}", json={"instanceId": INSTANCE, key: name}, timeout=30).json()
    print(f"  delete {name} -> {body.get('code')}")
PY
}
trap cleanup EXIT

echo "== 1. 建 topic/group 并发 3 条 =="
api <<'PY'
import os
topic, group = os.environ["TOPIC"], os.environ["GROUP"]
body = session.post(f"{BASE}/api/topics/create", json={
    "instanceId": INSTANCE, "name": topic, "type": "NORMAL", "remark": "dlq regression"}, timeout=30).json()
assert body.get("code") == 200, f"create topic: {body}"
body = session.post(f"{BASE}/api/groups/create", json={
    "instanceId": INSTANCE, "name": group, "consumeType": "CLUSTERING",
    "subscriptionMode": "Push"}, timeout=30).json()
assert body.get("code") == 200, f"create group: {body}"
ids = []
for i in range(3):
    sent = session.post(f"{BASE}/api/topics/send", json={
        "instanceId": INSTANCE, "topic": topic, "tag": "dlq", "key": f"dlq-key-{i}",
        "body": f"dlq regression {i}"}, timeout=30).json()
    assert sent.get("code") == 200, f"send {i}: {sent}"
    ids.append(sent["data"]["msgId"])
print(f"  topic={topic} group={group} msgIds={ids}")
PY

echo "== 2. 起必失败消费者（maxReconsumeTimes=2，约 40s 进 DLQ）=="
$SSH "mkdir -p $REMOTE_DIR"
scp -q "$SKILL_DIR/assets/FailingConsumer.java" "$SSH_TARGET:$REMOTE_DIR/"
$SSH "docker run -d --name $CONTAINER --network rocketmq_net -v $REMOTE_DIR:/clients:ro \
  -e NAMESRV_ADDR=$NAMESRV -e TOPIC=$TOPIC -e GROUP=$GROUP -e MAX_RECONSUME_TIMES=2 \
  $RMQ_IMAGE sh -c 'javac -encoding UTF-8 -cp \"lib/*\" -d /tmp/fc /clients/FailingConsumer.java && java -cp \"lib/*:/tmp/fc\" FailingConsumer'" >/dev/null
sleep 5
$SSH "docker logs $CONTAINER 2>&1 | tail -2"

echo "== 3. 等 DLQ 出现 3 条（最多 240s）=="
api <<'PY'
import os, time
group = os.environ["GROUP"]
deadline = time.time() + 240
count = 0
while time.time() < deadline:
    page = session.get(f"{BASE}/api/dlq", params={
        "instanceId": INSTANCE, "search": group, "page": 1, "pageSize": 20}, timeout=30).json()
    items = (page.get("data") or {}).get("items") or []
    hit = next((x for x in items if x.get("groupName") == group), None)
    if hit and hit.get("messageCount", 0) >= 3:
        count = hit["messageCount"]
        print(f"  DLQ group={group} messageCount={count} retryCount={hit.get('retryCount')} status={hit.get('status')}")
        break
    time.sleep(10)
assert count >= 3, f"DLQ 未在 240s 内攒到 3 条（最后 count={count}）"
PY

echo "== 4. 重投并确认消费者再次收到 =="
before=$($SSH "docker logs $CONTAINER 2>&1 | grep -c fail-consume" || true)
api <<'PY'
import os, time
now = int(time.time() * 1000)
body = session.post(f"{BASE}/api/dlq/resend", json={
    "instanceId": INSTANCE, "groupName": os.environ["GROUP"],
    "startTime": now - 3600_000, "endTime": now + 3600_000}, timeout=60).json()
assert body.get("code") == 200, f"resend: {body}"
print(f"  resend -> {body['data']}")
PY
sleep 15
after=$($SSH "docker logs $CONTAINER 2>&1 | grep -c fail-consume" || true)
echo "  fail-consume 次数 $before -> $after"
[[ "$after" -gt "$before" ]] || { echo "FAIL 重投后消费者没有再次收到"; exit 1; }

echo "PASS 死信链路：发送 → 重试 → DLQ → 重投 全通"
