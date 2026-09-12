# 按时间定位队列消息

## 适用场景

已知故障发生时间和受影响 Broker／队列，但不知道消息 offset 时，可在消息页面的队列浏览中按时间定位。普通 Topic 查询有扫描与结果数量上限，高流量 Topic 中的目标消息可能不在返回的 200 条结果中；队列定位直接使用 Broker 的时间索引，不扫描消息体。

## 操作步骤

1. 选择 Apache 实例及 Topic，加载队列。
2. 在 `Locate time (local)` 中选择故障时间。控件显示浏览器当地时间，请确认操作系统时区；请求发送对应的 Unix 毫秒时间戳。
3. 点击目标队列的 `Locate`。列表刷新该队列的可读范围，并将滑块移到查询到的附近位置。
4. 点击原有查看按钮，检查消息的实际存储时间；必要时继续调整 offset。

时间索引返回的是附近位置，不承诺消息的存储时间与输入时间相等。早于保留数据的时间定位到首个可读位置；超过快照范围的结果定位到最后一个可读位置。空队列没有可查看消息，并显示提示。

## 接口契约

```http
GET /api/messages/queue-position?instanceId=instance-a&topic=orders&brokerName=broker-a&queueId=2&timestamp=1788825600000
```

返回 `brokerName`、`queueId`、`minOffset`、`maxOffset` 和 `offset`。`maxOffset` 是不包含在内的上界，空队列的 `offset` 为 null。时间戳 0 是合法输入；负数队列编号、负数时间戳及缺少坐标返回 400。队列不在当前可读路由中返回 404；Broker 查询失败或返回不可用边界时返回 502，不伪报定位成功。

定位复用实例绑定的 Apache 客户端与凭据，沿用实例解析器的云实例限制。整个请求只读取路由、边界与时间索引，不更新任何消费组位点，不拉取消息体。队列保留期清理仍可发生在定位与查看之间；若查看时消息已经过期，应重新定位。

切换 Topic、切换实例或重新加载队列会使旧定位结果失效。一个队列的定位请求不会释放重新加载后同名队列的新请求；失败时保留原位置，允许重试。

## 验证与回滚

后端 `QueueTimestampLookupTest` 通过真实 HTTP 绑定、服务校验及 Provider 组合调用，在 SDK 边界模拟空队列、过期边界、时间索引失败、无路由和连接异常。前端覆盖接口参数、位置更新、查看衔接、空队列、失败重试及异步请求归属。

```bash
cd server
mvn -B -ntp -Dtest=QueueTimestampLookupTest,RocketMQMessageProviderTest,MessageServiceTest,MessageControllerTest test
cd ../web
npx vitest run src/components/__tests__/QueueTimestamp.test.tsx src/components/__tests__/QueueBrowser.test.tsx src/api/message.test.ts
```

无迁移，直接替换；没有新增依赖、配置或数据库字段。回滚本提交恢复原有队列浏览入口。协议采用 [RocketMQ 5.5.0 DefaultMQPullConsumerImpl](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/client/src/main/java/org/apache/rocketmq/client/impl/consumer/DefaultMQPullConsumerImpl.java) 的 `searchOffset` 调用。
