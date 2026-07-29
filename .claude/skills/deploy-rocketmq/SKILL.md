---
name: deploy-rocketmq
description: 部署 studio 测试集群。从 Apache RocketMQ 开源 develop 分支源码构建镜像，用 docker compose 在本地拉起 NameServer + 双 Broker + Proxy 集群，并启动 1 TPS 持续收发消息的 Producer / Consumer（均开启消息轨迹）。当用户说 部署 studio 测试集群 时触发。
---

# 部署 studio 测试集群

在本地用 docker compose 部署一套开源 RocketMQ 集群（NameServer + 双 Broker + Proxy），
集群名 `rocketmq-studio`，Broker 开启消息轨迹，附带 Producer / Consumer 容器以
1 TPS 持续收发消息并上报轨迹，供 RocketMQ Studio 本地调试使用。

所有部署产物在 `deploy/rocketmq/` 目录，详见其中的 `README.md`。

## 前置条件

- 已安装 `docker`（含 `docker compose`）。
- 网络可访问 `github.com`（构建阶段 git clone）；国内环境由 `build.sh` 自动切换
  apt / Maven 阿里源。
- 宿主机端口 9876、10909、10911、10912、10919、10921、10922、8080、8081 未被占用
  （与 studio 主 compose 的端口约定一致，不冲突）。

## 执行步骤

### 1. 构建镜像

```bash
cd deploy/rocketmq
./build.sh          # 自动判断地理位置：国内走阿里源，国外用默认源
```

- Dockerfile 为多阶段构建：builder 阶段 `git clone --depth 1 -b develop
  https://github.com/apache/rocketmq.git` 后执行
  `mvn -Prelease-all -DskipTests clean install`，运行阶段仅保留
  `bin/ conf/ lib/` 产物，基础镜像 `alibabadragonwell/dragonwell:17-anolis`（yum 系，国内拉取快）。
- 首次构建需 10-20 分钟；如需指定其他分支或 tag：
  `docker build --build-arg ROCKETMQ_BRANCH=rocketmq-all-5.5.0 -t apache-rocketmq:develop .`

### 2. 拉起集群

```bash
docker compose up -d
```

服务说明：

| 服务 | 说明 |
|------|------|
| nameserver | `mqnamesrv`，端口 9876 |
| broker-0 / broker-1 | `mqbroker -c broker-{0,1}.conf`，集群 `rocketmq-studio`，broker 名 `rocketmq-studio-{0,1}`，各限 1C2G，已开启 `traceOn=true`、`traceTopicEnable=true` |
| proxy | `mqproxy -pc rmq-proxy.json`，集群模式，remoting 8080 / gRPC 8081，1C2G，已关闭 topic 消息类型校验（`enableTopicMessageTypeCheck=false`，避免自动建 topic 类型为 UNSPECIFIED 时发送被拒） |
| producer | 编译并运行 `clients/TraceProducer.java`，1 TPS 发送到 `StudioTest`（带 Key `studio-key-<n>`），`enableMsgTrace=true`，接入点 `proxy:8080`（流量经 proxy 转发） |
| consumer | 编译并运行 `clients/TraceConsumer.java`，Push 消费 `StudioTest`，`enableMsgTrace=true`，接入点 `proxy:8080` |

- 两个 broker 容器内都监听 10909/10911/10912，broker-1 的宿主机端口错开为
  10919/10921/10922 避免冲突；容器网络内互访始终走 `broker-{0,1}:10911`。
- 堆内存通过 `JAVA_OPT_EXT` 环境变量收敛（开源启动脚本会将其追加到 JVM 参数末尾，
  覆盖脚本内置的大堆默认值），适合本地小内存环境。
- producer / consumer 直接用运行镜像自带 JDK + `lib/*` 依赖现场编译，无需额外构建。

### 3. 验证

