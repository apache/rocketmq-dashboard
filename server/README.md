# RocketMQ Studio Server — 后端包结构

Spring Boot 3.5 / Java 21 / MyBatis-Plus 单体后端，包根 `org.apache.rocketmq.studio`，
入口 `StudioApplication`。源码按业务域划包，本文档描述各包职责与分层约定。

## 顶层包总览

```
org.apache.rocketmq.studio
├── StudioApplication          # Spring Boot 入口
├── auth                       # 登录认证与会话
├── audit                      # 操作审计
├── cluster                    # 集群域：拓扑/集群/Proxy/NameServer/指标/K8s
├── common                     # 公共基础：domain/exception/util/config
├── instance                   # 实例域：实例注册 + Topic/Group/消息/DLQ/ACL/查询历史
├── model                      # 遗留共享模型（admin 客户端交互对象）
├── ops                        # 运维域：AI 助手/告警/审计/Dashboard
├── persistence                # MyBatis-Plus 实体与 Mapper
├── provider                   # 多厂商 SPI、实现与云凭据（apache/alibaba/tencent/credential）
└── settings                   # 通用设置与数据源
```

## 各包职责

### auth — 认证
登录/登出、`AuthInterceptor`（读者/管理员两级权限，凭据 reveal 类 GET 接口强制 admin）、
`AuthenticatedUserContext` 会话上下文。

### cluster — 集群域
| 子包 | 职责 |
|---|---|
| `cluster.broker` | 集群拓扑、Broker 配置读写、`MqAdminExtFactory`（按 namesrv 地址缓存 admin 客户端）、`RuntimeAdminClientResolver`（按实例 endpoint 解析） |
| `cluster.nameserver` | NameServer 地址注册表 CRUD |
| `cluster.proxy` | Proxy 节点管理（列表/重启/地址维护、兼容旧 `.do` 路径） |
| `cluster.client` | 客户端连接信息 |
| `cluster.metrics` | Prometheus 指标接入（多数据源、健康检查），`metrics.grafana` 子包提供 Grafana 面板嵌入 |
| `cluster.k8s` | K8s 证书管理 |
| `cluster.config` | Broker 配置更新 DTO/VO |

### common — 公共基础
- `common.domain`：`Result<T>` 统一响应包装、`PageResult`、`BaseEntity`（id/gmtCreate/gmtModified）、
  `DeleteRequestDTO`（通用删除入参）、`enums/`（InstanceType、InstanceVendor、TopicType 等枚举）
- `common.exception`：`BusinessException(code, msg)` + `GlobalExceptionHandler`（统一转 `Result`）
- `common.util`：`CredentialUtils` 等共享工具
- `common.config`：CORS / Web MVC 配置

### instance — 实例域（核心业务）
实例注册与实例维度的资源管理，REST 基路径 `/api/instances`、`/api/topics`、`/api/groups`、
`/api/messages`、`/api/dlq`、`/api/instance-acl`：

| 子包 | 职责 |
|---|---|
| `instance`（顶层） | 实例 CRUD；APACHE 实例手填 endpoint，ALIYUN/TENCENT 实例经对应云目录选择 |
| `instance.topic` | Topic CRUD/路由/订阅者、`MetadataService`（按 instanceId 路由到 provider）、消息发送、LiteTopic 子系统 |
| `instance.group` | 消费组 CRUD/进度/订阅/重置位点/诊断栈 |
| `instance.message` | 消息查询与轨迹（`MessageProvider` SPI）、查询历史（与实例绑定，随实例上下文记录/回放） |
| `instance.dlq` | 死信队列列表与重发（`DLQProvider` SPI） |
| `instance.acl` | 实例 ACL 规则与用户（凭据打码/reveal 的先例实现） |

### provider — 多厂商 SPI
统一实例操作抽象，按实例 vendor 路由：
- `InstanceProvider`：topic/group CRUD、进度、订阅、重置位点、消息查询、轨迹（首参均为 Studio 实例 id）
- `CloudCatalogProvider`：云目录发现（列 region / 列云实例 / 实例详情）
- `InstanceProviderRegistry`：vendor → provider 路由，`byInstanceId` 查实例分发
- `provider.apache`：开源实现层——`MetadataProvider`/`AdminClient` 接口为本包内部 SPI，
  `RocketMQMetadataProvider`/`RocketMQAdminClientImpl` 经 `MqAdminExtFactory`（按 namesrv 地址缓存、
  懒建连接）执行 admin 操作；`ApacheInstanceProvider` 委托这些 bean，开源行为零差异；
  `RocketMQMessage/DLQ/Client/Cluster/DashboardProvider` 与 Broker 配置服务也在本包
