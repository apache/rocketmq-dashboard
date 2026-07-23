# RocketMQ Dashboard 完整 PR 粒度拆分方案

基于 `origin/rocketmq-studio..HEAD` 的 572 个差异文件，按赛道一准则（5-10 个文件、`[studio]` 前缀、单页面/单功能聚焦）拆分为原子化 PR。

---

## 已合并 PR 追踪（rocketmq-studio 分支）

> 以下 PR 已合并到 `rocketmq-studio` 分支，对应的页面存在于 `web/src/pages/studio/`（TypeScript 前端）。
> **注意**：`frontend-new/`（JavaScript 前端）是独立的前端代码库，与 `web/` 中的页面互不冲突。

| PR | 页面 | 路径（web/ TypeScript 前端） | 合并状态 |
|----|------|------|------|
| #473 | Login + Auth + AI | `web/src/pages/login/`, `web/src/pages/ai/` | ✅ 已合并 |
| #474 | Ops | `web/src/pages/studio/Ops.tsx` | ✅ 已合并 |
| #475 | Producer | `web/src/pages/studio/Producer.tsx` | ✅ 已合并 |
| #476 | AlertManagement | `web/src/pages/studio/AlertManagement.tsx` | ✅ 已合并 |
| #477 | SslSettings | `web/src/pages/studio/SslSettings.tsx` | ✅ 已合并 |
| #479 | BrokerCluster | `web/src/pages/studio/BrokerCluster.tsx` | ✅ 已合并 |
| #480 | GroupManagement | `web/src/pages/studio/GroupManagement.tsx` | ✅ 已合并 |
| #481 | LiteTopic | `web/src/pages/studio/LiteTopic.tsx` | ✅ 已合并 |
| #482 | Proxy | `web/src/pages/studio/Proxy.tsx` | ✅ 已合并 |
| #483 | LlmSettings | `web/src/pages/studio/LlmSettings.tsx` | ✅ 已合并 |
| #490 | 剩余整合变更 | `web/src/services/`, `web/src/stores/`, `web/vite.config.ts` | ✅ 已合并 |
| #462 | 服务能力与交互增强 | `web/src/` 多文件 | ✅ 已合并 |
| #445 | 前端 API 对接 | `web/src/` 多文件 | ✅ 已合并 |
| #465 | 偏好持久化与无障碍 | `web/src/` 多文件 | ✅ 已合并 |

### 双前端架构说明

本项目存在**两套独立的前端代码库**：