```bash
# 全部容器 Up
docker compose ps

# 收发日志：producer 每秒一条 SEND_OK，consumer 对应 consume
docker compose logs -f producer consumer

# 集群注册正常：应看到 rocketmq-studio 下两个 broker
docker compose exec nameserver sh bin/mqadmin clusterList -n nameserver:9876

# 消息轨迹已落盘（RMQ_SYS_TRACE_TOPIC 有数据即轨迹链路打通）
docker compose exec broker-0 sh bin/mqadmin consumeMessage \
  -n nameserver:9876 -t RMQ_SYS_TRACE_TOPIC -c 5
```

验证通过标准：clusterList 能看到 `rocketmq-studio` 集群下 `rocketmq-studio-0`、
`rocketmq-studio-1` 两个 broker；producer 日志持续输出 `SEND_OK`；
`RMQ_SYS_TRACE_TOPIC` 能消费出轨迹数据。

### 4. 清理

```bash
docker compose down -v
```

## 常用查询命令（mqadmin）

以下命令均已实测可用，在任意集群容器内执行（示例用 nameserver）。
msgId / Key 可从 producer 日志获取：`send #16 SEND_OK <msgId> key=studio-key-16`。

### 查看集群状态 clusterList

```bash
docker compose exec nameserver sh bin/mqadmin clusterList -n nameserver:9876
```

输出每个 broker 的地址、版本、InTPS/OutTPS（本集群稳态约 1.0，对应 1 TPS 收发）、
磁盘水位等。`#ACTIVATED=true` 表示 broker 心跳正常。

### 按 msgId 精确查消息 queryMsgByUniqueKey

```bash
docker compose exec nameserver sh bin/mqadmin queryMsgByUniqueKey \
  -n nameserver:9876 -t StudioTest -i <msgId>
```

- `-i` 传客户端 msgId（即 UNIQ_KEY，producer 日志里那个）。
- 输出 Topic、Tags、Keys、Queue、Born/Store 时间与主机、Properties（可看到
  `TRACE_ON=true`），消息体落盘到 `/tmp/rocketmq/msgbodys/` 可直接查看。

### 查消息轨迹 queryMsgTraceById

```bash
docker compose exec nameserver sh bin/mqadmin queryMsgTraceById \
  -n nameserver:9876 -i <msgId>
```

- 从 `RMQ_SYS_TRACE_TOPIC` 检索该消息的轨迹：Pub（发送）与 Sub（消费）记录，
  含消费组、客户端地址、耗时、成功与否。
- 轨迹是客户端异步批量上报的，消息发送后需等几秒才能查到；
  只有 Pub 没有 Sub 说明消费轨迹尚未上报或消费未发生。

### 按业务 Key 查消息 queryMsgByKey

```bash
docker compose exec nameserver sh bin/mqadmin queryMsgByKey \
  -n nameserver:9876 -t StudioTest -k studio-key-16
```

- 依赖发送时 `msg.setKeys(...)` 建立的索引（本集群 producer 已设置
  `studio-key-<序号>`），返回命中的 msgId、Queue、Offset。
- 拿到 msgId 后可再用 `queryMsgByUniqueKey` / `queryMsgTraceById` 深挖详情与轨迹。

## 常见问题

- **producer 启动初期报 route not found / 类型校验失败**：broker 注册与 topic
  自动创建需要几秒，发送循环与 `restart: on-failure` 会自动重试，稍等即可。
- **宿主机客户端连不上 broker**：`brokerIP1=broker-{0,1}` 仅容器网络内可达；
  宿主机直连需改 `conf/broker-{0,1}.conf` 为 `brokerIP1=127.0.0.1` 并重启。
  Studio 后端若跑在另一个 compose 网络中，建议共享同一个 docker network。
- **构建阶段 clone 慢或失败**：确认能访问 github.com，或用
  `--build-arg ROCKETMQ_REPO=<镜像仓库地址>` 换源；Maven 慢用 `./build.sh`
  自动切阿里源。
