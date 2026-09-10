# RocksDB 双写队列检查

## 使用场景

在文件 ConsumeQueue 与 RocksDB ConsumeQueue 双写迁移期间，管理员需要检查
两种存储的索引写入进度。Broker 集群页的 `RocksDB check` 为单个已登记节点
提供配置审阅、单 Topic 检查提交和日志定位信息。

该功能不切换存储、不修复索引，也不修改双写配置。检查本身会访问存储、占用异步工作线程，
可能创建比较存储的队列对象，因此启动检查使用管理员 POST，预览使用 GET。

## 操作流程

1. 选择 Apache 实例，在目标 Broker 地址行打开检查窗口。
2. 点击 `Read storage scope`，读取该节点登记、双写配置和已配置 Topic。
3. 选择一个 Topic，输入正整数 Unix 毫秒时间。默认值为打开窗口前十分钟。
4. 确认节点、Topic、检查时间及扫描负载后点击 `Start check`。
5. 保存回执中的节点、Topic、检查时间、提交及响应时间，到该 Broker 日志查看结果。

预览只接受明确的 `rocksdbCQDoubleWriteEnable` 布尔值及可读取的
`combineCQLoadingCQTypes`。只有双写启用且包含 `default`、`defaultRocksDB`
两种存储才允许提交；提交前重新读取配置，变化时要求重新审阅。
配置反映当前属性，不能证明正在运行的具体存储实现，应同时核对版本与启动日志。

Topic 必须仍在所选节点配置中；不支持空 Topic 代表全集群或全部 Topic 的隐式扫描。
接口使用实例既有认证客户端，不接受未登记节点地址，不自动转向其它副本。

## 检查时间的含义

官方实现以 RocksDB 存储时间查找队列位点，并取它与最小保留位点的较大值，
向文件 ConsumeQueue 的当前末尾逐条比较物理位点、大小、标签及批次信息。
时间查找异常可能回退到最小保留位点；早于检查时间的队尾可能直接跳过。
因此时间参数既不是精确工作量上限，也不代表完整历史审计或原子快照。

时间越早可能扫描越多数据。应先在测试集群测量负载，在维护窗口选择适当时间，
避免并发启动多个检查。界面没有调度、后台轮询、取消或自动重试。
关闭页面只能中止浏览器等待，不能取消已经受理的 Broker 任务。

## 回执与结果

| 回执 | 含义与下一步 |
| --- | --- |
| `ACCEPTED` | Broker 原始状态为 2，异步检查已受理；尚无一致性结论 |
| `BROKER_RESPONSE` | 返回其它原始状态及详情；保留协议值，不据此宣称索引健康 |
| `UNKNOWN` | 无响应或通信异常；检查可能已启动，先查日志再决定是否重提 |

在提交时间附近搜索 `checkRocksdbCqWriteProgress result:` 以及
`checkRocksdbCqWriteProgress error`。原生 API 不返回任务 ID，也没有查询最终结果接口；
并发任务日志可能重叠，不能用时间戳伪造唯一关联。
原生日志中的 0、1、2、3 分别表示检查通过、不通过、检查中和错误。
非组合存储、缺少比较存储也可能产生“无需检查”的状态 0，应读取详情而非仅看数字。

## 验证与回滚

本地测试覆盖节点登记、配置变化、Topic 删除、时间边界、超时与中断、审计失败、
管理员权限、HTTP 契约、确认失效及组件卸载。浏览器通过本地拦截验证请求，未对真实 Broker 扫描。
真实双写集群的索引差异、日志关联、插件兼容性、性能、容量及故障注入尚未验证。

无新增依赖、数据库迁移或配置迁移。撤销提交即可移除入口；已经启动的原生检查
没有取消接口，撤销代码不能停止它。检查不提供修复操作，发现差异后需按存储迁移方案处理。

核验日期：2026-09-08。依据 RocketMQ 5.5.0 的
[AdminBrokerProcessor](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/broker/src/main/java/org/apache/rocketmq/broker/processor/AdminBrokerProcessor.java#L482)
与 [CombineConsumeQueueStore](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/store/src/main/java/org/apache/rocketmq/store/queue/CombineConsumeQueueStore.java#L456)。
