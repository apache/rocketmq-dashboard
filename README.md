## [Apache RocketMQ](https://github.com/apache/rocketmq) Dashboard — Control Plane 5.0
[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![CodeCov](https://codecov.io/gh/apache/rocketmq-dashboard/branch/master/graph/badge.svg)](https://codecov.io/gh/apache/rocketmq-dashboard)
[![Average time to resolve an issue](http://isitmaintained.com/badge/resolution/apache/rocketmq-dashboard.svg)](http://isitmaintained.com/project/apache/rocketmq-dashboard "Average time to resolve an issue")
[![Percentage of issues still open](http://isitmaintained.com/badge/open/apache/rocketmq-dashboard.svg)](http://isitmaintained.com/project/apache/rocketmq-dashboard "Percentage of issues still open")
[![Twitter Follow](https://img.shields.io/twitter/follow/ApacheRocketMQ?style=social)](https://twitter.com/intent/follow?screen_name=ApacheRocketMQ)

Unified management platform for Apache RocketMQ — Web Console, CLI, MCP Server, and LLM-native chat. Multi-architecture: 4.0 direct, 5.0 Proxy, Cloud.

**[Design Spec →](RIP-Control-Plane-5.0.md)**

## Modules

| Module | Description | Port | Docs |
|--------|-------------|------|------|
| **rocketmq-dashboard-app** | Web Console + REST API backend | 8082 | [README](rocketmq-dashboard-app/README.md) |
| **rocketmq-dashboard-cli** | `rmqctl` CLI (kubectl-style) | — | [README](rocketmq-dashboard-cli/README.md) |
| **rocketmq-dashboard-mcp** | MCP Server (AI Agent integration) | 8083 | [README](rocketmq-dashboard-mcp/README.md) |
| **rocketmq-dashboard-llm** | LLM Bridge (Console chat backend) | 8084 | [README](rocketmq-dashboard-llm/README.md) |
| **frontend-new** | React 19 + Ant Design 5 frontend | 3003 | — |

## Quick Start

### Docker

```shell
docker pull apacherocketmq/rocketmq-dashboard:latest
docker run -d --name rocketmq-dashboard \
  -e "JAVA_OPTS=-Drocketmq.namesrv.addr=127.0.0.1:9876" \
  -p 8082:8082 -t apacherocketmq/rocketmq-dashboard:latest
```

### Build from Source

Prerequisites: JDK 17+, Maven 3.8+, Node 18+

```shell
# Full build (all modules)
mvn clean package -DskipTests

# Dashboard only
mvn package -pl rocketmq-dashboard-app -DskipTests
java -jar rocketmq-dashboard-app/target/rocketmq-dashboard-app-2.1.1-SNAPSHOT.jar

# CLI only
mvn package -pl rocketmq-dashboard-cli -DskipTests
java -jar rocketmq-dashboard-cli/target/rocketmq-dashboard-cli-2.1.1-SNAPSHOT.jar --help

# MCP Server only
mvn package -pl rocketmq-dashboard-mcp -DskipTests
java -jar rocketmq-dashboard-mcp/target/rocketmq-dashboard-mcp-2.1.1-SNAPSHOT.jar --transport stdio

# LLM Bridge only
mvn package -pl rocketmq-dashboard-llm -DskipTests
java -jar rocketmq-dashboard-llm/target/rocketmq-dashboard-llm-2.1.1-SNAPSHOT.jar

# Frontend dev server
cd frontend-new && npm install && npm run start
```

### Configuration

Edit `rocketmq-dashboard-app/src/main/resources/application.yml`:

```yaml
rocketmq.config:
  namesrvAddrs:
    - 127.0.0.1:9876      # NameServer addresses
  proxyAddr: 127.0.0.1:8080   # Proxy address (5.0)
  loginRequired: false
  authMode: file
```

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│  Web Console (React) │ CLI (rmqctl) │ MCP Server │ LLM  │
├──────────────────────────────────────────────────────────┤
│  MetadataProvider (domain interface, 72 methods)         │
├──────────────────────────────────────────────────────────┤
│  AdminClient (protocol channel: Remoting / gRPC / Cloud) │
├──────────────────────────────────────────────────────────┤
│  4.0 Direct │ 5.0 Proxy Local │ 5.0 Proxy Cluster │ Cloud│
└──────────────────────────────────────────────────────────┘
```

## Documentation

| Document | Description |
|----------|-------------|
| [API Reference](docs/api-reference.md) | All REST endpoints (21 groups) |
| [Deployment Guide](docs/deployment-guide.md) | Full system deployment |
| [Migration Guide](docs/migration-v2.1-to-5.0.md) | v2.1.0 → Control Plane 5.0 |
| [Design Spec](RIP-Control-Plane-5.0.md) | RIP-1 + RIP-2 + RIP-3 unified spec |

## User Guide

[English](https://github.com/apache/rocketmq-dashboard/blob/master/docs/1_0_0/UserGuide_EN.md)

[中文](https://github.com/apache/rocketmq-dashboard/blob/master/docs/1_0_0/UserGuide_CN.md)

## Contributing

We are always very happy to have contributions. See the [RocketMQ contribution guide](http://rocketmq.apache.org/docs/how-to-contribute/).

## License
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html) Copyright (C) Apache Software Foundation
