# 消费队列时间跨度诊断

## 用途

消费组详情的队列进度页提供 `Inspect times` 入口，按 Topic 查看各个队列的
最早保留消息时间、最新保留消息时间，以及消费位点对应的 Broker 时间参考。
用于排查历史消息是否仍在保留窗口、不同队列时间分布差异，以及位点已越过保留范围等问题。

此功能仅支持真实 Apache 实例；云实例和模拟模式不展示入口。
点击 `Read time spans` 执行只读采样；再次点击可手动刷新。
关闭对话框或切换实例会取消浏览器请求并忽略迟到响应，不保证 Broker 已发出的读取立即停止。

## 接口与数据来源

`GET /api/consumer-time-spans?instanceId=...&topic=...&group=...`

服务端使用所选实例的 AdminClient 和凭据，依次读取 Topic 队列范围、
消费组位点及 RocketMQ 原生 `queryConsumeTimeSpan` 返回的时间跨度。
不接受客户端指定的 Broker 地址，不修改位点、消费开关或消息。
普通已登录读取者可使用此 GET 接口，遵守现有认证流程。

以 Topic 元数据中的队列为基础展示全部行，未返回时间跨度的队列明确提示缺失。
返回重复条目或其他队列的时间跨度时拒绝当前样本，避免拼接不同拓扑的数据。
SDK 查询多个路由 Broker 时任一个读取失败都会传播错误，不将失败转换为空时间线。

## 时间和位点的含义

| 显示项 | 含义 |
| --- | --- |
| Earliest retained | Broker 能读取到的最早保留消息的存储时间 |
| Latest retained | 队列 maxOffset - 1 位置的消息存储时间 |
| Broker cursor reference | Broker 根据消费位点生成的时间参考，需结合状态解读 |
| Recorded offset reference | 正消费位点前一条消息仍在采样保留范围，且 Broker 返回了正时间戳 |
| Earliest message fallback | 位点为 0，Broker 把时间参考回退到最早消息；不能证明消费过 |
| Offset outside retained range | 位点前一条记录在采样保留范围之外 |
| Consumer offset unavailable | 消费组统计中没有此队列的有效非负位点 |
| Cursor timestamp unavailable | 当前位点有记录，但对应时间参考缺失 |

时间均为消息存储时间，不是业务处理开始、结束或确认时间。
非正时间戳归一化为不可用，不显示 1970 年或负时间。
位点和毫秒时间戳以十进制字符串传输，避免 JavaScript Number 丢失整数精度。
页面转换为 UTC 日期前验证安全整数与 Date 范围，无法显示时保留原始数值说明。

时间条带表示保留时间区间，不表示消息数、消费完成百分比或保留策略期限。
只有正常位点参考落在有效时间区间内时才显示标记；单一时间点放在条带中间。
倒置或缺失时间区间不绘制条带；回退或范围外参考不画成正常消费标记。

## 一致性限制

三次读取并非原子快照；持续写入、保留清理、主从切换或消费推进会造成采样差异。
SDK 可按路由选择副本，响应没有逐行来源地址，界面不声称这是主节点一致性快照。
`sampledAt` 是 Studio 完成采样的时间，不是 Broker 存储事件时间。

RocketMQ 原始 delayTime 的计算依赖下一条消息时间戳，缺失时可能产生异常大值；
本功能不使用该值推断积压年龄或 SLO，也不推断消费健康、处理成功或重放安全性。

## 验证、迁移与回滚

服务与契约测试覆盖时间语义、回退、保留范围、元数据缺失、拓扑变化和精度；
前端覆盖时间条带、缺失提示、取消、手动刷新以及消费组页面回归。
构建、静态检查和测试在本地执行；真实集群 E2E、压力、容量和混沌验证尚未执行。

无新增依赖、数据库迁移或 Broker 配置变化，直接部署即可。
撤销本提交或部署前一版本即可回滚，无远端数据需要恢复。

依据：Apache RocketMQ 5.5.0 的 `AdminBrokerProcessor.queryConsumeTimeSpan`
和 `DefaultMQAdminExtImpl.queryConsumeTimeSpan`。最后核验日期：2026-09-08。
