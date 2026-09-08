# 单队列消费位点调整

消费组详情的队列进度表新增 `Set offset`，用于将一个队列的 Broker 持久化消费位点调整到明确位置。
例如只回放 `orders / broker-a / Queue 1` 的一段消息，而不改变同 Topic 其他队列的位置。

## 操作前提

本功能面向传统集群消费的离线维护。先停止该消费组全部消费者，并保持停止直到操作完成。
它不会停止应用进程，也不会处理 POP 的不可见消息或广播客户端的本地位点。
页面对云实例、Mock 模式以及已识别的 POP／广播组禁用入口。
启用登录校验时，预览和提交接口均需要管理员权限。

## 使用流程

1. 选择 Apache 实例，打开消费组详情的队列进度页。
2. 在目标队列行点击 `Set offset`，核对实例、消费组、Topic、Broker 和 Queue ID。
3. 输入目标整数位点，点击 `Preview change`。
4. 查看当前位点、保留范围、回放／跳过的位置数量与预计积压。
5. 确认后点击 `Confirm queue offset`。成功后刷新当前消费组进度，再按维护计划恢复应用。

目标范围为 `[minOffset, maxOffset]`，`maxOffset` 是下一条消息的位置，可用来跳过当前积压。
数量表示队列位置差，不保证等同于业务过滤后实际处理的消息条数。
输入、预览和提交中的位点均使用十进制字符串，避免超过 JavaScript 安全整数范围时丢失精度。
修改目标位点会清除预览；无变化的预览不能确认提交。

## 服务端检查

接口从所选实例的 Topic 路由解析指定 Broker 的主节点，不接受客户端指定网络地址，也不回退到从节点。
查询目标主节点的消费组连接，在线、权限失败或无法确认状态时拒绝调整。
只有原生 `CONSUMER_NOT_ONLINE` 或空连接集合被视为该节点的离线观测。
预览读取队列保留范围及已有消费位点，缺失值不当成零。
提交会重新读取连接、范围和消费位点，并要求当前位点与预览一致；否则返回 409，要求重新预览。

这些检查不是分布式锁或原子比较交换。客户端可能在检查后重新连接，因此操作期间必须由运维保持消费组停止。
目标主节点的连接观测不代表所有其他 Broker 的全局状态。
各 RPC 使用现有超时，网络错误后结果可能不确定；页面不会自动重试。

## 接口和实现依据

```http
POST /api/groups/queue-offset/preview
POST /api/groups/queue-offset
Content-Type: application/json

{"instanceId":"instance-a","group":"cg-orders","topic":"orders","brokerName":"broker-a","queueId":1,"offset":"20","expectedCurrentOffset":"40"}
```

预览不要求 `expectedCurrentOffset`；提交必须携带预览返回的 `currentOffset`。
成功结果返回本次检查的旧位点、目标和影响；不声称已对消费者应用做端到端验证。
写入使用 SDK 的 `updateConsumeOffset`，只发送指定队列的 `UPDATE_CONSUMER_OFFSET`。
不使用 `resetOffsetByQueueId`，因为该 SDK 方法还会调用按时间重置协议，旧 Broker 路径可能忽略 Queue ID 并影响其他队列。
相关原文见 [DefaultMQAdminExtImpl](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/tools/src/main/java/org/apache/rocketmq/tools/admin/DefaultMQAdminExtImpl.java)。

审计操作名为 `SET_QUEUE_CONSUMER_OFFSET`，保存实例、消费组、队列、原位置、目标和结果。
审计存储失败不会把已接受的 Broker 写入改报失败。
没有新增依赖、配置或数据库字段。无迁移，直接部署；回滚代码移除入口和接口。
数据回滚需在消费者仍停止时，根据审计中的原位置重新调整，且原位置必须仍在保留范围内。

本地验证覆盖 HTTP、真实实例解析及管理客户端包装、模拟 SDK、权限、审计、范围漂移与前端确认流程。
真实集群 E2E、性能、压力、容量与混沌测试尚未执行。浏览器检查只使用本地受控响应。最后核对日期：2026-09-08。
