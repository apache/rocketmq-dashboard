---
name: broker-busy
description: 排查 broker busy / 写入拒绝。当用户报"broker busy""REJECT""写不进""CPU 打满""broker 卡"时触发。通过 mqctl 调 studio 工具拿 broker 运行状态和关键配置,判断是 IO 瓶颈、GC 还是锁竞争,给出调优建议。
---

# broker busy 排查

## 适用场景

broker 报 `REJECT[BROKER_BUSY]` / 写入拒绝;CPU 打满;磁盘跟不上;put TPS 异常下降。

## 诊断流程

### 第 1 步:看 broker 运行状态

```
mqctl diagnose broker-busy
```

或单独取 broker 状态(需 studio 已暴露该 tool,见数据源):

```
mqctl call rmq.broker.status --cluster <集群> --input '<入参 JSON,以 studio 暴露时的 schema 为准>'
```

> `rmq.broker.status` 当前未暴露,上面的命令会失败;可用主仓库 mqadmin 兜底(直连):`mqadmin brokerStatus -n <namesrv> -b <brokerAddr>`。

关注:
- put/get TPS —— 是否吞吐异常。
- store 统计 —— 积压、commit/flush 是否滞后。
- runtime —— JVM、线程状态。

### 第 2 步:看关键 IO 配置

```
mqctl call rmq.broker.config --cluster <集群> --input '<入参 JSON,以 studio 暴露时的 schema 为准>'
```

> 同样未暴露,mqadmin 兜底:`mqadmin getBrokerConfig -n <namesrv> -b <brokerAddr>`。

关注这几个 IO 相关配置:
- `flushDiskType` —— 同步刷盘 / 异步刷盘。
- `transientStorePoolEnable` —— 是否开了堆外内存池(写绕开 page cache)。
- `useReentrantLockWhenPutMessage` —— 写锁类型(ReentrantLock vs 自旋)。
- `flushCommitLogLeastPages` / `commitCommitLogLeastPages` —— 刷盘/commit 攒页阈值。

### 第 3 步:机器级指标(CPU/磁盘/GC)

> **卡点**:OS 级 CPU/磁盘 util、JVM GC 这些机器指标,studio 直连拿不到,需在 broker 机器装 agent 或接 JMX(待确认)。目前没有现成 tool,需现场配合(登机器 top/iostat/jstat)。

## 判读规则

| 现象 | 判断 | 建议 |
|---|---|---|
| 磁盘 util 高 + flush 滞后 | IO 瓶颈 | 开 `transientStorePoolEnable=true`(堆外池)、改异步刷盘、换更快磁盘 |
| GC 频繁 / 停顿长 | JVM 内存问题 | 调堆/直接内存、查内存泄漏 |
| `putMessageLock` 等待高 | 写入串行瓶颈 | 调 `useReentrantLockWhenPutMessage`、拆 topic 分散写入 |
| CPU 用户态高(非 IO) | 计算密集 | 查是否有重过滤/序列化开销,扩 broker |

## 与消费堆积的关系

若消费堆积排查走到"不是客户端的锅、broker 给不动",转入本 skill 看 broker 是否 busy。

## 数据源(依赖的 tool)

| tool | 现状 | 用途 |
|---|---|---|
| `rmq.cluster.list` | 已暴露(集群级,非 broker 级) | 连通性确认、集群上下文 |
| `rmq.broker.status` | 未暴露(mqadmin brokerStatus 有数据) | broker 运行状态 |
| `rmq.broker.config` | 未暴露(mqadmin getBrokerConfig 有数据) | broker IO 配置 |
| broker 机器级指标(CPU/磁盘/GC) | 无采集机制,需 agent/JMX(待确认) | OS 与 JVM 指标 |
