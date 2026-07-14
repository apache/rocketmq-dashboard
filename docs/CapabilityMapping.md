# RocketMQ Dashboard v2.1.0 → Control Plane 5.0 能力映射

## 概述

本文档记录 RocketMQ Dashboard 从 v2.1.0 到 Control Plane 5.0 版本的能力映射关系，包括功能迁移状态、新增能力、问题修复和迁移指南。

---

## A-K 能力映射表

| 能力ID | 能力名称 | v2.1.0 状态 | CP5.0 状态 | 位置/模块 | 备注 |
|--------|----------|-------------|------------|-----------|------|
| A | 集群概览仪表盘 | ✅ 完整 | ✅ 增强 | `dashboard/` | 新增集群健康评分、趋势分析 |
| B | Topic 管理 | ✅ 完整 | ✅ 增强 | `topic/` | 支持 LiteTopic、消息轨迹增强 |
| C | Consumer 管理 | ✅ 完整 | ✅ 增强 | `consumer/` | Pop 消费模式支持 |
| D | 消息查询 | ✅ 完整 | ✅ 增强 | `message/` | 批量查询、消息导出 |
| E | Broker 管理 | ✅ 完整 | ✅ 增强 | `broker/` | Proxy 模式支持 |
| F | NameServer 管理 | ✅ 完整 | ✅ 保留 | `nameserver/` | 无重大变更 |
| G | ACL 权限管理 | ⚠️ 基础 | ✅ 完整 | `acl/` | ACL 2.0 多租户支持 |
| H | 告警管理 | ❌ 缺失 | ✅ 新增 | `alarm/` | 全新模块，支持多通道告警 |
| I | LLM/AI 集成 | ❌ 缺失 | ✅ 新增 | `llm/`, `mcp/` | 30+ MCP 工具，自然语言运维 |
| J | CLI 工具 | ❌ 缺失 | ✅ 新增 | `cli/` | GraalVM 原生镜像，跨平台 |
| K | SPI 扩展框架 | ❌ 缺失 | ✅ 新增 | `spi/` | MetadataProvider/ClusterProvider/AdminClient |

### 状态说明
- ✅ 完整：功能完整实现并测试
- ⚠️ 基础：基础功能存在，需要增强
- ❌ 缺失：v2.1.0 中不存在

---

## 5.0 新增独占能力

以下能力为 Control Plane 5.0 新增，v2.1.0 中不存在：

| 能力 | 描述 | 实现位置 | 依赖条件 |
|------|------|----------|----------|
| LiteTopic 管理 | 轻量级 Topic 模式，支持动态 Topic 创建 | `LiteTopicController`, `LiteTopic.jsx` | RocketMQ 5.0+ |
| Pop 消费模式 | 基于 Pop 的消息消费，支持负载均衡 | `ConsumerController` 增强 | RocketMQ 5.0+ |
| Proxy 集群管理 | Proxy 节点健康监控、配置热更新、扩缩容 | `ProxyController`, `BrokerCluster.jsx` | RocketMQ 5.0+ |
| 多架构协商 | 运行时 V4/V5/Cloud 架构协商 | `ClusterCapability`, SPI 层 | 自动检测 |
| MCP 工具集 | 30+ 自然语言运维工具 | `rocketmq-dashboard-mcp/` | LLM API Key |
| 多通道告警 | 邮件/钉钉/飞书/Webhook 告警 | `AlarmController`, `AlarmService` | 告警规则配置 |
| 增量部署 | K8s/Helm 自动化部署 | `deploy/kubernetes/`, `deploy/helm/` | K8s 集群 |
| 原生 CLI | 命令行运维工具 | `rocketmq-dashboard-cli/` | GraalVM 构建 |

---

## v2.1.0 问题修复状态

| Issue | 描述 | 修复状态 | 修复位置 |
|-------|------|----------|----------|
| #390 | 前端布局不一致 | ✅ 已修复 | `MainLayout.tsx`, `home/index.tsx` |
| #401 | 面包屑导航缺失 | ✅ 已修复 | `MainLayout.tsx` Breadcrumb 组件 |
| #381 | 首页快捷操作冗余 | ✅ 已修复 | `home/index.tsx` quickActions 精简为 4 项 |
| #380 | 侧边栏菜单结构混乱 | ✅ 已修复 | `StudioLayout.jsx` 菜单分组优化 |
| #402 | LiteTopic 前端缺失 | ✅ 已修复 | `LiteTopic.jsx` 完整页面 |
| #403 | K8s 部署清单缺失 | ✅ 已修复 | `deploy/kubernetes/`, `deploy/helm/` |
| #407 | CLI 跨平台构建缺失 | ✅ 已修复 | `.github/workflows/cli-build.yml` |

---

## 迁移指南

### 1. 配置迁移

**v2.1.0 配置项：**
```properties
# application.properties
rocketmq.namesrv.addr=127.0.0.1:9876
rocketmq.dashboard.login.username=admin
rocketmq.dashboard.login.password=admin
```

