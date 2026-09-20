# RocketMQ Studio

**English** | [中文](README_zh.md)

> Cross-cluster · Cross-architecture · Cross-cloud unified RocketMQ management platform

RocketMQ Studio is a unified management platform for RocketMQ, supporting multi-cluster, multi-architecture, and multi-cloud environments. It provides instance management, cluster operations, Topic / Consumer Group CRUD, ACL permission control, message query and tracing, dead letter queue handling, monitoring alerts, audit logs, and an AI assistant.

## Quick Start

```bash
(docker network create rocketmq_net 2>/dev/null || true) && docker compose -f deploy/rocketmq/docker-compose.yml up -d && docker compose -f deploy/docker-compose.yml up -d --build
```

Visit **http://127.0.0.1:6789** after startup.

Run from the repository root. The leading `docker network create` is required
because both compose files declare `rocketmq_net` as `external` and neither
creates it. The first compose file starts the bundled RocketMQ topology
(nameserver, broker-0/broker-1, proxy, producer/consumer load rig,
prometheus); the second builds and starts the Studio stack (mysql,
rocketmq-server, rocketmq-web). Check the topology is healthy with
`docker compose -f deploy/rocketmq/docker-compose.yml ps` before starting
Studio.
The default schema creates only Studio tables. It does not seed instances, topics, consumer groups, or ACL
records. Development-only sample data can be imported explicitly from `deploy/mysql/`; it is not part of the
default deployment. Import `upgrade-demo-instance.sql` first and then `upgrade-demo-acl.sql`; both scripts
target the current numeric-ID schema and are idempotent. They are sample-data loaders, not upgrade migrations,
and should never be imported into a production database.

**Studio ports:** Frontend 6789 (Nginx), Backend 8888 (Spring Boot)

**RocketMQ ports:** NameServer 9876, Broker 10911, Proxy Remoting 8080, Proxy gRPC 8081

To enable login protection for a shared environment, copy `deploy/.env.example` to
`deploy/.env`, set `STUDIO_AUTH_LOGIN_REQUIRED=true`, and configure
`STUDIO_AUTH_ADMIN_USERNAME` / `STUDIO_AUTH_ADMIN_PASSWORD`. These configured credentials are a
bootstrap seed: on the first login against an empty database Studio creates the configured users
in the `rmq_studio_user` table, and the database becomes the source of truth afterwards.
Administrators manage accounts (create users, enable/disable, reset passwords) on the
user-management page; browsers authenticate with an `HttpOnly` session cookie, and API clients can
request a bearer token explicitly. Disabling login protection only skips API interception for local
development.

## Screenshots

**Dashboard · AI chat** — a single entry point that switches between AI chat, cluster diagnosis, resource management, and message query, with model selection and MCP tool calls.

![Dashboard AI chat](docs/pics/home-ai-chat.png)

**Consumer group management** — the group list shows subscription mode, online clients, total lag, and consumption delay; expanding a row reveals per-Topic subscription consistency and filter expressions, alongside offset reset and config import/export.

![Consumer group management](docs/pics/group-list.png)

**Message query** — search messages by Topic / Message Key / Message ID within a time range, then inspect details, trace, verification, or download a single message.

![Message query](docs/pics/message-query.png)

## Features

| Module | Capabilities |
|--------|--------------|
| **Dashboard** | Global statistics for clusters, brokers, topics, and consumer groups with TPS trends |
| **Instances** | Multi-instance access (Proxy / Direct mode), instance CRUD |
| **Clusters** | Cluster details, Broker / NameServer / Proxy node operations, hot config updates, NameServer configuration drift detection |
| **K8s Certs** | Studio-local TLS / mTLS / ServiceAccount certificate configuration |
| **Topics** | Topic CRUD, route viewer, consumer list, multi-type support (Normal / FIFO / Delay / Transaction / Lite) |
| **Consumer Groups** | Consumer group CRUD, consumption progress, subscription details, offset reset, config import/export |
| **ACL** | ACL rules and user management, v1 / v2 dual version support |
| **Messages** | Query by Topic / MsgId / Key / time range, message trace visualization |
| **Dead Letter Queue** | DLQ message viewing and batch resend |
| **Clients** | Producer / Consumer online connection list, protocol and language version stats |
| **Alert Rules** | Multi-dimensional alert rules (disk / lag / TPS / node offline), DingTalk / email / SMS notifications |
| **System Alerts** | System-level alert viewing, acknowledgment, and cleanup |
| **Audit Logs** | Operation audit log query, filter by type / time / result, history cleanup |
| **AI Assistant** | Hosted-agent chat over SSE with server-side conversation history, visible chain of thought, and RocketMQ tool calls through the `rmqctl` MCP bridge; query / diagnose / manage / general modes |
| **Settings** | General preferences, LLM config, datasource management |

