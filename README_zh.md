# RocketMQ Studio

[English](README.md) | **中文**

> 跨集群 · 跨架构 · 跨云的 RocketMQ 统一管控平台

RocketMQ Studio 是一个面向多集群、多架构、多云环境的 RocketMQ 管控平台，提供实例管理、集群运维、Topic / 消费组 CRUD、ACL 权限管控、消息查询与轨迹追踪、死信队列处理、监控告警、审计日志以及 AI 智能助手等一站式能力。

## 一键构建 & 运行

```bash
(docker network create rocketmq_net 2>/dev/null || true) && docker compose -f deploy/rocketmq/docker-compose.yml up -d && docker compose -f deploy/docker-compose.yml up -d --build
```

在仓库根目录执行。开头的 `docker network create` 必不可少：两个 compose 文件都把
`rocketmq_net` 声明为 `external`，自身都不会创建它。第一个 compose 启动内置
RocketMQ 拓扑（nameserver、broker-0/broker-1、proxy、producer/consumer 测试挂具、
prometheus）；第二个构建并启动 Studio 三件套（mysql、rocketmq-server、
rocketmq-web）。启动 Studio 前可用
`docker compose -f deploy/rocketmq/docker-compose.yml ps` 确认 RocketMQ 已就绪。

启动后访问 **http://127.0.0.1:6789** 即可使用。

**Studio 服务端口：** 前端 6789（Nginx）、后端 8888（Spring Boot）

**RocketMQ 服务端端口：** NameServer 9876、Broker 10911、Proxy Remoting 8080、Proxy gRPC 8081

共享环境可复制 `deploy/.env.example` 为 `deploy/.env`，设置
`STUDIO_AUTH_LOGIN_REQUIRED=true`，并配置 `STUDIO_AUTH_ADMIN_USERNAME` /
`STUDIO_AUTH_ADMIN_PASSWORD` 开启登录保护。这里配置的账号只是引导种子：
针对空数据库的首次登录会把配置的用户写入 `rmq_studio_user` 表，此后数据库
才是账号与账号状态的唯一来源。管理员可以在用户管理页维护账号（创建用户、
启用/停用、重置密码）；浏览器使用 `HttpOnly` 会话 Cookie 认证，API 客户端
可显式换取 bearer token。关闭登录保护只会跳过本地开发场景下的 API 拦截。

## 界面预览

**首页 · AI 对话** — 统一入口，可切换 AI 对话 / 集群诊断 / 资源管理 / 消息查询四类场景，支持多模型选择与 MCP 工具调用。

![首页 AI 对话](docs/pics/home-ai-chat.png)

**Group 管理** — 消费组列表展示订阅模式、在线客户端数、总堆积量与消费延迟，展开行可查看订阅 Topic 的订阅一致性与过滤表达式，并支持重置位点、导入导出。

![Group 管理](docs/pics/group-list.png)

**消息查询** — 按 Topic / Message Key / Message ID 结合时间范围检索消息，单条消息可查看详情、轨迹、校验与下载。

![消息查询](docs/pics/message-query.png)

## 功能概览

| 模块 | 能力 |
|------|------|
| **监控面板** | 集群/ Broker / Topic / 消费组全局统计，TPS 趋势图 |
| **实例管理** | 多实例接入（Proxy / Direct 模式），实例 CRUD |
| **集群管理** | 集群详情、Broker / NameServer / Proxy 节点运维、集群配置热更新 |
| **K8s 证书** | Studio 本地 TLS / mTLS / ServiceAccount 证书配置 |
| **Topic 管理** | Topic CRUD、路由查看、消费者列表、多类型支持（Normal / FIFO / Delay / Transaction / Lite） |
| **消费组管理** | 消费组 CRUD、消费进度、订阅详情、位点重置、配置导入导出 |
| **ACL 权限** | ACL 规则与用户管理，支持 v1 / v2 双版本 |
| **消息查询** | 按 Topic / MsgId / Key / 时间范围查询，消息轨迹可视化 |
| **死信队列** | DLQ 消息查看与批量重发 |
| **客户端连接** | Producer / Consumer 在线连接列表，协议与语言版本统计 |
| **告警规则** | 多维度告警规则配置（磁盘 / 堆积 / TPS / 节点离线），支持钉钉 / 邮件 / 短信通知 |
| **系统告警** | 系统级告警查看、确认与清理 |
| **审计日志** | 操作审计日志查询、按类型 / 时间 / 结果过滤、历史清理 |
| **AI 助手** | 托管 Agent 的 SSE 流式对话，会话在服务端持久化并可回看历史，展示思维链，工具调用经 `rmqctl` 的 MCP 桥接；支持查询 / 诊断 / 管控 / 通用多模式 |
| **系统设置** | 通用偏好设置、LLM 配置、数据源管理 |

