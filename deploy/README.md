# 部署

## 一键部署

```bash
./deploy/deploy.sh           # 部署 server + web
./deploy/deploy.sh server    # 仅部署后端
./deploy/deploy.sh web       # 仅部署前端
```

脚本自动完成：本地构建 → 导出镜像 → SCP 传输 → 远程加载 → 重启容器 → 验证

## 配置

编辑 `deploy/.env` 修改部署目标：

```env
REMOTE_HOST=your-server-ip
REMOTE_USER=root
REMOTE_PATH=/opt/rocketmq-studio
PUBLIC_PORT=8080
```

## 本地 Docker Compose

复制示例配置后启动：

```bash
cp deploy/.env.example deploy/.env
cd deploy && docker compose up -d --build
```

默认访问地址为 `http://127.0.0.1:6789`。

## PostgreSQL 持久化

Studio 默认 Docker Compose 部署继续使用 MySQL。使用已有 PostgreSQL 16+ 实例时，启用
`postgres` profile，并提供数据库连接信息：

```env
SPRING_PROFILES_ACTIVE=postgres
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres.example:5432/rocketmq_studio
SPRING_DATASOURCE_USERNAME=rocketmq_studio
SPRING_DATASOURCE_PASSWORD=change-me
```

首次启动会执行 `server/src/main/resources/db/schema-postgresql.sql` 创建 Studio 自身的
配置、审计和管理记录表。该 schema 不保存 RocketMQ 消息、消费位点或 Broker 运行态数据；
这些数据仍通过 RocketMQ 管理接口读取。

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
