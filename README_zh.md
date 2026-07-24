# RocketMQ Studio

[English](README.md) | **中文**

> 跨集群 · 跨架构 · 跨云的 RocketMQ 统一管控平台

RocketMQ Studio 是一个面向多集群、多架构、多云环境的 RocketMQ 管控平台，提供实例管理、集群运维、Topic / 消费组 CRUD、ACL 权限管控、消息查询与轨迹追踪、死信队列处理、监控告警、审计日志以及 AI 智能助手等一站式能力。

## 安全启动

```bash
cd deploy
# 首次启动前，先按 deploy/README.md 创建严格的 cost-12 bcrypt 用户注册表，
# 并用临时 helper 填充私有命名卷。
docker compose up -d --build
```

凭据卷初始化是必需步骤：空卷会按设计失败关闭，系统不提供默认账户。首次
`up` 前请遵循[部署指南](deploy/README.md)，启动后仅在本机访问
**http://127.0.0.1:6789**。Web 只监听回环地址，后端不发布宿主机端口。

远程主机也应保持回环监听，并通过 SSH 隧道访问：

```bash
ssh -L 6789:127.0.0.1:6789 deploy-user@studio.example.com
```

需要网络浏览器直接访问时，使用
[`deploy/nginx/rocketmq-studio-tls.conf.example`](deploy/nginx/rocketmq-studio-tls.conf.example)
终止 HTTPS，禁止公开回环 HTTP 入口。注册表 schema、角色、会话、健康探针和
可信代理模型见 [`docs/security.md`](docs/security.md)。

**RocketMQ 服务端端口：** NameServer 9876、Broker 10911、Proxy Remoting 8080、Proxy gRPC 8081

## 功能概览

| 模块 | 能力 |
|------|------|
| **监控面板** | 集群/ Broker / Topic / 消费组全局统计，TPS 趋势图 |
| **实例管理** | 多实例接入（Proxy / Direct 模式），实例 CRUD |
| **集群管理** | 集群详情、Broker / NameServer / Proxy 节点运维、集群配置热更新 |
| **K8s 证书** | TLS / mTLS / ServiceAccount 证书管理与续期 |
| **Topic 管理** | Topic CRUD、路由查看、消费者列表、多类型支持（Normal / FIFO / Delay / Transaction / Lite） |
| **消费组管理** | 消费组 CRUD、消费进度、订阅详情、位点重置、配置导入导出 |
| **ACL 权限** | ACL 规则与用户管理，支持 v1 / v2 双版本 |
| **消息查询** | 按 Topic / MsgId / Key / 时间范围查询，消息轨迹可视化 |
| **死信队列** | DLQ 消息查看与批量重发 |
| **客户端连接** | Producer / Consumer 在线连接列表，协议与语言版本统计 |
| **告警规则** | 多维度告警规则配置（磁盘 / 堆积 / TPS / 节点离线），支持钉钉 / 邮件 / 短信通知 |
| **系统告警** | 系统级告警查看、确认与清理 |
| **审计日志** | 操作审计日志查询、按类型 / 时间 / 结果过滤、历史清理 |
| **AI 助手** | SSE 流式对话，支持查询 / 诊断 / 管控 / 通用多模式，MCP 工具集成 |
| **系统设置** | 通用偏好设置、LLM 配置、数据源管理 |

## 技术栈

- **前端** — React 18 + TypeScript + Vite + Ant Design + Tailwind CSS
- **后端** — Java 21 + Spring Boot 3.5 + 六边形架构（ArchUnit 约束）
- **部署** — Docker 多阶段构建，Nginx 反向代理，支持 Docker Compose 本地运行或 `deploy.sh` 远程部署

## 开发规范

- **代码风格** — 前端通过 ESLint + Prettier 统一格式，Husky pre-commit hook 自动检查
- **Commit 格式** — 遵循 Conventional Commits（`feat:` / `fix:` / `refactor:` / `chore:` / `docs:` / `perf:`）
- **架构测试** — 后端 `mvn test` 自动运行 ArchUnit 六边形架构约束检查
- **国际化** — 新增前端文案需同时提供中英文翻译（`web/src/i18n/`）

## License

[Apache License 2.0](LICENSE)