## AI Assistant

Studio **hosts** a general-purpose agent CLI — Claude Code or Qoder, plus a plain OpenAI-compatible
HTTP engine — rather than implementing its own tool-calling loop. RocketMQ capabilities reach the
agent through [`rmqctl`](rmqctl/), the Go CLI in this repository, running as an MCP stdio server that
calls back into Studio's own MCP endpoint. The same signed, instance-scoped, risk-gated tool surface
therefore serves the hosted agent, a developer typing `rmqctl topic list`, and any external agent
pointed at the output of `rmqctl mcp config`. See
[docs/ai-agent-architecture.md](docs/ai-agent-architecture.md) for the design and its trade-offs.

- **Conversations persist server-side** and are browsable from the history drawer. Reloading or
  reconnecting replays exactly what the live stream rendered, because both paths reduce to the same
  block model.
- **Chain of thought is displayed**, with model reasoning kept separate from the optional
  prompt-enhancement rewrite.
- **Tool calls render as cards** showing arguments, output, duration and risk level. L2/L3 mutations
  go through preview → confirm token → apply; L3 tools stay disabled unless
  `STUDIO_AI_ALLOW_L3_TOOLS=true`.
- **Multi-turn context** uses the agent CLI's own session resume, so tool state and reasoning survive
  across turns.
- **One button sends and stops.** It is a send arrow while idle and a stop square while generating;
  stopping terminates the agent process *and* its `rmqctl` child rather than merely dropping the
  connection.
- **Degrades to plain chat** when `rmqctl` is missing or `STUDIO_AI_RMQCTL_ENABLED=false`, and says so
  in the transcript instead of failing silently.

Generation is decoupled from the HTTP connection: closing the tab does not kill a run, and reopening
the conversation re-attaches to it. All AI-related environment variables are documented in
[`deploy/.env.example`](deploy/.env.example).

## Tech Stack

- **Frontend** — React 18 + TypeScript + Vite + Ant Design + Tailwind CSS
- **Backend** — Java 21 + Spring Boot 4.1 + Spring AI MCP 2.0 + MyBatis-Plus + Hexagonal Architecture (ArchUnit enforced)
- **CLI** — Go 1.27 ([`rmqctl`](rmqctl/)), both the MCP bridge the hosted agent uses and a standalone signed client for the same tool surface
- **Deployment** — Docker multi-stage builds (JDK runtime plus a Go stage that compiles `rmqctl` into the image), Nginx reverse proxy, Docker Compose or `deploy.sh` for remote deployment

## Development Guidelines

- **Branches** — `rocketmq-studio` is the development trunk: base your branches and pull requests on it. `master_archive` keeps the legacy dashboard history and is not used for development. See [CONTRIBUTING.md](CONTRIBUTING.md) for the full workflow
- **Code Style** — ESLint + Prettier for frontend, Husky pre-commit hook for auto-check
- **Commit Format** — Conventional Commits (`feat:` / `fix:` / `refactor:` / `chore:` / `docs:` / `perf:`)
- **Architecture Tests** — `mvn test` runs ArchUnit hexagonal architecture constraint checks
- **i18n** — New frontend text must include both Chinese and English translations (`web/src/i18n/`)
- **Table Width** — Tables must not show a horizontal scrollbar by default (only allowed when the window/container is manually narrowed); use `tableScrollX(columns)` from `web/src/utils/table.ts` to compute `scroll.x` from declared column widths instead of hardcoded magic numbers; when a modal contains a wide table, adjust the modal `width` dynamically per active tab (e.g., Group detail: Overview 800 / Progress 1080) so that container width ≥ table width; long-text columns (e.g., long Topic names) should use `ellipsis: true` + `title` for hover-to-see-full-name truncation (no wrapping)
- **Action Column Width** — Button-bearing action columns must be sized as "measured button row width + cell padding + right margin"; never shrink them by guesswork: `ant-flex` is a block-level container that stretches to fill the cell, so an undersized column pushes buttons against (or past) the table's right edge, causing flush edges or horizontal scrollbars; measure the actual button row width in DevTools before changing, and update the guarding test accordingly (e.g., `TopicPage.test.tsx` "keeps the action column wide enough")

## License

[Apache License 2.0](LICENSE)
