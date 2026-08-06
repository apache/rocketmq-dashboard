---
name: consumer-lag
description: 排查消费堆积(consumer lag)。当用户说"消费堆积了""lag 高""消费跟不上""消息堆着"时触发。通过 mqctl 调 studio 工具拿消费组堆积量(lag)和客户端线程栈,判断是卡在客户端还是 broker,给出处置建议。
---

# 消费堆积排查

## 适用场景

某消费组 lag 持续走高、不收敛;消费 TPS 远低于生产 TPS;消费端无报错但消息不前进。

## 诊断流程

### 第 1 步:量化堆积

直接跑诊断 skill,它会调 `rmq.group.list` 取堆积并按阈值分级:

```
mqctl diagnose consumer-lag --cluster <集群>
```

或单独取原始数据:

```
mqctl call rmq.group.list --cluster <集群>
```

关注:
- `totalLag` —— 总堆积量。
- per-queue lag —— 堆积分布是否均匀(需 `rmq.group.progress`,见数据源)。
- `onlineInstances` —— 消费端在线实例数。

### 第 2 步:区分根因(卡客户端 vs broker)

**lag 高 + `onlineInstances == 0`**:消费端没起来 / 重平衡异常。先查客户端日志、重启、确认订阅关系。

**lag 高 + 在线 > 0**:需要看消费端线程栈,判断卡没卡。取客户端 stack(按社区说明,机制为 studio 经 broker 转发、由 client 上报线程栈):

```
mqctl call rmq.client.stack --cluster <集群> --input '{"group":"<消费组>","clientId":"<id>"}'
```

> `rmq.client.stack` 当前 studio 未暴露(501 stub),上面的命令会失败;mqadmin 也没有取消费端线程栈的命令,需登消费端机器用 `jstack <pid>` 取栈。

判读:
- 线程卡在某行(死锁 / 慢调用 / 阻塞 IO,如卡在查 DB、调外部接口)→ **卡在客户端**。处置:修消费代码、扩并发、排查阻塞点。
- 线程正常在跑、没卡 → 不是客户端的锅,broker 给不动或生产太快 → 转入 `broker-busy` 排查。

### 第 3 步:细化(若 lag 分布不均)

- lag 集中在个别 queue → 可能单 broker / 分区倾斜。
- lag 各 queue 均匀 → 整体处理不过来,扩并发或扩 broker。

## 处置建议

| 根因 | 处置 |
|---|---|
| 客户端未启动/重平衡异常 | 查客户端日志、重启、确认订阅关系 |
| 客户端处理慢(线程卡) | 扩并发、优化消费逻辑、排查阻塞点 |
| broker 给不动 | 转 `broker-busy` 排查 IO/GC/锁 |
| 生产太快 | 限流上游、扩 broker |

## 数据源(依赖的 tool)

| tool | 现状                                                             | 用途 |
|---|------------------------------------------------------------------|---|
| `rmq.group.list` | 已暴露(含 totalLag)                                              | 量化堆积 |
| `rmq.group.progress` | 未暴露                                                           | per-queue lag 分布 |
| `rmq.client.stack` | 501 stub;按社区说明机制为 studio 经 broker 让 client 上报,待实现 | 消费端线程栈 |
