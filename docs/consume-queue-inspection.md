# ConsumeQueue 索引诊断

## 场景与入口

消息查询页切换到“按队列浏览”，选择 Topic 并加载队列，
点击目标队列的 `Inspect index`。此功能用于检查存储索引与扩展过滤信息，
例如消息体尚未拉取、SQL92 初筛行为需要核对、索引扩展数据缺失等场景。

功能仅对真实 Apache 实例开放，云厂商实例和模拟数据模式不提供入口。
服务端复用实例解析器与凭据配置，不接受客户端指定任意 Broker 地址。
读取者可调用只读接口，不需要管理员写权限。

## 请求与结果

`GET /api/messages/consume-queue` 参数：

| 参数 | 含义 |
| --- | --- |
| instanceId | 已登记的 Apache 实例 |
| topic、brokerName、queueId | Topic 与目标队列；queueId 非负 |
| index | 非负有符号 64 位十进制字符串 |
| count | 逻辑索引跨度，1–32，默认 16 |
| consumerGroup | 可选消费组，用于读取 Broker 当前订阅与初筛信息 |

服务端先查所选实例的集群登记信息，定位 brokerId=0 的主节点，
再通过 Topic 统计确认该队列存在并取得范围。没有主节点或队列时明确报错。
不回退到副本，不自动移动到别的队列，不持久化任何配置。

输入 index 可以等于队列末尾 max，此时返回 `atEnd=true` 与空数组，
避免 Broker 对不存在的迭代位置返回成功但没有消息体的响应。
小于 min 或大于 max 返回业务码 416，并提供采样范围。
Broker 范围不合法、返回体或索引列表缺失时返回 502，不伪装成空队列。

结果中的 minIndex、maxIndex、requestedIndex、physicalOffset 和 tagsCode
均为十进制字符串，浏览器不把它们转换为 JavaScript Number。
每个条目包含物理位置、物理大小、tagsCode、可用的扩展 JSON、位图及 Broker 提示。

## 如何解释过滤与条目位置

RocketMQ 的响应条目没有逐条逻辑 queueOffset。表格 Ordinal 仅表示返回顺序，
不能用 `index + ordinal - 1` 推算逻辑位置。批量队列尤其可能只返回少量条目；
count 表示逻辑跨度，不承诺返回恰好 count 条记录。

SDK 的 eval 是原生布尔值，默认 false。Broker 对不需要扩展过滤的条目会直接跳过，
因此本功能仅在请求指定消费组、Broker 返回订阅、且条目带扩展数据时展示布尔结果。
其余情况 `indexMatch=null`，页面显示 `Not evaluated`。
消费组离线、订阅不存在、扩展索引缺失都不能由默认 false 推断为“过滤未通过”。

`Passed` 仅表示 ConsumeQueue 阶段通过，仍可能需要 CommitLog 阶段过滤；
它不表示消费成功、最终表达式匹配或消息已经投递。本接口不读取消息正文。
Broker filter metadata、扩展 JSON 和位图保留原始内容以便诊断。

## 一致性与限制

Topic 范围与索引读取是两次请求，并非原子快照。保留策略、主从切换或持续写入
可能在两次采样之间改变范围；成功读取时使用索引响应自带的最新范围。
失败后可手动重新查询，不自动重试或修改输入。关闭弹窗会取消前端请求并丢弃迟到响应；
这不保证已发往 Broker 的读取立即停止。修改输入会清除旧诊断结果。

## 验证与回滚

本地验证覆盖精度、边界、初筛三态、缺失元数据、主节点约束、HTTP 契约、
取消与迟到响应，以及原消息页和队列浏览回归。实际集群存储布局与 Broker 版本
兼容性仍需在目标环境验证；未执行真实集群 E2E、压力、容量或混沌测试。

无新增依赖、无数据库迁移；直接部署包含功能的版本即可。
回滚时撤销本次提交或部署前一版本，无远端数据需要恢复。

协议依据：Apache RocketMQ `AdminBrokerProcessor.queryConsumeQueue`、
`QueryConsumeQueueResponseBody`、`ConsumeQueueData`。
核验日期：2026-09-08。
