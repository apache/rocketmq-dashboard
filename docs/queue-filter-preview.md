# 队列消费过滤预览

## 适用场景

消息已经存在于队列，但消费者没有收到时，可以使用消费端的 TAG 或 SQL92 表达式检查消息是否匹配。此功能通过 RocketMQ 原生过滤器读取消息，不在浏览器中模拟 SQL 语义，也不修改消费组位点。

## 操作步骤

1. 在消息页面选择队列浏览，加载 Topic 的队列。
2. 将队列滑块移到待检查范围的开始位置，点击对应行的 `Filter`。
3. 选择 `Tag` 或 `SQL92`，填写表达式并确认起始 offset。
4. 点击 `Preview from offset`；展开结果行查看消息体及属性。
5. 当仍有后续位置时，点击 `Next batch` 继续。修改表达式或起始 offset 后，旧的继续位置会被清除。

TAG 支持 `TagA || TagB` 和 `*`。SQL92 示例为 `amount > 100 AND region = 'east'`，要求 Broker 开启属性过滤能力。若 Broker 拒绝语法或能力请求，页面显示错误，不降级为无过滤查询。

## 结果与边界

每次请求只执行一次最多 20 条消息、3 秒超时的同步 pull；路由和边界读取另有各自 SDK 超时。一次没有匹配消息，不代表后续队列也没有匹配消息；`hasMore` 和 `nextOffset` 告知是否可以继续。

若保留期清理使起点失效，服务端修正到当时的可读边界，并显示调整提示。`OFFSET_ILLEGAL` 的 Broker 修正位置也会显示给用户，需显式继续。没有前进的异常批次返回错误，避免反复读取同一位置。

页面仅保留当前批次。关闭面板或切换实例、Topic 会取消浏览器请求，避免旧结果写入新页面；服务端已经开始的 RPC 仍受其超时约束，浏览器取消不等于远端调用立即终止。消息体和属性沿用现有显示截断规则，截断时明确提示。

此预览复用 Studio 的实例绑定 Apache pull 客户端，不会以业务消费组身份执行消费，也不注册用户的订阅关系；只能帮助核对 Broker 过滤结果，不能代替对业务消费者在线状态、权限及订阅一致性的检查。

## 接口与验证

`GET /api/messages/queue-filter-preview` 接受 `instanceId`、`topic`、`brokerName`、`queueId`、`offset`、`expressionType` 和 `expression`。表达式类型为 TAG 或 SQL92，表达式最多 4096 个字符。响应包含消息列表、起点、继续位置、可读边界、是否可继续及是否修正位置。

```bash
cd server
mvn -B -ntp -Dtest=QueueFilterPreviewTest,RocketMQMessageProviderTest,MessageServiceTest,MessageControllerTest verify
cd ../web
npx vitest run src/components/__tests__/QueueFilterPreview.test.tsx src/components/__tests__/QueueBrowser.test.tsx src/api/message.test.ts
```

后端测试组合真实 HTTP 校验、服务与 Provider，在 SDK 边界验证表达式、有限批次、空匹配继续、位点修正、过滤能力错误及中断。前端验证继续位置、修改表达式、取消请求、原队列入口和消息详情。

无迁移，直接替换；没有新增依赖、配置或数据库字段。回滚本提交移除新增只读入口，不需要调整消费组状态。协议依据 [RocketMQ 5.5.0 DefaultMQPullConsumerImpl](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/client/src/main/java/org/apache/rocketmq/client/impl/consumer/DefaultMQPullConsumerImpl.java)，核验日期 2026-09-08。
