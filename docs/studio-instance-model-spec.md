# Studio 实例模型与 Topic/Group 全局唯一规范

> 状态：**规范已定**。当前代码与本规范不一致（DB 唯一键仍为 `(cluster_id, instance_id, name)`），落地项见 §5，未排期。
>
> TL;DR (EN): One physical cluster may host multiple Studio instances; `instanceId` is a Studio-side ownership concept only — open-source RocketMQ has no namespace/instanceId. Topic and group names are **globally unique within one Studio deployment**, even across multiple nameservers/clusters. Environments that need identical resource names (dev/staging/prod) must run **separate Studio deployments**.

## 1. 层级模型

```
nameserver（接入点，物理） ← 多个 cluster 可共享
    └── cluster（物理集群） ← 1 个 cluster 可挂多个 instance
            └── instance（Studio 逻辑管理单元，instanceId = rmq_instance.name 全局唯一）
```

- 1 个 cluster 上可以有**多个 instance**（1:N）；每个 instance 由唯一 `instanceId` 标识。
- 多个 cluster 可能**共享同一 nameserver**。
- **instanceId 只是 Studio 侧概念**：开源 RocketMQ 运行时没有 namespace / instanceId——一个 nameserver 之下，topic/group 集合是确定性的、扁平的。

## 2. Topic/Group 全局唯一（核心约定）

- 一个 Studio 部署内，topic 名与 group 名各自**全局唯一**：数据库唯一键直接加在 `name` 上（替代现行 `(cluster_id, instance_id, name)` 三列 UK）。
- 一个名字只有一条管理记录：`instance_id` 仅表达**归属实例**（管理视图），`cluster_id` 仅表达物理集群引用——两者都不是资源身份的组成部分。
- **即使用户管理多套 nameserver / 多个物理集群，名字同样要求全局唯一**（不按 cluster 隔离）：
  - 管理考量：检索、审计、告警与 MCP/CLI 工具中资源语义单一，杜绝「同名不同集群」的歧义。
  - 技术考量：共享 nameserver 时，路由按 topic 名注册与查询（`TopicRouteData` 按名聚合、不按 cluster 隔离），跨集群同名 topic 本身就会造成路由混杂——全局唯一从源头消除该隐患。
- **对外参数命名**（MCP tools / REST / CLI 一体适用）：实例维度入参一律 **`instanceId`**，禁止以 `cluster` 作为实例参数名——`cluster`/`clusterId` 仅保留物理集群语义（如 `cluster.list`、`TopicVO.clusterId`）；`instanceId` 为**正式必填参数、无默认注入设计**，由调用方显式传入（工具 schema 与参数表必须显式列出，不得以「由 context 提供」等理由省略）；资源名入参一律 **`topicName` / `groupName`**，单资源操作为精确匹配，list 类工具以其为模糊查询参数（替代泛化的 `search`）；CLI/MCP **无 namespace 概念**——工具入/出参一律不暴露 `namespace`（开源链路无 `ns%topic` 物理前缀，namespace 仅为展示字段，从工具中删除；REST/VO 与 CSV 导出不受影响）。

## 3. 同名需求 = 部署多套 Studio

- 用户确需同名 topic/group（典型：**日常 / 预发 / 线上**三套环境复用同一批资源名）时，正确做法是**部署多套独立 Studio**（每环境一套），而不是在一个 Studio 内容忍重名。
- 一个 Studio = 一个环境的治理域，域内全局唯一天然成立。

## 4. 行为约束（由规范导出，REST / MCP / CLI 一体适用）

1. **create / import**：先按 `name` 查重——名字已存在且归属其他实例 → 拒绝（409，提示归属实例）；归属本实例 → 幂等 upsert（create==update 语义不变）。import **不得静默覆盖**物理配置，同名冲突逐条记入 failures。
2. **update / delete / 消息与位点操作**：仅归属实例可执行；跨实例请求拒绝（409，提示归属实例）。归属转移暂不提供（如确有需求另行设计「转移归属」功能）。
3. **list / describe**：管理视图——仅展示归属该实例的资源；**物理可见 ≠ 归属**（同一 cluster 新建的实例不会自动看到既有 topic）。既有集群资源经 import 按名进入某实例视图（受第 1 条查重约束）。
4. **delete**：删除物理资源 + 唯一管理记录；不存在多实例「僵尸记录」问题（一实例删除、另一实例仍显示）——该问题随三列 UK 废除而根除。

## 5. 落地项（待办，未排期）

- `server/src/main/resources/db/schema.sql`：
  - `uk_cluster_instance_topic (cluster_id, instance_id, name)` → `uk_topic_name (name)`；
  - `uk_cluster_instance_group (cluster_id, instance_id, name)` → `uk_group_name (name)`；
  - `cluster_id` / `instance_id` 列保留（归属与物理集群引用），维持普通索引（`idx_topic_instance` 等）。
- `RocketMQAdminClientImpl.createTopic / deleteTopic`（及 group 对应方法）：DB 查重与删除条件由 `(cluster_id, instance_id, name)` 改为按 `name`，加归属校验与 409 冲突错误；`MetadataService.importTopics` 随 `createTopic` 生效。
- **存量数据迁移**：迁移脚本先检测跨实例同名行；存在即停下报告、由人工合并或裁决归属，禁止自动丢弃。
- 测试与文档：补充归属冲突 / 跨实例拒绝用例。
