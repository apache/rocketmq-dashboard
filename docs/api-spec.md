<!--
请更新 docs/api-spec.md 接口文档，基于以下数据源：
1. 读取 web/src/mock/ 下所有 .ts 文件，提取每个文件的 interface/type 定义和 mock 数据的字段、类型、示例值
2. 读取 web/src/pages/ 下所有页面组件，提取：
   - Table columns 定义（title、dataIndex、render 函数）
   - 表单字段（Form.Item 的 name、label、component、validation）
   - 过滤/搜索参数
   - CRUD 操作（按钮、modal、调用逻辑）
   - 分页配置（pageSize、showSizeChanger）
3. 读取 web/src/api/ 下所有 API 客户端文件，提取已有的 endpoint URL 和参数
4. 读取 web/src/constants/ 下的枚举映射（如 TOPIC_TYPE_MAP、STATUS_MAP）
5. 将以上信息整合为 GET+POST 风格 API 文档，每个接口包含：
   - HTTP Method + Path
   - Query Parameters / Path Parameters / Request Body（字段、类型、必填、说明）
   - Response data 字段定义（字段、类型、说明、示例值）
   - 枚举值在附录 A 统一列出
6. 保持接口速查表与实际接口数量一致
7. 如果前端新增了页面或 mock 数据，在对应模块章节下新增接口
8. 如果前端删除了页面或字段，同步移除对应接口或字段
-->

# RocketMQ Studio API 接口规范

> 本文档基于前端页面所需展示的全部数据和格式编写，用于指导后端 Java 代码开发。
>
> Base URL: `/api`
> Content-Type: `application/json`
> 浏览器认证方式: `HttpOnly` 会话 Cookie。自动化 API 客户端可在登录时显式请求 bearer token，后续使用 `Authorization: Bearer <token>`。

## 接口设计风格

本项目采用 **GET + POST** 风格（非完整 RESTful），具体规则如下：

- **GET** 用于所有查询/读取操作
- **POST** 用于所有写入/操作类接口（创建、更新、删除、重启等）
- URL 路径使用动词标识操作类型：`/create`、`/update`、`/delete`、`/toggle`、`/restart`、`/cleanup` 等
- POST 操作如需定位特定资源，在 **Request Body** 中传递 `id` 或 `name`（不放在 URL 路径中）
- GET 操作仍使用 Path Params 标识资源（如 `GET /api/topics/:name/routes`）
- **唯一例外**：`PATCH /api/ai/conversations/:id`（AI 会话重命名 / 归档）是全仓第一个也是唯一一个 `PATCH`，刻意偏离本文档的 GET + POST 风格，理由与等价的 POST 改法见 §15.4。`DELETE` **不算**偏离——仓库已有 `DELETE /api/proxies/addresses` 与 `DELETE /api/alert-silences/:id` 两处先例。

## 接口速查

| # | Method | Path | 说明 |
|---|--------|------|------|
| 1 | POST | `/api/auth/login` | 登录 |
| 2 | POST | `/api/auth/logout` | 登出 |
| 3 | GET | `/api/dashboard` | 面板数据（统计 + 集群概览） |
| 4 | GET | `/api/instances` | 实例列表 |
| 5 | POST | `/api/instances/create` | 创建实例 |
| 6 | POST | `/api/instances/update` | 更新实例 |
| 7 | POST | `/api/instances/delete` | 删除实例 |
| 8 | GET | `/api/clusters` | 集群列表 |
| 9 | GET | `/api/clusters/:id` | 集群详情 |
| 10 | POST | `/api/clusters/config/update` | 更新集群配置 |
| 11 | POST | `/api/clusters/:clusterId/brokers/:name/restart` | 重启 Broker |
| 12 | POST | `/api/nameservers/create` | 创建 NameServer |
| 13 | POST | `/api/nameservers/update` | 更新 NameServer |
| 14 | POST | `/api/nameservers/restart` | 重启 NameServer |
| 15 | POST | `/api/nameservers/upgrade` | 升级 NameServer |
| 16 | POST | `/api/nameservers/delete` | 删除 NameServer |
| 17 | POST | `/api/proxies/restart` | 重启 Proxy |
| 18 | GET | `/api/k8s-certs` | K8s 证书列表 |
| 19 | POST | `/api/k8s-certs/create` | 添加证书 |
| 20 | POST | `/api/k8s-certs/update` | 更新证书 |
| 21 | POST | `/api/k8s-certs/delete` | 删除证书 |
| 22 | GET | `/api/topics` | Topic 列表 |
| 23 | POST | `/api/topics/create` | 创建 Topic |
| 24 | POST | `/api/topics/update` | 更新 Topic |
| 25 | POST | `/api/topics/delete` | 删除 Topic |
| 26 | GET | `/api/topics/:name/routes` | Topic 路由 |
| 27 | GET | `/api/topics/:name/consumers` | Topic 消费者 |
| 28 | POST | `/api/topics/send` | 发送消息到 Topic |
| 29 | GET | `/api/groups` | 消费组列表 |
| 30 | GET | `/api/groups/:name` | 消费组详情 |
| 31 | GET | `/api/groups/:name/progress` | 消费进度 |
| 32 | GET | `/api/groups/:name/subscriptions` | 订阅详情 |
| 33 | POST | `/api/groups/create` | 创建消费组 |
| 34 | POST | `/api/groups/delete` | 删除消费组 |
| 35 | POST | `/api/groups/reset-offset` | 重置位点 |
| 36 | POST | `/api/groups/import` | 导入配置 |
| 37 | GET | `/api/groups/export` | 导出配置 |
| 38 | GET | `/api/acl/rules` | ACL 规则列表 |
| 39 | POST | `/api/acl/rules/create` | 创建 ACL 规则 |
| 40 | POST | `/api/acl/rules/delete` | 删除 ACL 规则 |
| 41 | GET | `/api/acl/users` | ACL 用户列表 |
| 42 | POST | `/api/acl/users/create` | 创建 ACL 用户 |
| 43 | POST | `/api/acl/users/delete` | 删除 ACL 用户 |
| 44 | GET | `/api/messages` | 消息查询 |
| 45 | GET | `/api/messages/page` | 分页消息查询 |
| 46 | GET | `/api/messages/:msgId/trace` | 按消息 ID 查询轨迹 |
| 47 | GET | `/api/messages/trace-by-key` | 按业务 Key 查询轨迹 |
| 48 | GET | `/api/messages/queues` | Topic 队列位点范围 |
| 49 | GET | `/api/messages/queue-message` | 按队列位点读取消息 |
| 50 | POST | `/api/messages/direct-consume` | 指定客户端直接消费消息 |
| 51 | GET | `/api/dlq` | 死信队列列表 |
| 52 | POST | `/api/dlq/resend` | 重发死信 |
| 53 | GET | `/api/clients` | 客户端连接列表 |
| 54 | GET | `/api/producer/groups` | Producer Group 候选列表 |
| 55 | GET | `/api/producer/connection` | Producer 连接详情 |
| 56 | GET | `/api/alert-rules` | 告警规则列表 |
| 57 | POST | `/api/alert-rules/create` | 创建告警规则 |
| 58 | POST | `/api/alert-rules/update` | 更新告警规则 |
| 59 | POST | `/api/alert-rules/toggle` | 切换启用状态 |
| 60 | POST | `/api/alert-rules/delete` | 删除告警规则 |
| 61 | GET | `/api/system-alerts` | 系统告警列表 |
| 62 | POST | `/api/system-alerts/acknowledge` | 确认告警 |
| 63 | POST | `/api/system-alerts/clear-acknowledged` | 清除已确认告警 |
| 64 | GET | `/api/audit-logs` | 审计日志列表 |
| 65 | GET | `/api/audit-logs/filter-options` | 审计日志筛选项 |
| 66 | GET | `/api/audit-logs/export` | 导出审计日志 |
| 67 | POST | `/api/audit-logs/cleanup` | 清理审计日志 |
| 68 | GET | `/api/settings/general` | 获取通用设置 |
| 69 | POST | `/api/settings/general/save` | 保存通用设置 |
| 70 | GET | `/api/settings/datasources` | 数据源选择器列表（未分页） |
| 71 | GET | `/api/settings/datasources/page` | 分页数据源列表 |
| 72 | POST | `/api/settings/datasources/create` | 创建数据源 |
| 73 | POST | `/api/settings/datasources/update` | 更新数据源 |
| 74 | POST | `/api/settings/datasources/delete` | 删除数据源 |
| 75 | POST | `/api/settings/datasources/test` | 测试数据源连接 |
| 76 | POST | `/api/ai/conversations` | 创建 AI 会话 |
| 77 | GET | `/api/ai/conversations` | AI 会话列表（分页） |
| 78 | GET | `/api/ai/conversations/:id` | AI 会话详情 |
| 79 | PATCH | `/api/ai/conversations/:id` | 重命名 / 归档 AI 会话 |
| 80 | DELETE | `/api/ai/conversations/:id` | 删除 AI 会话 |
| 81 | GET | `/api/ai/conversations/:id/events` | AI 会话时间线（游标分页） |
| 82 | POST | `/api/ai/conversations/:id/messages` | 发送消息并流式返回（SSE） |
| 83 | GET | `/api/ai/runs/:runId/stream` | 重连进行中的 AI 任务（SSE） |
| 84 | POST | `/api/ai/runs/:runId/stop` | 停止 AI 任务 |
| 85 | GET | `/api/ai/agent-capabilities` | Agent 运行时能力探测 |
| 86 | GET | `/api/ai/conversations/:id/rmqctl-config` | 外部 Agent 的 rmqctl MCP 配置片段 |
| 87 | GET | `/api/ai/tools` | 可用工具列表 |
| 88 | POST | `/api/ai/tools/:name/execute` | 执行只读 AI 工具 |
| 89 | POST | `/api/metrics/query` | 查询监控指标数据 |
| 90 | GET | `/api/acl/cluster-config` | 集群 ACL 配置概要（存储级） |
| 91 | POST | `/api/acl/plain-access-config` | 创建/更新 Plain Access 账号 |
| 92 | GET | `/api/acl/users/:id/credentials` | 查看单个用户明文凭证 |
| 93 | GET | `/api/metrics/grafana/dashboards` | Grafana 看板列表 |
| 94 | GET | `/api/metrics/grafana/dashboards/:uid` | Grafana 看板 JSON 模型 |
| 95 | GET | `/api/metrics/grafana/dashboards/:uid/export` | 导出单个 Grafana 看板 JSON |
| 96 | GET | `/api/metrics/grafana/dashboards/export` | 打包导出全部 Grafana 看板 |
| 97 | GET | `/api/instances/:instanceId/capabilities` | 实例能力契约 |
| 98 | GET | `/api/topics/page` | Topic 分页列表 |
| 99 | GET | `/api/liteTopic/list` | LiteTopic 会话列表 |
| 100 | GET | `/api/liteTopic/session/:sessionId` | LiteTopic 会话详情 |
| 101 | POST | `/api/liteTopic/extendTTL` | 延长 LiteTopic TTL |
| 102 | GET | `/api/liteTopic/quota` | LiteTopic 配额 |
| 103 | GET | `/api/liteTopic/capability` | LiteTopic 能力检查 |
| 104 | GET | `/api/query-history/messages` | 消息查询历史（分页） |
| 105 | GET | `/api/query-history/traces` | 轨迹查询历史（分页） |
| 106 | GET | `/api/query-history/summary` | 查询历史汇总 |
| 107 | GET | `/api/query-history/messages/:id/results` | 消息查询结果回放 |
| 108 | GET | `/api/auth/status` | 登录策略与当前用户 |
| 109 | POST | `/api/auth/password` | 修改当前用户密码 |
| 110 | POST | `/api/ai/runs/:runId/speed` | 上报 Run 生成速度 |

## 通用响应格式

所有接口统一返回以下 JSON 结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

兼容接口 `GET /api/producer/connection` 直接返回 `ProducerConnectionResult`，
不使用上述 `Result.data` 包装；详见 10.3。

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | `int` | 200 表示成功，与 HTTP 状态码对齐 |
| `message` | `string` | 提示信息 |
| `data` | `any` | 响应数据 |

**错误码规范：**

| 错误码 | 含义 | 场景 |
|--------|------|------|
| `200` | 成功 | 正常响应 |
| `400` | 参数错误 | 请求参数缺失或格式错误 |
| `401` | 未认证 | Token 过期或缺失 |
| `403` | 无权限 | 无权访问该资源 |
| `404` | 不存在 | 资源未找到 |
| `409` | 冲突 | 与资源当前状态冲突，如会话已有进行中的 Run、停止的不是当前 Run（见 15.7 / 15.9） |
| `500` | 服务器异常 | 未预期的内部错误 |

---

## 1. 认证 Auth

### 1.1 登录

