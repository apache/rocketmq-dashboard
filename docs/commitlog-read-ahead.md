# CommitLog 运行时预读控制

## 用途与范围

真实 Apache 实例的 Broker 集群列表提供 `Read-ahead` 入口，调整所选节点的
CommitLog 文件预读建议。该操作用于维护窗口中的磁盘读取行为排查，
不改变消费者流控阈值、消息保留时间或逻辑消费位点。

操作只针对页面显示的 Broker 名称与地址。服务端在读取和提交前均检查该地址
仍登记在所选实例的同名 Broker 下；不会自动切换到另一个主节点或副本。
页面列出哪个节点，就只对该节点应用配置。

## 操作步骤

1. 在维护窗口记录磁盘 I/O、读延迟和页缓存情况，明确观察与恢复方案。
2. 点击 `Inspect current mode`，读取当前 `dataReadAheadEnable`。
3. 选择不同的目标模式并勾选确认，再点击 `Apply runtime mode`。
4. 查看 RPC 确认和配置回读，并检查 Broker 日志及实际 I/O 指标。
5. 如需恢复，重新读取当前配置，再应用记录中的原模式。

| 界面模式 | 原生参数 | 配置字段 |
| --- | --- | --- |
| `Normal read-ahead` | `MADV_NORMAL`，值 `0` | `dataReadAheadEnable=true` |
| `Random access advice` | `MADV_RANDOM`，值 `1` | `dataReadAheadEnable=false` |

预览缺少可识别的布尔配置时明确报错，不默认按关闭处理。
提交携带原值，当前值变化则返回冲突。相同值不重复触发文件扫描。

## 确认范围与限制

原生命令修改运行时配置，并扫描现有映射文件设置读取建议。
`CONFIG_CONFIRMED` 表示命令收到确认且配置回读一致，不表示每个文件的操作系统调用成功。
Windows 会跳过文件扫描；其他平台上的扫描异常和部分 `madvise` 失败可能只写日志，
Broker 仍可返回成功。本功能无法确认内核的实际预读行为，也不提供性能提升保证。

当命令异常、回读失败或回读不一致时，回执为 `UNKNOWN`，并分别保留是否收到命令确认、
原配置和已取得的回读。不能把未知结果当作未修改，不自动重试或回滚。
浏览器关闭、切换实例或断网不能撤回已发送的 Broker 请求。

原生操作没有显式持久化配置；重启后的行为应以部署文件和 Broker 启动配置为准。
其他后续配置保存动作也可能持久化当前运行时字段，不应依赖重启作为自动回滚。
需持久化时应通过既定配置发布流程处理，并重新验证启动行为。

## 验证与代码回滚

POST 沿用管理员权限拦截。操作审计记录节点地址、原值、目标值和结果；
审计存储失败不会覆盖已经产生的 Broker 回执。

本地测试覆盖实例与节点绑定、参数映射、旧值冲突、未知配置、RPC 与回读分别失败、
中断标记、审计失败和前端确认生命周期。浏览器通过本地 GET/POST 拦截验证，
未改变真实 Broker 配置。真实 Linux/Windows 内核效果、I/O 性能、压力、容量与故障切换验证未执行。

无新增依赖或数据库迁移。撤销代码提交即可移除入口和接口；运行时配置按前述步骤恢复。
最后核验日期：2026-09-08。

源码依据为 RocketMQ 5.5.0 的
[AdminBrokerProcessor.setCommitLogReadaheadMode](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/broker/src/main/java/org/apache/rocketmq/broker/processor/AdminBrokerProcessor.java#L1007)、
[CommitLog.scanFileAndSetReadMode](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/store/src/main/java/org/apache/rocketmq/store/CommitLog.java#L2513)
和 [Configuration](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/remoting/src/main/java/org/apache/rocketmq/remoting/Configuration.java#L281)。
