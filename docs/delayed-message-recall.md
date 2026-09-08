# 定时消息撤回

消息查询页提供 `Recall delayed message` 入口，使用原生产者发送回执中的撤回句柄请求 Broker 撤回待投递定时消息。
入口仅对真实 Apache 实例启用。启用登录校验时，检查句柄与撤回接口均需要管理员权限。

## 操作流程

1. 在消息查询页选中原发送使用的 Apache 实例，点击 `Recall delayed message`。
2. 输入原 Topic 和生产者发送回执中的 `recallHandle`，点击 `Inspect handle`。
3. 核对解码后的 Topic、Broker、原消息 ID 和句柄时间。
4. 点击 `Confirm recall` 提交。成功时展示 Broker 返回的消息 ID 和接收提示。
5. 如需确认业务结果，结合原生产应用和消息轨迹检查；不要仅根据接口成功判断消息从未被投递。

检查阶段仅解析实例与句柄，不打开生产者连接、不写入撤回标记。
修改 Topic 或句柄会清除已检查的目标，必须重新检查后确认。
提交期间锁定表单和关闭按钮，完成后显示结果；失败不会自动重试。
切换实例会卸载旧窗口，迟到响应不会写入新实例窗口。

## 句柄与限制

仅消息 ID 无法执行撤回。句柄由原始发送回执提供，不能从普通消息查询结果重建。
原生 SDK 的 v1 句柄包含 Topic、Broker 名称、时间和消息 ID，Studio 直接复用 SDK 解码器。
Topic 必须与句柄一致。句柄时间保持 SDK 的值，可能包含 Broker 为时间取整补偿的 1 毫秒。
本地不根据时钟推断句柄仍然有效；最终时效检查由 Broker 完成。

功能要求 Broker 支持定时消息撤回并允许该操作。旧延迟等级消息和已投递消息不能通过本功能撤销。
过期、Topic 不存在、Broker 角色或权限拒绝等错误直接显示，不切换到其他实例或 Broker 重试。
Broker 接收的是定时删除标记，SDK 成功回执不等于对最终投递状态的独立证明。
网络超时后结果可能不确定，重新提交前应核对生产应用与轨迹状态。

## 接口与审计

```http
POST /api/messages/recall/preview
POST /api/messages/recall
Content-Type: application/json

{"instanceId":"instance-a","topic":"orders","recallHandle":"<producer-receipt-handle>"}
```

检查接口返回目标信息；撤回接口返回 `messageId` 和 `acceptedAt`。
执行路径复用实例绑定的 Producer 池。SDK 异常保留现有 502 映射，中断状态会恢复。
审计操作名为 `RECALL_DELAY_MESSAGE`，记录实例、Topic、Broker、消息 ID、时间及操作结果；不记录完整句柄。
审计存储失败不会把 Broker 已接受的操作改报为失败。

## 兼容性与验证

无新增依赖、数据库字段或配置。无迁移，直接部署；回滚提交可移除入口和接口。
回滚代码不能恢复已经被 Broker 接收的撤回操作，恢复业务任务需由原生产应用明确重新发送。
本地测试覆盖真实实例解析／客户端池到模拟 SDK 的链路、HTTP 校验、权限、审计和前端确认流程。
浏览器验证使用受控本地接口响应，不向真实 Broker 写入消息。真实集群 E2E、压力、容量与混沌尚未执行。

语义依据：RocketMQ 5.5.0 的 [RecallMessageProcessor](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/broker/src/main/java/org/apache/rocketmq/broker/processor/RecallMessageProcessor.java)
与 [RecallMessageHandle](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/common/src/main/java/org/apache/rocketmq/common/producer/RecallMessageHandle.java)。最后核对日期：2026-09-08。
