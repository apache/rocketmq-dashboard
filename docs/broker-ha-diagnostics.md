# Broker 主从复制状态

Broker 集群页的 `HA status` 按钮读取所选 Apache 实例中一个 Broker 名称的全部登记副本。
诊断用于排查主从复制落后、连接缺失和传输停滞；操作不会切换主从或修改 Broker 配置。
Mock 模式下按钮禁用，避免将模拟列表与真实诊断混合。

## 查询与解释

1. 选择 Apache 实例，在 Broker 列表点击 `HA status`。
2. 查看每个 Broker ID 的地址、实际报告的主从角色、日志偏移和查询结果。
3. 展开主节点查看从节点确认偏移、差距、传输位置、每秒速率以及 Broker 报告的同步状态。
4. 展开从节点查看上游地址、本地日志位置、主节点刷盘位置、最后读写时间与传输速率。
5. 点击 `Refresh snapshot` 手动重新采样；关闭窗口会取消浏览器请求并丢弃迟到响应。

偏移和差距单位为字节，不是消息条数。响应使用十进制字符串，浏览器不会将其转换为浮点数。
`inSync` 和同步副本数来自 Broker；页面没有另行推算阈值。
未报告的读写时间显示 `Not reported`，不将零时间戳显示成 1970 年。
副本从 NameServer 返回的 Broker 地址表选取，接口不接受任意 Broker 地址。

## 接口

```http
GET /api/brokers/ha?instanceId=instance-a&brokerName=broker-a
```

返回 `brokerName`、采样完成时间 `sampledAt`、完整性 `complete` 和节点数组 `nodes`。
节点按 Broker ID 排序；角色使用实时响应，不按 Broker ID 推断。
不支持 HA 查询、连接失败、权限不足或空响应会记录在该节点的 `error`，其他节点仍继续查询。
找不到 Broker 返回业务错误 404；实例解析、凭据及厂商约束沿用现有运行时解析器。

## 边界与兼容性

各副本依次采样，不构成原子快照。查询成本为一次拓扑 RPC 加每个登记副本一次 HA RPC，
延迟由现有管理客户端的 RPC 超时和副本数共同决定。没有新增后台轮询或重试任务。
浏览器取消不保证已发出的 Broker RPC 立刻终止。服务端线程中断时停止继续探测并保留中断标志。
HA 服务支持取决于 Broker 版本和部署模式；不支持时明确显示错误，不回填健康状态。
这些数据不足以证明可安全故障切换，也不替代 Controller 的同步状态集合。

本功能没有新增依赖或持久化字段。无迁移，直接部署；回滚该提交即可移除入口与接口。

## 验证依据

协议字段对应 RocketMQ 5.5.0 的 [HARuntimeInfo](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/remoting/src/main/java/org/apache/rocketmq/remoting/protocol/body/HARuntimeInfo.java)，
解释参考官方 [haStatus 命令](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/tools/src/main/java/org/apache/rocketmq/tools/command/ha/HAStatusSubCommand.java)。核对日期：2026-09-08。
本地测试覆盖 HTTP 到服务映射、主从分支、部分失败、精度、中断、实例参数和前端请求生命周期。
真实 RocketMQ 集群 E2E、性能、压力、容量及混沌测试尚未执行；浏览器交互使用本地受控响应。
