---
name: regression-rocketmq
description: RocketMQ Studio 真实环境回归。当用户说 回归 studio、跑 studio 全功能测试、验证死信/收发/轨迹、studio 发版前验证、regression-rocketmq 时触发。流程：环境体检 → 用 deploy-rocketmq 起或校验开源集群（本地有 k8s 证书则再用 deploy-rocketmq-k8s.sh 拉起/校验一套 k8s 开源集群）→ 清遗留资源 → 跑 e2e 套件 → 死信全链路 → 收发与轨迹抽查 → 出报告。
---

# RocketMQ Studio 真实环境回归

对**真实部署**的 Studio + 真实 RocketMQ 集群做功能回归：API 层跑 studio-docs 的 e2e 套件，
死信/收发/轨迹走 broker 侧真实链路，最后出一份报告。不做 mock。

## 流程

1. **体检**：`set -a; source <terrances>/project/rocketmq-studio-docs/e2e-tests/.env; set +a`
   后跑 `scripts/check_env.sh`。按输出补缺：
   - studio 不通 / 登录失败 → 检查 `.env` 凭据，或按 deploy-rocketmq 模式 A 重新部署；
   - 无 `rmq-*` 容器 → 按 **deploy-rocketmq 模式 C** 起集群（与 studio 同机时共用 `rocketmq_net`）；
   - `kubectl` 有 context 但缺 `rocketmq1` namespace → 跑
     `project/rocketmq-studio-docs/deploy/rocketmq-k8s/deploy-rocketmq-k8s.sh`（幂等，含内网 SLB 与
     4 类 topic），并在 studio 确认 `open-source-k8s` 实例存在；无 k8s 证书则跳过并在报告里注明。
2. **清遗留**：`scripts/clean_leftovers.py`（上一轮崩掉会留下告警规则 / topic / group，
   让本轮创建类用例 409）。`run_e2e.sh` 已内含此步。
3. **e2e 套件**：`scripts/run_e2e.sh`（默认 `-m "not cloud"`；要连云实例传 `'-m cloud'`，
   全量传 `''`）。红了先单跑那条看 message 再分类：409 / 「id is required」多是遗留或套件落后于
   API（对照 server controller 的 DTO），404 多是路由改名（grep `@RequestMapping`）；
   排除这两类仍红才是产品回归。
4. **死信全链路**：`scripts/dlq_check.sh`，约 2 分钟（并发消费重试阶梯 10s/30s，
   `MAX_RECONSUME_TIMES=2`）。它自建 topic/group、发 3 条、起必失败消费者、等 DLQ 攒到 3 条、
   重投并确认消费者再次收到、trap 收尾删除 topic/group。
5. **收发 / 轨迹抽查**：API 侧由 e2e `test_messages.py` 覆盖（发送、msgId/key/时间查询、轨迹）；
   broker 侧抽查 `mqadmin clusterList`（两 broker `#ACTIVATED=true`、In/Out TPS ≈ 挂具速率）与
   `queryMsgTraceById`（Pub + Sub 齐全），命令见 deploy-rocketmq skill「常用查询命令」。
6. **浏览器抽检（可选）**：playwright-core harness 扫关键页——无 `GMT` 后缀、无 0 宽进度条、
   时间单元格不截断、筛选条不折行。**量宽度前先 `fc-list :lang=zh` 确认 headless 环境有中文字体**，
   豆腐块量出的中文宽度偏小，据此定的列宽在真实浏览器里会裁字。
7. **报告**：落 `project/rocketmq-studio-docs/test/report-<YYYYmmdd-HHMMSS>.md`（沿用该目录既有
   命名），写明：环境（studio commit / 集群形态）、e2e 通过数、死信结论、broker 抽查、遗留问题。

## 通过标准

- e2e `not cloud` 全绿；
- `dlq_check.sh` 打印 `PASS 死信链路…`；
- `clusterList` 两 broker ACTIVATED，模式 C 挂具 producer 持续 `SEND_OK`；
- 浏览器抽检无 ❌ 项（做了才要求）。

## 已知坑

- 部署开了登录（`STUDIO_AUTH_LOGIN_REQUIRED=true`）时 e2e 靠 `.env` 的
  `STUDIO_USERNAME` / `STUDIO_PASSWORD` 登录拿 cookie；免登录部署两键留空即可。
- 告警规则删除接口按 **id**（`DeleteAlertRuleDTO`）；按 name 删返回 400 且旧测试不断言，
  清理静默失败 → 下一轮 create 409「same evaluation conditions already exists」。
- `/api/dashboard/overview`、`/api/dashboard/metrics` 不存在；真实路由是
  `GET /api/dashboard?instanceId=` 与 `GET /api/metrics/grafana/dashboards`。
- 死信链路必须用**直连 nameserver 的 push 消费** + `RECONSUME_LATER` + `setMaxReconsumeTimes`；
  走 proxy 的 pop 消费不产生 `%RETRY%`/同一路径的 DLQ 表现不同。
- 收尾必须删 group 再删 topic：`%DLQ%<group>` 遗留会污染下一轮 DLQ 列表与计数断言。

## Resources

- `scripts/check_env.sh` —— 环境体检（只读）
- `scripts/clean_leftovers.py` —— 清 e2e / 回归遗留资源
- `scripts/run_e2e.sh` —— 清遗留 + 跑 pytest 套件
- `scripts/dlq_check.sh` —— 死信全链路（含清理）
- `assets/FailingConsumer.java` —— 必失败 push 消费者，在目标机用集群镜像现场编译运行
