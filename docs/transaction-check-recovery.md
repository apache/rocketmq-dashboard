# 恢复被丢弃事务的回查

最后验证日期：2026-09-08。协议依据 RocketMQ 5.5.0。

## 适用场景

事务半消息超过回查次数后，默认 Broker 监听器会把它移到 `TRANS_CHECK_MAX_TIME_TOPIC`。
修复生产者不可用或事务状态查询故障后，可通过恢复入口重新请求生产者检查业务事务。
此操作不直接提交或回滚事务，最终处理结果取决于生产者的事务检查逻辑。

只接受该丢弃队列中的消息，普通 Topic 消息和仍在半消息队列中的消息不会被恢复。
需先确认业务状态查询已恢复，原生产者组可以回答回查，并考虑重复检查的业务影响。
原消息 Broker 必须仍为所选实例登记的主节点；原地址已变成从节点或不再登记时拒绝恢复。

## 操作步骤

1. 在消息页选择 Apache 实例，查询 `TRANS_CHECK_MAX_TIME_TOPIC` 中的目标消息。
2. 消息详情新增可复制的 `Physical offset ID`，与客户端消息 ID 分开展示。
   点击 `Recover this transaction` 可将物理 ID 带入表单。
   也可通过页面顶部 `Recover transaction checks` 输入从排障工具取得的物理 ID。
3. 点击 `Inspect discarded transaction`，查看原 Topic、生产者组、旧回查次数、
   事务 ID、存储时间及 Broker 地址。预览不写入 Broker。
4. 核对目标并勾选确认后点击 `Recover checks`。提交会重新读取并验证源消息。
5. 收到 Broker 接受回执后，检查生产者和消息轨迹，确认最终事务结果。

物理 ID 为 32 或 56 位十六进制，分别编码 IPv4 或 IPv6 地址及物理偏移。
原生消息响应增加可选 `offsetMsgId`，从 SDK 的物理 ID 字段读取，保留原 `msgId`。
云实例和 Mock 模式不提供恢复入口。登录启用时，预览与提交均要求管理员权限。
改变 ID 会使预览与确认失效；处理期间锁定表单，切换实例后忽略旧请求结果。

## 协议与限制

服务先复用实例拓扑校验，拒绝指向未知地址的物理 ID，再确认当前主节点。
读取结果必须属于丢弃队列，并与请求的地址和 CommitLog 位点一致；
原 Topic 和生产者组缺失时拒绝恢复。预览不返回消息正文。
IPv6 地址匹配沿用现有拓扑校验规则，登记地址与 SDK 解码地址无法匹配时会拒绝。

提交调用 SDK `resumeCheckHalfMessage`。Broker 将消息重新写入半消息队列并重置回查次数。
页面只报告 Broker 接受，不把这一响应等同于事务提交成功。
丢弃源消息不会由此删除，重复恢复可以产生新的回查请求；页面不自动重试。
RPC 超时后的结果可能不确定，应先检查 Broker 和生产者记录再决定下一步。
拓扑检查与写入不是分布式事务，操作期间应避免主动切换 Broker 角色。

操作记录包含所选实例、物理 ID、原 Topic、生产者组和 Broker 地址，不包含消息正文。
审计存储失败不把 Broker 已接受的操作变成可重试失败。
依据官方 [DefaultTransactionalMessageCheckListener](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/broker/src/main/java/org/apache/rocketmq/broker/transaction/queue/DefaultTransactionalMessageCheckListener.java)、
[DefaultMQAdminExtImpl](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/tools/src/main/java/org/apache/rocketmq/tools/admin/DefaultMQAdminExtImpl.java)
和 [AdminBrokerProcessor](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/broker/src/main/java/org/apache/rocketmq/broker/processor/AdminBrokerProcessor.java)。

## 验证与回滚

后端执行 `TransactionRecoveryTest`、`RocketMQMessageProviderTest`、
`RuntimeAdminClientResolverTest`、`AuthInterceptorTest`、`OperationAuditServiceTest`。
前端执行事务恢复 API、弹窗和两个 MessagePage 测试文件，并执行 ESLint、TypeScript 与构建。
浏览器通过精确本地 API 拦截验证消息详情入口、预览、确认和接受回执；没有真实集群写入。
真实集群 E2E、性能、压力、容量及混沌验证未执行。

无新增依赖、配置或数据库字段，无迁移，直接部署；旧客户端可忽略新增物理 ID 字段。
代码回滚移除入口与接口。已接受的恢复没有通用撤销命令，不能通过回滚页面撤销；
应依据生产者事务状态和消息处理记录完成业务处置。
