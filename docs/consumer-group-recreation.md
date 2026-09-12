# 消费组重建与配置保留

Studio 的 Apache 消费组创建接口支持对同名消费组重复执行，CSV 导入也使用这条路径。重建用于恢复或同步资源，不应自动恢复消费、取消顺序消费或覆盖 Broker 独立配置。

## 原有问题

旧实现为每次创建构造同一份默认 `SubscriptionGroupConfig`，随后发送给所有目标 Broker。已有消费组的 `consumeEnable=false` 会变成 `true`，`consumeMessageOrderly=true` 会变成 `false`，重试队列数、消费超时、Broker 偏好等也会恢复默认值。

这会直接影响已暂停的消费流量和顺序消费行为。只修复页面的重复点击无法解决从 CSV 重新导入、脚本重复创建或恢复已有组的场景。

另一个同领域问题出现在配置更新：Broker 返回的属性名称是 `priority.factor` 等存储键，而更新接口接受 `+priority.factor` 或 `-priority.factor` 这样的修改操作。直接回传存储属性会使原本合法的重试次数修改被 Broker 拒绝。

## 修复行为

每个 Broker 使用自己的消费组配置作为更新基础，保留其消费开关、顺序消费、广播开关、重试队列数、超时和其他协议字段。不会把某个 Broker 的设置复制给整个集群。

| 请求或状态 | 行为 |
| --- | --- |
| 已有组，创建请求带正数重试次数 | 仅替换重试次数，保留其他配置 |
| 已有组，创建请求没有重试次数 | 保留已有值，包括通过设置接口配置的 0 次 |
| Broker 明确返回组不存在 | 使用现有新建默认值，并应用请求中的正数重试次数 |
| 权限不足、连接失败或超时 | 返回失败，不将不可读取的配置当成不存在 |
| 已有 Broker 属性 | 发送空属性修改集合，由 Broker 保留已有属性 |
| 部分 Broker 完成后失败 | 审计记录已完成与总 Broker 数，不伪报全部成功 |

创建接口的旧模型用 0 同时表示省略值，因此需要将重试次数明确设为 0 时，继续使用消费组设置接口。本次不改变该接口约定。

数据库记录和返回结果使用实际写入的重试次数。各 Broker 原先存在差异、且请求未指定重试次数时，元数据采用按地址排序的首个 Broker 值，与逐 Broker 保留策略兼容。

## 操作边界

RocketMQ 的组配置读取接口在 Broker 启用自动创建时可能创建缺失组，因此整个流程不承诺只读预检或分布式原子性。网络故障也可能发生在某个 Broker 已接受更新之后；应根据审计结果检查实际状态并重试。

属性处理依据 RocketMQ 5.5.0 的 [SubscriptionGroupManager](https://github.com/apache/rocketmq/blob/rocketmq-all-5.5.0/broker/src/main/java/org/apache/rocketmq/broker/subscription/SubscriptionGroupManager.java)：空修改集合保留现有属性。测试直接调用对应 SDK 中的 `AttributeUtil` 验证协议行为。

## 验证与兼容

使用 Java 21，在 `server` 目录执行：

```bash
mvn -B -ntp -Dtest=ConsumerGroupRecreationTest,RocketMQAdminClientImplTest,MetadataServiceTest test
```

验证包括暂停且顺序消费的组重新创建、不同 Broker 的独立策略、0 次重试保留、明确缺失、权限错误、部分执行失败、属性修改协议以及既有创建和元数据服务测试。

无迁移，直接替换。没有新增依赖、配置或数据库字段。回滚修复提交即可恢复原行为；已经被旧逻辑覆盖的 Broker 设置需要运营者依据原配置恢复，本次不会猜测原值。