```
POST /api/auth/login
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | `string` | 是 | 用户名 |
| `password` | `string` | 是 | 密码 |

**可选 Request Header：**

| Header | 值 | 说明 |
|--------|----|------|
| `X-RocketMQ-Studio-Session-Delivery` | `bearer` | 仅供非浏览器 API 客户端使用。响应 `data.token` 返回 bearer token，且不设置 Cookie。省略时使用 `HttpOnly` 会话 Cookie，响应不包含 token。 |

**Response `data`:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `token` | `string` | 仅当请求 `X-RocketMQ-Studio-Session-Delivery: bearer` 时返回的会话 token |
| `expiresIn` | `number` | 过期时间（秒） |
| `user` | `object` | 用户信息 |
| `user.userId` | `number` | 用户 ID（数据库主键）。配置引导用户（未落库）登录时为空 |
| `user.username` | `string` | 用户名 |
| `user.admin` | `boolean` | 是否管理员 |

启用 `studio.auth.login-required` 后，非管理员会话为只读角色。GET、HEAD
和只读查询接口可访问；创建、更新、删除、重启、发送消息等写操作要求
`user.admin=true`，否则返回 HTTP 403。登录、登出、CORS 预检及明确的只读
POST 查询不受管理员限制。

### 1.2 登出

```
POST /api/auth/logout
```

**Response `data`:** `null`

### 1.3 获取登录策略与当前用户

```
GET /api/auth/status
```

**Response `data`:** `AuthStatus`

| 字段 | 类型 | 说明 |
|------|------|------|
| `loginRequired` | `boolean` | 是否要求登录（部署配置或通用设置的 `requireLogin` 决定） |
| `authenticated` | `boolean` | 当前请求是否已认证 |
| `user` | `object \| null` | 已认证时的用户信息（同 1.1 `LoginVO` 的 `user`），未认证为 `null` |

响应带 `Cache-Control: no-store`。

### 1.4 修改当前用户密码

```
POST /api/auth/password
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `currentPassword` | `string` | 是 | 当前密码 |
| `newPassword` | `string` | 是 | 新密码 |

**Response `data`:** `null`

**错误：**

| 状态码 | 场景 |
|--------|------|
| `401` | 未认证 |
| `503` | Studio 用户管理未初始化（本地模式无用户表） |

---

## 2. 监控面板 Dashboard

### 2.1 获取面板数据（统计 + 集群概览）

```
GET /api/dashboard
```

**Response `data`:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `stats` | `DashboardStats` | 面板统计数据 |
| `clusters` | `ClusterOverview[]` | 集群概览列表 |

**DashboardStats 字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `totalClusters` | `number` | 集群总数 |
| `healthyClusters` | `number` | 健康集群数 |
| `totalBrokers` | `number` | Broker 总数（≥ 0） |
| `totalProxies` | `number` | Proxy 总数（≥ 0） |
| `totalNameServers` | `number` | NameServer 总数（≥ 0） |
| `totalTopics` | `number` | Topic 总数 |
| `totalConsumerGroups` | `number` | 消费组总数 |
| `totalMessagesToday` | `number` | 今日消息总量 |
| `messagesPerSecond` | `number` | 每秒消息数 |
| `tpsIn` | `number` | 入站 TPS |
| `tpsOut` | `number` | 出站 TPS |

**ClusterOverview 字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `string` | 集群 ID |
| `name` | `string` | 集群名称 |
| `type` | `string` | 集群类型，枚举: `V4_DIRECT` / `V5_PROXY_LOCAL` / `V5_PROXY_CLUSTER` |
| `status` | `string` | 状态: `healthy` / `warning` / `error` / `offline` |
| `brokers` | `number` | Broker 数量（≥ 0） |
| `proxies` | `number` | Proxy 数量（≥ 0，不会为负数） |
| `topics` | `number` | Topic 数量 |
| `groups` | `number` | 消费组数量 |
| `tpsIn` | `number` | 入站 TPS |
| `tpsOut` | `number` | 出站 TPS |
| `version` | `string` | RocketMQ 版本号 |
| `throughput` | `number[]` | TPS 趋势数据（12 个采样点） |

**示例：**

```json
{
  "stats": {
    "totalClusters": 2,
    "healthyClusters": 2,
    "totalBrokers": 12,
    "totalProxies": 5,
    "totalNameServers": 5,
    "totalTopics": 304,
    "totalConsumerGroups": 152,
    "totalMessagesToday": 18420000,
    "messagesPerSecond": 85800,
    "tpsIn": 85800,
    "tpsOut": 222400
  },
  "clusters": [
    {
      "id": "cluster-prod",
      "name": "rocketmq-prod",
      "type": "V5_PROXY_CLUSTER",
      "status": "healthy",
      "brokers": 8,
      "proxies": 3,
      "topics": 256,
      "groups": 128,
      "tpsIn": 78000,
      "tpsOut": 203600,
      "version": "5.2.0",
      "throughput": [62000, 68000, 74000, 71000, 78000, 82000, 79000, 85000, 81000, 78000, 76000, 80000]
    }
  ]
}
```

---

### 3.1 获取实例列表

```
GET /api/instances?type={type}&search={keyword}
```

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `type` | `string` | 否 | 按类型过滤: `CLOUD` / `PROXY_LOCAL` / `PROXY_CLUSTER` / `DIRECT` |
| `search` | `string` | 否 | 按名称或地址搜索 |

**Response `data`:** `Instance[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `number` | 实例 ID |
| `name` | `string` | 实例名称 |
| `remark` | `string` | 备注 |
| `type` | `string` | 接入类型: `CLOUD` / `PROXY_LOCAL` / `PROXY_CLUSTER` / `DIRECT` |
| `endpoint` | `string` | 接入地址 |
| `topicCount` | `number` | Topic 数量 |
| `consumerGroupCount` | `number` | 消费组数量 |
| `gmtCreate` | `string` | 创建时间 |
| `gmtModified` | `string` | 更新时间 |

### 3.2 创建实例

```
POST /api/instances/create
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | `string` | 是 | 实例名称 |
| `type` | `string` | 是 | Apache 实例使用 `PROXY_LOCAL` / `PROXY_CLUSTER` / `DIRECT`；`CLOUD` 仅用于云厂商代管的实例，手工创建会被拒绝 |
| `endpoint` | `string` | 是 | 接入地址 |

**Response `data`:** `Instance`

### 3.3 更新实例

```
POST /api/instances/update
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `string` | 是 | 实例 ID |
| `name` | `string` | 是 | 实例名称 |
| `type` | `string` | 是 | `PROXY_LOCAL` / `PROXY_CLUSTER` / `DIRECT`；`CLOUD` 仅用于云厂商代管的实例 |
| `endpoint` | `string` | 是 | 接入地址 |

**Response `data`:** `Instance`

### 3.4 删除实例

```
POST /api/instances/delete
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `string` | 是 | 实例 ID |

**Response `data`:** `null`

### 3.5 获取实例能力契约

```
GET /api/instances/{instanceId}/capabilities
```

**Path Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `instanceId` | `string` | 是 | 实例 ID（全局唯一字符串） |

**Response `data`:** `InstanceCapabilities`

| 字段 | 类型 | 说明 |
|------|------|------|
| `instanceId` | `string` | 实例 ID |
| `vendor` | `string` | 厂商: `APACHE` / `ALIYUN` / `TENCENT` |
| `accessType` | `string` | 接入类型: `PROXY_LOCAL` / `PROXY_CLUSTER` / `DIRECT` 等 |
| `capabilities` | `string[]` | 能力列表: `TOPIC_MANAGEMENT` / `CONSUMER_GROUP_MANAGEMENT` / `MESSAGE_QUERY` / `MESSAGE_TRACE` / `ACL_MANAGEMENT` / `DLQ_MANAGEMENT` |

---

## 4. 集群管理 Cluster / NameServer / Proxy

> 前端在同一个页面展示，因此 API 合并在此章节。

### 4.1 获取集群列表

```
GET /api/clusters
```

**Response `data`:** `ClusterInfo[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `string` | 集群 ID |
| `name` | `string` | 集群名称 |
| `nsClusterName` | `string` | NameServer 集群名 |
| `type` | `string` | 集群类型，枚举: `V5_PROXY_CLUSTER` |
| `endpoint` | `string` | NameServer 地址，如 `10.101.2.1:9876` |
| `status` | `string` | 状态: `healthy` / `warning` / `error` / `offline` |
| `version` | `string` | 版本号 |
| `brokers` | `BrokerInfo[]` | Broker 节点列表 |
| `proxies` | `ProxyInfo[]` | Proxy 节点列表 |
| `nameServers` | `NameServerInfo[]` | NameServer 节点列表 |
| `config` | `ClusterConfig` | 集群配置 |
| `topicCount` | `number` | Topic 数量 |
| `groupCount` | `number` | 消费组数量 |
| `tpsHistory` | `number[]` | TPS 历史数据 |

#### BrokerInfo

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | `string` | Broker 名称 |
| `addr` | `string` | 地址，如 `10.101.2.11:10911` |
| `version` | `string` | 版本号 |
| `status` | `string` | 状态: `running` / `readonly` / `maintenance` |
| `diskUsage` | `number` | 磁盘使用率（0-100） |
| `tpsIn` | `number` | 入站 TPS |
| `tpsOut` | `number` | 出站 TPS |

#### ProxyInfo

| 字段 | 类型 | 说明 |
|------|------|------|
| `addr` | `string` | 地址 |
| `status` | `string` | 状态: `healthy` / `warning` / `error` / `offline` |
| `connections` | `number` | 当前连接数 |
| `grpcPort` | `number` | gRPC 端口 |
| `remotingPort` | `number` | Remoting 端口 |

#### NameServerInfo

| 字段 | 类型 | 说明 |
|------|------|------|
| `addr` | `string` | 地址，如 `10.101.2.1:9876` |
| `status` | `string` | 状态: `healthy` / `warning` / `error` / `offline` |

#### ClusterConfig

| 字段 | 类型 | 说明 |
|------|------|------|
| `writeQueueNums` | `number` | 写队列数 |
| `readQueueNums` | `number` | 读队列数 |
| `maxMessageSize` | `number` | 最大消息大小（字节） |
| `msgTraceTopicName` | `string` | 消息轨迹 Topic 名 |
| `autoCreateTopicEnable` | `boolean` | 是否自动创建 Topic |
| `autoCreateSubscriptionGroup` | `boolean` | 是否自动创建订阅组 |
| `deleteWhen` | `string` | 删除时间，如 `"04"` |
| `fileReservedTime` | `number` | 文件保留时间（小时） |
| `flushDiskType` | `string` | 刷盘方式: `ASYNC_FLUSH` / `SYNC_FLUSH` |
| `brokerPermission` | `number` | Broker 权限值（6 = 读写） |

### 4.2 获取集群详情

```
GET /api/clusters/:id
```

**Response `data`:** `ClusterInfo`（同 4.1 的单条记录）

### 4.3 更新集群配置

```
POST /api/clusters/config/update
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `string` | 是 | 集群 ID |
| `flushDiskType` | `string` | 否 | `ASYNC_FLUSH` / `SYNC_FLUSH` |
| `autoCreateTopicEnable` | `boolean` | 否 | 自动创建 Topic |
| `autoCreateSubscriptionGroup` | `boolean` | 否 | 自动创建订阅组 |
| `maxMessageSize` | `number` | 否 | 最大消息大小（字节，1,048,576-134,217,728） |
| `fileReservedTime` | `number` | 否 | 文件保留时间（小时，1-720） |
| `writeQueueNums` | `number` | 否 | 写队列数（1-256） |
| `readQueueNums` | `number` | 否 | 读队列数（1-256） |
| `brokerPermission` | `number` | 否 | Broker 权限（0-7） |

未提供的可选字段保持原配置不变。

**Response `data`:** `ClusterInfo`（同 4.1 的单条记录）

### 4.4 重启 Broker

```
POST /api/clusters/:clusterId/brokers/:name/restart
```

**Path Parameters:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `clusterId` | `string` | 集群 ID |
| `name` | `string` | Broker 名称 |

**Response `data`:** `{ success: boolean, message: string }`

### 4.5 创建 NameServer

```
POST /api/nameservers/create
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `clusterId` | `string` | 是 | 所属集群 ID |
| `addr` | `string` | 是 | NameServer 地址，如 `10.0.1.1:9876` |

**Response `data`:** `NameServerInfo`

### 4.6 更新 NameServer

```
POST /api/nameservers/update
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `clusterId` | `string` | 是 | 所属集群 ID |
| `addr` | `string` | 是 | 原地址 |
| `newAddr` | `string` | 否 | 新地址（不传则不修改） |

**Response `data`:** `NameServerInfo`

### 4.7 重启 NameServer

```
POST /api/nameservers/restart
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `addr` | `string` | 是 | NameServer 地址 |

**Response `data`:** `{ success: boolean }`

### 4.8 升级 NameServer

```
POST /api/nameservers/upgrade
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `addr` | `string` | 是 | NameServer 地址 |

**Response `data`:** `{ success: boolean }`

### 4.9 删除 NameServer

```
POST /api/nameservers/delete
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `addr` | `string` | 是 | NameServer 地址 |

**Response `data`:** `{ success: boolean }`

### 4.10 检查 NameServer 配置漂移

```
GET /api/nameservers/config-diff?clusterId={clusterId}
```

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `clusterId` | `string` | 是 | 要检查的集群 ID |

该接口逐个读取集群内的 NameServer 配置，只比较服务端白名单中的非敏感运行参数。完整配置、路径、密码和凭据不会返回；单个节点读取失败时仍返回其他节点的结果，并将 `complete` 标记为 `false`。

**Response `data`:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `cluster` | `string` | 集群 ID |
| `complete` | `boolean` | 是否成功读取全部 NameServer 节点 |
| `driftDetected` | `boolean` | 可达节点间是否存在配置差异 |
| `nodeCount` | `number` | NameServer 节点总数 |
| `reachableNodeCount` | `number` | 成功读取的节点数 |
| `comparedKeys` | `string[]` | 本次比较的安全配置项 |
| `nodes` | `NodeStatus[]` | 节点地址和可达状态 |
| `differences` | `ConfigDifference[]` | 配置不一致的键及各节点值 |

`differences[].values[].configured` 用于区分未配置和已配置为空值；`value` 仅包含白名单配置项的值。

### 4.11 重启 Proxy

```
POST /api/proxies/restart
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `addr` | `string` | 是 | Proxy 地址 |

