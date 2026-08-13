---
name: deploy-rocketmq
description: 部署 RocketMQ Studio 与开源 RocketMQ。当用户说 部署 studio、把 studio 部署到远程机器/测试机、更新远程 studio、单机部署开源 rocketmq、远程部署 rocketmq 集群、单机部署 rocketmq 测试客户端、部署 studio 测试集群 时触发。
---

# 部署 RocketMQ Studio / 开源 RocketMQ

三种部署模式，全部遵循同一条主线：

> **本地把源码打成 tar.gz → 复制到目标机器 → 在目标机器上构建镜像并启动。**
> 构建统一在目标机执行，复用目标机本地缓存（Maven `~/.m2`、docker 层缓存），
> 不做镜像中转。

| 模式 | 场景 |
|------|------|
| A. Studio 部署到目标机器 | 把本项目（server + web + mysql）部署到一台远程 Linux 机器，浏览器访问 |
| B. 单机部署开源 RocketMQ | 在目标机器部署纯净开源集群（nameserver + 双 broker + proxy），不带测试挂具 |
| C. 单机部署 RocketMQ 测试客户端 | 在目标机器部署模式 B 集群 + producer/consumer 1 TPS 轨迹挂具，供 Studio 调试 |

> ⚠️ `deploy/deploy.sh` 是旧的 podman 时代脚本，与当前 docker compose 流程不一致，**不要使用**。

## 通用前置条件（目标机器）

- 已安装 `docker`（含 `docker compose`）。国内机器安装要点：
  - 用阿里源：`dnf config-manager --add-repo=https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo`；
    Alibaba Cloud Linux 3 需 `sed -i 's#$releasever#8#g' /etc/yum.repos.d/docker-ce.repo`（releasever=3 无对应目录）。
  - 配 registry-mirrors（国内拉不动 Docker Hub，mysql:8.0 / dragonwell 等基础镜像必需），
    写入 `/etc/docker/daemon.json` 的 `registry-mirrors`（如 `https://docker.m.daocloud.io` 等可用镜像站），
    然后 `systemctl enable --now docker`。
- 用户已配置好到目标机的 SSH 密钥/证书访问（可免密登录；用
  `ssh -o BatchMode=yes <user>@<host> whoami` 验证）。未配置时先与用户确认登录方式。
- 目标机器语言必须为 `en_US.UTF-8`：`export LANG=en_US.UTF-8`。SSH 会话内临时生效；
  需持久化时追加到 `~/.bashrc` 或 `/etc/profile.d/`。用 `ssh <user>@<host> 'echo $LANG'` 验证。
- 放行所需端口：模式 A 放行 web 端口（默认 6789）；模式 B/C 如需跨机访问放行
  9876、10909、10911、10912、20909、20911、20912、8080、8081。

---

## 模式 A：部署 Studio 到目标机器

### 核心原则

- **后端编译在目标机器上做**：复用目标机宿主机 `~/.m2/repository` 缓存，增量构建快。
- **国内目标机必须先检查 Maven 镜像源**（见步骤 2），否则依赖下载极慢或失败。
- 默认 `STUDIO_AUTH_LOGIN_REQUIRED=false` 免登录，方便直接查看效果；如需开启登录，
  改为 `true` 并配好 admin 账号密码后重建 server。

### 1. 初始化部署目录与 .env（仅首次）

```bash
SSH="ssh <user>@<host>"
$SSH bash -s <<'EOF'
mkdir -p /opt/rocketmq-studio
docker network inspect rocketmq_net >/dev/null 2>&1 || docker network create rocketmq_net
cat > /opt/rocketmq-studio/.env <<'ENV'
TZ=Asia/Shanghai
MYSQL_ROOT_PASSWORD=rocketmq
STUDIO_AUTH_LOGIN_REQUIRED=false
STUDIO_AUTH_ADMIN_USERNAME=admin
STUDIO_AUTH_ADMIN_PASSWORD=rocketmq
RMQ_LLM_TOKEN=
RMQ_ANTHROPIC_BASE_URL=
ENV
chmod 600 /opt/rocketmq-studio/.env
EOF
```

- `MYSQL_ROOT_PASSWORD` 同时被 compose 用于 mysql 和 server 的 `SPRING_DATASOURCE_PASSWORD`，务必一致。
- compose 中 `rocketmq` 网络声明为 external（名 `rocketmq_net`），不存在时 `up` 会失败，必须先创建。

### 2. 检查并配置 Maven 阿里源（国内目标机必做）

