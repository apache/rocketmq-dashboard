# FIFO 与定时消息发送

## 解决的问题

Studio 可以创建 FIFO 和 DELAY Topic，但原发送表单只能构造普通消息。RocketMQ 5.x 根据消息系统属性判断类型：FIFO 需要消息组，定时消息需要投递时间。把这些字段放进自定义属性会被客户端拒绝，因为它们属于保留属性。

## 使用方式

在 Apache 实例的 Topic 列表中打开原有发送对话框，填写消息体、Tag、Key 和业务属性。

| Topic 类型 | 新增输入及行为 |
| --- | --- |
| NORMAL | 无新增必填项，沿用普通发送 |
| FIFO | 必填 `Message group`，按组选择固定队列并设置 Broker 识别的消息组属性 |
| DELAY | 必填未来的 `Delivery time (local)`，将当地时间转换成绝对毫秒时间戳 |
| TRANSACTION | 显示专用事务生产者说明并禁用发送，避免把普通消息误用于事务 Topic |

不同消息组可能分配到同一个队列。同组的队列一致性依赖当时的路由列表；Broker 扩缩容可能改变哈希映射。多个操作者或生产者并发发送时，本工具不提供应用级全局顺序，应由业务生产者协调顺序。

定时发送的最大间隔、投递精度及 Broker 定时能力由集群配置决定。本工具只要求时间在未来，不硬编码集群的最大延迟。发送成功表示 Broker 接受消息，不表示定时时间已经到达或消费已经完成。

## 请求兼容

`POST /api/topics/send` 增加三个可选字段：

```json
{
  "instanceId": "instance-a",
  "topic": "order-events",
  "body": "event payload",
  "messageType": "FIFO",
  "messageGroup": "order-123"
}
```

DELAY 请求使用 `messageType: "DELAY"` 和 `deliveryTimestamp`，不携带 `messageGroup`。省略 `messageType` 时保持 NORMAL；混用组和定时时间、缺少必填字段或使用已过期时间返回 400，并记录失败审计。事务和 Lite 消息需要对应的专用生产者，此接口不伪造事务提交或 Lite 协议。实际 Topic 类型仍由 Broker 校验。

实现复用现有客户端池、实例凭据、消息大小限制及发送确认检查。FIFO 失败时不会降级为随机队列重发；消息已经发送成功后，审计存储失败也不会把成功变成可重试的发送错误。

## 验证与回滚

`TypedMessageSendTest` 直接调用 RocketMQ 的 `TopicMessageType.parseFromMessageProperty` 验证消息类型，并使用真实哈希选择器验证同组位置。另覆盖时间属性、旧请求、错误参数、发送失败和成功后的审计异常。HTTP 契约与 Topic 页面测试验证参数绑定、必填项和消息类型互斥。

无迁移，直接替换。没有新增依赖、配置或数据库字段；回滚本提交恢复原发送入口。回滚不会撤销 Broker 已接受的消息，已发送的定时消息仍按 Broker 状态执行。

协议依据：[RocketMQ 5.5.0 TopicMessageType](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/common/src/main/java/org/apache/rocketmq/common/attribute/TopicMessageType.java) 与 [Message](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/common/src/main/java/org/apache/rocketmq/common/message/Message.java)，核验日期 2026-09-08。
