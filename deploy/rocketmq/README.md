# RocketMQ 开源集群本地部署（docker compose）

从 Apache RocketMQ 开源 develop 分支源码构建镜像，用 docker compose 拉起
NameServer + 双 Broker + Proxy（集群名 `rocketmq-studio`），并附带 1 TPS
持续收发消息的 Producer / Consumer 容器（均开启消息轨迹），供 RocketMQ Studio
本地调试使用。

## 目录结构

```
deploy/rocketmq/
├── Dockerfile              # git clone develop 分支 + Maven 构建，多阶段产出运行镜像
├── build.sh                # 构建入口：自动判断地理位置，国内走阿里源
├── docker-compose.yml      # nameserver / broker-0 / broker-1 / proxy / producer / consumer
├── conf/
│   ├── broker-0.conf       # rocketmq-studio-0，traceOn + traceTopicEnable 开启轨迹
│   ├── broker-1.conf       # rocketmq-studio-1，同上
│   └── rmq-proxy.json      # Proxy 集群模式，指向 nameserver:9876，关闭 topic 类型校验
└── clients/
    ├── TraceProducer.java  # 1 TPS 发送（带 Key），enableMsgTrace=true，经 proxy:8080 接入
    └── TraceConsumer.java  # Push 消费，enableMsgTrace=true，经 proxy:8080 接入
```

## 快速开始

```bash
cd deploy/rocketmq

# 1. 构建镜像（首次约 10-20 分钟，含 clone + mvn 编译；国内自动切阿里源）
./build.sh

# 2. 拉起集群与收发客户端
# 此 Compose 项目会自动创建名为 rocketmq_net 的共享网络；Studio 的 Compose
# 配置会以 external 网络方式加入它，因此首次启动无需手工执行 docker network create。
docker compose up -d

# 3. 观察收发日志（1 TPS）
docker compose logs -f producer consumer
```

## 验证

```bash
# 集群状态：rocketmq-studio 下两个 broker
docker compose exec nameserver sh bin/mqadmin clusterList -n nameserver:9876

# 消息轨迹数据（轨迹写入 RMQ_SYS_TRACE_TOPIC）
docker compose exec broker-0 sh bin/mqadmin consumeMessage \
  -n nameserver:9876 -t RMQ_SYS_TRACE_TOPIC -c 5
```

更多查询命令（queryMsgByUniqueKey / queryMsgTraceById / queryMsgByKey）见
`.claude/skills/deploy-rocketmq/SKILL.md`。

## 接入端点

| 组件 | 容器内地址 | 宿主机地址 |
|------|-----------|-----------|
| NameServer | nameserver:9876 | localhost:9876 |
| Broker-0 | broker-0:10911 | localhost:10911 |
| Broker-1 | broker-1:20911 | localhost:20911 |
| Proxy remoting | proxy:8080 | localhost:8080 |
| Proxy gRPC | proxy:8081 | localhost:8081 |

注意：`brokerIP1=broker-{0,1}`，仅容器网络内客户端可直连 Broker。
宿主机客户端直连需改为 `brokerIP1=127.0.0.1` 后重启对应 broker。

## 清理

```bash
docker compose down -v
```