后端 JAR 用 maven 容器挂载宿主机 `~/.m2` 编译，镜像源配置来自宿主机 `~/.m2/settings.xml`。
先检查：

```bash
$SSH 'grep -q "maven.aliyun.com" ~/.m2/settings.xml 2>/dev/null && echo OK || echo MISSING'
```

输出 `MISSING` 时帮目标机写入（已有 settings.xml 则把 mirror 合并进 `<mirrors>`，不要整文件覆盖）：

```bash
$SSH 'mkdir -p ~/.m2 && cat > ~/.m2/settings.xml <<'"'"'XML'"'"'
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
XML'
```

### 3. 本地打包源码并复制到目标机

```bash
cd <项目根目录>   # rocketmq-studio 仓库根
tar czf /tmp/src.tar.gz --exclude='web/node_modules' --exclude='web/dist' --exclude='server/target' server web deploy
scp /tmp/src.tar.gz <user>@<host>:/opt/rocketmq-studio/
$SSH 'cd /opt/rocketmq-studio && rm -rf server web deploy && tar xzf src.tar.gz && rm src.tar.gz'
```

### 4. 在目标机上编译后端 + 构建镜像

复用宿主机 `~/.m2` 缓存编译 JAR，再用 Dockerfile 的 `runtime-prebuilt` target 打镜像
（不要走默认多阶段 build target，那会在 Docker 内从零下载依赖、用不上宿主机缓存）：

```bash
$SSH 'cd /opt/rocketmq-studio && \
  docker run --rm -e HOME=/tmp \
    -v $PWD/server:/app -v $HOME/.m2:/maven-cache -w /app \
    maven:3.9.16-eclipse-temurin-21 \
    mvn -B -ntp -s /maven-cache/settings.xml -Dmaven.repo.local=/maven-cache/repository package -DskipTests && \
  docker build --target runtime-prebuilt -t rocketmq-server:latest server/'
```

- 若目标机 `~/.m2/settings.xml` 不存在（海外机器通常不需要镜像源），去掉 `-s /maven-cache/settings.xml`。
- maven 基础镜像本身也要拉取，国内依赖通用前置条件里的 registry-mirrors。

构建 web（页脚「构建 <短commit>」由 `VITE_GIT_COMMIT` 注入，先取当前 git 短 commit）：

```bash
$SSH 'cd /opt/rocketmq-studio && \
  docker build --build-arg VITE_GIT_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo dev) -t rocketmq-web:latest web/'
```

### 5. 启动与验证

```bash
$SSH 'cd /opt/rocketmq-studio && docker compose --env-file .env -f deploy/docker-compose.yml up -d'

# 三容器均 healthy 即成功
$SSH 'docker ps'
$SSH 'docker exec rocketmq-server curl -fsS http://localhost:8888/actuator/health'
curl -s -o /dev/null -w 'web %{http_code}\n' http://<host>:6789/
```

### 6. 更新代码后重新部署

重复步骤 3 → 5 即可。注意：

- mysql 数据在独立数据卷 `mysql-data`，重建 server/web 不影响数据；**不要 `down -v`**。
- 仅改 server 时只需重编 server（步骤 4 前半段）并 `docker compose ... up -d rocketmq-server`。

### 可选：注入演示数据

`deploy/mysql/upgrade-demo-instance.sql` / `upgrade-demo-acl.sql` 可注入演示实例/ACL。
导入后必须补 vendor，否则 `/api/instances` 报 500（demo SQL 早于 vendor 功能）：

```bash
$SSH 'docker exec rocketmq-studio-mysql mysql -uroot -procketmq rocketmq \
  -e "UPDATE rmq_instance SET vendor=\"APACHE\" WHERE vendor IS NULL OR vendor=\"\";"'
```

---

## 模式 B：单机部署开源 RocketMQ（纯净集群）

在目标机器部署一套开源 RocketMQ 集群（NameServer + 双 Broker + Proxy，集群名
`rocketmq-studio`，Broker 开启消息轨迹），不带 producer/consumer 测试挂具，
供任意客户端或 Studio 接入。部署产物在仓库 `deploy/rocketmq/` 目录。

### 1. 本地打包并复制到目标机

```bash
SSH="ssh <user>@<host>"
tar czf /tmp/rocketmq-deploy.tar.gz -C deploy rocketmq
scp /tmp/rocketmq-deploy.tar.gz <user>@<host>:/tmp/
$SSH 'mkdir -p /opt/rocketmq && rm -rf /opt/rocketmq/rocketmq && \
  tar xzf /tmp/rocketmq-deploy.tar.gz -C /opt/rocketmq && rm /tmp/rocketmq-deploy.tar.gz'
```

