# Broker 队列存储清理

## 适用场景与前提

真实 Apache 实例的 Broker 集群列表提供 `Queue cleanup` 入口，执行原生队列存储维护。
操作会删除 Broker 判定可清理的存储，不能自动撤销。维护前应具备已验证的存储备份、
恢复流程和业务窗口，并暂停可能影响 Topic 配置及相关数据范围的并发管理操作。

本功能只操作页面明确显示的一个登记节点，不把操作扩散到整个 Broker 组或集群。
它没有试运行模式；显示的当前 Topic 配置列表也不是待删除文件或队列的清单。
原生接口不返回精确候选集合、删除数量或回收字节数。

## 两种操作

| 操作 | 原生依据 |
| --- | --- |
| `Remove queues for unconfigured topics` | 依据 Broker 当前 Topic 配置保留集合，清理不再配置的 Topic 队列存储；默认存储实现排除系统 Topic 和 LMQ |
| `Remove expired ConsumeQueues` | 依据当前 CommitLog 最小物理位点，清理其最后物理位点早于保留起点的队列；默认文件队列实现排除系统 Topic |

过期队列可能仍属于已配置的 Topic；配置列表不是该操作的排除列表。
操作由实际 Broker 存储实现决定，扩展存储或插件的具体删除行为应先在对应环境验证。
清理不等同于删除 Broker Topic 配置；也不主动调用手工删除 CommitLog 的命令。

## 执行步骤

1. 选择目标 Broker 节点，打开维护窗口并选择所需操作。
2. 点击 `Review node scope`，核对 Broker 名称、地址和当前已配置 Topic。
3. 评估对应存储实现的删除规则，确认备份和恢复方案。
4. 输入界面显示的完整节点地址，再点击 `Run cleanup`。
5. 查看回执，检查 Broker 日志、队列存储和业务消费状态。

选择另一种操作会清除旧范围和确认文本，需重新审阅。
提交前服务端重新检查实例登记信息和 Topic 名称集合；地址不再登记或 Topic 集合变化，
会阻止执行并要求重新审阅。这不是分布式锁，检查之后仍可能发生并发变化。
CommitLog 保留起点也可能推进，实际删除范围以 Broker 执行时的状态为准。

## 回执与中断

`BROKER_REPORTED_COMPLETION` 仅表示 SDK 收到 Broker 成功响应。
不要据此推算删除数量、磁盘释放量或每个文件的删除成功率，应以实际日志与存储检查为准。

调用异常、超时或没有成功确认时，返回 `UNKNOWN`。操作可能已部分完成，
不能认定没有删除，也不能直接自动重试。本功能保留审计记录并要求人工核实。
浏览器断网、关闭或切换实例无法停止已经发送到 Broker 的清理。
审计持久化失败不会覆盖已取得的 Broker 回执。

操作后的数据恢复依赖维护前验证过的备份与 Broker 恢复流程。
撤销 Dashboard 的代码提交不会恢复已删除数据；重新创建 Topic 也不能替代存储恢复。
无法接受该恢复条件时，应在实际清理前停止操作。

## 验证与代码回滚

POST 沿用管理员权限拦截，GET 只读取范围。没有增加配置表或依赖。
本地测试覆盖节点与实例绑定、两种原生命令、确认文本、范围变化、权限、
超时不确定性、中断标记、审计失败和前端生命周期。
浏览器验证使用明确的本地 GET/POST 拦截，没有删除真实 Broker 文件。
真实存储清理、备份恢复演练、压力、容量和网络分区验证未执行。

无数据结构迁移。代码回滚可撤销本提交；实际数据恢复按前述备份方案执行。
最后核验日期：2026-09-08。

协议依据为 RocketMQ 5.5.0 的
[AdminBrokerProcessor](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/broker/src/main/java/org/apache/rocketmq/broker/processor/AdminBrokerProcessor.java#L2350)、
[DefaultMessageStore.cleanUnusedTopic](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/store/src/main/java/org/apache/rocketmq/store/DefaultMessageStore.java#L1608)
与 [ConsumeQueueStore.cleanExpired](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/store/src/main/java/org/apache/rocketmq/store/queue/ConsumeQueueStore.java#L558)。
