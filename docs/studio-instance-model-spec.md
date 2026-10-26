# Studio 实例模型与 Topic/Group 唯一性域规范

> 状态：**已落地**。资源身份由「名字全局唯一」改为「按唯一性域 + broker 集群」，DB 唯一键为
> `uk_topic_domain (domain, cluster_id, name)` / `uk_group_domain (domain, cluster_id, name)`，
> 存量库由 `ResourceOwnershipSchemaMigration` 在启动时回填并换键（见 §5）。本文取代此前的
> 「Topic/Group 全局唯一」口径。
>
> TL;DR (EN): One physical cluster may host multiple Studio instances; `instanceId` is a Studio-side
> ownership concept only — open-source RocketMQ has no namespace/instanceId. A topic or group name is
> unique **per uniqueness domain and broker cluster**, where the domain is the NameServer registry entry
> an open-source instance is bound to (`ns:<nameserver_id>`) or the cloud instance itself
> (`cloud:<cloudInstanceId>`). Instances bound to different NameServer clusters may therefore reuse the
> same names within one Studio deployment, while two instances sharing one NameServer and one broker
> cluster still cannot claim the same slot.

## 1. 层级模型

```
nameserver（接入点，物理；rmq_nameserver 一行） ← 多个 cluster 可共享
    └── cluster（物理集群） ← 1 个 cluster 可挂多个 instance
            └── instance（Studio 逻辑管理单元，instanceId = rmq_instance.name 全局唯一）
                    └── topic / group（资源身份 = domain + cluster_id + name）
```

- 1 个 cluster 上可以有**多个 instance**（1:N）；每个 instance 由唯一 `instanceId` 标识。
- 多个 cluster 可能**共享同一 nameserver**。
- **开源（APACHE）实例在创建时绑定一个 nameserver 注册项**（`rmq_instance.nameserver_id`，创建后
  不可更改）；直连模式的接入地址必须始终指向该集群。云厂商实例的域由 `cloud_instance_id` 给出，
  不需要绑定 nameserver。
- **instanceId 只是 Studio 侧概念**：开源 RocketMQ 运行时没有 namespace / instanceId——一个
  nameserver 之下，topic/group 集合是确定性的、扁平的。

## 2. 唯一性域与 slot（核心约定）

- **域（domain）**：`ns:<nameserver_id>`（开源实例）或 `cloud:<cloudInstanceId>`（云实例），落库为
  `rmq_instance_topic.domain` / `rmq_instance_group.domain`（NOT NULL）。域表达「这个名字在哪一套
  接入点下才可能被路由到」。
- **slot = (domain, cluster_id, name)**：唯一键直接建在这三列上，**一个 slot 只归属一个实例**。
  一个名字在一个域内可以有多个 slot（同域不同 broker cluster 各一条管理记录）。
- 由此得到三条边界：
  - **跨域同名互不影响**——日常 / 预发 / 线上各自绑定自己的 nameserver，就能在一个 Studio 内共存。
  - **同域跨 cluster 同名可共存**——路由本身按 cluster 隔离，各 cluster 上是各自独立的物理资源。
  - **同域同 cluster 同名 = 同一个物理资源**，归属唯一；他人已占有则拒绝（见 §4）。
- `instance_id` 仅表达**归属实例**（管理视图），不是资源身份的组成部分。
- 未绑定 nameserver 的开源实例无法确定域：`ResourceOwnershipGuard.domainOf` 抛 409，提示带
  nameserver 重新创建该实例（老实例由启动迁移自动补绑，见 §5）。
- **对外参数命名**（MCP tools / REST / CLI 一体适用）：实例维度入参一律 **`instanceId`**，禁止以 `cluster` 作为实例参数名——`cluster`/`clusterId` 仅保留物理集群语义（如 `cluster.list`、`TopicVO.clusterId`）；`instanceId` 为**正式必填参数、无默认注入设计**，由调用方显式传入（工具 schema 与参数表必须显式列出，不得以「由 context 提供」等理由省略）；资源名入参一律 **`topicName` / `groupName`**，单资源操作为精确匹配，list 类工具以其为模糊查询参数（替代泛化的 `search`）；CLI/MCP **无 namespace 概念**——工具入/出参一律不暴露 `namespace`（开源链路无 `ns%topic` 物理前缀，namespace 仅为展示字段，从工具中删除；REST/VO 与 CSV 导出不受影响）。
- `clusterId` 只在「域内多个 broker cluster 都有该名字」时才是必填的消歧参数，其余情况由后端解析
  （见 §4 第 2 条），不把它变成所有接口的常规必填项。

