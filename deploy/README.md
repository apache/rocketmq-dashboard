# 部署

## 一键部署

```bash
./deploy/deploy.sh           # 部署 server + web
./deploy/deploy.sh server    # 仅部署后端
./deploy/deploy.sh web       # 仅部署前端
```

脚本自动完成：本地构建 → 导出镜像 → SCP 传输 → 远程加载 → 重启容器 → 验证。
后端通过 Maven 容器构建，并默认挂载宿主机的 `~/.m2`，后续构建会复用已下载的依赖。

## 配置

编辑 `deploy/.env` 修改部署目标：

```env
REMOTE_HOST=your-server-ip
REMOTE_USER=root
REMOTE_PATH=/opt/rocketmq-studio
PUBLIC_PORT=8080

# 可选：自定义宿主机 Maven 缓存和构建镜像
MAVEN_CACHE_DIR=/home/your-user/.m2
MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-21
```

`MAVEN_CACHE_DIR` 默认为当前用户的 `~/.m2`。如果其中存在 `settings.xml`，构建容器也会自动使用该配置。

## 本地 Docker Compose

复制示例配置后启动：

```bash
cp deploy/.env.example deploy/.env
cd deploy && docker compose up -d --build
```

默认访问地址为 `http://127.0.0.1:6789`。

后端提供两个独立的编排探针，前端 Nginx 仅透出这两个探针与 Actuator health：

- `GET /livez`：仅检查 Studio 进程存活状态，不依赖数据库、RocketMQ、Prometheus 或云 API。
- `GET /readyz`：检查 Studio 是否可接收控制面请求，包含数据库连接状态。Docker Compose
  使用该端点决定何时启动前端。

RocketMQ、Prometheus 和云 API 故障由 Studio 的运行态诊断页面展示，不纳入 liveness，避免下游
故障触发 Studio 容器反复重启。

默认 schema 只创建 Studio 所需的表，不会写入实例、Topic、消费组或 ACL 示例数据。需要演示数据时，
请先初始化当前 `server/src/main/resources/db/schema.sql`，再在开发环境中按顺序导入：

```bash
docker exec -i rocketmq-studio-mysql mysql -uroot -pstudio123 rocketmq \
  < deploy/mysql/upgrade-demo-instance.sql
docker exec -i rocketmq-studio-mysql mysql -uroot -pstudio123 rocketmq \
  < deploy/mysql/upgrade-demo-acl.sql
```

两个脚本均按当前 numeric-ID schema 写入并可重复执行；它们只负责演示数据，不创建或迁移业务表，
不要在生产环境导入。
## 开启登录保护

`studio.auth.login-required` 默认为 `false`，便于本地开发和演示环境直接访问。共享环境建议在
`deploy/.env` 中开启登录保护并设置管理员账号：

```env
STUDIO_AUTH_LOGIN_REQUIRED=true
STUDIO_AUTH_ADMIN_USERNAME=admin
STUDIO_AUTH_ADMIN_PASSWORD=change-me
```

开启后，`/api/auth/login` 使用 JSON request body 接收用户名和密码，密码不会出现在 URL 查询
参数中。浏览器登录成功后会话写入 `HttpOnly` 会话 Cookie，后续 `/api/**` 请求随 Cookie 自动
携带；未携带有效会话的请求会返回 `401 Unauthorized`。非浏览器 API 客户端可在登录时通过
`X-RocketMQ-Studio-Session-Delivery: bearer` 请求头显式换取 bearer token。

`STUDIO_AUTH_ADMIN_USERNAME` / `STUDIO_AUTH_ADMIN_PASSWORD` 只是首次启动的引导账号：当数据库
用户表为空时，首次登录会把已配置用户写入 `rmq_studio_user` 表，此后以数据库为账号数据的唯一
来源，管理员可在「用户管理」页面创建用户、启用/禁用账号和重置密码。如未配置有效用户名和密码
且用户表为空，后端会拒绝登录以避免误签发会话。
`studio.auth.login-required=false` 仅用于本地开发场景跳过 `/api/**` 拦截。

