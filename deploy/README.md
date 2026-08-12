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

# deploy.sh server/all 必填：数据库需可从远程 Podman 网络访问
SPRING_DATASOURCE_URL=jdbc:mysql://database-host:3306/rocketmq?useSSL=false&serverTimezone=UTC
SPRING_DATASOURCE_USERNAME=rocketmq
SPRING_DATASOURCE_PASSWORD=change-me

# 可选：Apache RocketMQ 默认 NameServer 地址
STUDIO_ROCKETMQ_NAMESRV_ADDR=nameserver.example.com:9876

# 可选：自定义宿主机 Maven 缓存和构建镜像
MAVEN_CACHE_DIR=/home/your-user/.m2
MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-21
```

`MAVEN_CACHE_DIR` 默认为当前用户的 `~/.m2`。如果其中存在 `settings.xml`，构建容器也会自动使用该配置。
远程后端固定使用 `prod` profile；脚本会在构建前校验数据库配置，避免容器退回开发环境的
内存 H2。首次使用前请先在目标数据库执行 `server/src/main/resources/db/schema.sql`。

## 本地 Docker Compose

复制示例配置后启动：

```bash
cp deploy/.env.example deploy/.env
cd deploy && docker compose up -d --build
```

默认访问地址为 `http://127.0.0.1:6789`。

默认 schema 只创建 Studio 所需的表，不会写入实例、Topic、消费组或 ACL 示例数据。需要演示数据时，
请在开发环境中显式导入 `deploy/mysql/upgrade-demo-instance.sql` 和
`deploy/mysql/upgrade-demo-acl.sql`，不要在生产环境导入这些脚本。
## 开启登录保护

`studio.auth.login-required` 默认为 `false`，便于本地开发和演示环境直接访问。共享环境建议在
`deploy/.env` 中开启登录保护并设置管理员账号：

```env
STUDIO_AUTH_LOGIN_REQUIRED=true
STUDIO_AUTH_ADMIN_USERNAME=admin
STUDIO_AUTH_ADMIN_PASSWORD=change-me
```

开启后，`/api/auth/login` 使用 JSON request body 接收用户名和密码，密码不会出现在 URL 查询
参数中。登录成功后前端会把返回的 token 作为 `Authorization: Bearer <token>` 发送给后续
`/api/**` 请求；未携带有效 token 的请求会返回 `401 Unauthorized`。

`/api/auth/login` 始终只接受已配置用户；如未配置有效用户名和密码，后端会拒绝登录以避免误签发 token。
`studio.auth.login-required=false` 仅用于本地开发场景跳过 `/api/**` 拦截。

## 前置条件

- 本地安装 Docker
- 远程机器可通过 SSH 免密登录
- 远程机器已安装 Podman（Alibaba Cloud Linux 3 默认提供）
