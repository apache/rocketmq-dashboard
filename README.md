# RocketMQ Studio

**English** | [中文](README_zh.md)

> Cross-cluster · Cross-architecture · Cross-cloud unified RocketMQ management platform

RocketMQ Studio is a unified management platform for RocketMQ, supporting multi-cluster, multi-architecture, and multi-cloud environments. It provides instance management, cluster operations, Topic / Consumer Group CRUD, ACL permission control, message query and tracing, dead letter queue handling, monitoring alerts, audit logs, and an AI assistant.

## Secure Quick Start

```bash
cd deploy
# First create a strict cost-12 bcrypt user registry and populate the private
# named volume with the ephemeral helper in deploy/README.md.
docker compose up -d --build
```

The credential-volume bootstrap is required; an empty volume intentionally
fails closed and no default account exists. Follow the
[deployment guide](deploy/README.md) before the first `up`. Compose mounts the
private registry directory read-only while configuring the exact
`/run/secrets/studio-users.json` file, so atomic helper replacements remain
visible to the running server. Then visit **http://127.0.0.1:6789**. The web
listener is loopback-only and the backend has no host port.

For a remote host, keep the listener private and open an SSH tunnel:

```bash
ssh -L 6789:127.0.0.1:6789 deploy-user@studio.example.com
```

For browser access over a network, terminate HTTPS with
[`deploy/nginx/rocketmq-studio-tls.conf.example`](deploy/nginx/rocketmq-studio-tls.conf.example);
do not expose the loopback HTTP listener. See
[`docs/security.md`](docs/security.md) for the registry schema, roles, session
limits, health probes, and proxy trust model.

**RocketMQ ports:** NameServer 9876, Broker 10911, Proxy Remoting 8080, Proxy gRPC 8081

## Features

| Module | Capabilities |
|--------|--------------|
| **Dashboard** | Global statistics for clusters, brokers, topics, and consumer groups with TPS trends |
| **Instances** | Multi-instance access (Proxy / Direct mode), instance CRUD |
| **Clusters** | Cluster details, Broker / NameServer / Proxy node operations, hot config updates |
| **K8s Certs** | TLS / mTLS / ServiceAccount certificate management and renewal |
| **Topics** | Topic CRUD, route viewer, consumer list, multi-type support (Normal / FIFO / Delay / Transaction / Lite) |
| **Consumer Groups** | Consumer group CRUD, consumption progress, subscription details, offset reset, config import/export |
| **ACL** | ACL rules and user management, v1 / v2 dual version support |
| **Messages** | Query by Topic / MsgId / Key / time range, message trace visualization |
| **Dead Letter Queue** | DLQ message viewing and batch resend |
| **Clients** | Producer / Consumer online connection list, protocol and language version stats |
| **Alert Rules** | Multi-dimensional alert rules (disk / lag / TPS / node offline), DingTalk / email / SMS notifications |
| **System Alerts** | System-level alert viewing, acknowledgment, and cleanup |
| **Audit Logs** | Operation audit log query, filter by type / time / result, history cleanup |
| **AI Assistant** | SSE streaming chat, supports query / diagnose / manage / general modes, MCP tool integration |
| **Settings** | General preferences, LLM config, datasource management |

## Tech Stack

- **Frontend** — React 18 + TypeScript + Vite + Ant Design + Tailwind CSS
- **Backend** — Java 21 + Spring Boot 3.5 + Hexagonal Architecture (ArchUnit enforced)
- **Deployment** — Docker multi-stage builds, Nginx reverse proxy, Docker Compose or `deploy.sh` for remote deployment

## Development Guidelines

- **Code Style** — ESLint + Prettier for frontend, Husky pre-commit hook for auto-check
- **Commit Format** — Conventional Commits (`feat:` / `fix:` / `refactor:` / `chore:` / `docs:` / `perf:`)
- **Architecture Tests** — `mvn test` runs ArchUnit hexagonal architecture constraint checks
- **i18n** — New frontend text must include both Chinese and English translations (`web/src/i18n/`)

## License

[Apache License 2.0](LICENSE)