## 生命周期操作执行器

Broker 重启、NameServer 创建/地址更新/重启/升级/删除和 Proxy 重启需要部署控制面参与，RocketMQ Admin 协议本身
不负责进程生命周期。Studio 支持通过服务端配置调用一个部署侧可执行文件；默认关闭，未配置时相关
接口会返回 501。

```env
STUDIO_LIFECYCLE_ENABLED=true
STUDIO_LIFECYCLE_EXECUTABLE=/opt/rocketmq/bin/studio-lifecycle
STUDIO_LIFECYCLE_TIMEOUT=PT30S
STUDIO_LIFECYCLE_MAX_OUTPUT_BYTES=8192
STUDIO_LIFECYCLE_ALLOWED_OPERATIONS=BROKER_RESTART,NAMESERVER_CREATE,NAMESERVER_UPDATE,NAMESERVER_RESTART,NAMESERVER_UPGRADE,NAMESERVER_DELETE,PROXY_RESTART
```

Studio 使用无 shell 的固定参数启动该文件。部署侧执行器负责 Docker Compose、Kubernetes、SSH 或其他
运维平台的实际编排，参数如下：

| allowlist 值 | 子命令 | 目标参数 |
| --- | --- | --- |
| `BROKER_RESTART` | `broker-restart` | `--target <broker-name> --target-address <broker-address>` |
| `NAMESERVER_CREATE` | `nameserver-create` | `--target <new-nameserver-address> [--target-version <version>]` |
| `NAMESERVER_UPDATE` | `nameserver-update` | `--target <existing-nameserver-address> --target-address <new-nameserver-address> [--target-version <version>]` |
| `NAMESERVER_RESTART` | `nameserver-restart` | `--target <nameserver-address>` |
| `NAMESERVER_UPGRADE` | `nameserver-upgrade` | `--target <nameserver-address> --target-version <version>` |
| `NAMESERVER_DELETE` | `nameserver-delete` | `--target <nameserver-address>` |
| `PROXY_RESTART` | `proxy-restart` | `--target <proxy-address>` |

固定参数顺序为 `子命令 --cluster-id <cluster> --target <目标> [--target-address <地址>]
[--target-version <版本>] --request-id <uuid>`，各参数均作为独立 token 传递，不经 shell 解析。
创建时目标是新地址；地址更新时目标是已发现的旧地址，`--target-address` 是替换的新地址。版本仅是
部署元数据；只升级版本应使用 `NAMESERVER_UPGRADE`。标准输出和标准错误会合并，最多保留
`STUDIO_LIFECYCLE_MAX_OUTPUT_BYTES` 字节，并返回给管理员和写入审计记录；适配器不得输出密钥或令牌。

`/api/nameservers/create` 和 `/api/nameservers/update` 返回带 `requestId` 的派发结果，而不是一个
已运行的 NameServer。两者均不修改 Studio 的 NameServer 注册表；`/api/nameservers/registry/*`
只管理地址目录，不创建或更新进程。部署适配器需将集群和地址映射至自己控制的资源清单，拒绝未知
目标并处理幂等、部署位置和回滚，不能把请求地址当作命令执行。重试可能再次派发，不能假定恰好执行一次。

退出码 0 只表示操作已被部署控制面接受，不代表新地址可用或健康；健康检查、滚动策略和回滚由执行器负责。未启用、未配置或未
加入 allowlist 时返回 501；进程启动失败或非零退出返回 502；超时返回 504，并终止适配器进程树。请只
允许管理员访问这些接口，并把执行文件安装在 Studio 服务运行环境中。开启登录保护时，全局
`AuthInterceptor` 会拒绝 reader 对全部生命周期 POST 接口的访问；`studio.auth.login-required=false`
是显式关闭鉴权的本地开发模式，不提供管理员边界。

## 前置条件

- 本地安装 Docker
- 远程机器可通过 SSH 免密登录
- 远程机器已安装 Podman（Alibaba Cloud Linux 3 默认提供）