**Response `data`:** `{ success: boolean }`

### 4.12 获取 K8s 证书列表

K8s 证书接口仅管理 Studio 本地配置记录，不会连接 Kubernetes API，也不会创建、修改或删除集群中的 Secret 或证书资源。

```
GET /api/k8s-certs
```

**Response `data`:** `K8sCertInfo[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `string` | 证书 ID |
| `name` | `string` | 证书名称 |
| `namespace` | `string` | K8s 命名空间 |
| `cluster` | `string` | 所属集群名称 |
| `type` | `string` | 证书类型: `TLS` / `mTLS` / `ServiceAccount` |
| `issuer` | `string` | 签发者 |
| `notBefore` | `string` | 生效时间 (ISO 8601) |
| `notAfter` | `string` | 过期时间 (ISO 8601) |
| `status` | `string` | 状态: `valid` / `expiring` / `expired` |
| `daysRemaining` | `number` | 剩余天数 |
| `san` | `string[]` | Subject Alternative Name 列表 |

### 4.13 添加 K8s 证书

```
POST /api/k8s-certs/create
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | `string` | 是 | 证书名称 |
| `namespace` | `string` | 是 | K8s 命名空间 |
| `cluster` | `string` | 是 | 所属集群 |
| `type` | `string` | 是 | 证书类型: `TLS` / `mTLS` / `ServiceAccount` |
| `issuer` | `string` | 否 | 签发者 |

**Response `data`:** `K8sCertInfo`

### 4.14 更新 K8s 证书

```
POST /api/k8s-certs/update
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `string` | 是 | 证书 ID |
| `name` | `string` | 否 | 证书名称 |
| `namespace` | `string` | 否 | K8s 命名空间 |
| `cluster` | `string` | 否 | 所属集群 |
| `type` | `string` | 否 | 证书类型 |
| `issuer` | `string` | 否 | 签发者 |

**Response `data`:** `K8sCertInfo`

### 4.15 删除 K8s 证书

```
POST /api/k8s-certs/delete
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `string` | 是 | 证书 ID |

**Response `data`:** `null`

---

## 5. Topic 管理

### 5.1 获取 Topic 列表

```
GET /api/topics?clusterId={clusterId}&type={type}&search={keyword}
```

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `clusterId` | `string` | 否 | 按集群过滤 |
| `type` | `string` | 否 | 按类型过滤: `NORMAL` / `FIFO` / `DELAY` / `TRANSACTION` / `LITE` |
| `search` | `string` | 否 | 按名称搜索 |

**Response `data`:** `Topic[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | `string` | Topic 名称 |
| `namespace` | `string` | 命名空间 |
| `type` | `string` | 类型: `NORMAL` / `FIFO` / `DELAY` / `TRANSACTION` / `LITE` |
| `clusterId` | `string` | 所属集群 ID |
| `writeQueues` | `number` | 写队列数 |
| `readQueues` | `number` | 读队列数 |
| `perm` | `string` | 权限: `RW` / `RO` / `WO` |
| `messageCount` | `number` | 消息总量 |
| `tps` | `number` | 当前 TPS |
| `consumerGroupCount` | `number` | 订阅消费组数 |
| `remark` | `string` | 备注 |
| `createdAt` | `string` | 创建时间 (ISO 8601) |
| `updatedAt` | `string` | 更新时间 (ISO 8601) |

### 5.2 分页获取 Topic 列表

```
GET /api/topics/page?instanceId={instanceId}&clusterId={clusterId}&type={type}&search={keyword}&page={page}&pageSize={pageSize}
```

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `instanceId` | `string` | 否 | 实例 ID（全局唯一字符串） |
| `clusterId` | `string` | 否 | 按集群过滤 |
| `type` | `string` | 否 | 按类型过滤 |
| `search` | `string` | 否 | 按名称搜索 |
| `page` | `number` | 否 | 页码，默认 `1` |
| `pageSize` | `number` | 否 | 每页条数，默认 `20`，最大 `100` |

**Response `data`:** `PageResult<Topic>`

| 字段 | 类型 | 说明 |
|------|------|------|
| `items` | `Topic[]` | 当前页数据，结构同 5.1 |
| `total` | `number` | 总条数；云厂商实例返回 provider 原生分页的匹配总数，不会被单次列表上限截断 |
| `page` | `number` | 当前页码 |
| `size` | `number` | 每页条数（回显请求中的 `pageSize`） |

### 5.3 创建 Topic

```
POST /api/topics/create
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | `string` | 是 | Topic 名称 |
| `namespace` | `string` | 否 | 命名空间 |
| `type` | `string` | 是 | 类型 |
| `clusterId` | `string` | 是 | 所属集群 |
| `writeQueues` | `number` | 是 | 写队列数 |
| `readQueues` | `number` | 是 | 读队列数 |
| `perm` | `string` | 否 | 权限，默认 `RW` |
| `remark` | `string` | 否 | 备注 |

**Response `data`:** `Topic`

### 5.4 更新 Topic

```
POST /api/topics/update
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | `string` | 是 | Topic 名称（不可修改） |
| `namespace` | `string` | 否 | 命名空间 |
| `type` | `string` | 是 | 类型 |
| `clusterId` | `string` | 是 | 所属集群 |
| `writeQueues` | `number` | 是 | 写队列数 |
| `readQueues` | `number` | 是 | 读队列数 |
| `perm` | `string` | 否 | 权限，默认 `RW` |
| `remark` | `string` | 否 | 备注 |

**Response `data`:** `Topic`

### 5.5 删除 Topic

```
POST /api/topics/delete
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | `string` | 是 | Topic 名称 |

**Response `data`:** `null`

### 5.6 获取 Topic 路由信息

```
GET /api/topics/:name/routes
```