- `provider.alibaba`：阿里云 RocketMQ 5.x OpenAPI 完整实现（`AliyunClientFactory` 按
  credential#region 缓存 AsyncClient、异常统一映射、`AliyunConverters` 集中模型转换、
  `/api/cloud/aliyun/*` 目录端点）
- `provider.tencent`：腾讯云 RocketMQ 5.x Trocket OpenAPI 实现；`TencentCatalogService` 提供
  region/实例目录，`TencentInstanceProvider` 提供 Topic、消费组、消息查询与轨迹能力，
  `TencentAclService` 提供 ACL 管理，目录端点位于 `/api/cloud/tencent/*`
- `provider.credential`：云厂商凭据管理（`rmq_cloud_credential` 表 CRUD，`/api/cloud-credentials`）：
  vendor+access_key 唯一键，SK base64 存储，列表打码 + `/{id}/credentials` reveal 接口，
  编解码与打码统一走 `common.util.CredentialUtils`

### settings / audit
通用设置（`rmq_settings`）、外部数据源配置与连通性测试、写操作审计。

## 分层与命名约定

- **Controller → Service → Repository**：Controller 只做入参校验与 `Result` 包装；
  Repository 接口 + `MybatisPlus*` 实现（含 entity↔VO 映射）
- **入参/出参**：写操作入参统一 `Create/Update/Delete*DTO`（jakarta validation），
  出参统一 `*VO`；禁止写接口直接收 VO
- **异常**：`BusinessException(400, msg)` 裸数字码（400/401/403/404/409/500/501/502/503/504），
  `GlobalExceptionHandler` 统一转 `Result`
- **返回值**：REST 一律 `Result<T>` 包装
- **Lombok**：POJO `@Data`/`@Builder`/`@NoArgsConstructor`/`@AllArgsConstructor`，
  服务 `@RequiredArgsConstructor`，日志 `@Slf4j`
- **SPI 模式**：跨实现的能力（消息、DLQ、诊断、Dashboard、多厂商）定义 Provider 接口，
  开源实现统一放 `provider/apache/`（admin 客户端经 `MqAdminExtFactory` 单轨获取），
  云厂商实现放 `provider/<vendor>/` 保持高内聚
- **敏感字段**：VO 上 `@ToString.Exclude`；存储 base64（见 `CredentialUtils`）；
  列表打码、reveal 接口 admin-only
- **实例标识**：`rmq_instance.id` 是数据库内部使用的自增 `BIGINT` 主键，`name` 是带唯一约束、
  创建后不可变的人类可读外部标识。REST 的 `instanceId` 参数应通过
  `InstanceRepository#findByIdentifier` 或 `InstanceService#resolveInstanceId` 解析：先按唯一名称查找，
  再兼容数字主键。Topic、消费组和数据源绑定等现有字符串关联保存实例名称；新增代码应区分
  内部数字主键与外部实例名称，不要假定 `name` 是 `rmq_instance` 的数据库主键
- **测试命名**：测试类使用 Maven Surefire 可发现的 `*Test` 命名；测试方法使用能描述行为的
  camelCase 名称，且统一以 `Test` 结尾（如 `shouldReturnPageResultTest`）
- **checkstyle**：validate 阶段强制，禁止中文字符，Java 代码注释一律用英文

## 构建与测试

```bash
cd server
mvn -B -ntp package -DskipTests      # 构建（checkstyle 在 validate 阶段强制）
mvn -B -ntp test                      # 运行全部测试
mvn -B -ntp -Dtest=AuthServiceTest test  # 运行指定测试类；可替换为目标类名
```

依赖纪律：RocketMQ 系依赖（`org.apache.rocketmq:*`）只用 Apache 开源版本（当前基线 5.5.0），
禁止内部/商业版本号；禁用 `com.aliyun.openservices:ons-client`，客户端收发用开源
`rocketmq-client`；云厂商管控面走 OpenAPI SDK（`alibabacloud-rocketmq20220801` /
`tencentcloud-sdk-java-trocket`）。
