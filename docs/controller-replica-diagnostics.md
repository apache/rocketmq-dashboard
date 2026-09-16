# Controller 副本成员诊断

## 使用方式

在 RocketMQ 集群页面选择真实 Apache 实例，从 Broker 行进入 `Replica membership`，
点击 `Read controller state`。功能只读，不触发选主、修改配置或清理成员。
关闭窗口或切换实例会取消浏览器请求并丢弃迟到结果。

后端从所选实例的登记信息解析 Broker 地址，优先使用主节点；主节点尚未登记时，
可从仍登记的副本读取配置。页面显示实际配置来源地址。
只有 `enableControllerMode=true` 且存在 `controllerAddr` 时才查询 Controller。
缺少配置字段显示 `UNKNOWN`，关闭模式显示 `DISABLED`，不把不支持误判成健康集群。

## 元数据观察

配置中的 Controller 地址去重后逐一读取。每个节点分别展示 Controller 组名、
报告的 Leader ID、Leader 地址、自身角色和原始 peers。
单个节点失败不会覆盖其他节点的元数据；失败响应保留类型或 Broker 响应码。
界面不会返回 Broker 配置中的其他属性。

| 元数据一致性 | 含义 |
| --- | --- |
| `MATCHING` | 所有配置节点均有返回，组名、Leader ID 和地址字段相同 |
| `DIVERGENT` | 至少两个成功响应对这些字段的报告不同 |
| `PARTIAL` | 成功响应相同，但至少一个配置节点查询失败 |
| `UNAVAILABLE` | 没有成功元数据响应，或未能查询 Controller |

这些状态描述本次字段比较，不代表多数派可用、选主成功或故障转移就绪。
字段相同也可能共同报告空 Leader；此时不会继续查询成员，并给出缺失提示。
读取是顺序进行的，选主期间不同响应可能来自不同时间点。

## Broker 成员与同步集合

从首个成功且报告 Leader 地址的 Controller 开始发现 Leader，查询所选 Broker 名称的成员记录。
RocketMQ SDK 会重新查询元数据并向当时的 Leader 请求同步集合，
因此成员信息与前面的 Controller 元数据不是原子快照。
页面标注发现入口地址，不把该地址冒充最终处理成员查询的 Leader。

成员面板展示主 Broker ID、地址、主任期、同步集合任期，以及集合内外的副本。
Broker ID 以十进制字符串传输；不能把 NameServer 中表示主节点的路由别名 `0`
当作 Controller 的稳定成员 ID。

`Sync-set member` 和 `Controller liveness` 分别展示同步集合成员资格和存活标记。
集合内的节点也可能暂时被报告为不存活，不能将两者合并为一个“健康”状态。
缺失的存活标记显示 `Unknown`；本功能不推导日志复制字节差或数据无损保证。
缺少所选 Broker 成员记录、成员 ID 重复或同一成员出现在两个集合时明确报错，
不会以空列表或部分成员代替完整记录。

成员查询失败仍保留先前读取的 Controller 元数据。网络恢复后可以手动刷新，
刷新开始时清除旧结果，避免把旧采样误认为当前结果。

## 验证、限制与回滚

本地测试覆盖实例解析、登记副本回退、配置缺失、单节点失败、Leader 视图差异、
成员集合冲突、整数精度、中断恢复和前端异步生命周期。
浏览器验证使用本地只读接口拦截，不连接真实 Controller。
未完成真实选主期间的集成、压力、容量或网络分区测试。

无新增依赖，无数据迁移。撤销本提交即可移除入口与接口，不改变 Broker 或 Controller 状态。
最后核验日期：2026-09-08。

协议依据为 RocketMQ 5.5.0 的
[GetMetaDataResponseHeader](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/remoting/src/main/java/org/apache/rocketmq/remoting/protocol/header/controller/GetMetaDataResponseHeader.java)、
[BrokerReplicasInfo](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/remoting/src/main/java/org/apache/rocketmq/remoting/protocol/body/BrokerReplicasInfo.java)
和 [MQClientAPIImpl.getInSyncStateData](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/client/src/main/java/org/apache/rocketmq/client/impl/MQClientAPIImpl.java#L3405)。
