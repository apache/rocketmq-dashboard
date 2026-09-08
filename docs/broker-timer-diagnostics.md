# Broker 定时服务诊断

最后验证日期：2026-09-08。适用于所选 Apache 实例中登记了主节点的 Broker。

## 使用

进入集群管理的 Broker 列表，选择实例，在目标行点击 `Timer status`。
面板展示主节点地址、采样时间、定时配置和运行指标；点击 `Refresh snapshot` 手动重取。
关闭面板或切换实例会取消浏览器请求并忽略迟到结果，不增加后台轮询。
Mock 模式不提供此查询，云实例沿用页面原有的 Apache 实例筛选。

配置包括时间轮开关、入队与出队暂停、RocksDB 定时引擎与扫描暂停、撤回开关、
时间精度和最大延迟。页面保留 Broker 返回的原值，鼠标悬停标签可查看原配置名。
这些配置用于解释当前观测，不提供配置修改或定时引擎切换。

## 指标含义

| Broker 字段 | 展示单位与含义 |
| --- | --- |
| `timerReadBehind` | 秒，定时出队进度落后时间；不是毫秒，也不是消费组延迟 |
| `timerOffsetBehind` | 队列位置数，定时入队进度差 |
| `timerCongestNum` | 时间轮待处理条目，包含尚未到期的消息，不能直接解释为超期消息数 |
| `timerEnqueueTps` | 消息/秒，定时入队速率 |
| `timerDequeueTps` | 消息/秒，定时出队速率 |

计数通过 JSON 字符串传递，保留超过 JavaScript 安全整数范围的精度。
未返回的字段显示 `Not reported`，不回填为零。关闭时间轮时，Broker 仍可能返回零指标；
面板明确提示关闭状态，不根据这些零值给出健康结论。RocksDB 引擎切换期间需结合各暂停配置判断。

## 接口与边界

`GET /api/brokers/timer?instanceId=...&brokerName=...` 使用现有认证和实例管理客户端，
通过该实例的 NameServer 查询 Broker，只读取主节点 ID 0，不接受任意地址或回退到从节点。
名称不存在返回 404；未登记主节点返回 409。

配置与运行指标是两次独立 RPC，并非原子快照。单个 RPC 失败时返回对应来源的错误，
另一个来源仍可查看；两个来源均失败时也会显示失败原因，不伪造可用数据。
版本不支持的字段保持缺失。仅返回列出的定时字段，不将全部 Broker 配置转发给浏览器。
中断会终止后续探测并恢复线程中断标记。

字段语义依据 RocketMQ 5.5.0 官方
[TimerMessageStore](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/store/src/main/java/org/apache/rocketmq/store/timer/TimerMessageStore.java)
与 [AdminBrokerProcessor](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/broker/src/main/java/org/apache/rocketmq/broker/processor/AdminBrokerProcessor.java)。
其他版本或引擎应结合相应 Broker 实现解释，不将本面板视为自动告警或健康检查。

## 验证与回滚

后端：`mvn -B -ntp -Dtest=BrokerTimerTest,RuntimeAdminClientResolverTest,ClusterControllerTest verify`。
前端：运行 `brokerTimer.test.ts`、`BrokerTimerDialog.test.tsx` 和 `BrokerCluster.test.tsx`，
并执行修改文件 ESLint 与 `npm run build`。浏览器使用本地受控响应验证真实列表入口和面板。
真实集群 E2E、性能、压力、容量和混沌验证未执行。

无新增依赖、配置、数据库字段或 Broker 写入。无迁移，直接部署；
回滚代码即可移除接口和入口，不涉及数据回滚。