**Response `data`:** `BrokerRoute[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| `brokerName` | `string` | Broker 名称 |
| `brokerAddr` | `string` | Broker 地址 |
| `writeQueues` | `number` | 写队列数 |
| `readQueues` | `number` | 读队列数 |
| `perm` | `string` | 权限 |

### 5.7 获取 Topic 消费者列表

```
GET /api/topics/:name/consumers
```

**Response `data`:** `TopicConsumerInfo[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| `group` | `string` | 消费组名称 |
| `consumeType` | `string` | 消费类型: `CLUSTERING` / `BROADCASTING` |
| `messageModel` | `string` | 消费模式描述 |
| `consumeTps` | `number` | 消费 TPS |
| `diffTotal` | `number` | 堆积消息数 |

### 5.8 发送消息到 Topic

```
POST /api/topics/send
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `topic` | `string` | 是 | Topic 名称 |
| `tag` | `string` | 否 | 消息 Tag |
| `key` | `string` | 否 | 消息 Key（用于消息查询） |
| `body` | `string` | 是 | 消息体内容 |
| `properties` | `Record<string, string>` | 否 | 消息自定义属性键值对 |

**Response `data`:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `msgId` | `string` | 消息 ID |
| `sendTime` | `string` | 发送时间 (ISO 8601) |
| `offsetMsgId` | `string` | 含偏移量的消息 ID |

**示例：**

```json
// Request
{
  "topic": "order-create",
  "tag": "order",
  "key": "ORDER-20260708-001",
  "body": "{\"orderId\":\"20260708001\",\"amount\":299.00,\"status\":\"CREATED\"}",
  "properties": {
    "source": "rocketmq-studio",
    "env": "test"
  }
}

// Response
{
  "msgId": "7F000001234567890000",
  "sendTime": "2026-07-08T10:30:45.123Z",
  "offsetMsgId": "7F000001234567890000-0:0:0:0"
}
```

---

## 6. 消费组管理 Consumer Group

### 6.1 获取消费组列表

```
GET /api/groups?clusterId={clusterId}&search={keyword}
```

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `clusterId` | `string` | 否 | 按集群过滤 |
| `search` | `string` | 否 | 按名称搜索 |

**Response `data`:** `ConsumerGroup[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | `string` | 消费组名称 |
| `namespace` | `string` | 命名空间 |
| `clusterId` | `string` | 所属集群 ID |
| `subscriptionMode` | `string` | 订阅模式: `Push` / `Pop` |
| `consumeType` | `string` | 消费类型: `CLUSTERING` / `BROADCASTING` |
| `onlineInstances` | `number` | 在线实例数；`-1` 表示连接信息不可用 |
| `totalLag` | `number` | 总堆积消息数 |
| `subscribedTopics` | `string[]` | 订阅的 Topic 列表 |
| `subscriptionDataType` | `string` | 订阅数据类型: `NORMAL` / `FIFO` / `DELAY` / `TRANSACTION` |
| `deliveryOrderType` | `string?` | 顺序类型（FIFO 时）: `PARTITON_ORDER` / `MESSAGES_ORDER` |
| `retryMaxTimes` | `number` | 最大重试次数 |
| `delaySeconds` | `number` | 延迟秒数 |
| `createdAt` | `string` | 创建时间 |
| `updatedAt` | `string` | 更新时间 |

### 6.2 获取消费组详情

```
GET /api/groups/:name
```

**Response `data`:** `ConsumerGroup`（含 `instances` 字段）

| 字段 | 类型 | 说明 |
|------|------|------|
| `instances` | `ConsumerInstance[]` | 消费者实例列表 |

#### ConsumerInstance

| 字段 | 类型 | 说明 |
|------|------|------|
| `clientId` | `string` | 客户端 ID |
| `protocol` | `string` | 协议: `gRPC` / `Remoting` |
| `address` | `string` | 客户端地址 |
| `subscribedTopics` | `string[]` | 订阅的 Topic |
| `lastHeartbeat` | `string` | 最后心跳时间 |
| `topicLag` | `Record<string, number>` | 各 Topic 堆积数 `{ "topic-name": lag_count }` |

### 6.3 获取消费进度

```
GET /api/groups/:name/progress
```

**Response `data`:** `QueueProgress[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| `broker` | `string` | Broker 名称 |
| `queueId` | `number` | 队列 ID |
| `brokerOffset` | `number` | Broker 端 offset |
| `consumerOffset` | `number` | 消费端 offset |
| `diffTotal` | `number` | 堆积差值 |

### 6.4 获取订阅详情

```
GET /api/groups/:name/subscriptions
```

**Response `data`:** `SubscriptionEntry[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| `topic` | `string` | Topic 名称 |
| `expression` | `string` | 过滤表达式 |
| `type` | `string` | 数据类型: `NORMAL` / `FIFO` / `DELAY` / `TRANSACTION` |
| `filterMode` | `string` | 过滤模式: `Tag 过滤` / `SQL92 过滤` / `全量` |
| `consistency` | `string` | 一致性: `一致` / `不一致` |

### 6.5 创建消费组

```
POST /api/groups/create
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | `string` | 是 | 消费组名称 |
| `namespace` | `string` | 否 | 命名空间 |
| `clusterId` | `string` | 是 | 所属集群 |
| `subscriptionMode` | `string` | 是 | `Push` / `Pop` |
| `consumeType` | `string` | 是 | `CLUSTERING` / `BROADCASTING` |
| `subscribedTopics` | `string[]` | 否 | 订阅 Topic |
| `retryMaxTimes` | `number` | 否 | 最大重试次数 |

**Response `data`:** `ConsumerGroup`

### 6.6 删除消费组

```
POST /api/groups/delete
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | `string` | 是 | 消费组名称 |

**Response `data`:** `null`

### 6.7 重置消费位点

```
POST /api/groups/reset-offset
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | `string` | 是 | 消费组名称 |
| `timestamp` | `string` | 是 | 重置到指定时间 (ISO 8601) |
| `topic` | `string` | 否 | 指定 Topic，不传则全部重置 |

**Response `data`:** `null`

### 6.8 导入消费组配置

```
POST /api/groups/import
```

**Request Body:** `multipart/form-data`

| 字段 | 类型 | 说明 |
|------|------|------|
| `file` | `File` | JSON 配置文件 |

**Response `data`:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `imported` | `number` | 成功导入数量 |
| `failed` | `number` | 失败数量 |
| `errors` | `string[]` | 错误信息列表 |

### 6.9 导出消费组配置

```
GET /api/groups/export?names={name1,name2}
```

**Response:** `Content-Type: application/json`, `Content-Disposition: attachment`

---

## 7. ACL 管理

### 7.1 获取 ACL 规则列表

```
GET /api/acl/rules?principal={principal}&resource={resource}&scope={scope}&decision={decision}&instanceId={instanceId}&page={page}&pageSize={pageSize}
```

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `principal` | `string` | 否 | 按主体过滤 |
| `resource` | `string` | 否 | 按资源过滤 |
| `scope` | `string` | 否 | 按作用域过滤 |
| `decision` | `string` | 否 | 按决策过滤 |
| `instanceId` | `string` | 否 | 按所属实例过滤 |
| `page` | `number` | 否 | 页码，默认 `1` |
| `pageSize` | `number` | 否 | 每页条数，默认 `20` |

**Response `data`:** `PageResult<AclRule>`

| 字段 | 类型 | 说明 |
|------|------|------|
| `items` | `AclRule[]` | 当前页规则 |
| `total` | `number` | 总条数 |
| `page` | `number` | 当前页码 |
| `size` | `number` | 当前页大小 |

#### AclRule

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `number` | 规则 ID |
| `principal` | `string` | 主体用户名 |
| `resource` | `string` | 资源名称/模式 |
| `resourceType` | `string` | 资源类型: `Topic` / `Group` / `Cluster` |
| `resourcePattern` | `string` | 匹配模式: `LITERAL` / `PREFIX` |
| `actions` | `string[]` | 操作列表: `PUB` / `SUB` / `ALL` |
| `decision` | `string` | 决策: `ALLOW` / `DENY` |
| `scope` | `string` | 作用域: `cluster` / `namespace` |
| `aclVersion` | `string` | ACL 版本: `1.0` / `2.0` |
| `gmtCreate` | `string` | 创建时间 (ISO 8601) |

### 7.2 创建 ACL 规则

```
POST /api/acl/rules/create
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `principal` | `string` | 是 | 主体 |
| `resource` | `string` | 是 | 资源 |
| `resourceType` | `string` | 是 | 资源类型 |
| `resourcePattern` | `string` | 是 | 匹配模式 |
| `actions` | `string[]` | 是 | 操作列表 |
| `decision` | `string` | 是 | ALLOW / DENY |
| `scope` | `string` | 否 | 作用域 |

**Response `data`:** `AclRule`

### 7.3 删除 ACL 规则

```
POST /api/acl/rules/delete
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `string` | 是 | 规则 ID |

**Response `data`:** `null`

### 7.4 获取 ACL 用户列表

```
GET /api/acl/users
```

**Response `data`:** `AclUser[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `number` | 用户 ID |
| `username` | `string` | 用户名 |
| `accessKey` | `string` | AccessKey（脱敏显示） |
| `secretKey` | `string` | SecretKey（脱敏显示） |
| `admin` | `boolean` | 是否管理员 |
| `clusters` | `string[]` | 授权集群列表 |
| `gmtCreate` | `string` | 创建时间 |

完整的 AccessKey 和 SecretKey 仅在创建用户的响应中返回一次，后续列表查询和更新响应只返回脱敏值。

### 7.5 创建 ACL 用户

```
POST /api/acl/users/create
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | `string` | 是 | 用户名 |
| `admin` | `boolean` | 否 | 是否管理员，默认 false |
| `clusters` | `string[]` | 否 | 授权集群 |

**Response `data`:** `AclUser`（含生成的 accessKey/secretKey）

### 7.6 更新 ACL 用户

```
POST /api/acl/users/update
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `string` | 是 | 用户 ID |
| `username` | `string` | 否 | 用户名 |
| `admin` | `boolean` | 是 | 是否管理员 |
| `clusters` | `string[]` | 否 | 授权集群 |

更新请求不得包含 `accessKey` 或 `secretKey`；服务端会读取原用户并保留已存储凭证。若用户 ID 不存在，返回业务错误 `ACL user not found: {id}`。

**Response `data`:** `AclUser`（accessKey/secretKey 为脱敏值）

### 7.7 删除 ACL 用户

```
POST /api/acl/users/delete
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `string` | 是 | 用户 ID |

**Response `data`:** `null`

### 7.8 获取集群 ACL 配置概要

```
GET /api/acl/cluster-config?clusterId={clusterId}
```

**该接口返回 Dashboard 存储（`rmq_acl_user` / `rmq_acl_rule`）的存储级概要，不是对 Broker 运行时状态的实时查询。** `clusterId` 用于圈定概要范围：账号的集群绑定为空视为全局生效，否则仅当其集群列表包含 `clusterId` 时纳入。因此：

- `aclVersion` 表示存储所管理的账号模型（当前固定为 `ACL 2.0`），不反映 Broker 实际开启的 ACL 版本；
- `aclEnabled` 表示该集群是否存在已配置的账号，不等于 Broker 端鉴权开关；
- `globalWhiteRemoteAddresses` 当前存储未建模，固定返回空数组；
- `accounts` 中每个账号的 `secretKey` 均为脱敏值，明文仅通过 7.10 的显式凭证接口获取。

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `clusterId` | `string` | 是 | 集群 ID，用于圈定账号范围 |

**Response `data`:** `AclClusterConfig`

| 字段 | 类型 | 说明 |
|------|------|------|
| `clusterId` | `string` | 请求的集群 ID（回显） |
| `aclEnabled` | `boolean` | 存储中该集群是否存在已配置账号 |
| `aclVersion` | `string` | 存储管理的账号模型版本 |
| `globalWhiteRemoteAddresses` | `string[]` | 全局 IP 白名单（当前固定为空） |
| `accounts` | `PlainAccessConfig[]` | 账号列表，`secretKey` 为脱敏值 |
| `accountCount` | `number` | 账号数量 |

### 7.9 创建/更新 Plain Access 账号

```
POST /api/acl/plain-access-config
```

以 `accessKey` 为账号标识执行 upsert：账号身份写入 `rmq_acl_user`，资源权限以先删后插方式整体替换写入 `rmq_acl_rule`（每条资源权限生成唯一规则 ID `plain-{accessKey}-t-{index}` / `plain-{accessKey}-g-{index}`），两步在同一事务内完成，中途失败会整体回滚。

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `accessKey` | `string` | 是 | 账号 AccessKey（更新时不可变更） |
| `secretKey` | `string` | 条件 | 新建账号必填；更新时留空表示保留原密钥 |
| `whiteRemoteAddress` | `string` | 否 | IP 白名单，持久化存储，空表示不限制 |
| `admin` | `boolean` | 否 | 是否管理员 |
| `defaultTopicPerm` | `string` | 否 | 默认 Topic 权限 |
| `defaultGroupPerm` | `string` | 否 | 默认 Group 权限 |
| `topicPerms` | `string[]` | 否 | 逐 Topic 权限，格式 `resource=action` |
| `groupPerms` | `string[]` | 否 | 逐 Group 权限，格式 `resource=action` |

**Response `data`:** `PlainAccessConfig`。`secretKey` 仅在本次显式提交时回显，保留原密钥时返回 `null`。

### 7.10 查看单个用户明文凭证

```
GET /api/acl/users/:id/credentials
```

返回单个用户的明文 AccessKey / SecretKey（存储为 base64，读取时解码）。明文凭证只通过该显式端点提供；用户列表与集群 ACL 概要均只返回脱敏值。

**Response `data`:** `AclUser`（`accessKey` / `secretKey` 为明文）

---

## 8. 消息查询 Message

### 8.1 查询消息列表

```
GET /api/messages
```

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `instanceId` | `string` | 是 | 实例 ID（全局唯一字符串） |
| `topic` | `string` | 条件必填 | Topic 名称；按 `msgId` 或 `key` 查询时也必须提供 |
| `msgId` | `string` | 否 | 消息 ID（精确查询） |
| `tag` | `string` | 否 | 消息 Tag |
| `key` | `string` | 否 | 业务 Key |
| `startTime` | `number` | 否 | 开始时间（Unix 毫秒时间戳） |
| `endTime` | `number` | 否 | 结束时间（Unix 毫秒时间戳） |

`topic` 与 `msgId` 至少提供一个，否则返回 400（`topic or msgId is required`）；指定 `msgId` 时 `topic` 也必填，否则返回 400（`topic is required when msgId is specified`）。未指定 `msgId` 或 `key` 的 Topic 扫描默认查询最近 1 小时，时间范围最大 7 天。

**Response `data`:** `MessageRecord[]`

#### MessageRecord

| 字段 | 类型 | 说明 |
|------|------|------|
| `msgId` | `string` | 消息 ID |
| `topic` | `string` | Topic 名称 |
| `tag` | `string` | 消息 Tag |
| `key` | `string` | 消息 Key |
| `brokerName` | `string` | 所属 Broker 名称 |
| `queueId` | `number` | 队列 ID |
| `queueOffset` | `number` | 队列位点 |
| `body` | `string` | UTF-8 文本或 Base64 编码后的消息体 |
| `bodyEncoding` | `string` | 消息体编码，如 `UTF-8` / `BASE64` |
| `bodyTruncated` | `boolean` | 消息体是否被截断 |
| `storeTime` | `number` | Broker 存储时间（Unix 毫秒时间戳） |
| `bornHost` | `string` | 发送方地址 |
| `storeHost` | `string` | 存储 Broker 地址 |
| `properties` | `Record<string, string>` | 消息属性键值对 |
| `propertiesTruncated` | `boolean` | 消息属性是否被截断 |
| `size` | `number` | 消息大小（字节） |

### 8.2 分页查询消息

```
GET /api/messages/page
```

除 8.1 的查询参数外，还支持：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | `number` | 否 | 页码，默认 `1` |
| `pageSize` | `number` | 否 | 每页条数，默认 `50`，范围 `1..200` |

**Response `data`:** `MessageQueryPage`

| 字段 | 类型 | 说明 |
|------|------|------|
| `items` | `MessageRecord[]` | 当前页消息 |
| `total` | `number` | 当前 Broker 查询结果集大小；可能受 Provider 上限约束 |
| `page` | `number` | 当前页码 |
| `size` | `number` | 当前页大小 |
| `resultMayBeTruncated` | `boolean` | `true` 表示 Provider 结果可能已达到扫描上限，不能视为完整总量 |

### 8.3 按消息 ID 获取轨迹

```
GET /api/messages/:msgId/trace
```

**Path Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `msgId` | `string` | 是 | 消息 ID |

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `instanceId` | `string` | 是 | 实例 ID |
| `topic` | `string` | 否 | 原始 Topic；Tencent Provider 要求非空 |
| `traceTopic` | `string` | 否 | 自定义轨迹 Topic；空值使用 `RMQ_SYS_TRACE_TOPIC` |

**Response `data`:** `TraceRecord`

```json
{
  "nodes": [
    {
      "title": "produce",
      "timestamp": 1782901425100,
      "status": "finish",
      "costTime": 3,
      "description": "producer=order-service, storeHost=10.0.2.3:10911"
    }
  ],
  "consumerStatus": [
    {
      "group": "cg-order-processor",
      "deliveryStatus": "success",
      "consumeTime": 1782901425230,
      "retryCount": 0
    }
  ]
}
```

#### TraceNode

| 字段 | 类型 | 说明 |
|------|------|------|
| `title` | `string` | 节点类型，取值随厂商而异。Apache：`produce` / `consume` / `endTransaction` / `recall`；腾讯云：`produce` / `persist` / `consume`；阿里云：展示文案而非枚举，形如 `Producer` / `Broker <operateType>` / `Consumer <group>` |
| `timestamp` | `number` | 事件时间（Unix 毫秒时间戳）。字段为原始 `long`，厂商未赋值时为 `0`（如阿里云 Consumer 节点在无消费记录的分支只返回 `title` 与 `status`） |
| `status` | `string` | 状态。生产节点为 `finish` / `failed`；消费节点另含 `process` / `error`（腾讯云按 `toConsumeTraceStatus` 映射为 `finish` / `process` / `error`）；阿里云 Consumer 节点原样透传云侧 `consumeStatus`，Broker 节点不返回状态故为 `null` |
| `costTime` | `number` | 耗时（毫秒） |
| `description` | `string` | 详细描述 |

#### ConsumerStatus

| 字段 | 类型 | 说明 |
|------|------|------|
| `group` | `string` | 消费组名称 |
| `deliveryStatus` | `string` | 投递状态: `success` / `failed` / `pending` |
| `consumeTime` | `number` | 消费时间（Unix 毫秒时间戳；无时间时为 `0`） |
| `retryCount` | `number` | 重试次数 |

### 8.4 按业务 Key 获取轨迹

```
GET /api/messages/trace-by-key
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `instanceId` | `string` | 是 | 实例 ID |
| `key` | `string` | 是 | 业务 Key |
| `topic` | `string` | 否 | 原始 Topic |
| `traceTopic` | `string` | 否 | 自定义轨迹 Topic；空值使用默认轨迹 Topic |

**Response `data`:** `TraceRecord`，字段见 8.3。

### 8.5 获取 Topic 队列位点范围

```
GET /api/messages/queues
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `instanceId` | `string` | 是 | 实例 ID |
| `topic` | `string` | 是 | Topic 名称 |

**Response `data`:** `QueueOffset[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| `brokerName` | `string` | Broker 名称 |
| `queueId` | `number` | 队列 ID |
| `minOffset` | `number` | 当前最小可读位点 |
| `maxOffset` | `number` | 当前最大位点 |

### 8.6 按队列位点读取消息

```
GET /api/messages/queue-message
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `instanceId` | `string` | 是 | 实例 ID |
| `topic` | `string` | 是 | Topic 名称 |
| `brokerName` | `string` | 是 | Broker 名称 |
| `queueId` | `number` | 是 | 队列 ID，必须非负 |
| `offset` | `number` | 是 | 精确队列位点，必须非负 |

**Response `data`:** `MessageRecord | null`

### 8.7 指定客户端直接消费消息

```
POST /api/messages/direct-consume
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `instanceId` | `string` | 是 | 实例 ID |
| `topic` | `string` | 是 | Topic 名称 |
| `msgId` | `string` | 是 | 消息 ID |
| `consumerGroup` | `string` | 是 | 消费组名称 |
| `clientId` | `string` | 是 | 在线消费客户端 ID |

**Response `data`:** `DirectConsumeMessageResult`

| 字段 | 类型 | 说明 |
|------|------|------|
| `consumeResult` | `string` | Broker 返回的直接消费结果 |
| `remark` | `string` | Broker 返回的说明 |
| `spentTimeMillis` | `number` | Broker 执行耗时（毫秒） |
| `order` | `boolean` | 是否为顺序消费 |
| `autoCommit` | `boolean` | 客户端是否启用自动提交 |

---

## 9. 死信队列 Dead Letter Queue

### 9.1 获取 DLQ 列表

```
GET /api/dlq?instanceId={instanceId}&search={keyword}&page={page}&pageSize={pageSize}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `instanceId` | `string` | 是 | 实例 ID（全局唯一字符串） |
| `search` | `string` | 否 | 按 Group 名称或 DLQ Topic 搜索，大小写不敏感 |
| `page` | `number` | 否 | 页码，默认 `1` |
| `pageSize` | `number` | 否 | 每页条数，默认 `20`，最大 `100` |

**Response `data`:** `PageResult<DLQGroup>`

| 字段 | 类型 | 说明 |
|------|------|------|
| `items` | `DLQGroup[]` | 当前页数据 |
| `total` | `number` | 匹配总数 |
| `page` | `number` | 当前页码 |
| `size` | `number` | 当前页大小 |

| 字段 | 类型 | 说明 |
|------|------|------|
| `groupName` | `string` | 消费组名称 |
| `dlqTopic` | `string` | 死信 Topic 名称（格式: `%DLQ%{groupName}`） |
| `messageCount` | `number` | 死信消息数量 |
| `lastEnqueueTime` | `string` | 最后入队时间 (ISO 8601) |
| `retryCount` | `number` | 已重试次数 |
| `status` | `string` | 状态: `ACTIVE` / `EMPTY` / `UNAVAILABLE` |
| `statsAvailable` | `boolean` | 是否成功读取 DLQ Topic 统计信息 |

### 9.2 重发死信消息

```
POST /api/dlq/resend
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `instanceId` | `string` | 是 | 实例 ID（全局唯一字符串） |
| `groupName` | `string` | 是 | 消费组名称 |
| `startTime` | `number` | 否 | 重投时间范围起始（Unix 毫秒时间戳） |
| `endTime` | `number` | 否 | 重投时间范围结束（Unix 毫秒时间戳） |
| `targetTopic` | `string` | 否 | 目标 Topic，不传则重投回原 Topic |

**Response `data`:** `DLQResendResult`

| 字段 | 类型 | 说明 |
|------|------|------|
| `matched` | `number` | 命中的死信消息数 |
| `resent` | `number` | 成功重投条数 |
| `failed` | `number` | 重投失败条数 |
| `outcome` | `string` | 结果: `SUCCESS` / `PARTIAL` / `FAILED` / `NO_MESSAGES` |
| `scanIncomplete` | `boolean` | 是否有部分队列扫描失败 |
| `failedQueueCount` | `number` | 扫描失败的队列数 |

---

## 10. 客户端连接 Clients

### 10.1 获取客户端连接列表

```
GET /api/clients?clusterId={clusterId}&type={type}
```

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `clusterId` | `string` | 否 | 按集群过滤 |
| `type` | `string` | 否 | 按类型过滤: `Producer` / `Consumer` |

**Response `data`:** `ClientConnection[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| `clientId` | `string` | 客户端 ID |
| `type` | `string` | 类型: `Producer` / `Consumer` |
| `groupOrTopic` | `string` | 消费组名或 Topic 名 |
| `protocol` | `string` | 协议: `gRPC` / `Remoting` |
| `address` | `string` | 客户端地址 |
| `language` | `string` | 客户端语言: `Java` / `Go` / `Python` / `Rust` / `C++` / `C#` / `Node.js` / `PHP` |
| `version` | `string` | SDK 版本号 |
| `connectedAt` | `string` | 连接时间 |
| `clusterName` | `string` | 所属集群名称（显示在第一列） |

### 10.2 获取 Producer Group 候选列表

```
GET /api/producer/groups
```

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `instanceId` | `string` | 是 | 实例 ID |
| `topic` | `string` | 否 | 按 Topic 过滤 Producer Group |
| `query` | `string` | 否 | 按 Group 名称搜索 |
| `limit` | `number` | 否 | 返回上限，默认 `20`；小于 `1` 时按 `1`，大于 `100` 时按 `100` |

**Response `data`:** `string[]`

### 10.3 获取 Producer 连接详情

```
GET /api/producer/connection
```

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `instanceId` | `string` | 是 | 实例 ID |
| `topic` | `string` | 是 | Topic 名称 |
| `producerGroup` | `string` | 否 | Producer Group；省略时返回该 Topic 的全部 Producer 连接 |

该兼容接口不使用通用 `Result.data` 包装，直接返回 `ProducerConnectionResult`：

| 字段 | 类型 | 说明 |
|------|------|------|
| `connectionSet` | `ProducerConnection[]` | 匹配的 Producer 连接 |
| `summary` | `ProducerConnectionSummary` | 连接完整性和分布摘要 |
| `complete` | `boolean` | 是否覆盖全部可发现的 Broker 与 Producer Group |
| `failedBrokers` | `string[]` | Producer Group 发现失败的 Broker 地址；完整扫描时为空 |
| `failedProducerGroups` | `string[]` | 连接查询失败的 Producer Group；完整扫描时为空 |

省略 `producerGroup` 的 Topic 聚合查询采用部分成功语义：只要至少一个 Broker 可查询，且并非
所有已发现的 Producer Group 都查询失败，就返回可用连接；覆盖缺口通过 `complete=false` 和
失败列表公开。所有 Broker 或所有已发现 Group 都无法查询时仍返回 `502`。显式
`producerGroup` 查询保持原有语义。

#### ProducerConnection

| 字段 | 类型 | 说明 |
|------|------|------|
| `clientId` | `string` | 客户端 ID |
| `clientAddr` | `string` | 客户端地址 |
| `topic` | `string` | Topic 名称 |
| `producerGroup` | `string` | Producer Group |
| `language` | `string` | 客户端语言 |
| `versionDesc` | `string` | 客户端版本 |

#### ProducerConnectionSummary

| 字段 | 类型 | 说明 |
|------|------|------|
| `totalConnections` | `number` | 连接总数 |
| `uniqueClientCount` | `number` | 唯一客户端数 |
| `uniqueAddressCount` | `number` | 唯一地址数 |
| `uniqueLanguageCount` | `number` | 客户端语言种类数 |
| `uniqueVersionCount` | `number` | 客户端版本种类数 |
| `languages` | `{ value: string, count: number }[]` | 按客户端语言统计的分布 |
| `versions` | `{ value: string, count: number }[]` | 按客户端版本统计的分布 |
| `duplicateClientIds` | `string[]` | 重复出现的客户端 ID |
| `warnings` | `string[]` | `NO_CONNECTIONS` / `DUPLICATE_CLIENT_ID` / `MIXED_CLIENT_VERSION` / `INCOMPLETE_CLIENT_METADATA` / `INCOMPLETE_SCAN` |
| `readiness` | `string` | `READY` / `WARNING` / `UNAVAILABLE` |

---

## 11. 告警规则 Alert Rules

### 11.1 获取告警规则列表

```
GET /api/alert-rules
```

**Response `data`:** `AlertRule[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `string` | 规则 ID |
| `name` | `string` | 规则名称 |
| `metric` | `string` | 监控指标: `磁盘使用率` / `消费堆积量` / `TPS 异常` / `Broker 离线` / `Proxy 连接数` |
| `operator` | `string` | 比较运算符: `>` / `<` / `>=` / `<=` |
| `threshold` | `number` | 阈值 |
| `thresholdUnit` | `string` | 单位: `%` / `条` / `TPS` / `个` |
| `duration` | `string` | 持续时间: `1分钟` / `5分钟` / `15分钟` / `30分钟` |
| `channels` | `string[]` | 通知渠道: `dingtalk` / `email` / `sms` |
| `enabled` | `boolean` | 是否启用 |
| `lastTriggered` | `string \| null` | 最后触发时间，null 表示未触发 |
| `description` | `string` | 规则描述 |

### 11.2 创建告警规则

```
POST /api/alert-rules/create
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | `string` | 是 | 规则名称 |
| `metric` | `string` | 是 | 监控指标 |
| `operator` | `string` | 是 | 运算符 |
| `threshold` | `number` | 是 | 阈值 |
| `duration` | `string` | 是 | 持续时间 |
| `channels` | `string[]` | 是 | 通知渠道 |
| `description` | `string` | 否 | 描述 |

**Response `data`:** `AlertRule`

### 11.3 更新告警规则

```
POST /api/alert-rules/update
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `string` | 是 | 规则 ID |
| `name` | `string` | 是 | 规则名称 |
| `metric` | `string` | 是 | 监控指标 |
| `operator` | `string` | 是 | 运算符 |
| `threshold` | `number` | 是 | 阈值 |
| `duration` | `string` | 是 | 持续时间 |
| `channels` | `string[]` | 是 | 通知渠道 |
| `description` | `string` | 否 | 描述 |

**Response `data`:** `AlertRule`

### 11.4 切换告警规则启用状态

```
POST /api/alert-rules/toggle
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `string` | 是 | 规则 ID |
| `enabled` | `boolean` | 是 | 启用/禁用 |

**Response `data`:** `AlertRule`

### 11.5 删除告警规则

```
POST /api/alert-rules/delete
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `string` | 是 | 规则 ID |

**Response `data`:** `null`

---

## 12. 系统告警 System Alerts

### 12.1 获取系统告警列表

```
GET /api/system-alerts?level={level}
```

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `level` | `string` | 否 | 按级别过滤: `error` / `warning` / `info` |

**Response `data`:** `SystemAlert[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `string` | 告警 ID |
| `level` | `string` | 级别: `error`（严重） / `warning`（警告） / `info`（信息） |
| `title` | `string` | 告警标题 |
| `description` | `string` | 告警详情 |
| `time` | `string` | 时间（短格式: `HH:mm`） |
| `acknowledged` | `boolean` | 是否已确认 |

### 12.2 确认告警

```
POST /api/system-alerts/acknowledge
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `string` | 是 | 告警 ID |

**Response `data`:** `SystemAlert`

### 12.3 清除已确认告警

```
POST /api/system-alerts/clear-acknowledged
```

**Response `data`:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `cleared` | `number` | 清除数量 |

---

## 13. 审计日志 Audit

### 13.1 获取审计日志列表

```
GET /api/audit-logs?page={page}&pageSize={pageSize}&search={search}&operationType={type}&resourceType={resourceType}&target={target}&clusterId={clusterId}&clusterIdMissing={missing}&startDate={start}&endDate={end}&result={result}
```

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | `number` | 否 | 页码，默认 1 |
| `pageSize` | `number` | 否 | 每页条数，默认 20 |
| `search` | `string` | 否 | 模糊搜索（匹配 operator / target / detail） |
| `operationType` | `string` | 否 | 操作类型过滤 |
| `resourceType` | `string` | 否 | 资源类型过滤 |
| `target` | `string` | 否 | 操作对象精确过滤，使用等值匹配而非模糊搜索 |
| `clusterId` | `string` | 否 | 集群 ID 过滤 |
| `clusterIdMissing` | `boolean` | 否 | 默认 `false`；为 `true` 时只返回集群 ID 为 null 或空字符串的记录，并忽略 `clusterId` |
| `startDate` | `string` | 否 | 开始日期 (YYYY-MM-DD) |
| `endDate` | `string` | 否 | 结束日期 (YYYY-MM-DD) |
| `result` | `string` | 否 | 结果过滤，传入筛选项接口返回的原始值 |

`startDate` 或 `endDate` 格式错误，以及 `startDate` 晚于 `endDate` 时，接口返回 HTTP 400。

资源操作时间线使用 `resourceType + target + clusterId` 作为资源身份。对于没有集群范围的记录，
省略 `clusterId` 并传入 `clusterIdMissing=true`。

**Response `data`:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `items` | `AuditRecord[]` | 记录列表 |
| `total` | `number` | 总条数 |
| `page` | `number` | 当前页码 |
| `size` | `number` | 每页条数 |

#### AuditRecord

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `string` | 记录 ID |
| `timestamp` | `string` | 操作时间（`YYYY-MM-DD HH:mm:ss`） |
| `operator` | `string` | 操作人（如 `admin`, `ops-zhang`, `system`） |
| `operationType` | `string` | 持久化的操作类型代码，如 `CREATE_TOPIC` / `RESET_OFFSET` |
| `resourceType` | `string` | 资源类型代码，如 `TOPIC` / `GROUP` / `CLUSTER` |
| `target` | `string \| null` | 操作对象，无操作对象时为 `null` |
| `clusterId` | `string \| null` | 所属集群 ID，无集群上下文时为 `null` |
| `detail` | `string` | 详细描述 |
| `result` | `string` | 持久化的结果代码，如 `SUCCESS` / `FAILED` / `PARTIAL` |
| `errorMessage` | `string` | 失败或部分成功时的错误信息 |

### 13.2 获取审计日志筛选项

```
GET /api/audit-logs/filter-options
```

**Response `data`:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `operationTypes` | `string[]` | 数据库中已存在的操作类型原始值 |
| `resourceTypes` | `string[]` | 数据库中已存在的资源类型原始值 |
| `clusterIds` | `string[]` | 数据库中已存在的非空集群 ID |
| `results` | `string[]` | 数据库中已存在的结果原始值 |

筛选项保留数据库中的原始值，请将选中值原样传入列表或导出接口。

### 13.3 导出审计日志

```
GET /api/audit-logs/export?search={search}&operationType={type}&resourceType={resourceType}&target={target}&clusterId={clusterId}&clusterIdMissing={missing}&startDate={start}&endDate={end}&result={result}
```

查询参数与列表接口相同，但不包含 `page` 和 `pageSize`。接口返回全部匹配记录，不受当前表格分页影响。

**Response `data`:** 带 UTF-8 BOM 的 CSV 字符串。CSV 字段使用双引号包裹，并转义逗号、双引号和换行符。以 `=`、`+`、`-`、`@`、制表符或换行符开头的字段会添加单引号前缀，避免电子表格将其作为公式执行。

`startDate` 或 `endDate` 格式错误，以及 `startDate` 晚于 `endDate` 时，接口返回 HTTP 400。

### 13.4 清理审计日志

```
POST /api/audit-logs/cleanup
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `beforeDays` | `number` | 是 | 清理多少天之前的日志（1-365） |

**Response `data`:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `deleted` | `number` | 清理数量 |

---

## 14. 系统设置 Settings

### 14.1 获取通用设置

```
GET /api/settings/general
```

**Response `data`:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `theme` | `string` | 主题: `light` / `dark` / `system` |
| `compact` | `boolean` | 紧凑模式 |
| `desktopNotify` | `boolean` | 桌面通知 |
| `notifySound` | `boolean` | 通知声音 |
| `sessionTimeout` | `number` | 会话超时（分钟，5-1440） |
| `requireLogin` | `boolean` | 是否需要登录 |
| `llmProvider` | `string` | LLM 提供商: `openai` / `azure` / `anthropic` / `deepseek` / `tongyi` / `ollama` / `bedrock` |
| `apiKeyConfigured` | `boolean` | 是否已配置 API Key；响应不会返回密钥内容 |
| `model` | `string` | 模型名称 |
| `baseUrl` | `string` | Base URL |

### 14.2 保存通用设置

```
POST /api/settings/general/save
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `theme` | `string` | 是 | 主题: `light` / `dark` / `system` |
| `compact` | `boolean` | 是 | 紧凑模式 |
| `desktopNotify` | `boolean` | 是 | 桌面通知 |
| `notifySound` | `boolean` | 是 | 通知声音 |
| `sessionTimeout` | `number` | 是 | 会话超时（分钟，5-1440） |
| `requireLogin` | `boolean` | 是 | 是否需要登录 |
| `llmProvider` | `string` | 是 | LLM 提供商 |
| `apiKey` | `string` | 否 | 新 API Key；省略或传空值时保留现有密钥 |
| `clearApiKey` | `boolean` | 否 | 传 `true` 时显式清除现有密钥，优先级高于 `apiKey` |
| `model` | `string` | 是 | 模型名称 |
| `baseUrl` | `string` | 是 | Base URL |

**Response `data`:** `null`

### 14.3 获取数据源选择器列表（未分页）

```
GET /api/settings/datasources
```

用于选择器、下拉框等需要一次性加载全部数据源的场景；不支持 `search`、`type`、`page` 或 `pageSize`。设置页表格应使用 14.4 的分页接口。

**Response `data`:** `DataSource[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| `key` | `string` | 数据源 ID |
| `name` | `string` | 名称 |
| `type` | `string` | 类型: `Prometheus` / `VictoriaMetrics` / `Thanos` / `Mimir` / `Cortex` / `ARMS` |
| `url` | `string` | 连接 URL |
| `auth` | `string` | 认证方式: `None` / `Basic Auth` / `Bearer Token` |
| `status` | `string?` | 连接状态，可能为空 |
| `instanceIds` | `string[]?` | 绑定的实例 ID 列表；为空或省略表示全局可用 |

### 14.4 分页获取数据源列表

```
GET /api/settings/datasources/page?search={keyword}&type={type}&page={page}&pageSize={pageSize}
```

用于设置页表格的搜索、类型筛选和分页加载；与 14.3 的未分页 selector endpoint 区分使用。

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `search` | `string` | 否 | 按数据源名称等库存字段搜索 |
| `type` | `string` | 否 | 按数据源类型过滤，值同 `DataSource.type` |
| `page` | `number` | 否 | 页码，默认 `1`，必须大于等于 `1` |
| `pageSize` | `number` | 否 | 每页条数，默认 `20`，范围 `1`-`100` |

**Response `data`:** `PageResult<DataSource>`

| 字段 | 类型 | 说明 |
|------|------|------|
| `items` | `DataSource[]` | 当前页数据，结构同 14.3 |
| `total` | `number` | 匹配筛选条件的数据源总数 |
| `page` | `number` | 当前页码（回显请求中的 `page`） |
| `size` | `number` | 每页条数（回显请求中的 `pageSize`） |

### 14.5 创建数据源

```
POST /api/settings/datasources/create
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | `string` | 是 | 名称 |
| `type` | `string` | 是 | 类型 |
| `url` | `string` | 是 | URL |
| `auth` | `string` | 否 | 认证方式，默认 `None` |

**Response `data`:** `DataSource`

### 14.6 更新数据源

```
POST /api/settings/datasources/update
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `key` | `string` | 是 | 数据源 ID |
| `name` | `string` | 是 | 名称 |
| `type` | `string` | 是 | 类型 |
| `url` | `string` | 是 | URL |
| `auth` | `string` | 否 | 认证方式，默认 `None` |

**Response `data`:** `DataSource`

### 14.7 删除数据源

```
POST /api/settings/datasources/delete
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `key` | `string` | 是 | 数据源 ID |

**Response `data`:** `null`

### 14.8 测试数据源连接

```
POST /api/settings/datasources/test
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `url` | `string` | 是 | 连接 URL |
| `type` | `string` | 是 | 类型 |
| `auth` | `string` | 否 | 认证方式 |

**Response `data`:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | `boolean` | 是否连接成功 |
| `message` | `string?` | 错误信息 |

---

## 15. AI 交互

> **Breaking change**：本节 15.1 ~ 15.11 是新增接口，同时**删除**了旧的两个端点
> `POST /api/ai/chat`（单轮 SSE 对话）与 `POST /api/ai/execute`（执行 AI 指令）。
> **不提供兼容层，不做数据迁移。** 直接调用这两个端点的外部脚本会失败，需改用 15.7（发消息）
> 与 15.9（停止）。随端点一起废弃的还有旧 SSE 线格式：`event: message`、`event: enhance`
> 与 `data: [DONE]` 哨兵都不再产生，新客户端遇到它们会显式报错而不是静默结束（见 15.7）。
>
> 会话历史此前只存在于浏览器 `sessionStorage`（关标签页即丢失），现在持久化到
> `rmq_ai_conversation` / `rmq_ai_run` / `rmq_ai_event` 三张表，由启动时的迁移自动建表。
> 旧历史记录不迁移——它本来就按标签页存，没有可迁移的东西。

### 概念模型

三层，与接口层级一一对应：

| 概念 | 说明 | 存储表 |
|------|------|--------|
| **会话 Conversation** | 一次持续的对话容器：标题、归属人、绑定的 RocketMQ 实例、引擎/模型 | `rmq_ai_conversation` |
| **运行 Run** | 会话内的一次执行（一轮问答），有独立状态机、token 统计与停止原因 | `rmq_ai_run` |
| **事件 Event** | Run 产生的一条时间线记录：用户消息、模型推理、正文、工具调用/结果、通知、错误、终态 | `rmq_ai_event` |

**归属与鉴权**：所有接口按调用者身份做 owner 过滤，访问他人的会话或 Run 一律返回 `404`
（而不是 `403`，避免用状态码枚举 id）。写操作（创建会话、发消息、停止、改名、归档、删除）
需要 admin；读操作（列表、详情、时间线、`GET /api/ai/runs/:runId/stream`）任何已登录用户可用。
理由是托管 Agent 的工具调用用**服务端解析出的实例凭据**签名，不是调用者身份，所以能发起 Run
就等于能执行 L2 变更。

**Run 状态机**：`QUEUED` → `RUNNING` → `COMPLETED` / `STOPPED` / `FAILED`，后三者为终态。
同一会话最多只有一个非终态 Run，第二个并发请求返回 `409`。

**生成与 HTTP 连接解耦**：Run 不属于发起它的那个请求。关掉流（或关掉标签页）不会停止生成，
`GET /api/ai/runs/:runId/stream`（15.8）可以重新接上。

### 共用响应结构

**`AiConversationVO`**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `number` | 会话 ID |
| `title` | `string` | 标题，由首条用户消息前 40 字规则生成，可用 15.4 改名 |
| `owner` | `string` | 归属用户名 |
| `engine` | `string` | 引擎：`http` / `claude-code` / `qoder` |
| `model` | `string` | 模型名 |
| `mode` | `string` | 模式：`chat` / `diagnose` / `manage` / `query` |
| `instanceId` | `string?` | 工具绑定的 RocketMQ 实例；未绑定时为 `null` |
| `runtimeSessionId` | `string?` | 上游 Agent CLI 自己的会话 ID（供 `--resume`）；首轮结束前为 `null` |
| `lastSeq` | `number` | 事件序号高水位缓存，权威值是 `MAX(rmq_ai_event.seq)`；不会为 `null`（默认 `0`） |
| `archived` | `boolean` | 是否已归档 |
| `createdAt` | `string` | 创建时间（ISO-8601，无时区偏移的 UTC） |
| `updatedAt` | `string` | 最后更新时间，格式同上 |

**`AiRunVO`**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `number` | Run ID |
| `conversationId` | `number` | 所属会话 ID |
| `turn` | `number` | 会话内的轮次，从 1 开始，`(conversationId, turn)` 唯一 |
| `status` | `string` | `QUEUED` / `RUNNING` / `COMPLETED` / `STOPPED` / `FAILED` |
| `engine` | `string` | **准入时固化的快照**，之后改设置不会改写历史 |
| `model` | `string` | 同上 |
| `startedAt` | `string?` | 开始时间 |
| `finishedAt` | `string?` | 结束时间 |
| `durationMs` | `number?` | 耗时（毫秒） |
| `inputTokens` | `number?` | 输入 token 数 |
| `outputTokens` | `number?` | 输出 token 数 |
| `stopReason` | `string?` | 非成功终态的原因：`USER_STOP` / `SHUTDOWN` / `TIMEOUT` / `OUTPUT_LIMIT` / `PROVIDER_ERROR` / `SERVER_RESTART` / `OVERLOADED` / `ORPHANED` |
| `errorCode` | `string?` | 失败时的错误码 |
| `errorMessage` | `string?` | 失败时的错误信息 |

**`AiActiveRunRef`**：`{ "id": number, "status": "QUEUED" | "RUNNING" }`，仅暴露非终态 Run。

### 15.1 创建 AI 会话

```
POST /api/ai/conversations
```

**Request Body:**（两个字段均可选，请求体本身也可省略）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `instanceId` | `string` | 否 | Agent 工具绑定的 RocketMQ 实例（即 `rmqctl --instance-id`），最长 128 字符 |
| `mode` | `string` | 否 | 模式，最长 16 字符；默认 `chat`，未知值报 `400` |

**Response `data`:** `AiConversationVO`

### 15.2 获取 AI 会话列表

```
GET /api/ai/conversations?page={page}&size={size}&search={keyword}&archived={archived}
```

只返回调用者自己的会话，按 `gmt_modified desc, id desc` 排序——今天续聊的会话浮到顶部。

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | `number` | 否 | 页码，默认 `1`；小于 1 会被服务端夹到 1，不报 `400` |
| `size` | `number` | 否 | 每页条数，默认 `20`，范围 `1`-`100`，越界同样夹取 |
| `search` | `string` | 否 | 标题子串匹配；LIKE 通配符 `\`、`%`、`_` 会被转义，全空白视为不过滤 |
| `archived` | `boolean` | 否 | `true` 只看已归档，`false` 只看未归档，省略表示两者都要 |

**Response `data`:** `PageResult<AiConversationListItemVO>`

| 字段 | 类型 | 说明 |
|------|------|------|
| `items` | `object[]` | 当前页数据 |
| `items[].id` | `number` | 会话 ID |
| `items[].title` | `string` | 标题 |
| `items[].engine` | `string` | 引擎 |
| `items[].model` | `string` | 模型 |
| `items[].mode` | `string` | 模式 |
| `items[].instanceId` | `string?` | 绑定实例 |
| `items[].lastRunId` | `number?` | 最新一次 Run 的 ID；从未使用过的会话为 `null` |
| `items[].lastRunStatus` | `string?` | 最新一次 Run 的状态，供列表直接显示 |
| `items[].updatedAt` | `string` | 最后更新时间 |
| `items[].createdAt` | `string` | 创建时间 |
| `total` | `number` | 匹配条件的总数 |
| `page` | `number` | 当前页码 |
| `size` | `number` | 每页条数 |

### 15.3 获取 AI 会话详情

```
GET /api/ai/conversations/:id
```

比列表多带一个「仍在生成的 Run」，好让刷新后的页面重新接上流，而不是显示一份看着像已结束
的转录。

**Path Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `number` | 是 | 会话 ID |

**Response `data`:** `AiConversationVO` 的全部字段，另加：

| 字段 | 类型 | 说明 |
|------|------|------|
| `activeRun` | `AiActiveRunRef?` | 处于 `QUEUED`/`RUNNING` 的 Run；会话空闲时为 `null` |

**错误：** `404` —— id 不存在，**或**属于别人（两种情况返回同一个状态码）。

### 15.4 更新 AI 会话（重命名 / 归档）

```
PATCH /api/ai/conversations/:id
```

**Request Body:**（省略的字段保持原值）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `title` | `string` | 否 | 新标题，最长 512 字符 |
| `archived` | `boolean` | 否 | 是否归档 |

一次什么都没改的更新**不会**推高 `gmt_modified`，否则把会话改成它已有的标题就会重排列表。

> **关于 `PATCH` 的刻意偏离**：这是全仓库第一个也是唯一一个 `PATCH`（`DELETE` **不算**偏离，仓库
> 已有 `DELETE /api/proxies/addresses`、`DELETE /api/alert-silences/:id` 两处先例），刻意偏离本文档
> 开头「GET + POST 风格」的约定。理由是局部更新语义：请求体里**省略**的字段保持原值，「只改标题」
> 与「只归档」是同一个端点的两种调用；换成 POST + 动词路径就得拆成 `/rename` 与 `/archive` 两个
> 端点，或者用 body 里的 `null` 表达「不改」，两者都更别扭。
>
> 若维护者希望全仓风格统一，改回 `POST /api/ai/conversations/update`（`id` 放 body）即可，
> **权限行为完全不变**：`AuthInterceptor.requiresAdmin` 对非 `GET`/`HEAD`/`OPTIONS` 的请求，只有
> 落在 `READER_POST_PATHS` 白名单里的 `POST` 才放行给 reader，而 `PATCH` 因为不是 `POST` 一律要求
> admin——两种写法都是 admin-only，前提是新路径不进那个白名单。

**Response `data`:** `AiConversationVO`

**错误：** `404` —— id 不存在或属于别人。

### 15.5 删除 AI 会话

```
DELETE /api/ai/conversations/:id
```

硬删除。服务端先停掉进行中的 Run，再按 events → runs → conversation 的顺序删除（本项目不声明
外键，级联由代码保证），最后清掉该会话的 Agent workspace（里面含 `claude` 的 resume 状态）。

**Response `data`:** `null`

**错误：** `404` —— id 不存在或属于别人。

### 15.6 获取会话时间线

```
GET /api/ai/conversations/:id/events?after={seq}&limit={limit}
```

按 `seq` 游标分页：`after` 是**排他**下界，响应里的 `nextAfter` 是下一页的游标，到尾时为 `null`。

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `after` | `number` | 否 | 只返回 `seq > after` 的事件，默认 `0`（即从头开始） |
| `limit` | `number` | 否 | 每页条数，默认 `200`，服务端上限 `500` |

**Response `data`:** `AiTimelineVO`

| 字段 | 类型 | 说明 |
|------|------|------|
| `items` | `object[]` | 时间线条目 |
| `items[].id` | `number` | 事件行 ID |
| `items[].turn` | `number` | 所属轮次 |
| `items[].seq` | `number` | 会话内单调递增序号，重连游标用的就是它 |
| `items[].createdAt` | `string` | 落库时间 |
| `items[].runId` | `number` | 产生该事件的 Run，**永不为 null** |
| `items[].event` | `object` | **已反序列化**的事件体，不是原始的 `type` + `payload` 两列 |
| `nextAfter` | `number?` | 下一页游标（本页最后一行的 `seq`），到尾时为 `null` |
| `activeRun` | `AiActiveRunRef?` | 仍在生成的 Run，空闲时为 `null` |

**`items[].event` 的类型判别**：`type` 字段取值如下，各类型的字段与 15.7 的同名 live 事件一致
（差异见「相对 live 事件的差异」一列）：

| `type` | 说明 | 相对 live 事件的差异 |
|--------|------|---------------------|
| `user` | 用户消息，`{ text, enhancedPrompt? }` | live 侧没有对应帧（用户消息不回流） |
| `thinking` | 模型推理或 prompt 增强改写，`{ text, source }` | 正文字段叫 `text`，live 侧叫 `content` |
| `text` | 助手正文，`{ text }` | live 侧是增量 `text_delta.content`，落库时相邻片段已合并 |
| `tool_use` | 工具调用，`{ tcId, tool, input }` | live 侧叫 `tool_start` |
| `tool_result` | 工具结果，`{ tcId, tool, output, outputBytes, truncated, success, durationMs?, error? }` | live 侧叫 `tool_done` |
| `notice` | 运行时通知，`{ level, message }` | 同形 |
| `error` | 错误，`{ code, message, hint? }` | 同形 |
| `run_status` | 终态，`{ status, reason? }` | live 侧是 `run_finished`；终态**要**落库，好让重载的会话不 join Run 表也能显示「已停止」 |

`run_started` / `run_finished` 不落库（Run 行才是真相）。无法解码的历史行会被替换成一条
`warn` 级 `notice` 占位，而不是让整条时间线失败。

**示例：**

```json
{
  "items": [
    {
      "id": 9001,
      "turn": 3,
      "seq": 41,
      "createdAt": "2026-09-18T04:12:33",
      "runId": 41,
      "event": { "type": "user", "text": "查看集群状态" }
    },
    {
      "id": 9002,
      "turn": 3,
      "seq": 42,
      "createdAt": "2026-09-18T04:12:34",
      "runId": 41,
      "event": {
        "type": "tool_result",
        "tcId": "toolu_01A",
        "tool": "rmq.topic.list",
        "output": "{\"items\":[{\"name\":\"StudioTest\"}]}",
        "outputBytes": 33,
        "truncated": false,
        "success": true,
        "durationMs": 212
      }
    },
    {
      "id": 9003,
      "turn": 3,
      "seq": 43,
      "createdAt": "2026-09-18T04:12:41",
      "runId": 41,
      "event": { "type": "run_status", "status": "COMPLETED" }
    }
  ],
  "nextAfter": null,
  "activeRun": null
}
```

**错误：** `404` —— id 不存在或属于别人。

### 15.7 发送消息并流式返回

```
POST /api/ai/conversations/:id/messages
```

发送一轮用户消息，并以 SSE 流式返回它开启的 Run。**这是 `POST /api/ai/chat` 的替代者。**

准入在返回前完成：用户消息已落库、`run_started` 已发布，所以开始读 body 的客户端不可能漏掉
第一帧。

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `message` | `string` | 是 | 用户消息，非空，最长 8192 字符 |
| `model` | `string` | 否 | 覆盖本轮模型，最长 128 字符；Run 行会快照它 |
| `engine` | `string` | 否 | 覆盖本轮引擎（`http` / `claude-code` / `qoder`），最长 16 字符 |
| `mode` | `string` | 否 | 覆盖本轮模式，最长 16 字符 |
| `enhance` | `boolean` | 否 | 是否先做 prompt 增强改写；改写过程以 `source: "enhance"` 的 `thinking` 帧流回，默认 `false` |
| `resume` | `boolean` | 否 | 是否复用上游 Agent 会话（`claude --resume`）。**省略 = 沿用会话自身状态**，`false` = 强制开新会话，`true` = 强制复用 |

**Response:** `text/event-stream`

响应头：`X-Accel-Buffering: no`、`Cache-Control: no-cache, no-transform`、
`Connection: keep-alive`。服务端每 **15 s** 发一个 `:hb` 心跳注释帧；客户端 idle 超时取 **30 s**
（心跳的 2 倍），超时即判定连接被缓冲或已断开。

**SSE 线格式**：所有领域事件统一走 `event: agent`，具体类型由 payload 里的 `type` 字段判别；
另有 `event: error` 与 `event: done` 两个终态控制帧。

| 事件名 | 含义 |
|--------|------|
| `agent` | 一个领域帧，payload 是下表 8 种 `type` 之一 |
| `error` | 终止性错误，payload 为 `{ code, message, hint?, status? }` |
| `done` | 流结束（**替代旧的 `data: [DONE]` 哨兵**） |
| `:hb`（注释帧） | 心跳，无 data，客户端忽略 |

`event: agent` 的 8 种 `type`：

| `type` | 字段 | 说明 |
|--------|------|------|
| `run_started` | `runId`, `conversationId`, `title`, `turn` | Run 已准入，不渲染任何内容 |
| `text_delta` | `content` | 助手正文增量 |
| `thinking` | `content`, `source` | 推理增量；`source` 为 `model`（模型自己的推理）或 `enhance`（Studio 的 prompt 改写）。**两者只在 `source` 相同时才可合并渲染** |
| `tool_start` | `tcId`, `tool`, `input` | 工具调用开始 |
| `tool_done` | `tcId`, `tool`, `output`, `outputBytes`, `truncated`, `success`, `durationMs?`, `error?` | 工具调用结束；`output` 服务端截断到 32 KiB，`outputBytes` 是真实大小，`truncated` 标记是否被截 |
| `notice` | `level`, `message` | 运行时通知，`level` 为 `info` / `warn` / `error`。`error` 级 notice 不等于失败——Run 会继续 |
| `error` | `code`, `message`, `hint?` | 错误 |
| `run_finished` | `runId`, `status`, `durationMs` | Run 到达终态，不渲染任何内容 |

**示例（一轮完整问答）：**

```
event: agent
data: {"type":"run_started","runId":41,"conversationId":7,"title":"查看集群状态","turn":3}

event: agent
data: {"type":"thinking","content":"用户想确认堆积，先查 topic 路由","source":"model"}

event: agent
data: {"type":"tool_start","tcId":"toolu_01A","tool":"rmq.topic.list","input":{"instanceId":"open-source-local"}}

event: agent
data: {"type":"tool_done","tcId":"toolu_01A","tool":"rmq.topic.list","output":"{\"items\":[{\"name\":\"StudioTest\"}]}","outputBytes":33,"truncated":false,"durationMs":212,"success":true}

event: agent
data: {"type":"text_delta","content":"集群当前有 "}

event: agent
data: {"type":"run_finished","runId":41,"status":"COMPLETED","durationMs":8123}

event: done
data:
```

**拒绝语义（重要）**：这个端点的两种结果都是事件流，所以**拒绝不以 HTTP 错误状态码返回**，
而是以 200 + 一个 `event: error` 帧返回，帧里带对应的 `status` 与 `code`。原因是请求声明了
`Accept: text/event-stream`，映射也只产出它，JSON 转换器写不出 `Result` 信封。

| 场景 | `event: error` 的 `status` |
|------|---------------------------|
| 请求体校验失败（如 `message` 为空或超长） | `400` |
| 会话不存在或属于别人 | `404` |
| 该会话已有进行中的 Run | `409` |
| 未配置可用的 LLM 提供方 / 缺少模型 | `400`（`code` 为 `llm.config.incomplete` / `llm.config.model_required`） |

### 15.8 重连进行中的 AI 任务

```
GET /api/ai/runs/:runId/stream?after={seq}
```

接上一个正在生成的 Run：先回放 `seq > after` 的已落库事件（翻译回 live 帧），把本连接注册为
观察者，然后 tail 到 `done`。已经终态的 Run 会回放完立即关闭。

**Path Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `runId` | `number` | 是 | Run ID |

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `after` | `number` | 否 | 只回放 `seq > after` 的事件，默认 `0`（即整个 Run）。刚加载完时间线的客户端传自己持有的最大 `seq` |

**Response:** `text/event-stream`，线格式、响应头、心跳与拒绝语义同 15.7。

**错误：** Run 不存在，或其会话属于别人 —— 以 `event: error` 帧返回（`status: 404`）。

### 15.9 停止 AI 任务

```
POST /api/ai/runs/:runId/stop
```

停止一个 Run。会真正杀掉 Agent CLI 子进程树（先 `descendants()` 后父进程），不是只断开 TCP。

**幂等**：它支撑的是一个用户可能连按两次的按钮，所以对已终态的 Run 返回 `200` 空操作。

停止**不会**关闭流：15.7 / 15.8 的连接会继续开着，用来投递终态 `run_status` 帧与 `done`，
按钮的「停止中」状态靠它退出。

**Response `data`:** `AiRunVO`

**错误：**

| 状态码 | 场景 |
|--------|------|
| `404` | Run 不属于调用者 |
| `409` | 该 Run 已不是所属会话的当前 Run —— **不会杀任何东西**。调用者手里的 `runId` 可能来自一整轮之前的 `run_started`，这里唯一不可挽回的错误就是停掉用户正在看的那个答案 |

### 15.10 获取 Agent 运行时能力

```
GET /api/ai/agent-capabilities
```

请求时探测、短暂缓存。存在这个接口是为了让 UI 能**解释**一次拒绝，而不是抛一个看不懂的错误：
`mcpEnabled=false` 时任何层级的工具都执行不了，`l3ToolsAllowed=false` 时托管 Agent 跑不了破坏性
工具——这两件事都是部署事实，composer 没有别的途径知道。

**Response `data`:** `AiAgentCapabilitiesVO`（5 个字段都是必填 `boolean`，永不为 `null`）

| 字段 | 类型 | 说明 |
|------|------|------|
| `rmqctlAvailable` | `boolean` | `rmqctl` 在 PATH 上且可执行 |
| `claudeAvailable` | `boolean` | `claude` CLI 在 PATH 上且可执行 |
| `qoderAvailable` | `boolean` | `qoder` CLI 在 PATH 上且可执行 |
| `mcpEnabled` | `boolean` | Studio MCP server 已启用，即 Agent 工具整体可用 |
| `l3ToolsAllowed` | `boolean` | 配置允许破坏性（L3）工具 |

**示例：**

```json
{
  "rmqctlAvailable": true,
  "claudeAvailable": true,
  "qoderAvailable": false,
  "mcpEnabled": true,
  "l3ToolsAllowed": false
}
```

### 15.11 获取外部 Agent 的 rmqctl MCP 配置

```
GET /api/ai/conversations/:id/rmqctl-config
```

返回一段可直接粘贴的 `{"mcpServers":{...}}` 配置，用于让 Studio **不托管**的 Agent
（Claude Desktop、Cursor 等）接入这个会话绑定的实例。走的是同一个签名网关、同一套实例绑定与
风险闸门——托管 Agent 只是这条工具通道的第一个客户，这个接口让该说法可被验证。

**永不含密钥，也不可能含**：凭据在用户自己的 `rmqctl` 配置里保持 `env:` 引用形式。这也是片段
里省略 `--config`（服务端会传的那个路径只存在于它自己的容器内）、并把 `server` 单独返回的原因。

**Response `data`:** `AiRmqctlConfigVO`

| 字段 | 类型 | 说明 |
|------|------|------|
| `snippet` | `string` | `{"mcpServers":{...}}` JSON 文档，可直接粘贴进外部 Agent |
| `instanceId` | `string` | 工具绑定的实例（`rmqctl --instance-id`） |
| `server` | `string` | 签名 MCP 请求需要到达的 Studio base URL |

**错误：**

| 状态码 | 场景 |
|--------|------|
| `404` | 会话属于别人 |
| `400` | 会话未绑定实例 —— `rmqctl` 拒绝给 `--instance-id` 兜默认值，所以一个没有它的片段会在第一次工具调用时才失败，不如在配置阶段就报错 |

### 15.12 获取可用工具列表

```
GET /api/ai/tools
```

**Response `data`:** `Tool[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | `string` | 工具名称 |
| `description` | `string` | 工具描述 |
| `parameters` | `object` | 参数 Schema |

### 15.13 执行只读工具

```
POST /api/ai/tools/:name/execute
```

**Path Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | `string` | 是 | 工具名称；NameServer 配置漂移检查使用 `rmq.nameserver.config.diff` |

**`rmq.nameserver.config.diff` Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `cluster` | `string` | 是 | Studio 集群 ID |

该工具逐个读取集群内的 NameServer 配置，只比较预定义的非敏感运行参数白名单。完整配置、路径、密码和凭据不会返回。单节点失败不会丢弃其他节点的检查结果。

**Response `data`:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `cluster` | `string` | 集群 ID |
| `complete` | `boolean` | 是否成功读取全部 NameServer；为 `false` 时漂移结论不完整 |
| `driftDetected` | `boolean` | 可达节点之间是否发现白名单配置差异 |
| `nodeCount` | `number` | NameServer 节点总数 |
| `reachableNodeCount` | `number` | 成功读取配置的节点数 |
| `comparedKeys` | `string[]` | 本次允许比较的配置键白名单 |
| `nodes` | `object[]` | 节点地址及可达状态 |
| `nodes[].address` | `string` | NameServer 地址 |
| `nodes[].reachable` | `boolean` | 是否成功读取该节点配置 |
| `differences` | `object[]` | 可达节点之间存在差异的配置项 |
| `differences[].key` | `string` | 配置键 |
| `differences[].values` | `object[]` | 各可达节点的配置值 |
| `differences[].values[].address` | `string` | NameServer 地址 |
| `differences[].values[].configured` | `boolean` | 节点是否显式包含该配置键 |
| `differences[].values[].value` | `string?` | 白名单配置值；未配置时为 `null` |

**示例：**

```json
{
  "cluster": "cluster-prod",
  "complete": true,
  "driftDetected": true,
  "nodeCount": 2,
  "reachableNodeCount": 2,
  "comparedKeys": ["listenPort", "serverWorkerThreads"],
  "nodes": [
    {"address": "10.0.0.1:9876", "reachable": true},
    {"address": "10.0.0.2:9876", "reachable": true}
  ],
  "differences": [
    {
      "key": "serverWorkerThreads",
      "values": [
        {"address": "10.0.0.1:9876", "configured": true, "value": "8"},
        {"address": "10.0.0.2:9876", "configured": true, "value": "16"}
      ]
    }
  ]
}
```

---

### 15.14 上报生成速度

```
POST /api/ai/runs/:runId/speed
```

**Path Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `runId` | `number` | 是 | Run ID |

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `tokensPerSecond` | `number` | 是 | 客户端在流式输出期间实测的生成速度（token/s，非负） |

持久化客户端实测的生成速度，回放的会话记录会在回答旁显示同样的数字。Run 不属于调用者时按
不存在处理（404）。

**Response `data`:** `null`

---

## 16. 监控指标 Metrics

### 16.1 查询监控指标数据

```
POST /api/metrics/query
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `metric` | `string` | 是 | PromQL 表达式，最大 4096 个字符 |
| `start` | `number` | 是 | 起始时间（Unix 时间戳，秒） |
| `end` | `number` | 是 | 结束时间（Unix 时间戳，秒） |
| `step` | `string` | 是 | 查询分辨率，可以是持续时间或秒数（如 `"30s"`、`"5m"`、`"1h"`） |

**Request 示例：**

```json
{
  "metric": "sum(rate(rocketmq_messages_in_total[1m])) by (node_id)",
  "start": 1784112606,
  "end": 1784114406,
  "step": "30s"
}
```

**Response `data`:** `MetricData`

| 字段 | 类型 | 说明 |
|------|------|------|
| `resultType` | `string` | Prometheus 结果类型；范围查询通常为 `matrix` |
| `series` | `MetricSeries[]` | 查询返回的时间序列 |
| `warnings` | `string[]` | Prometheus 返回的非致命警告，没有警告时为空数组 |

**MetricSeries：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `labels` | `object` | 序列的完整标签集合，包括可能存在的 `__name__` |
| `values` | `MetricSample[]` | 浮点样本；没有浮点样本时为空数组 |
| `histograms` | `MetricHistogramSample[]` | Native Histogram 样本；没有 Histogram 样本时为空数组 |

同一序列可能只有 `values`、只有 `histograms`，或同时包含两者。

**MetricSample：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `timestamp` | `number` | Unix 时间戳，可能包含小数秒 |
| `value` | `string` | Prometheus 样本原始字符串，保留小数精度以及 `NaN`、`+Inf`、`-Inf` |

**MetricHistogramSample：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `timestamp` | `number` | Unix 时间戳，可能包含小数秒 |
| `histogram` | `object` | Prometheus Native Histogram 原始对象，包含 `count`、`sum` 和 `buckets` |

**Prometheus 配置：**

```yaml
studio:
  metrics:
    prometheus:
      base-url: ${STUDIO_METRICS_PROMETHEUS_BASE_URL:}
      connect-timeout: ${STUDIO_METRICS_PROMETHEUS_CONNECT_TIMEOUT:3s}
      read-timeout: ${STUDIO_METRICS_PROMETHEUS_READ_TIMEOUT:10s}
      username: ${STUDIO_METRICS_PROMETHEUS_USERNAME:}
      password: ${STUDIO_METRICS_PROMETHEUS_PASSWORD:}
      bearer-token: ${STUDIO_METRICS_PROMETHEUS_BEARER_TOKEN:}
```

- `base-url` 是 Prometheus 或 Prometheus-compatible 服务的 URL 前缀；服务会在其后追加 `/api/v1/query_range`。
- 未配置 `base-url` 时，查询接口返回 HTTP 503。
- `connect-timeout` 默认 3 秒，`read-timeout` 默认 10 秒。
- Bearer Token 的优先级高于 Basic Auth；Basic Auth 必须同时配置 `username` 和 `password`。
- 密码和 Token 等敏感配置应通过环境变量或其他外部化配置传入，不应提交到代码仓库。

**错误响应：**

| HTTP 状态 | 场景 |
|-----------|------|
| `400` | JSON 无法解析、字段类型错误、请求校验失败或 PromQL 参数错误 |
| `422` | Prometheus 无法执行 PromQL 表达式 |
| `502` | 无法连接 Prometheus，或 Prometheus 返回非法响应 |
| `503` | Prometheus 未配置或暂时不可用 |
| `504` | Prometheus 查询超时 |

### 16.2 Grafana 看板列表

```
GET /api/metrics/grafana/dashboards
```

返回内置的 RocketMQ Grafana 看板资产元数据。服务端只返回可以解析为 JSON
对象且包含有效 `uid` 的看板；无效资产会被跳过。

**Response `data`:** `GrafanaDashboardInfo[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| `uid` | `string` | Grafana 看板 UID，同时用于详情和导出接口 |
| `title` | `string` | 看板标题 |
| `description` | `string` | 看板说明 |
| `tags` | `string[]` | 看板标签 |

**Response 示例：**

```json
[
  {
    "uid": "rocketmq-overview",
    "title": "RocketMQ Cluster Overview",
    "description": "Overview dashboard for RocketMQ cluster metrics",
    "tags": ["rocketmq"]
  }
]
```

### 16.3 获取 Grafana 看板 JSON 模型

```
GET /api/metrics/grafana/dashboards/:uid
```

**Path Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `uid` | `string` | 是 | Grafana 看板 UID |

**Response `data`:** `Record<string, any>`

响应体是完整 Grafana dashboard JSON 模型，前端可用于预览或复制配置。

**错误响应：**

| HTTP 状态 | 场景 |
|-----------|------|
| `404` | 指定 UID 的内置看板不存在 |
| `500` | 看板 JSON 读取失败 |

### 16.4 导出单个 Grafana 看板

```
GET /api/metrics/grafana/dashboards/:uid/export
```

**Path Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `uid` | `string` | 是 | Grafana 看板 UID |

**Response:**

- `Content-Type: application/json`
- `Content-Disposition: attachment; filename="{uid}.json"`
- Body 为原始 Grafana dashboard JSON 文件内容。

**错误响应：**

| HTTP 状态 | 场景 |
|-----------|------|
| `404` | 指定 UID 的内置看板不存在 |
| `500` | 看板 JSON 读取失败 |

### 16.5 打包导出全部 Grafana 看板

```
GET /api/metrics/grafana/dashboards/export
```

返回全部有效内置 Grafana 看板的 zip 压缩包。压缩包内每个条目按
`{uid}.json` 命名，条目集合与 `GET /api/metrics/grafana/dashboards`
返回的可见看板列表保持一致。

**Response:**

- `Content-Type: application/zip`
- `Content-Disposition: attachment; filename="rocketmq-grafana-dashboards.zip"`
- Body 为 zip 文件二进制内容。

**错误响应：**

| HTTP 状态 | 场景 |
|-----------|------|
| `404` | 没有可导出的有效内置看板 |
| `500` | 看板 JSON 读取或 zip 打包失败 |

---

## 附录 A：枚举值速查

| 枚举 | 值 |
|------|---|
| **集群状态** | `healthy`, `warning`, `error`, `offline` |
| **Broker 状态** | `running`, `readonly`, `maintenance` |
| **节点状态** | `healthy`, `warning`, `error`, `offline` |
| **Topic 类型** | `NORMAL`, `FIFO`, `DELAY`, `TRANSACTION`, `LITE` |
| **Topic 权限** | `RW`, `RO`, `WO` |
| **消费类型** | `CLUSTERING`, `BROADCASTING` |
| **订阅模式** | `Push`, `Pop` |
| **协议** | `gRPC`, `Remoting` |
| **客户端语言** | `Java`, `Go`, `Python`, `Rust`, `Cpp`, `CSharp`, `NodeJS`, `PHP` |
| **ACL 资源类型** | `Topic`, `Group`, `Cluster` |
| **ACL 匹配模式** | `LITERAL`, `PREFIX` |
| **ACL 操作** | `PUB`, `SUB`, `ALL` |
| **ACL 决策** | `ALLOW`, `DENY` |
| **告警级别** | `error`, `warning`, `info` |
| **审计结果** | `SUCCESS`, `FAILED`, `PARTIAL` |
| **投递状态** | `success`, `failed`, `pending` |
| **证书状态** | `valid`, `expiring`, `expired` |
| **证书类型** | `TLS`, `mTLS`, `ServiceAccount` |
| **刷盘方式** | `ASYNC_FLUSH`, `SYNC_FLUSH` |
| **集群类型** | `V4_DIRECT`, `V5_PROXY_LOCAL`, `V5_PROXY_CLUSTER` |
| **顺序类型** | `PARTITON_ORDER`, `MESSAGES_ORDER` |
| **通知渠道** | `dingtalk`, `email`, `sms` |
| **LLM 提供商** | `openai`, `azure`, `anthropic`, `deepseek`, `tongyi`, `ollama`, `bedrock` |
| **数据源类型** | `Prometheus`, `VictoriaMetrics`, `Thanos`, `Mimir`, `Cortex`, `ARMS` |
| **AI 会话模式** | `chat`, `diagnose`, `manage`, `query` |
| **AI 引擎** | `http`, `claude-code`, `qoder` |
| **AI Run 状态** | `QUEUED`, `RUNNING`, `COMPLETED`, `STOPPED`, `FAILED`（后三者为终态） |
| **AI 停止原因** | `USER_STOP`, `SHUTDOWN`, `TIMEOUT`, `OUTPUT_LIMIT`, `PROVIDER_ERROR`, `SERVER_RESTART`, `OVERLOADED`, `ORPHANED` |
| **AI 思维链来源** | `model`（模型推理）, `enhance`（Studio 的 prompt 增强改写） |
| **AI 通知级别** | `info`, `warn`, `error` |
| **AI live 事件类型**（SSE `event: agent`） | `run_started`, `text_delta`, `thinking`, `tool_start`, `tool_done`, `notice`, `error`, `run_finished` |
| **AI 时间线事件类型**（持久化） | `user`, `thinking`, `text`, `tool_use`, `tool_result`, `notice`, `error`, `run_status` |
| **AI SSE 事件名** | `agent`, `error`, `done`（另有 `:hb` 心跳注释帧） |
