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

`docker-compose.yml` 中的 `mysql` 与 `rocketmq-server` 加入共享网络 `rocketmq_net`（声明为
`external`），因此首次启动前需要先创建该网络，否则 Compose 会以
`network rocketmq_net declared as external, but could not be found` 终止：

若 `deploy/.env` 不存在，先从示例复制；不要覆盖已有配置。启动前配置管理员用户名和唯一密码，
并显式设置 `STUDIO_AUTH_LOGIN_REQUIRED=true`。仅本地 HTTP 开发使用
`STUDIO_AUTH_SESSION_COOKIE_SECURE=false`；HTTPS（包括反向代理终止 TLS）保持 `true`。

```bash
docker network inspect rocketmq_net >/dev/null 2>&1 || docker network create rocketmq_net
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build
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

`studio.auth.login-required` 和 Compose 回退值默认为 `true`，但 `deploy/.env.example`
目前显式设置为 `false`，两者不可混为一谈。需要保护的部署应在实际运行环境中显式开启保护，
并在首次使用空数据库前配置完整引导凭据。下面的占位值必须替换：

```env
STUDIO_AUTH_LOGIN_REQUIRED=true
STUDIO_AUTH_ADMIN_USERNAME=admin
STUDIO_AUTH_ADMIN_PASSWORD=change-me
```

本地 Compose 使用 `deploy/.env`；`deploy.sh` 启动远程容器时使用目标机
`$REMOTE_PATH/.env`，不会自动复制本地引导凭据。单独运行后端不会自动读取 `deploy/.env`，
且在这两个环境变量未设置时保留 `admin` / `admin` 回退值；对外开放前应覆盖默认值。
Compose 会把缺失的引导变量作为空字符串传入，因此不能依赖此回退值。不要提交真实凭据。

开启后，`/api/auth/login` 使用 JSON request body 接收用户名和密码，密码不会出现在 URL 查询
参数中。浏览器登录成功后会话写入 `HttpOnly` 会话 Cookie，后续 `/api/**` 请求随 Cookie 自动
携带；未携带有效会话的请求会返回 `401 Unauthorized`。非浏览器 API 客户端可在登录时通过
`X-RocketMQ-Studio-Session-Delivery: bearer` 请求头显式换取 bearer token。

`STUDIO_AUTH_ADMIN_USERNAME` / `STUDIO_AUTH_ADMIN_PASSWORD` 只是首次启动的引导账号：当数据库
用户表为空时，首次登录会把已配置用户写入 `rmq_studio_user` 表，此后以数据库为账号数据的唯一
来源，管理员可在「用户管理」页面创建用户、启用/禁用账号和重置密码。如未配置有效用户名和密码
且用户表为空，后端会拒绝登录以避免误签发会话。补全引导凭据并重启后端即可恢复首次登录，
无需删除数据库。已有数据库用户仍可登录，更改引导变量不会重置其密码。

首次登录可以在登录保护开启时完成引导。`deploy/.env.example` 中现有部署警告与分阶段初始化
顺序保持不变；修改这些部署策略需要另行讨论。`studio.auth.login-required=false` 仅用于
本地开发场景跳过 `/api/**` 拦截，应用不会验证网络隔离。静态配置为 `true` 时，数据库中的
`requireLogin=false` 不会关闭保护；读取运行时策略失败也会保持保护。

## 前置条件

- 本地安装 Docker
- 远程机器可通过 SSH 免密登录
- 远程机器已安装 Podman（Alibaba Cloud Linux 3 默认提供）