## 3. 同名需求 = 绑不同的 nameserver

- 用户确需同名 topic/group（典型：**日常 / 预发 / 线上**三套环境复用同一批资源名）时，正确做法是
  **给每套环境的实例绑定各自的 nameserver 集群**——域不同，名字自然互不冲突。
- 部署多套 Studio 仍然可行，但不再是同名需求的唯一手段。
- 一个 Studio = 一个治理面板，面板内可以并列多套环境；真正的冲突只发生在「同域 + 同 broker
  cluster + 同名」，而那本来就是同一个物理资源。

## 4. 行为约束（由规范导出，REST / MCP / CLI 一体适用）

1. **create / import**：目标 cluster 取「本实例在该域内已有的同名注册」，没有则用实例默认 cluster
   （`createTarget`）；**其他 cluster 上他人的同名注册不阻塞创建**。解析出的 slot 归属他实例 →
   拒绝（409，提示归属实例）；归属本实例 → 幂等 upsert（create==update 语义不变）。import
   **不得静默覆盖**物理配置，同名冲突逐条记入 failures。
2. **配置类变更（update / delete / topic 与 group 配置）**：必须有唯一物理目标（`checkSlot`）。
   `clusterId` 为空时，该名字在域内只有一条注册就用它，有多条则拒绝（409，提示需要指定物理目标）；
   仅归属实例可执行，跨实例请求拒绝（409，提示归属实例）。归属转移暂不提供（如确有需求另行设计
   「转移归属」功能）。
3. **按名路由的操作（发消息、查消息、重置位点等）**：走域级归属（`checkOwnerInDomain`）——本实例
   自己的注册优先命中；域内只有他人注册时拒绝（409）。
4. **list / describe**：管理视图——仅展示归属该实例的资源；**物理可见 ≠ 归属**（同一 cluster 新建的
   实例不会自动看到既有 topic）。既有集群资源经 import 按 slot 进入某实例视图（受第 1 条约束）。
5. **delete**：删除物理资源 + 该 slot 的管理记录；同域其他 cluster、其他域的同名注册不受影响。
   归属一旦提交，不因后续远端操作失败而释放（避免半删状态被他人抢占）。

## 5. 实现位置

- `server/src/main/resources/db/schema.sql`：`rmq_instance.nameserver_id`（应用层校验存在性，不加外键）、
  两张资源表的 `domain` 列、`uk_topic_domain` / `uk_group_domain` 与 `idx_*_domain`。
- `ResourceOwnershipGuard`：`domainOf`（算域）、`createTarget` / `checkSlot` / `checkOwnerInDomain` /
  `slots`（解析 slot 与归属）、`write`（带行锁落归属）；云资源尚未全局登记，写操作直接拒绝而不把
  OpenAPI 可见性当归属。
- `ResourceOwnershipSchemaMigration`：启动时补列 → 逐实例解析域（nameserver 注册表里没有的接入地址
  自动注册一行，云实例缺 `cloud_instance_id` 则拒绝启动）→ 回填资源行 `domain`（归属为空的历史行按
  `cluster_id` 兜底，仍无法判定则拒绝启动）→ 校验同 slot 无重名（有则拒绝启动、要求人工裁决）→
  **先建 `uk_*_domain` 再删旧唯一键**，中途失败不丢失既有约束。
- `InstanceService` / `CreateInstanceDTO` / `UpdateInstanceDTO` / `InstanceVO` /
  `MybatisPlusInstanceRepository`：`nameserverId` 创建必填、更新不可改；`NameserverRegistryService`
  提供选择器所需的注册项与存在性校验。
- 调用点：`RocketMQAdminClientImpl`（topic / group 的创建、删除、配置）、`MetadataService`（import）、
  `LiteTopicService`、`MessageService`、`RocketMQMessageProvider` / `RocketMQLiteTopicProvider`。
- 前端：实例页的 nameserver 选择器与列；创建实例 / Topic / Group 三处表单的问号弹窗
  （`ResourceRuleHelp` + `ResourceOwnershipTopology`）——把域边界、归属拓扑与四条归属规则讲在
  「创建动作发生的地方」，不设独立说明页。