## AI 助手

Studio 只负责**托管**一个通用 Agent CLI（Claude Code 或 Qoder，另提供对接 OpenAI 兼容接口的 HTTP
引擎），不自己实现工具调用循环。RocketMQ 的能力经仓库内的 Go CLI [`rmqctl`](rmqctl/) 提供给 Agent：
它以 MCP stdio server 的形式被 Agent 拉起，再签名回连 Studio 自己的 MCP 端点。因此同一套带签名、
绑定实例、带风险闸门的工具面，同时服务于托管 Agent、手敲 `rmqctl topic list` 的开发者，以及任何
指向 `rmqctl mcp config` 输出的外部 Agent。设计与取舍见
[docs/ai-agent-architecture.md](docs/ai-agent-architecture.md)。

- **会话在服务端持久化**，可从历史抽屉回看。刷新或断线重连回放出的时间线与直播时渲染的完全一致，
  因为两条路径归约到同一套 block 模型。
- **展示思维链**，且模型推理与可选的 prompt 增强改写严格分开，不会混成一个块。
- **工具调用以卡片呈现**，含入参、输出、耗时与风险等级。L2/L3 变更走「预览 → confirm token →
  执行」两步；L3 工具默认关闭，需显式设置 `STUDIO_AI_ALLOW_L3_TOOLS=true`。
- **多轮上下文**使用 Agent CLI 自身的会话 resume，工具状态与推理跨轮保留。
- **发送与停止是同一个按钮**：空闲时是发送箭头，生成中变成停止方块；停止会真正终止 Agent 进程
  **及其 `rmqctl` 子进程**，而不只是断开连接。
- **`rmqctl` 缺失或 `STUDIO_AI_RMQCTL_ENABLED=false` 时降级为纯聊天**，并在时间线里明确告知，
  不会静默失败。

生成过程与 HTTP 连接解耦：关掉标签页不会终止正在跑的 run，重新打开会话会自动接回。AI 相关的环境
变量全部记录在 [`deploy/.env.example`](deploy/.env.example)。

## 技术栈

- **前端** — React 18 + TypeScript + Vite + Ant Design + Tailwind CSS
- **后端** — Java 21 + Spring Boot 4.1 + Spring AI MCP 2.0 + MyBatis-Plus + 六边形架构（ArchUnit 约束）
- **命令行** — Go 1.27（[`rmqctl`](rmqctl/)），既是托管 Agent 使用的 MCP 桥接，也是面向同一套工具面的独立签名客户端
- **部署** — Docker 多阶段构建（JDK 运行阶段 + 把 `rmqctl` 编译进镜像的 Go 阶段），Nginx 反向代理，支持 Docker Compose 本地运行或 `deploy.sh` 远程部署

## 开发规范

- **分支说明** — `rocketmq-studio` 是开发主干，请基于它切分支、提 PR；`master_archive` 是原 dashboard 的历史归档，不用于开发；完整流程见 [CONTRIBUTING.md](CONTRIBUTING.md)
- **代码风格** — 前端通过 ESLint + Prettier 统一格式，Husky pre-commit hook 自动检查
- **Commit 格式** — 遵循 Conventional Commits（`feat:` / `fix:` / `refactor:` / `chore:` / `docs:` / `perf:`）
- **架构测试** — 后端 `mvn test` 自动运行 ArchUnit 六边形架构约束检查
- **国际化** — 新增前端文案需同时提供中英文翻译（`web/src/i18n/`）
- **表格宽度** — 表格默认不出横向滚动条（仅窗口/容器被人为缩窄时才出现）；列宽用 `web/src/utils/table.ts` 的 `tableScrollX(columns)` 按声明列宽自动累加算出 `scroll.x`，禁止写死魔术数字；弹窗内表格列多或内容长时，按当前 Tab 动态调整弹窗 `width`（如 Group 详情弹窗：概览 800、消费进度 1080）使容器宽 ≥ 表宽；长文本列（如长 Topic 名）用列 `ellipsis: true` + `title` 悬停显示全名截断，允许显示不全、不换行
- **操作列宽度** — 带按钮的操作列宽度必须按「按钮行实测宽度 + 单元格 padding + 右侧留白」确定，禁止凭感觉改小：`ant-flex` 是块级容器会撑满单元格，列宽不足时按钮贴表格右边缘甚至溢出产生横向滚动条；改动前先量按钮行实际宽度（浏览器 DevTools），并同步更新对应守护测试（如 `TopicPage.test.tsx` 「keeps the action column wide enough」）

## License

[Apache License 2.0](LICENSE)
