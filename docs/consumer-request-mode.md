# 配置消费请求模式

## 适用范围

真实 Apache 实例的消费组详情中，消费进度表提供 `Configure POP/PULL` 入口。
它配置一个普通 Topic 与消费组组合在所有 Topic 主 Broker 上的请求模式，
用于采用服务端队列分配的兼容消费者。云实例和 Mock 模式不显示入口。

`PULL`、`POP` 是 RocketMQ 原生请求模式，不等同于把任意客户端的 Push/Pull API 自动转换。
本功能不会升级客户端、修改订阅配置、重置消费位点或排空未确认的 POP 消息。
切换前需要规划客户端兼容性、在途消息、重试队列与恢复窗口。

## 操作步骤

1. 停止消费组的全部消费者，并保持停止状态。
2. 从所需 Topic 的进度行打开窗口，选择请求模式和 POP 共享参数。
3. 点击 `Preview broker modes`，核对所有 Broker 的有效模式、共享值和配置来源。
4. 勾选确认项后提交。提交前后端重新读取路由、连接状态和配置，发现变化即要求重新预览。
5. 核对逐 Broker 回执及实际配置，再按既定方案恢复兼容消费者。

预览和提交都要求消费组已在每个 Topic 主 Broker 上配置且处于离线状态。
只有空连接集合或明确的 `CONSUMER_NOT_ONLINE` 响应码可证明当次检查离线；
权限错误、超时及未知响应不能放行。没有登记主节点时不自动向副本写入。

## 默认值与显式覆盖

后端读取 Broker 的 `messageRequestModeMap`，只返回目标 Topic 与消费组的条目。
不存在条目时显示 `Broker default`；使用 `defaultMessageRequestMode`，
只有默认模式为 POP 时才使用 `defaultPopShareQueueNum`，PULL 的有效共享值为 `0`。
存在条目时显示 `Explicit override`，保留存储的模式和参数。
缺失配置、拒绝读取或格式错误不能当作“没有覆盖”。

界面同时展示 `serverLoadBalancerEnable`，便于判断队列共享规则的适用条件。
当请求模式已经与默认值相同，提交仍会写入显式覆盖；相同的既有显式覆盖则不重复写入。
后续 Broker 默认值变化不会自动影响已写入的覆盖。

| POP 共享值 | RocketMQ 5.5.0 分配语义 |
| --- | --- |
| `-1`、`0` | 每个客户端可访问全部队列 |
| 正整数 N | 符合分配条件时，额外共享客户端排序列表中后续 N 个客户端的队列 |
| N 大于或等于客户端数量减一 | 回退为全部队列 |

共享值不是队列数量硬上限，也不是并发线程数。
输入支持 `-1` 至 `2147483647` 的整数；选择 PULL 时固定提交 `0`。
重试 Topic、死信 Topic 不支持此入口。

## 结果、故障与恢复

写入按 Broker 名称排序执行，使用原生 `setMessageRequestMode`，每次写入后重新读取配置。
`CONFIRMED` 表示写入收到确认且回读与请求的显式覆盖相同；`UNCHANGED` 表示无需写入。
`UNKNOWN` 表示写入或回读无法确认，后续 Broker 标为 `NOT_ATTEMPTED` 并停止执行。
先前成功结果会保留，不自动重试或回滚。审计记录原值、原配置来源和请求值，
审计存储失败不会覆盖 Broker 操作结果。

多 Broker 操作没有原子性。检查之后客户端仍可能重新连接，其他管理员也可能改配置。
浏览器断网、关闭或切换实例不能撤回已经发出的请求；丢失响应后应逐 Broker 重新读取，
不能假定全部失败。再次操作必须重新预览。

回滚业务配置时，保持消费者停止，并按保存的预览恢复各 Broker 原模式与共享值。
原生接口没有删除覆盖的操作；把值恢复为原默认值仍会留下显式覆盖，
不能恢复为继续继承未来默认值。需要恢复继承关系时，按 Broker 配置维护流程处理，
不要删除消费组作为替代。上线前应把此限制纳入迁移方案。

## 实现与验证

MQAdminExt 提供写入封装，但没有读取请求模式的封装。
读取复用现有 DefaultMQAdminExt 的公开 SDK 访问器、认证传输和 VIP 通道设置，
发送官方 `GET_ALL_MESSAGE_REQUEST_MODE` 命令；没有反射或额外创建未认证客户端。
POST 沿用管理员权限拦截，GET 仅用于预览读取。

本地测试覆盖默认回退、显式覆盖、协议读取、VIP 地址、全 Topic 范围、预览冲突、
权限、中断、部分成功、审计异常与前端确认流程。
浏览器验证以本地接口拦截完成，没有修改真实 Broker。
真实 POP 客户端切换、压力、容量及网络分区验证未执行。

无新增依赖，无数据库迁移；撤销代码提交可移除入口和接口，已写入的配置需按上述恢复步骤处理。
最后核验日期：2026-09-08。协议依据为 RocketMQ 5.5.0 的
[QueryAssignmentProcessor](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/broker/src/main/java/org/apache/rocketmq/broker/processor/QueryAssignmentProcessor.java)、
[MessageRequestModeManager](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/broker/src/main/java/org/apache/rocketmq/broker/loadbalance/MessageRequestModeManager.java)
及 [AdminBrokerProcessor](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/broker/src/main/java/org/apache/rocketmq/broker/processor/AdminBrokerProcessor.java#L2037)。
