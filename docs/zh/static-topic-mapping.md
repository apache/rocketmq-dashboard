# 静态 Topic 队列映射诊断

## 场景与入口

静态 Topic 将逻辑队列映射到 Broker 的物理队列，迁移后同一逻辑队列可能有多个历史分段。
普通路由表只能展示队列数量及权限，不能解释逻辑偏移属于哪个历史物理队列。
Apache 实例 Topic 列表的 `Static mapping` 打开只读诊断窗口，点击
`Read mappings` 后读取路由摘要及各路由 Broker 的完整本地映射。

接口为 `GET /api/static-topic-mappings?instanceId=...&topic=...`。
没有创建静态 Topic、迁移队列、修复映射、重置位点或修改配置的写操作。

## 数据来源与范围

服务先读取所选实例的 Topic 路由及 Broker 登记，再取路由 Broker 名称与
`topicQueueMappingByBroker` 名称的并集，逐个读取已登记主节点的 Topic 配置。
地址必须来自当前实例登记，不直接信任客户端地址或路由里的地址。
主节点缺失时明确显示不可用，不自动改读副本。

SDK 的 `examineTopicConfig` 返回声明类型为 `TopicConfig`，实际协议解析为
`TopicConfigAndQueueMapping`，请求带 `lo=true`，包含完整 `mappingDetail`。
只有明确的扩展返回类型中 `mappingDetail=null` 才显示 `NO_MAPPING`；
响应缺失、普通基类响应、Topic 或 Broker 身份不符均不能视为没有映射。

历史分段里出现但不在当前路由或映射广播中的 Broker 不会额外查询。
因此结果不是全历史 Broker 扫描，也不能证明全集群队列归属完整。

## 阅读结果

| 字段 | 含义 |
| --- | --- |
| Route epoch / scope / total logical queues | NameServer 广播的映射摘要 |
| Advertised current queue IDs | 该广播中的逻辑队列 ID 到物理队列 ID 映射 |
| Local epoch / scope / total logical queues | 所选主节点本地映射的版本、范围和总逻辑队列数 |
| Local dirty flag | Broker 原始 dirty 标记，不自行推断其根因 |
| Logical queue | 本地 hostedQueues 中的全局逻辑队列 ID |
| Last mapped broker | 该本地分段列表最后一项的 Broker，不等同于已确认的全局当前所有者 |
| Generation | 分段代数，按 Broker 返回的历史顺序展示 |
| Logical start | 分段逻辑起始位点；-1 表示尚未决定 |
| Physical start / end | 物理队列位点，末尾不包含在区间内；-1 的末尾表示开放边界 |

这里的物理位点仍是队列位点，不是 CommitLog 字节地址。
已确定分段的换算关系为：逻辑位点 = 逻辑起点 + 物理位点 − 物理起点。
界面不推算开放分段长度，也不把映射范围当作当前保留消息范围。
SDK 中预留的起止时间字段不作为可靠时间线展示。

所有 64 位版本号与位点以十进制字符串传输及显示，避免超过 JavaScript 安全整数范围后失真。
普通整数 ID 保持整数；不按位点数值重新排序历史分段。

## 不一致与失败

各 Broker 返回 `MAPPING`、`NO_MAPPING` 或 `UNAVAILABLE`。
一个节点读取失败时保留其它节点样本，同时标记部分数据不可用。
路由摘要与本地版本、范围或总队列数不同会提示差异；两者是分时采样，
差异可能发生在迁移或广播更新期间，不能直接断言某份元数据损坏。

整个路由或登记不可读时显示错误，不能伪造空的健康结果。
重新读取先清理旧样本，切换实例或关闭窗口中止浏览器等待并忽略迟到结果。
没有自动轮询或自动修复；应结合迁移记录、Broker 日志和当前路由再次核对。

## 验证与回滚

本地测试覆盖历史顺序、超大位点、开放和未定边界、路由与本地版本差异、
未登记主节点、部分通信失败、空映射与不可用映射区别、中断、HTTP 契约及界面刷新。
浏览器验证使用本地只读响应拦截，未修改真实集群。
真实静态 Topic 迁移、保留期变化、并发选主、性能、压力、容量及混沌测试未执行。

无新增依赖或数据库迁移。撤销代码提交即可移除入口和接口，不需要恢复 Broker 数据。
核验日期：2026-09-08。协议依据：
[MQClientAPIImpl.getTopicConfig](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/client/src/main/java/org/apache/rocketmq/client/impl/MQClientAPIImpl.java#L3274)、
[TopicQueueMappingDetail](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/remoting/src/main/java/org/apache/rocketmq/remoting/protocol/statictopic/TopicQueueMappingDetail.java)
和 [LogicQueueMappingItem](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/remoting/src/main/java/org/apache/rocketmq/remoting/protocol/statictopic/LogicQueueMappingItem.java)。
