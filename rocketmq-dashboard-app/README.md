# RocketMQ Dashboard App

The Spring Boot web application for the [RocketMQ Control Plane 5.0](../RIP-Control-Plane-5.0.md) (RIP-1). Provides the REST API backend and serves the React frontend.

## Quick Start

```bash
# Build
cd rocketmq-dashboard
mvn package -pl rocketmq-dashboard-app -DskipTests

# Run (requires NameServer)
java -jar rocketmq-dashboard-app/target/rocketmq-dashboard-app-2.1.1-SNAPSHOT.jar

# Or with Maven
mvn spring-boot:run -pl rocketmq-dashboard-app
```

Default port: **8082**. Login: `rocketmq` / `1234567` (when `loginRequired: true`).

## Configuration

All settings in `src/main/resources/application.yml`:

```yaml
rocketmq.config:
  namesrvAddrs:
    - 127.0.0.1:9876
  proxyAddr: 127.0.0.1:8080
  loginRequired: false
  authMode: file          # file | acl
```

## Architecture

Three-layer abstraction (RIP-1 ARCH-01):

```
Web UI / REST Controllers
        ↓
MetadataProvider (domain interface)
        ↓
AdminClient (protocol channel)
        ↓
RocketMQ Backend (4.0 direct | 5.0 Proxy | Cloud)
```

### Multi-Architecture Support

| Architecture | Provider | Client |
|-------------|----------|--------|
| 4.0 Direct | `V4ClusterProvider` / `V4MetadataProvider` | `RemotingAdminClient` |
| 5.0 Proxy Local | `V5ProxyClusterProvider` / `V5ProxyMetadataProvider` | `GrpcAdminClient` |
| 5.0 Proxy Cluster | `V5ProxyClusterProvider` / `V5ProxyMetadataProvider` | `GrpcAdminClient` |
| Cloud (Aliyun/Tencent/Huawei) | Cloud providers | `CloudAdminClient` |

Runtime switching via `POST /api/architecture/switch` — no restart required.

## Modules

| Module | Scope | Key Files |
|--------|-------|-----------|
| **ARCH-01** | Multi-architecture SPIs | `architecture/ClusterProvider.java`, `AdminClient.java`, `MetadataProvider.java` |
| **META-01** | Metadata management | `architecture/MetadataProvider.java` (72 methods) |
| **AUTH-01** | ACL 1.0 + 2.0 | `AclController.java`, `Acl2Controller.java`, `Acl2Service.java` |
| **CLIENT-01** | Dual-protocol clients | `ClientController.java`, `UnifiedClientService.java` |
| **METRICS-01** | Prometheus integration | `MetricsController.java`, `MetricsEnhancedService.java` |
| **BASE-01** | Baseline parity | All traditional controllers (11 categories) |

## REST API

Full API reference: [`docs/api-reference.md`](../docs/api-reference.md)

Key endpoint groups:
- `/api/architecture/*` — cluster topology, capabilities, runtime switching
- `/api/namespace/*` — namespace CRUD (V5+)
- `/topic/*` — topic management
- `/consumer/*` — consumer group management
- `/api/client/*` — dual-protocol client unified view
- `/api/acl2/*` — ACL 2.0 policies
- `/api/metrics/*` — Prometheus dashboards, alerts, Grafana export
- `/api/llm/*` — LLM Bridge (Phase 3)

## Frontend

React 19 + Ant Design 5 frontend in `../frontend-new/`. Dev server on port 3003 proxies to backend 8082.

```bash
cd ../frontend-new
npm install
npm run start
```

Production build: `npm run build` → copied to `target/classes/public/` by maven-antrun-plugin.