**CP5.0 配置项：**
```yaml
# application.yml
rocketmq:
  namesrv:
    addr: ${NAMESRV_ADDR:127.0.0.1:9876}
  proxy:
    addr: ${PROXY_ADDR:}  # 新增：Proxy 地址（5.0 模式）
  dashboard:
    login:
      username: ${DASHBOARD_USERNAME:admin}
      password: ${DASHBOARD_PASSWORD:admin}
    llm:
      enabled: ${LLM_ENABLED:false}  # 新增：LLM 集成
      api-key: ${LLM_API_KEY:}
      provider: ${LLM_PROVIDER:openai}  # openai/azure/ollama
```

**迁移步骤：**
1. 备份 v2.1.0 `application.properties`
2. 转换为 `application.yml` 格式（使用在线工具或手动转换）
3. 添加新增配置项（proxy.addr, llm.*）
4. 环境变量化敏感配置（密码、API Key）

### 2. API 端点映射

| v2.1.0 端点 | CP5.0 端点 | 变更说明 |
|-------------|------------|----------|
| `/api/topic/list` | `/api/topic/list` | ✅ 兼容，新增 `namespace` 参数 |
| `/api/consumer/list` | `/api/consumer/list` | ✅ 兼容，新增 `popMode` 参数 |
| `/api/message/query` | `/api/message/query` | ✅ 兼容，新增 `batchSize` 参数 |
| `/api/broker/status` | `/api/broker/status` | ✅ 兼容 |
| `/api/cluster/list` | `/api/cluster/list` | ✅ 兼容，返回 Proxy 信息 |
| ❌ 不存在 | `/api/liteTopic/list` | 🆕 LiteTopic 管理 |
| ❌ 不存在 | `/api/alarm/list` | 🆕 告警管理 |
| ❌ 不存在 | `/api/llm/chat` | 🆕 LLM 对话 |

### 3. 前端迁移

**路由映射：**
```javascript
// v2.1.0
/topic → Topic 管理页面
/consumer → Consumer 管理页面
/message → 消息查询页面

// CP5.0
/topic → Topic 管理页面（增强）
/liteTopic → LiteTopic 管理页面（新增）
/consumer → Consumer 管理页面（Pop 模式）
/message → 消息查询页面（批量查询）
/alarm → 告警管理页面（新增）
/ai → AI 对话页面（新增）
```

**组件迁移：**
- v2.1.0 使用 Ant Design 4.x → CP5.0 升级到 5.x
- 新增 `useClusterCapabilities()` Hook，用于能力感知渲染
- 新增 `CapabilityAware` 组件，根据集群能力动态显示 UI

### 4. 功能对账清单

**部署前检查：**
- [ ] NameServer 地址配置正确
- [ ] Proxy 地址配置（5.0 模式）
- [ ] 数据库迁移脚本执行（如有）
- [ ] LLM API Key 配置（可选）
- [ ] 告警通道配置（可选）
- [ ] K8s/Helm 部署清单验证

**功能验证：**
- [ ] 集群概览正常显示
- [ ] Topic/Consumer/Message 管理正常
- [ ] LiteTopic 管理正常（5.0 模式）
- [ ] Proxy 集群管理正常（5.0 模式）
- [ ] 告警规则配置正常
- [ ] LLM 对话功能正常（如启用）
- [ ] CLI 工具可正常连接

---

## 架构变更总结

### v2.1.0 架构
```
┌─────────────┐
│   Frontend  │  React + Ant Design 4.x
└──────┬──────┘
       │
┌──────▼──────┐
│   Backend   │  Spring Boot 2.x
└──────┬──────┘
       │
┌──────▼──────┐
│ NameServer  │  Remoting 协议
└─────────────┘
```

### CP5.0 架构
```
┌─────────────────────────────┐
│      Frontend (New)         │  React 18 + Ant Design 5.x
│  ┌──────────────────────┐  │
│  │ CapabilityAware Hook │  │  能力感知渲染
│  └──────────────────────┘  │
└──────────────┬──────────────┘
               │
┌──────────────▼──────────────┐
│       Backend (Multi)       │
│  ┌──────────────────────┐  │
│  │   Legacy Module      │  │  兼容 v2.1.0
│  ├──────────────────────┤  │
│  │   Server Module      │  │  DDD 架构
│  ├──────────────────────┤  │
│  │   LLM Module         │  │  AI 桥接
│  ├──────────────────────┤  │
│  │   MCP Module         │  │  30+ 工具
│  ├──────────────────────┤  │
│  │   CLI Module         │  │  原生镜像
│  └──────────────────────┘  │
└──────────────┬──────────────┘
               │
        ┌──────┴──────┐
        │             │
┌───────▼─────┐  ┌────▼─────┐
│ NameServer  │  │  Proxy   │  双模式支持
│ (Remoting)  │  │  (gRPC)  │
└─────────────┘  └──────────┘
```

---

## 总结

Control Plane 5.0 在保持 v2.1.0 核心功能完整的基础上，新增了：
- **SPI 扩展框架**：支持多架构、多版本 RocketMQ
- **Proxy 模式支持**：完整支持 RocketMQ 5.0 架构
- **LLM/AI 集成**：自然语言运维能力
- **告警管理**：多通道告警通知
- **CLI 工具**：命令行运维工具
- **增量部署**：K8s/Helm 自动化部署

所有 v2.1.0 已知问题均已修复，API 保持向后兼容，前端升级平滑。