| 前端 | 技术栈 | 路径 | 状态 | 说明 |
|------|--------|------|------|------|
| **web/** | TypeScript + Vite + React | `web/src/pages/studio/` | ✅ 已在 rocketmq-studio | PR #473-#490 已合并的页面 |
| **frontend-new/** | JavaScript + CRA + React | `frontend-new/src/pages/` | ❌ 不在 rocketmq-studio | 本方案需提交的 122 个文件 |

两套前端的页面功能有重叠（如 Login、Ops、Producer 等），但代码实现完全独立：
- `web/` 使用 TypeScript、Vite 构建、Ant Design Pro 风格
- `frontend-new/` 使用 JavaScript、Create React App 构建、自定义组件风格

**提交策略**：`frontend-new/` 的所有页面作为新前端代码库整体提交，无需与 `web/` 已合并页面去重。

### server/ 模块已合并状态

| 文件 | 状态 | 说明 |
|------|------|------|
| `GlobalExceptionHandler.java` | ✅ 已在 rocketmq-studio | 5个处理器超集，使用 Result 统一响应 |
| `PrometheusException.java` | ✅ 已在 rocketmq-studio | 位于 `cluster/metrics/` 包 |
| `RealClientProvider.java` | ⚠️ 有差异 | 需提交（PR-SERVER-1） |
| `AiLlmConfigDTO.java` | ⚠️ 有差异 | 需提交（PR-SERVER-1） |
| `LlmConfig.java` | ⚠️ 有差异 | 需提交（PR-SERVER-1） |
| `LlmGatewayImpl.java` | ⚠️ 有差异 | 需提交（PR-SERVER-1） |
| `LlmSettingsResolver.java` | ⚠️ 有差异 | 需提交（PR-SERVER-1） |

### 已删除文件（工作区待提交）

| 文件 | 状态 | 说明 |
|------|------|------|
| `frontend-new/src/pages/Namespace/Namespace.css` | 🗑️ 已删除 | Namespace 页面已废弃 |
| `frontend-new/src/pages/Namespace/Namespace.jsx` | 🗑️ 已删除 | Namespace 页面已废弃 |
| `server/.../NamespaceController.java` | 🗑️ 已删除 | Namespace 后端已废弃 |
| `server/.../NamespaceVO.java` | 🗑️ 已删除 | Namespace VO 已废弃 |
| `frontend-new/src/components/StudioLayout/StudioLayout.jsx` | ✏️ 已修改 | 移除 Namespace 菜单项 |
| `frontend-new/src/router/index.jsx` | ✏️ 已修改 | 移除 Namespace 路由 |
| `server/.../MetadataService.java` | ✏️ 已修改 | 移除 Namespace 相关逻辑 |

---

## 总览统计

| 模块 | 文件数 | PR 数量 |
|------|--------|---------|
| 项目基础设施（root/.github/style/docs/deploy） | 39 | 6 |
| src/ 后端核心（controller/service/model/architecture 等） | 295 | 28 |
| server/ studio 后端 | 5 | 1 |
| frontend-new/ 前端 | 122 | 26 |
| web/ 旧版前端改造 | 16 | 3 |
| rocketmq-dashboard-cli/ | 47 | 4 |
| rocketmq-dashboard-mcp/ | 22 | 3 |
| rocketmq-dashboard-llm/ | 20 | 3 |
| rocketmq-dashboard-app/ | 2 | 1 |
| **合计** | **572** | **~75** |

---

## 一、项目基础设施 PR（优先提交，无业务依赖）

### PR-INFRA-1：项目构建配置
- **标题**: `[studio] feat: add project build configuration and root files`
- **文件数**: 4
- **文件**: `pom.xml`, `package-lock.json`, `.asf.yaml`, `.travis.yml`

### PR-INFRA-2：GitHub 社区模板
- **标题**: `[studio] feat: add GitHub issue/PR templates and CI workflow`
- **文件数**: 3
- **文件**: `.github/ISSUE_TEMPLATE.md`, `.github/PULL_REQUEST_TEMPLATE.md`, `.github/workflows/cli-build.yml`

### PR-INFRA-3：代码风格与版权
- **标题**: `[studio] feat: add code style and copyright configuration`
- **文件数**: 4
- **文件**: `style/copyright/Apache.xml`, `style/copyright/profiles_settings.xml`, `style/rmq_checkstyle.xml`, `style/rmq_codeStyle.xml`

### PR-INFRA-4：Docker 部署配置
- **标题**: `[studio] feat: add Docker and docker-compose deployment`
- **文件数**: 3
- **文件**: `deploy/Dockerfile`, `deploy/docker-compose.yml`, `src/main/docker/Dockerfile`

### PR-INFRA-5：Kubernetes 部署配置
- **标题**: `[studio] feat: add Kubernetes and Helm deployment manifests`
- **文件数**: 15
- **文件**: `deploy/kubernetes/configmap.yaml`, `deploy/kubernetes/deployment.yaml`, `deploy/kubernetes/frontend-deployment.yaml`, `deploy/kubernetes/hpa.yaml`, `deploy/kubernetes/ingress.yaml`, `deploy/kubernetes/namespace.yaml`, `deploy/kubernetes/service.yaml`, `deploy/helm/rocketmq-dashboard/Chart.yaml`, `deploy/helm/rocketmq-dashboard/templates/NOTES.txt`, `deploy/helm/rocketmq-dashboard/templates/_helpers.tpl`, `deploy/helm/rocketmq-dashboard/templates/configmap.yaml`, `deploy/helm/rocketmq-dashboard/templates/deployment.yaml`, `deploy/helm/rocketmq-dashboard/templates/hpa.yaml`, `deploy/helm/rocketmq-dashboard/templates/ingress.yaml`, `deploy/helm/rocketmq-dashboard/templates/service.yaml`, `deploy/helm/rocketmq-dashboard/values.yaml`
- **说明**: 超过10个文件，但均为部署配置且高度关联，建议整体提交或拆为 K8s + Helm 两个 PR

### PR-INFRA-6：用户指南文档
- **标题**: `[studio] docs: add user guide and milestone documentation`
- **文件数**: 13
- **文件**: `docs/1_0_0/Milestone.md`, `docs/1_0_0/UserGuide_CN.md`, `docs/1_0_0/UserGuide_CN/image-*.png`(8个), `docs/1_0_0/UserGuide_EN.md`, `docs/CapabilityMapping.md`
- **说明**: 文档类 PR 可适当放宽文件数限制

---

## 二、src/ 后端核心 PR（按功能域拆分）

### 2.1 应用入口与配置（6个文件）

#### PR-BE-1：应用入口与核心配置
- **标题**: `[studio] feat: add application entry point and core configuration`
- **文件数**: 8
- **文件**: `src/main/java/.../App.java`, `src/main/java/.../config/ArchitectureConfig.java`, `src/main/java/.../config/AuthWebMVCConfigurerAdapter.java`, `src/main/java/.../config/CollectExecutorConfig.java`, `src/main/java/.../config/CredentialEncryptionService.java`, `src/main/java/.../config/RMQConfigure.java`, `src/main/java/.../config/SecurityConfig.java`, `src/main/resources/application.yml`

#### PR-BE-2：应用资源与安全配置
- **标题**: `[studio] feat: add security resources and role-permission configuration`
- **文件数**: 5
- **文件**: `src/main/resources/logback.xml`, `src/main/resources/role-permission.yml`, `src/main/resources/users.properties`, `src/main/resources/rmqcngkeystore.jks`, `src/main/resources/META-INF/services/org.apache.rocketmq.dashboard.architecture.impl.cloud.CloudProviderFactory$CloudProviderPlugin`

---

### 2.2 架构层（22个文件，按功能拆分）

#### PR-BE-3：架构核心接口与 AdminClient
- **标题**: `[studio] feat: add architecture core interfaces and admin client abstraction`
- **文件数**: 5
- **文件**: `src/main/java/.../architecture/AdminClient.java`, `src/main/java/.../architecture/ClusterAccessType.java`, `src/main/java/.../architecture/ClusterProvider.java`, `src/main/java/.../architecture/MetadataProvider.java`, `src/main/java/.../config/ArchitectureConfig.java`(如已在 PR-BE-1 则移除)

#### PR-BE-4：V4/V5 架构实现
- **标题**: `[studio] feat: add V4 remoting and V5 proxy architecture providers`
- **文件数**: 6
- **文件**: `src/main/java/.../architecture/impl/GrpcAdminClient.java`, `src/main/java/.../architecture/impl/RemotingAdminClient.java`, `src/main/java/.../architecture/impl/V4ClusterProvider.java`, `src/main/java/.../architecture/impl/V4MetadataProvider.java`, `src/main/java/.../architecture/impl/V5ProxyClusterProvider.java`, `src/main/java/.../architecture/impl/V5ProxyMetadataProvider.java`

#### PR-BE-5：云厂商架构实现
- **标题**: `[studio] feat: add cloud provider architecture (Aliyun/Huawei/AWS/Tencent)`
- **文件数**: 12
- **文件**: `src/main/java/.../architecture/impl/cloud/AbstractCloudClusterProvider.java`, `src/main/java/.../architecture/impl/cloud/AbstractCloudMetadataProvider.java`, `src/main/java/.../architecture/impl/cloud/AliyunClusterProvider.java`, `src/main/java/.../architecture/impl/cloud/AliyunMetadataProvider.java`, `src/main/java/.../architecture/impl/cloud/AwsCloudProviderPlugin.java`, `src/main/java/.../architecture/impl/cloud/CloudAdminClient.java`, `src/main/java/.../architecture/impl/cloud/CloudApiHttpClient.java`, `src/main/java/.../architecture/impl/cloud/CloudProviderFactory.java`, `src/main/java/.../architecture/impl/cloud/HuaweiCloudClusterProvider.java`, `src/main/java/.../architecture/impl/cloud/HuaweiCloudMetadataProvider.java`, `src/main/java/.../architecture/impl/cloud/TencentCloudClusterProvider.java`, `src/main/java/.../architecture/impl/cloud/TencentCloudMetadataProvider.java`
- **说明**: 超过10个文件，但云厂商实现高度内聚，建议整体提交；或拆为 Aliyun + Huawei/AWS/Tencent 两个 PR

---

### 2.3 Admin 连接池（5个文件）

#### PR-BE-6：MQAdmin 连接池管理
- **标题**: `[studio] feat: add MQAdmin connection pool and aspect management`
- **文件数**: 7
- **文件**: `src/main/java/.../admin/MQAdminFactory.java`, `src/main/java/.../admin/MQAdminPooledObjectFactory.java`, `src/main/java/.../admin/MqAdminExtObjectPool.java`, `src/main/java/.../admin/UserMQAdminPoolManager.java`, `src/main/java/.../admin/UserSpecificMQAdminPooledObjectFactory.java`, `src/main/java/.../aspect/admin/MQAdminAspect.java`, `src/main/java/.../aspect/admin/annotation/OriginalControllerReturnValue.java`

---

### 2.4 Model 层（62个文件，按业务域拆分）

#### PR-BE-7：用户与认证模型
- **标题**: `[studio] feat: add user and authentication models`
- **文件数**: 8
- **文件**: `src/main/java/.../model/User.java`, `src/main/java/.../model/UserInfo.java`, `src/main/java/.../model/UserInfoDto.java`, `src/main/java/.../model/LoginInfo.java`, `src/main/java/.../model/LoginResult.java`, `src/main/java/.../model/request/UserCreateRequest.java`, `src/main/java/.../model/request/UserUpdateRequest.java`, `src/main/java/.../model/request/UserInfoParam.java`

#### PR-BE-8：ACL 安全模型
- **标题**: `[studio] feat: add ACL security models`
- **文件数**: 7
- **文件**: `src/main/java/.../model/ACLPolicy.java`, `src/main/java/.../model/ACLUser.java`, `src/main/java/.../model/AccessControlList.java`, `src/main/java/.../model/Acl2PolicyContext.java`, `src/main/java/.../model/AclInfo.java`, `src/main/java/.../model/Policy.java`, `src/main/java/.../model/PolicyRequest.java`

#### PR-BE-9：消息与追踪模型
- **标题**: `[studio] feat: add message and trace models`
- **文件数**: 9
- **文件**: `src/main/java/.../model/MessageInfo.java`, `src/main/java/.../model/MessagePage.java`, `src/main/java/.../model/MessagePageTask.java`, `src/main/java/.../model/MessageQueryByPage.java`, `src/main/java/.../model/MessageTraceView.java`, `src/main/java/.../model/MessageView.java`, `src/main/java/.../model/trace/MessageTraceGraph.java`, `src/main/java/.../model/trace/MessageTraceStatusEnum.java`, `src/main/java/.../model/trace/ProducerNode.java`, `src/main/java/.../model/trace/SubscriptionNode.java`, `src/main/java/.../model/trace/TraceNode.java`
- **说明**: trace 子包5个文件 + 消息6个文件，共11个，可拆为消息模型 + 追踪模型两个 PR

#### PR-BE-10：消费者与主题模型
- **标题**: `[studio] feat: add consumer and topic models`
- **文件数**: 10
- **文件**: `src/main/java/.../model/ConsumerGroupInfo.java`, `src/main/java/.../model/ConsumerGroupRollBackStat.java`, `src/main/java/.../model/ConsumerMonitorConfig.java`, `src/main/java/.../model/GroupConsumeInfo.java`, `src/main/java/.../model/TopicConsumerInfo.java`, `src/main/java/.../model/TopicInfo.java`, `src/main/java/.../model/TopicType.java`, `src/main/java/.../model/QueueStatInfo.java`, `src/main/java/.../model/QueueOffsetInfo.java`, `src/main/java/.../model/SubscriptionInfo.java`

#### PR-BE-11：集群与架构模型
- **标题**: `[studio] feat: add cluster and architecture models`
- **文件数**: 7
- **文件**: `src/main/java/.../model/ClusterCapability.java`, `src/main/java/.../model/ClusterTopology.java`, `src/main/java/.../model/CloudProviderConfig.java`, `src/main/java/.../model/ClientInstance.java`, `src/main/java/.../model/ConnectionInfo.java`, `src/main/java/.../model/NamespaceInfo.java`, `src/main/java/.../model/MetricsDataSourceConfig.java`, `src/main/java/.../model/MetricsHealthResult.java`

#### PR-BE-12：DLQ 与 LiteTopic 模型
- **标题**: `[studio] feat: add DLQ and LiteTopic models`
- **文件数**: 6
- **文件**: `src/main/java/.../model/DlqMessageExcelModel.java`, `src/main/java/.../model/DlqMessageRequest.java`, `src/main/java/.../model/DlqMessageResendResult.java`, `src/main/java/.../model/LiteTopicQuota.java`, `src/main/java/.../model/LiteTopicSession.java`, `src/main/java/.../model/LiteTopicSummary.java`

#### PR-BE-13：请求模型
- **标题**: `[studio] feat: add request DTO models`
- **文件数**: 9
- **文件**: `src/main/java/.../model/request/AclRequest.java`, `src/main/java/.../model/request/ArchitectureSwitchRequest.java`, `src/main/java/.../model/request/ConsumerConfigInfo.java`, `src/main/java/.../model/request/DeleteSubGroupRequest.java`, `src/main/java/.../model/request/MessageQuery.java`, `src/main/java/.../model/request/MetricsDataSourceRequest.java`, `src/main/java/.../model/request/ResetOffsetRequest.java`, `src/main/java/.../model/request/SendTopicMessageRequest.java`, `src/main/java/.../model/request/TopicConfigInfo.java`, `src/main/java/.../model/request/TopicTypeList.java`, `src/main/java/.../model/request/TopicTypeMeta.java`
- **说明**: 11个文件，可拆为 ACL/架构请求 + 消费者/消息请求 + 主题请求 三个 PR

#### PR-BE-14：通用模型
- **标题**: `[studio] feat: add common utility models`
- **文件数**: 2
- **文件**: `src/main/java/.../model/Entry.java`, `src/main/java/.../model/request/UserCreateRequest.java`(如已在上文则移除)

---

### 2.5 Controller 层（26个文件，按业务域拆分）

#### PR-BE-15：核心 Controller（Login/Ops/Dashboard/Monitor）
- **标题**: `[studio] feat: add core controllers (login, ops, dashboard, monitor)`
- **文件数**: 5
- **文件**: `src/main/java/.../controller/LoginController.java`, `src/main/java/.../controller/OpsController.java`, `src/main/java/.../controller/DashboardController.java`, `src/main/java/.../controller/MonitorController.java`, `src/main/java/.../controller/CsrfTokenController.java`

#### PR-BE-16：Topic/Consumer/Message Controller
- **标题**: `[studio] feat: add topic, consumer and message controllers`
- **文件数**: 5
- **文件**: `src/main/java/.../controller/TopicController.java`, `src/main/java/.../controller/ConsumerController.java`, `src/main/java/.../controller/MessageController.java`, `src/main/java/.../controller/MessageTraceController.java`, `src/main/java/.../controller/DlqMessageController.java`

#### PR-BE-17：ACL/Proxy/Cluster Controller
- **标题**: `[studio] feat: add ACL, proxy and cluster controllers`
- **文件数**: 6
- **文件**: `src/main/java/.../controller/AclController.java`, `src/main/java/.../controller/Acl2Controller.java`, `src/main/java/.../controller/ProxyController.java`, `src/main/java/.../controller/ProxyAdminController.java`, `src/main/java/.../controller/ClusterController.java`, `src/main/java/.../controller/ArchitectureController.java`

#### PR-BE-18：Metrics/Namespace/LiteTopic/Client/Producer Controller
- **标题**: `[studio] feat: add metrics, namespace, lite topic and client controllers`
- **文件数**: 7
- **文件**: `src/main/java/.../controller/MetricsController.java`, `src/main/java/.../controller/MetricsDataController.java`, `src/main/java/.../controller/NamespaceController.java`, `src/main/java/.../controller/LiteTopicController.java`, `src/main/java/.../controller/ClientController.java`, `src/main/java/.../controller/ProducerController.java`, `src/main/java/.../controller/NamesvrController.java`

#### PR-BE-19：辅助 Controller
- **标题**: `[studio] feat: add compatibility alias and skill registry controllers`
- **文件数**: 3
- **文件**: `src/main/java/.../controller/CompatibilityAliasController.java`, `src/main/java/.../controller/SkillRegistryController.java`, `src/main/java/.../controller/TestController.java`

---

### 2.6 Service 层（75个文件，按业务域拆分）

#### PR-BE-20：Service 接口定义
- **标题**: `[studio] feat: add service interface definitions`
- **文件数**: 10
- **文件**: `src/main/java/.../service/AbstractCommonService.java`, `src/main/java/.../service/AclService.java`, `src/main/java/.../service/Acl2Service.java`, `src/main/java/.../service/ArchitectureBasedService.java`, `src/main/java/.../service/ClientService.java`, `src/main/java/.../service/ClusterService.java`, `src/main/java/.../service/ClusterInfoService.java`, `src/main/java/.../service/ConsumerService.java`, `src/main/java/.../service/DashboardCollectService.java`, `src/main/java/.../service/DashboardService.java`

#### PR-BE-21：Service 接口定义（续）
- **标题**: `[studio] feat: add service interface definitions (part 2)`
- **文件数**: 10
- **文件**: `src/main/java/.../service/DlqMessageService.java`, `src/main/java/.../service/LiteTopicService.java`, `src/main/java/.../service/LoginService.java`, `src/main/java/.../service/MessageService.java`, `src/main/java/.../service/MessageTraceService.java`, `src/main/java/.../service/MetricsEnhancedService.java`, `src/main/java/.../service/MetricsProvider.java`, `src/main/java/.../service/MetricsService.java`, `src/main/java/.../service/MonitorService.java`, `src/main/java/.../service/NamespaceService.java`

#### PR-BE-22：Service 接口定义（续2）
- **标题**: `[studio] feat: add service interface definitions (part 3)`
- **文件数**: 8
- **文件**: `src/main/java/.../service/OpsService.java`, `src/main/java/.../service/PermissionService.java`, `src/main/java/.../service/ProducerService.java`, `src/main/java/.../service/ProxyAdminService.java`, `src/main/java/.../service/ProxyService.java`, `src/main/java/.../service/TopicService.java`, `src/main/java/.../service/UnifiedClientService.java`, `src/main/java/.../service/UserService.java`

#### PR-BE-23：Service 实现 — ACL/认证/用户
- **标题**: `[studio] feat: add ACL, login and user service implementations`
- **文件数**: 7
- **文件**: `src/main/java/.../service/impl/AclServiceImpl.java`, `src/main/java/.../service/impl/Acl2ServiceImpl.java`, `src/main/java/.../service/impl/LoginServiceImpl.java`, `src/main/java/.../service/impl/UserServiceImpl.java`, `src/main/java/.../service/impl/PermissionServiceImpl.java`, `src/main/java/.../service/strategy/AclUserStrategy.java`, `src/main/java/.../service/strategy/FileUserStrategy.java`, `src/main/java/.../service/strategy/UserContext.java`, `src/main/java/.../service/strategy/UserStrategy.java`
- **说明**: 9个文件，含 strategy 子包

#### PR-BE-24：Service 实现 — Topic/Consumer/Message
- **标题**: `[studio] feat: add topic, consumer and message service implementations`
- **文件数**: 5
- **文件**: `src/main/java/.../service/impl/TopicServiceImpl.java`, `src/main/java/.../service/impl/ConsumerServiceImpl.java`, `src/main/java/.../service/impl/MessageServiceImpl.java`, `src/main/java/.../service/impl/MessageTraceServiceImpl.java`, `src/main/java/.../service/impl/DlqMessageServiceImpl.java`

#### PR-BE-25：Service 实现 — 集群/Proxy/架构
- **标题**: `[studio] feat: add cluster, proxy and architecture service implementations`
- **文件数**: 6
- **文件**: `src/main/java/.../service/impl/ClusterServiceImpl.java`, `src/main/java/.../service/impl/ProxyServiceImpl.java`, `src/main/java/.../service/impl/ProxyAdminServiceImpl.java`, `src/main/java/.../service/impl/ClientServiceImpl.java`, `src/main/java/.../service/impl/UnifiedClientServiceImpl.java`, `src/main/java/.../service/impl/LiteTopicServiceImpl.java`

#### PR-BE-26：Service 实现 — 运维/监控/指标
- **标题**: `[studio] feat: add ops, monitor and metrics service implementations`
- **文件数**: 7
- **文件**: `src/main/java/.../service/impl/OpsServiceImpl.java`, `src/main/java/.../service/impl/MonitorServiceImpl.java`, `src/main/java/.../service/impl/DashboardServiceImpl.java`, `src/main/java/.../service/impl/DashboardCollectServiceImpl.java`, `src/main/java/.../service/impl/MetricsServiceImpl.java`, `src/main/java/.../service/impl/MetricsEnhancedServiceImpl.java`, `src/main/java/.../service/impl/MetricsProviderImpl.java`

#### PR-BE-27：Service 实现 — MQAdmin 客户端
- **标题**: `[studio] feat: add MQAdmin client service implementations`
- **文件数**: 8
- **文件**: `src/main/java/.../service/client/MQAdminExtImpl.java`, `src/main/java/.../service/client/MQAdminInstance.java`, `src/main/java/.../service/client/MultiProxyAdminClient.java`, `src/main/java/.../service/client/ProxyAdmin.java`, `src/main/java/.../service/client/ProxyAdminGrpcClient.java`, `src/main/java/.../service/client/ProxyAdminImpl.java`, `src/main/java/.../service/client/GrpcClientCollector.java`, `src/main/java/.../service/client/RemotingClientCollector.java`

#### PR-BE-28：Service 实现 — 指标/命名空间/提供者/检查器
- **标题**: `[studio] feat: add metrics, namespace, provider and checker service implementations`
- **文件数**: 9
- **文件**: `src/main/java/.../service/impl/NamespaceServiceImpl.java`, `src/main/java/.../service/impl/ProducerServiceImpl.java`, `src/main/java/.../service/impl/AbstractFileStore.java`, `src/main/java/.../service/metrics/MetricsAggregationService.java`, `src/main/java/.../service/metrics/MetricsAggregationServiceImpl.java`, `src/main/java/.../service/metrics/PrometheusMetricsQueryClient.java`, `src/main/java/.../service/provider/UserInfoProvider.java`, `src/main/java/.../service/provider/UserInfoProviderImpl.java`, `src/main/java/.../service/checker/CheckerType.java`, `src/main/java/.../service/checker/RocketMqChecker.java`, `src/main/java/.../service/checker/impl/ClusterHealthCheckerImpl.java`, `src/main/java/.../service/checker/impl/TopicOnlyOneBrokerCheckerImpl.java`
- **说明**: 12个文件，可拆为 namespace/producer + metrics + checker 三个 PR

---

### 2.7 Skill 框架（10个文件）

#### PR-BE-29：Skill 可扩展框架
- **标题**: `[studio] feat: add skill extensible framework for LLM tool integration`
- **文件数**: 10
- **文件**: `src/main/java/.../skill/AbstractSkill.java`, `src/main/java/.../skill/Skill.java`, `src/main/java/.../skill/SkillExecutor.java`, `src/main/java/.../skill/SkillParameter.java`, `src/main/java/.../skill/SkillRegistry.java`, `src/main/java/.../skill/SkillResult.java`, `src/main/java/.../skill/skills/ClusterInfoSkill.java`, `src/main/java/.../skill/skills/ConsumerQuerySkill.java`, `src/main/java/.../skill/skills/MessageQuerySkill.java`, `src/main/java/.../skill/skills/TopicQuerySkill.java`

---

### 2.8 定时任务（9个文件）

#### PR-BE-30：定时采集与监控任务
- **标题**: `[studio] feat: add scheduled collection and monitoring tasks`
- **文件数**: 9
- **文件**: `src/main/java/.../task/AccumulationCollectTask.java`, `src/main/java/.../task/CollectTaskRunnble.java`, `src/main/java/.../task/DashboardCollectTask.java`, `src/main/java/.../task/HotTopicCollectTask.java`, `src/main/java/.../task/MonitorTask.java`, `src/main/java/.../task/NetworkThroughputCollectTask.java`, `src/main/java/.../task/ReplicaSyncCollectTask.java`, `src/main/java/.../task/StorageLatencyCollectTask.java`, `src/main/java/.../task/TransactionCollectTask.java`

---

### 2.9 工具/支持/拦截器/过滤器（17个文件）

#### PR-BE-31：工具类
- **标题**: `[studio] feat: add utility classes`
- **文件数**: 8
- **文件**: `src/main/java/.../util/AclVersionDetector.java`, `src/main/java/.../util/ClientDiagnosticsUtil.java`, `src/main/java/.../util/ExcelUtil.java`, `src/main/java/.../util/JsonUtil.java`, `src/main/java/.../util/MatcherUtil.java`, `src/main/java/.../util/MsgTraceDecodeUtil.java`, `src/main/java/.../util/UserInfoContext.java`, `src/main/java/.../util/WebUtil.java`

#### PR-BE-32：全局异常处理与响应包装
- **标题**: `[studio] feat: add global exception handler and response advice`
- **文件数**: 5
- **文件**: `src/main/java/.../support/GlobalExceptionHandler.java`, `src/main/java/.../support/GlobalRestfulResponseBodyAdvice.java`, `src/main/java/.../support/JsonResult.java`, `src/main/java/.../support/AutoCloseConsumerWrapper.java`, `src/main/java/.../exception/ServiceException.java`

#### PR-BE-33：权限与安全拦截
- **标题**: `[studio] feat: add permission, auth interceptor and security filter`
- **文件数**: 6
- **文件**: `src/main/java/.../permisssion/Permission.java`, `src/main/java/.../permisssion/PermissionAspect.java`, `src/main/java/.../permisssion/UserRoleEnum.java`, `src/main/java/.../interceptor/AuthInterceptor.java`, `src/main/java/.../filter/HttpBasicAuthorizedFilter.java`, `src/main/java/.../adapter/PrometheusMetricsAdapter.java`

#### PR-BE-34：LLM 代理控制器
- **标题**: `[studio] feat: add LLM proxy controller in main application`
- **文件数**: 1
- **文件**: `src/main/java/.../llm/LlmProxyController.java`

#### PR-BE-35：Proto 定义文件
- **标题**: `[studio] feat: add proxy admin protobuf definition`
- **文件数**: 1
- **文件**: `src/main/proto/proxy_admin.proto`

---

### 2.10 测试文件（45个文件，按对应模块分组）

#### PR-BE-36：基础测试设施
- **标题**: `[studio] test: add base test infrastructure and test resources`
- **文件数**: 8
- **文件**: `src/test/java/.../BaseTest.java`, `src/test/java/.../testbase/RocketMQConsoleTestBase.java`, `src/test/java/.../testbase/TestConstant.java`, `src/test/java/.../testbase/TestRocketMQServer.java`, `src/test/resources/application.properties`, `src/test/resources/logback.xml`, `src/test/resources/logback_rocketmq_client.xml`, `src/test/resources/users.properties`

#### PR-BE-37：Controller 测试
- **标题**: `[studio] test: add controller unit tests`
- **文件数**: 10
- **文件**: `src/test/java/.../controller/BaseControllerTest.java`, `src/test/java/.../controller/AclControllerTest.java`, `src/test/java/.../controller/ClusterControllerTest.java`, `src/test/java/.../controller/ConsumerControllerTest.java`, `src/test/java/.../controller/DashboardControllerTest.java`, `src/test/java/.../controller/DlqMessageControllerTest.java`, `src/test/java/.../controller/LoginControllerTest.java`, `src/test/java/.../controller/MessageControllerTest.java`, `src/test/java/.../controller/MessageTraceControllerTest.java`, `src/test/java/.../controller/MetricsControllerTest.java`

#### PR-BE-38：Controller 测试（续）
- **标题**: `[studio] test: add controller unit tests (part 2)`
- **文件数**: 7
- **文件**: `src/test/java/.../controller/MonitorControllerTest.java`, `src/test/java/.../controller/NamesvrControllerTest.java`, `src/test/java/.../controller/OpsControllerTest.java`, `src/test/java/.../controller/ProducerControllerTest.java`, `src/test/java/.../controller/ProxyAdminControllerTest.java`, `src/test/java/.../controller/TopicControllerTest.java`, `src/test/java/.../controller/AclControllerTest.java`(如已在上文则移除)

#### PR-BE-39：Admin/Config/Architecture 测试
- **标题**: `[studio] test: add admin, config and architecture tests`
- **文件数**: 9
- **文件**: `src/test/java/.../admin/MQAdminAspectTest.java`, `src/test/java/.../admin/MQAdminExtImplTest.java`, `src/test/java/.../admin/MQAdminPoolTest.java`, `src/test/java/.../config/ArchitectureConfigTest.java`, `src/test/java/.../config/AuthWebMVCConfigurerAdapterTest.java`, `src/test/java/.../config/CollectExecutorConfigTest.java`, `src/test/java/.../config/RMQConfigureTest.java`, `src/test/java/.../architecture/impl/GrpcAdminClientTest.java`, `src/test/java/.../architecture/impl/V4MetadataProviderSecurityTest.java`, `src/test/java/.../architecture/impl/V4MetadataProviderTest.java`, `src/test/java/.../architecture/impl/V5ProxyMetadataProviderTest.java`
- **说明**: 11个文件，可拆为 admin/config + architecture 两个 PR

#### PR-BE-40：Service/Util/Task 测试
- **标题**: `[studio] test: add service, utility and task tests`
- **文件数**: 10
- **文件**: `src/test/java/.../service/impl/MessageServiceImplTest.java`, `src/test/java/.../service/impl/ProxyAdminServiceImplTest.java`, `src/test/java/.../service/impl/TopicServiceImplTest.java`, `src/test/java/.../service/client/GrpcClientCollectorTest.java`, `src/test/java/.../service/client/MultiProxyAdminClientTest.java`, `src/test/java/.../service/client/ProxyAdminGrpcClientTest.java`, `src/test/java/.../task/DashboardCollectTaskTest.java`, `src/test/java/.../util/AutoCloseConsumerWrapperTests.java`, `src/test/java/.../util/MatcherUtilTest.java`, `src/test/java/.../util/MsgTraceDecodeUtilTest.java`, `src/test/java/.../util/MockObjectUtil.java`, `src/test/java/.../util/MyPrintingResultHandler.java`, `src/test/java/.../permission/PermissionAspectTest.java`, `src/test/java/.../web/WebStaticApplicationTests.java`
- **说明**: 14个文件，可拆为 service + client + util/task 三个 PR

---

## 三、server/ Studio 后端 PR

> **状态说明**：server/ 模块仅 5 个文件与 rocketmq-studio 有差异（均为 Modified，非 Added）。
> GlobalExceptionHandler.java 和 PrometheusException.java 已在 rocketmq-studio 中，无需提交。
> NamespaceController.java 和 NamespaceVO.java 已在工作区删除，待 commit 后将不再出现在 diff 中。

#### PR-SERVER-1：Studio Server LLM 集成
- **标题**: `[studio] feat: add studio server LLM integration and real client provider`
- **文件数**: 5
- **文件**: `server/src/main/java/.../cluster/client/RealClientProvider.java`, `server/src/main/java/.../ops/ai/AiLlmConfigDTO.java`, `server/src/main/java/.../ops/ai/LlmConfig.java`, `server/src/main/java/.../ops/ai/LlmGatewayImpl.java`, `server/src/main/java/.../ops/ai/LlmSettingsResolver.java`
- **变更类型**: Modified（5个文件均已在 rocketmq-studio 存在，本次为增量修改）

---

## 四、frontend-new/ 前端 PR（赛道一，单页面聚焦）

> 以下沿用已有 PR-GRANULARITY-ANALYSIS.md 的拆分方案，补充完整文件清单。

### 4.1 基础设施层（优先提交）

#### PR-FE-0a：i18n 语言上下文
- **标题**: `[studio] feat: add i18n language context`
- **文件数**: 2
- **文件**: `frontend-new/src/i18n/index.js`, `frontend-new/src/i18n/LanguageContext.js`

#### PR-FE-0b：主题上下文与 Store
- **标题**: `[studio] feat: add theme context and store`
- **文件数**: 4
- **文件**: `frontend-new/src/store/index.js`, `frontend-new/src/store/context/ThemeContext.js`, `frontend-new/src/store/reducers/themeReducer.js`, `frontend-new/src/store/actions/themeActions.js`

#### PR-FE-0c：StudioLayout 布局组件
- **标题**: `[studio] feat: add StudioLayout component`
- **文件数**: 3
- **文件**: `frontend-new/src/components/StudioLayout/StudioLayout.jsx`, `frontend-new/src/components/StudioLayout/StudioLayout.css`, `frontend-new/src/components/Navbar.jsx`

#### PR-FE-0d：前端构建配置与入口
- **标题**: `[studio] feat: add frontend build configuration and entry point`
- **文件数**: 10
- **文件**: `frontend-new/.env.development`, `frontend-new/.env.production`, `frontend-new/.env.template`, `frontend-new/.gitignore`, `frontend-new/Dockerfile`, `frontend-new/README.md`, `frontend-new/nginx.conf`, `frontend-new/package.json`, `frontend-new/package-lock.json`, `frontend-new/src/index.js`
- **说明**: 构建配置 PR 可适当放宽文件数

#### PR-FE-0e：前端应用壳与路由
- **标题**: `[studio] feat: add frontend App shell and router configuration`
- **文件数**: 6
- **文件**: `frontend-new/src/App.css`, `frontend-new/src/App.jsx`, `frontend-new/src/App.test.js`, `frontend-new/src/index.css`, `frontend-new/src/router/index.jsx`, `frontend-new/src/api/remoteApi/remoteApi.js`

#### PR-FE-0f：前端构建产物与静态资源
- **标题**: `[studio] chore: add frontend build artifacts and static assets`
- **文件数**: 9
- **文件**: `frontend-new/build/asset-manifest.json`, `frontend-new/build/favicon.ico`, `frontend-new/build/index.html`, `frontend-new/build/manifest.json`, `frontend-new/build/robots.txt`, `frontend-new/build/static/js/453.39fa76bc.chunk.js`, `frontend-new/build/static/js/453.39fa76bc.chunk.js.map`, `frontend-new/public/favicon.ico`, `frontend-new/public/index.html`, `frontend-new/public/manifest.json`, `frontend-new/public/robots.txt`
- **说明**: 构建产物是否需要提交需确认，可能应加入 .gitignore

---

### 4.2 独立页面 PR（低耦合，2-4 个文件）

| PR | 页面 | 路由 | 标题 | 文件数 |
|----|------|------|------|--------|
| PR-FE-1 | Login | `/login` | `[studio] feat: add login page` | 1 |
| PR-FE-2 | Ops | `/ops` | `[studio] feat: add ops page` | 1 |
| PR-FE-3 | Producer | `/producer` | `[studio] feat: add producer page` | 1 |
| PR-FE-4 | Alert | `/alert` | `[studio] feat: add alert management page` | 1 |
| PR-FE-5 | SslSettings | `/ssl-settings` | `[studio] feat: add SSL settings page` | 2 |
| PR-FE-6 | LlmSettings | `/llm-settings` | `[studio] feat: add LLM settings page` | 2 |
| PR-FE-7 | BrokerCluster | `/cluster` | `[studio] feat: add broker cluster overview page` | 2 |
| PR-FE-8 | GroupManagement | `/consumer` | `[studio] feat: add consumer group management page` | 2 |
| PR-FE-9 | LiteTopic | `/liteTopic` | `[studio] feat: add lite topic page` | 2 |
| PR-FE-10 | Proxy | `/proxy` | `[studio] feat: add proxy cluster page` | 3 |
| PR-FE-11 | Cluster | `/cluster`(旧版) | `[studio] feat: add cluster page` | 1 |
| ~~PR-FE-NS~~ | ~~Namespace~~ | ~~`/namespace`~~ | ~~已废弃，工作区已删除~~ | ~~0~~ |

---

### 4.3 中等耦合页面 PR（3-6 个文件，含专属组件）

| PR | 页面 | 标题 | 文件数 |
|----|------|------|--------|
| PR-FE-12 | Message | `[studio] feat: add message query page` | 3 |
| PR-FE-13 | DlqMessage | `[studio] feat: add DLQ message page` | 3 |
| PR-FE-14 | MessageTrace | `[studio] feat: add message trace page` | 3 |
| PR-FE-15 | Acl | `[studio] feat: add ACL management page` | 4 |
| PR-FE-16 | Consumer | `[studio] feat: add consumer group list page` | 5 |

---

### 4.4 高耦合页面 PR（需拆分为子 PR）

#### Topic 页面（12 个文件）

| 子 PR | 标题 | 文件数 |
|-------|------|--------|
| PR-FE-17a | `[studio] feat: add topic page core` | 2 |
| PR-FE-17b | `[studio] feat: add topic dialog components` | 8 |
| PR-FE-17c | `[studio] feat: add topic modify and capability components` | 4 |

#### Dashboard + Home 页面（依赖 LLM 组件）

| 子 PR | 标题 | 文件数 |
|-------|------|--------|
| PR-FE-18a | `[studio] feat: add dashboard page core` | 2 |
| PR-FE-18b | `[studio] feat: add home page core` | 2 |

---

### 4.5 LLM 组件 PR（赛道三，需先发 Issue 讨论）

> ⚠️ 赛道二/三涉及底层架构，**必须先发 Issue 讨论方案**，不可直接提交 PR

| PR | 标题 | 文件数 |
|----|------|--------|
| PR-FE-LLM-1 | `[studio] feat: add LLM context providers` | 3 |
| PR-FE-LLM-2 | `[studio] feat: add LLM sidebar and overlay components` | 5 |
| PR-FE-LLM-3 | `[studio] feat: add LLM result display components` | 5 |
| PR-FE-LLM-4 | `[studio] feat: add LLM timeseries and dry-run components` | 3 |
| PR-FE-LLM-5 | `[studio] feat: add CommandBar and ChatMessage components` | 2 |

---

### 4.6 Store Context PR

| PR | 标题 | 文件数 |
|----|------|--------|
| PR-FE-CTX-1 | `[studio] feat: add ClusterCapabilities context` | 2 |
| PR-FE-CTX-2 | `[studio] feat: add LlmContext provider` | 1 |
| PR-FE-CTX-3 | `[studio] feat: add OperationEvent context` | 1 |

---

### 4.7 其他前端文件

| PR | 标题 | 文件数 |
|----|------|--------|
| PR-FE-MISC-1 | `[studio] feat: add architecture component and demo page` | 3 |
| PR-FE-MISC-2 | `[studio] feat: add remoteApi and test utilities` | 2 |
| PR-FE-MISC-3 | `[studio] feat: add theme styles and reportWebVitals` | 2 |

---

## 五、web/ 旧版前端改造 PR

> **状态说明**：web/ 模块的 16 个文件均为 **Modified**（已在 rocketmq-studio 存在，本次为增量修改）。
> 这些修改是在 PR #473-#490 已合并页面基础上的增强，包括 i18n 改造、API 对接和 UI 优化。
> `web/src/pages/studio/` 下的页面（Login/Ops/Producer/Alert/SslSettings/LlmSettings/BrokerCluster/GroupManagement/LiteTopic/Proxy）已通过 PR 合并，不在本次 diff 中。

#### PR-WEB-1：i18n 国际化改造
- **标题**: `[studio] feat: add i18n support for legacy web frontend`
- **文件数**: 6
- **变更类型**: Modified
- **文件**: `web/src/components/StatusBadge.tsx`, `web/src/constants/theme.ts`, `web/src/i18n/translations.ts`, `web/src/i18n/__tests__/LangContext.test.tsx`, `web/src/pages/cluster/index.tsx`, `web/src/pages/instance/consumer.tsx`

#### PR-WEB-2：API 对接与功能增强
- **标题**: `[studio] feat: align web API with real backend controllers`
- **文件数**: 5
- **变更类型**: Modified
- **文件**: `web/src/api/auth.ts`, `web/src/api/instance.ts`, `web/src/services/aclService.ts`, `web/src/services/consumerService.ts`, `web/src/utils/format.ts`

#### PR-WEB-3：UI 增强与样式优化
- **标题**: `[studio] feat: enhance web UI components and styles`
- **文件数**: 5
- **变更类型**: Modified
- **文件**: `web/.env.production`, `web/src/components/MiniLine.tsx`, `web/src/index.css`, `web/src/pages/home/index.tsx`, `web/src/pages/instance/index.tsx`

---

## 六、rocketmq-dashboard-cli/ PR

#### PR-CLI-1：CLI 核心框架与命令
- **标题**: `[studio] feat: add CLI core framework and command definitions`
- **文件数**: 10
- **文件**: `rocketmq-dashboard-cli/pom.xml`, `rocketmq-dashboard-cli/src/main/java/.../cli/RmqctlCommand.java`, `rocketmq-dashboard-cli/src/main/java/.../cli/GlobalOptions.java`, `rocketmq-dashboard-cli/src/main/java/.../cli/GenerateCompletion.java`, `rocketmq-dashboard-cli/src/main/java/.../cli/TopicCommand.java`, `rocketmq-dashboard-cli/src/main/java/.../cli/ClusterCommand.java`, `rocketmq-dashboard-cli/src/main/java/.../cli/ConsumerCommand.java`, `rocketmq-dashboard-cli/src/main/java/.../cli/BrokerCommand.java`, `rocketmq-dashboard-cli/src/main/java/.../cli/MessageCommand.java`, `rocketmq-dashboard-cli/src/main/java/.../cli/AclCommand.java`

#### PR-CLI-2：CLI 扩展命令
- **标题**: `[studio] feat: add CLI extended commands (config, metrics, namespace, etc.)`
- **文件数**: 7
- **文件**: `rocketmq-dashboard-cli/src/main/java/.../cli/ConfigCommand.java`, `rocketmq-dashboard-cli/src/main/java/.../cli/MetricsCommand.java`, `rocketmq-dashboard-cli/src/main/java/.../cli/NamespaceCommand.java`, `rocketmq-dashboard-cli/src/main/java/.../cli/ClientCommand.java`, `rocketmq-dashboard-cli/src/main/java/.../cli/ExplainCommand.java`, `rocketmq-dashboard-cli/src/main/java/.../cli/context/AdminClientHelper.java`, `rocketmq-dashboard-cli/src/main/java/.../cli/context/CliConfig.java`, `rocketmq-dashboard-cli/src/main/java/.../cli/context/CliContext.java`

#### PR-CLI-3：CLI 输出与 Schema
- **标题**: `[studio] feat: add CLI output formatting and tool schema`
- **文件数**: 8
- **文件**: `rocketmq-dashboard-cli/src/main/java/.../cli/output/ErrorModel.java`, `rocketmq-dashboard-cli/src/main/java/.../cli/output/OutputFormatter.java`, `rocketmq-dashboard-cli/src/main/java/.../cli/schema/ParamSchema.java`, `rocketmq-dashboard-cli/src/main/java/.../cli/schema/RiskLevel.java`, `rocketmq-dashboard-cli/src/main/java/.../cli/schema/ToolDefinition.java`, `rocketmq-dashboard-cli/src/main/java/.../cli/schema/ToolRegistry.java`, `rocketmq-dashboard-cli/src/main/java/.../cli/security/AuditLogger.java`, `rocketmq-dashboard-cli/src/main/java/.../cli/security/DryRunResult.java`

#### PR-CLI-4：CLI 测试与 Native Image 配置
- **标题**: `[studio] test: add CLI unit tests and native image configuration`
- **文件数**: 10
- **文件**: `rocketmq-dashboard-cli/README.md`, `rocketmq-dashboard-cli/dependency-reduced-pom.xml`, `rocketmq-dashboard-cli/src/main/resources/META-INF/native-image/...`, `rocketmq-dashboard-cli/src/test/java/.../cli/AbstractCliTest.java`, `rocketmq-dashboard-cli/src/test/java/.../cli/ClusterCommandTest.java`, `rocketmq-dashboard-cli/src/test/java/.../cli/CommandClassesTest.java`, `rocketmq-dashboard-cli/src/test/java/.../cli/ConfigCommandTest.java`, `rocketmq-dashboard-cli/src/test/java/.../cli/ExplainCommandTest.java`, `rocketmq-dashboard-cli/src/test/java/.../cli/GenerateCompletionTest.java`, `rocketmq-dashboard-cli/src/test/java/.../cli/RmqctlCommandTest.java`

#### PR-CLI-5：CLI 测试（续）
- **标题**: `[studio] test: add CLI context, output and schema tests`
- **文件数**: 9
- **文件**: `rocketmq-dashboard-cli/src/test/java/.../cli/context/CliConfigTest.java`, `rocketmq-dashboard-cli/src/test/java/.../cli/context/CliContextTest.java`, `rocketmq-dashboard-cli/src/test/java/.../cli/output/ErrorModelTest.java`, `rocketmq-dashboard-cli/src/test/java/.../cli/output/OutputFormatterTest.java`, `rocketmq-dashboard-cli/src/test/java/.../cli/schema/ParamSchemaTest.java`, `rocketmq-dashboard-cli/src/test/java/.../cli/schema/RiskLevelTest.java`, `rocketmq-dashboard-cli/src/test/java/.../cli/schema/ToolDefinitionTest.java`, `rocketmq-dashboard-cli/src/test/java/.../cli/schema/ToolRegistryTest.java`, `rocketmq-dashboard-cli/src/test/java/.../cli/security/AuditLoggerTest.java`, `rocketmq-dashboard-cli/src/test/java/.../cli/security/DryRunResultTest.java`

---

## 七、rocketmq-dashboard-mcp/ PR

#### PR-MCP-1：MCP 核心框架
- **标题**: `[studio] feat: add MCP server core framework and protocol handler`
- **文件数**: 7
- **文件**: `rocketmq-dashboard-mcp/pom.xml`, `rocketmq-dashboard-mcp/src/main/java/.../mcp/McpProtocolHandler.java`, `rocketmq-dashboard-mcp/src/main/java/.../mcp/McpServerApplication.java`, `rocketmq-dashboard-mcp/src/main/java/.../mcp/McpServerConfig.java`, `rocketmq-dashboard-mcp/src/main/java/.../mcp/config/SecurityConfig.java`, `rocketmq-dashboard-mcp/src/main/java/.../mcp/resources/ResourceProvider.java`, `rocketmq-dashboard-mcp/src/main/resources/application.yml`

#### PR-MCP-2：MCP 工具与安全
- **标题**: `[studio] feat: add MCP tool registry and security gate`
- **文件数**: 5
- **文件**: `rocketmq-dashboard-mcp/src/main/java/.../mcp/tools/McpToolRegistry.java`, `rocketmq-dashboard-mcp/src/main/java/.../mcp/tools/SecurityCheckResult.java`, `rocketmq-dashboard-mcp/src/main/java/.../mcp/tools/SecurityGate.java`, `rocketmq-dashboard-mcp/src/main/java/.../mcp/transport/McpMessageHandler.java`, `rocketmq-dashboard-mcp/src/main/java/.../mcp/transport/McpTransport.java`

#### PR-MCP-3：MCP 传输层与测试
- **标题**: `[studio] feat: add MCP transport layer and unit tests`
- **文件数**: 10
- **文件**: `rocketmq-dashboard-mcp/src/main/java/.../mcp/transport/SseTransport.java`, `rocketmq-dashboard-mcp/src/main/java/.../mcp/transport/StdioTransport.java`, `rocketmq-dashboard-mcp/src/test/java/.../mcp/McpProtocolHandlerTest.java`, `rocketmq-dashboard-mcp/src/test/java/.../mcp/McpServerApplicationTest.java`, `rocketmq-dashboard-mcp/src/test/java/.../mcp/McpServerTest.java`, `rocketmq-dashboard-mcp/src/test/java/.../mcp/resources/ResourceProviderTest.java`, `rocketmq-dashboard-mcp/src/test/java/.../mcp/tools/McpToolRegistryExtTest.java`, `rocketmq-dashboard-mcp/src/test/java/.../mcp/tools/SecurityGateTest.java`, `rocketmq-dashboard-mcp/src/test/java/.../mcp/transport/SseTransportTest.java`, `rocketmq-dashboard-mcp/README.md`

---

## 八、rocketmq-dashboard-llm/ PR

#### PR-LLM-1：LLM 核心服务
- **标题**: `[studio] feat: add LLM proxy service and configuration`
- **文件数**: 8
- **文件**: `rocketmq-dashboard-llm/pom.xml`, `rocketmq-dashboard-llm/src/main/java/.../llm/LlmApplication.java`, `rocketmq-dashboard-llm/src/main/java/.../llm/LlmConfig.java`, `rocketmq-dashboard-llm/src/main/java/.../llm/LlmProxyService.java`, `rocketmq-dashboard-llm/src/main/java/.../llm/LlmAuditLogger.java`, `rocketmq-dashboard-llm/src/main/java/.../llm/DashboardApiClient.java`, `rocketmq-dashboard-llm/src/main/java/.../llm/ClusterCapability.java`, `rocketmq-dashboard-llm/src/main/resources/application.yml`

#### PR-LLM-2：LLM 控制器与安全
- **标题**: `[studio] feat: add LLM MCP bridge controller and security config`
- **文件数**: 4
- **文件**: `rocketmq-dashboard-llm/src/main/java/.../llm/McpBridgeController.java`, `rocketmq-dashboard-llm/src/main/java/.../llm/ToolFilter.java`, `rocketmq-dashboard-llm/src/main/java/.../llm/config/SecurityConfig.java`, `rocketmq-dashboard-llm/README.md`

#### PR-LLM-3：LLM 测试
- **标题**: `[studio] test: add LLM service unit tests`
- **文件数**: 8
- **文件**: `rocketmq-dashboard-llm/src/test/java/.../llm/DashboardApiClientTest.java`, `rocketmq-dashboard-llm/src/test/java/.../llm/LlmAuditLoggerTest.java`, `rocketmq-dashboard-llm/src/test/java/.../llm/LlmConfigTest.java`, `rocketmq-dashboard-llm/src/test/java/.../llm/LlmProxyServiceTest.java`, `rocketmq-dashboard-llm/src/test/java/.../llm/McpBridgeControllerPrivateTest.java`, `rocketmq-dashboard-llm/src/test/java/.../llm/McpBridgeControllerTest.java`, `rocketmq-dashboard-llm/src/test/java/.../llm/MultiTurnChatTest.java`, `rocketmq-dashboard-llm/src/test/java/.../llm/ToolFilterTest.java`

---

## 九、rocketmq-dashboard-app/ PR

#### PR-APP-1：App 模块
- **标题**: `[studio] feat: add app module pom and readme`
- **文件数**: 2
- **文件**: `rocketmq-dashboard-app/pom.xml`, `rocketmq-dashboard-app/README.md`

---

## 十、LICENSE 变更

#### PR-LICENSE-1：许可证更新
- **标题**: `[studio] chore: update LICENSE file`
- **文件数**: 1
- **文件**: `LICENSE`

---

## 提交顺序建议

```
阶段1: 基础设施（无业务依赖，最先提交）
  PR-INFRA-1 → PR-INFRA-2 → PR-INFRA-3 → PR-INFRA-4 → PR-INFRA-5 → PR-INFRA-6
  PR-LICENSE-1

阶段2: 后端核心 — 模型层（被其他层依赖）
  PR-BE-7~14 (Model 层，可并行)

阶段3: 后端核心 — 配置与架构（被 Service/Controller 依赖）
  PR-BE-1 → PR-BE-2 → PR-BE-3 → PR-BE-4 → PR-BE-5 → PR-BE-6

阶段4: 后端核心 — Service 层（被 Controller 依赖）
  PR-BE-20~28 (Service 接口 + 实现)

阶段5: 后端核心 — Controller 层 + 支撑组件
  PR-BE-15~19 (Controller)
  PR-BE-29~35 (Skill/Task/Util/Support/Interceptor)

阶段6: 后端测试
  PR-BE-36~40 (Test)

阶段7: Studio Server
  PR-SERVER-1

阶段8: 前端基础设施
  PR-FE-0a~0f

阶段9: 前端页面（赛道一，可并行）
  PR-FE-1~11 (低耦合页面)
  PR-FE-12~16 (中等耦合页面)
  PR-FE-17a~c, PR-FE-18a~b (高耦合页面)

阶段10: 前端 LLM 组件（赛道三，需先 Issue 讨论）
  PR-FE-CTX-1~3 → PR-FE-LLM-1~5

阶段11: 旧版前端改造
  PR-WEB-1~3

阶段12: CLI 模块
  PR-CLI-1~5

阶段13: MCP 模块
  PR-MCP-1~3

阶段14: LLM 模块
  PR-LLM-1~3

阶段15: App 模块
  PR-APP-1
```

---

## 自查清单（每次提交前必检）

- [ ] PR 标题以 `[studio]` 开头
- [ ] 修改文件数控制在 5-10 个以内（文档/配置类可适当放宽）
- [ ] 赛道一改动仅聚焦于单个页面的功能完整性
- [ ] 赛道二/三已提前发起 Issue 并获得社区确认
- [ ] 已补充必要的单元测试 / E2E 测试
- [ ] 每个 PR 可独立编译通过（不依赖未合并的 PR）
- [ ] 每个 PR 可独立回滚（不影响已合并的 PR）