### 2. 在目标机上构建镜像

```bash
$SSH 'cd /opt/rocketmq/rocketmq && ./build.sh'
```

- `build.sh` 自动判断地理位置：国内切阿里 apt/Maven 源，国外用默认源。
- Dockerfile 为多阶段构建：builder 阶段 `git clone --depth 1 -b develop
  https://github.com/apache/rocketmq.git` 后执行
  `mvn -Prelease-all -DskipTests clean install`，运行阶段仅保留
  `bin/ conf/ lib/` 产物，基础镜像 `alibabadragonwell/dragonwell:17-anolis`（yum 系，国内拉取快）。
- 构建阶段需访问 github.com，国内机器若 clone 慢或失败，用
  `--build-arg ROCKETMQ_REPO=<镜像仓库地址>` 换源。
- 首次构建需 10-20 分钟；如需指定其他分支或 tag：
  `./build.sh --build-arg ROCKETMQ_BRANCH=rocketmq-all-5.5.0`

### 3. 启动（仅核心服务）

```bash
$SSH 'docker network inspect rocketmq_net >/dev/null 2>&1 || docker network create rocketmq_net'
$SSH 'cd /opt/rocketmq/rocketmq && docker compose up -d nameserver broker-0 broker-1 proxy'
```

- broker-0 监听 10909/10911/10912，broker-1 在 broker-1.conf 里通过 `listenPort=20911`
  直接监听 20909/20911/20912（fast/ha 端口自动 -2/+1），容器与宿主机端口 1:1 映射。
- 堆内存通过 `JAVA_OPT_EXT` 环境变量收敛（开源启动脚本会将其追加到 JVM 参数末尾，
  覆盖脚本内置的大堆默认值），适合小内存环境。

### 4. 验证

```bash
$SSH 'cd /opt/rocketmq/rocketmq && docker compose ps'
$SSH 'cd /opt/rocketmq/rocketmq && \
  docker compose exec -T nameserver sh bin/mqadmin clusterList -n nameserver:9876'
```

通过标准：clusterList 能看到 `rocketmq-studio` 集群下 `rocketmq-studio-0`、
`rocketmq-studio-1` 两个 broker，且 `#ACTIVATED=true`。

### 5. 网络接入说明

- **与 Studio 同机部署**：两边共用 `rocketmq_net` 网络，Studio server 容器直接用
  `nameserver:9876` 接入（compose 默认 `STUDIO_ROCKETMQ_NAMESRV_ADDR=nameserver:9876`）。
- **跨机访问**：`brokerIP1=broker-{0,1}` 仅容器网络内可达，宿主机/其他机器直连需改
  `conf/broker-{0,1}.conf` 为 `brokerIP1=<目标机器 IP>` 并重启 broker，同时放行
  9876、10909、10911、10912、20909、20911、20912、8080、8081。
- proxy 对外提供 remoting 8080 / gRPC 8081 接入点，已关闭 topic 消息类型校验
  （`enableTopicMessageTypeCheck=false`，避免自动建 topic 类型为 UNSPECIFIED 时发送被拒）。

---

## 模式 C：单机部署 RocketMQ 测试客户端

在目标机器部署模式 B 的集群，并额外拉起 Producer / Consumer 容器以 1 TPS 持续
收发消息并上报轨迹，供 RocketMQ Studio 调试使用。

### 1. 准备与构建

同模式 B 步骤 1 → 3，但启动全部服务（含 producer/consumer）：

```bash
$SSH 'cd /opt/rocketmq/rocketmq && docker compose up -d'
```

producer / consumer 服务说明（容器名均带 `rmq-` 前缀）：

| 服务 | 说明 |
|------|------|
| producer | 编译并运行 `clients/TraceProducer.java`，1 TPS 发送到 `StudioTest`（带 Key `studio-key-<n>`），`enableMsgTrace=true`，直连 `nameserver:9876` |
| consumer | 编译并运行 `clients/TraceConsumer.java`，Push 消费 `StudioTest`，`enableMsgTrace=true`，直连 `nameserver:9876` |

producer / consumer 直接用运行镜像自带 JDK + `lib/*` 依赖现场编译，无需额外构建。

### 2. 验证

