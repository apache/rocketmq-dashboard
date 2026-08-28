# 部署

## 一键部署

```bash
./deploy/deploy.sh           # 部署 server + web
./deploy/deploy.sh server    # 仅部署后端
./deploy/deploy.sh web       # 仅部署前端
```

脚本自动完成：本地构建 → 导出镜像 → SCP 传输 → 远程加载 → 重启容器 → 验证。
后端通过目标机上的 Maven 容器构建，并默认挂载目标登录用户的 `~/.m2`，后续构建会复用已下载的依赖。

## 配置

编辑 `deploy/.env` 修改部署目标：

```env
REMOTE_HOST=your-server-ip
REMOTE_USER=root
REMOTE_PATH=/opt/rocketmq-studio
PUBLIC_PORT=8080

# 可选：自定义宿主机 Maven 缓存和构建镜像
MAVEN_CACHE_DIR=/home/your-user/.m2
MAVEN_IMAGE=maven:3.9.16-eclipse-temurin-21
```

`REMOTE_PATH` 必须是不含空白、单引号或冒号的绝对路径。`MAVEN_CACHE_DIR` 也是目标机上的绝对路径
（不含换行或冒号），默认为远程登录用户的 `~/.m2`；路径可以包含空格。脚本会将同一路径挂载为
`/maven-cache`，如果其中存在 `settings.xml`，构建容器也会自动使用该配置。

`deploy.sh` 以本地 `deploy/.env` 作为唯一部署配置，并随源码同步到远端
`$REMOTE_PATH/deploy/.env`。Compose 启动同样读取该文件，因此首次部署不需要手工创建远端根目录
`.env`。`PUBLIC_PORT` 同时控制 Compose 的前端端口映射和脚本的部署验证地址。

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

## 前置条件

- 本地安装 Docker
- 远程机器可通过 SSH 免密登录
- 远程机器已安装 Podman（Alibaba Cloud Linux 3 默认提供）