```bash
# 收发日志：producer 每秒一条 SEND_OK，consumer 对应 consume
$SSH 'docker logs -f --tail 50 rmq-producer'

# 消息轨迹已落盘（RMQ_SYS_TRACE_TOPIC 有数据即轨迹链路打通）
$SSH 'cd /opt/rocketmq/rocketmq && docker compose exec -T broker-0 \
  sh bin/mqadmin consumeMessage -n nameserver:9876 -t RMQ_SYS_TRACE_TOPIC -c 5'
```

验证通过标准：模式 B 的 clusterList 标准之外，producer 日志持续输出 `SEND_OK`；
`RMQ_SYS_TRACE_TOPIC` 能消费出轨迹数据。

### 3. 清理

```bash
$SSH 'cd /opt/rocketmq/rocketmq && docker compose down -v'
```

---

## 常用查询命令（mqadmin）

以下命令均已实测可用，在任意集群容器内执行（示例用 nameserver）。远程执行时
用 `ssh <user>@<host> 'cd /opt/rocketmq/rocketmq && docker compose exec -T ...'` 包装。
msgId / Key 可从 producer 日志获取：`send #16 SEND_OK <msgId> key=studio-key-16`。

### 查看集群状态 clusterList

```bash
docker compose exec nameserver sh bin/mqadmin clusterList -n nameserver:9876
```

输出每个 broker 的地址、版本、InTPS/OutTPS（测试集群稳态约 1.0，对应 1 TPS 收发）、
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

- 依赖发送时 `msg.setKeys(...)` 建立的索引（测试集群 producer 已设置
  `studio-key-<序号>`），返回命中的 msgId、Queue、Offset。
- 拿到 msgId 后可再用 `queryMsgByUniqueKey` / `queryMsgTraceById` 深挖详情与轨迹。

## 常见问题

- **producer 启动初期报 route not found / 类型校验失败**（模式 C）：broker 注册与
  topic 自动创建需要几秒，发送循环与 `restart: on-failure` 会自动重试，稍等即可。
- **宿主机客户端连不上 broker**：`brokerIP1=broker-{0,1}` 仅容器网络内可达；
  直连需改 `conf/broker-{0,1}.conf` 的 `brokerIP1`（同机宿主机改 `127.0.0.1`，
  跨机改为机器 IP）并重启。Studio 后端若跑在另一个 compose 网络中，建议共享
  同一个 docker network（`rocketmq_net`）。
- **构建阶段 clone 慢或失败**：确认能访问 github.com，或用
  `--build-arg ROCKETMQ_REPO=<镜像仓库地址>` 换源；Maven 慢用 `./build.sh`
  自动切阿里源。
- **compose 报 external 网络不存在**：先 `docker network create rocketmq_net`。
- **目标机拉基础镜像超时/403**：国内机器配置 `/etc/docker/daemon.json`
  registry-mirrors 后重启 docker。
- **公共 Docker Hub 镜像源全部失效**（2026-08 实测：1ms.run / xuanyuan / daocloud /
  1panel / ustc 均限流或停服，`docker pull` 永久 Waiting）：
  - dragonwell 基础镜像可改从厂商仓库拉：
    `docker pull dragonwell-registry.cn-hangzhou.cr.aliyuncs.com/dragonwell/dragonwell:17-anolis`
    （拉完 `docker tag` 成 Dockerfile 期望的名字），阿里云 ECS 上秒拉。
  - `maven:3.9.16-eclipse-temurin-21` 拉不下来时，用 dragonwell 镜像容器内自装 Maven 编译：
    ```bash
    docker run --rm -e HOME=/tmp -v $PWD/server:/app -v $HOME/.m2:/maven-cache -w /app \
      alibabadragonwell/dragonwell:21 sh -c '
        export JAVA_HOME=$(dirname $(dirname $(readlink -f $(command -v java))))
        curl -fsSL https://mirrors.aliyun.com/apache/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.tar.gz | tar xz -C /opt
        export PATH=/opt/apache-maven-3.9.16/bin:$PATH
        mvn -B -ntp -s /maven-cache/settings.xml -Dmaven.repo.local=/maven-cache/repository package -DskipTests'
    ```
- **Maven 分发包下载慢**：`archive.apache.org` 国内常只有几 KB/s；改从
  `https://mirrors.aliyun.com/apache/maven/maven-3/<版本>/binaries/apache-maven-<版本>-bin.tar.gz`
  下载（阿里源实测 10MB/s+；如 3.9.16 即 `maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.tar.gz`）。
- **目标机已有同名镜像**（如上次部署遗留的 `apache-rocketmq:develop`、`mysql:8.0`）：
  可跳过构建直接 `docker compose up -d`，先 `docker images` 确认。
