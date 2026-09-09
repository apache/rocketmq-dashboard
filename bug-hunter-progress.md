# Bug Hunter Progress

## 环境
- 仓库: apache/rocketmq-dashboard (fork: unbridled-41/rocketmq-studio 线)
- 工作主线分支: `rocketmq-studio`（PR base；master 为无关的旧版 dashboard 代码）
- 工具链: Java 21 + Maven（server/），Node 24 + Vite/Vitest（web/）
- CI: .github/workflows/ci.yml — 后端 `mvn -B -ntp clean package` + 测试；前端 vitest
- 已合并 PR（本账号，勿重复）:
  - #3112 fix(dlq): enforce broker topology guard on the selected DLQ msgId lookup
  - #2835 fix(dlq): validate the resend target topic before dispatching messages
  - #2833 fix(message): enforce broker topology guard on the primary msgId lookup path
  - 更早: #2752, #2724, #2697, #2670, #2676 等（见 git log）

## 基线
- 后端 `mvn test`: （运行中）
- 前端 `npm ci && npm test`: （运行中）

## 候选记录
| # | 区域 | BUG | EVIDENCE | ROOT_CAUSE | PRIORITY | FIX_CONFIDENCE | 结论 |
|---|------|-----|----------|-----------|----------|----------------|------|
| 1 | ops/alert (#3104) | 跨指标样本使缺失指标的告警永久 FIRING | 回归测试红灯：同标签 consumer.lag.total 样本使 consumer.delay.seconds 规则未 RESOLVED | reconcileMissingActiveStates 构建 presentKeys 未校验 metric 相等 | 83 | 92 | ✅ PR #3145 (draft) |
| 2 | web/cluster (#3103) | 注册表 Broker 配置写错实例 | 代码证实：config preview/update/diff 用路由实例 ref；新增跨实例/无映射/歧义 3 测试均红灯 | 注册表行未携带 source instance，动作沿用路由实例 | 83 | 82 | ✅ PR #3151 (draft) |
| 3 | web/cluster | Broker diff 弹窗无请求代际守卫：关闭后自重开、慢响应覆盖新集群数据 | 克隆 NS 变体既有回归测试模式，2 个新测试红灯 | openBrokerConfigDiff 缺少 nsConfigDiffRequestRef 同款守卫（孪生缺陷） | 71 | 92 | ✅ PR #3154 (draft) |
| 4 | web/message | 轨迹 Tab：空输入查询使 in-flight load 的 finally 失效，spinner 永久卡死且错误被吞 | 新回归测试红灯（错误文本被 loading 优先级吞掉） | runTraceQuery 先 bump generation 再校验空输入，空分支未重置 traceLoading | 72 | 88 | ✅ PR #3156 (draft) |
| 5 | web/topic | 搜索词带空格时客户端再过滤清空表格（服务端已 trim，行 178 未 trim） | 探索代理定位 topic.tsx:178，未复写测试 | fetch 用 trim 后值，visibleTopics 用原始值 | ~73 | ~90 | 未实现（已达 4 个目标，留档） |
| 6 | ops/alert export (#2651) | 导出在结果收缩后死循环 | 当前代码已有空页 break + total min + MAX_PAGES | - | - | - | 已修复，放弃 |
| 7 | ops/deliveries (#2646) | 重试完成后用旧 filters 刷新 | 当前代码用 refreshNonce + current filters | - | - | - | 已修复，放弃 |
| 8 | studio/UserManagement (#2739) | 用户状态切换重叠乱序 | 当前代码有 mutatingUserIdsRef per-user guard | - | - | - | 已修复，放弃 |
| 9 | cluster (#2738) | diskUsage 显示为分数 | RocketMQClusterProvider 已 *100D | - | - | - | 已修复，放弃 |
| 10 | studio/LiteTopic (#2889) | TTL 扩展可重复提交 | handleExtendTTL 无 in-flight ref guard | 双击竞态 | ~55 | 90 | 评分低于 70，仅记录 |

## 已检查并放弃的区域
- #2652（TPS 转换）已被合并 PR #2656 修复
- #3040 针对经典 dashboard（master 分支），近期全部 PR base 均为 rocketmq-studio，未处理
- frontend 探索代理复核后放弃：alerts/systemAlerts/handleConfigSubmit 双击窗口（antd loading 一 commit 内禁用，无可靠触发路径）；consumer 模式过滤（API 限制）；i18n t() 重复占位符（现无触发字符串）

## 验证记录
- 后端基线（pristine rocketmq-studio）：2035 tests，3 个预存失败（AuthCorsIntegrationTest x2、AliyunInstanceProviderTest.getGroupProgressShouldMapLagRowsTest）——与本次修改无关
- #3145：NativeAlert*Test 40/40 通过（新测试先红后绿）；alert 包全量通过
- #3151：ClusterPage 25/25、cluster+i18n 67/67 通过（3 个新测试经 stash 源码验证先红）；tsc/eslint 干净
- #3154：ClusterPage 24/24 通过（2 个新测试先红后绿）；tsc/eslint 干净
- #3156：message 两套测试 28/28 通过（新测试先红后绿）；tsc/eslint 干净
- CI：upstream rocketmq-studio 分支近期 CI 全部 startup_failure（工作流未启动，与本次 PR 无关）；4 个 PR 均 MERGEABLE、draft 状态

## 最终状态（2026-09-05）
4 个独立分支 × 各 1 commit × 各 1 Draft PR，全部 base=rocketmq-studio，未合并：
- #3145 fix(alert): require metric equality when reconciling native alert presence → Fixes #3104
- #3151 fix(cluster): route registry config actions through the row's owning instance → Fixes #3103
- #3154 fix(cluster): discard superseded Broker config diff responses
- #3156 fix(message): keep the trace tab responsive after an empty trace query

Issue 认领留言（应要求补充）：
- #3104 → https://github.com/apache/rocketmq-dashboard/issues/3104#issuecomment-5543278812 （指向 PR #3145）
- #3103 → https://github.com/apache/rocketmq-dashboard/issues/3103#issuecomment-5543281478 （指向 PR #3151，并交叉引用 #3154 的独立修复）
- #3154/#3156 为代码走查发现，无对应 Issue，未替其新建 Issue

## 补充验证（2026-09-05，应用户要求补全证据链；仅补 comment，未制造 commit）
- 后端（#3145 分支）全量 `mvn test`：2036 tests（基线 2035 + 1 新回归），3 失败与 pristine 基线逐字相同（AuthCorsIntegrationTest ×2、AliyunInstanceProviderTest ×1）——零新增失败；编译覆盖 backend-build job。
- web pristine 基线：默认 20s 超时全量两次运行失败集不同（9 失败/4 文件、15/7）→ 全量套件在本沙箱有负载脆弱；60s 超时基线 3 失败（AclPage、NotificationDeliveries、ConsumerPage），但隔离运行 pristine 上 53/53 全过 → 全部为负载/顺序脆弱，无确定性失败。
- 三个前端分支各跑 60s 超时全量 + `npm run build`：
  - #3151：925 tests（+3 新），3 失败均为未触碰文件（Topic/Consumer），隔离 50/50 + 24/24 全过；build ✓
  - #3154：924 tests（+2 新），1 失败为 ConsumerPage 已证实脆弱用例；build ✓
  - #3156：923 tests（+1 新），1 失败为同一 ConsumerPage 用例；build ✓
- 4 个 PR 各补一条完整证据链 comment（红灯实际输出、模块/全量/构建结果、基线对照、CI 不可用原因与佐证）：
  - #3145 → issuecomment-5543774707
  - #3151 → issuecomment-5543778581
  - #3154 → issuecomment-5543783663
  - #3156 → issuecomment-5543785810
- 结论：4 个 PR 的代码与测试均已充分，无缺测试或需改码之处；缺的只是 CI 无法运行情况下的验证记录，已全部补齐。

## Issue 建档（2026-09-05，应用户要求为每个 PR 建立对应 Issue）
- #3145 → 已有 Issue #3104（open），不重复创建；PR 正文已有 `Fixes #3104`
- #3151 → 已有 Issue #3103（open），不重复创建；PR 正文已有 `Fixes #3103`
- #3154 → 新建 Issue #3292（查重：#2729 closed 仅覆盖 NameServer diff+连接测试；#2915 是失败详情；#3103 是写错实例）；PR 描述已加 `Related to #3292`
- #3156 → 新建 Issue #3293（查重：#734 closed 是代际守卫的 reset/切消息场景；#1316 closed 是后端 400 校验）；PR 描述已加 `Related to #3293`
- 均为 Bug 类型，遵循 ISSUE_TEMPLATE.md（该 tracker 仅收 bug/feature）；未加标签/负责人/里程碑
- 注：`gh pr edit` 因仓库 Projects(classic) GraphQL 弃用报错，改用 REST PATCH /pulls 编辑成功

## 状态变更（2026-09-05，应用户要求）
- 4 个 PR 已全部由 Draft 标记为 Ready for review（gh pr ready；已验证 draft=false、state=OPEN）；仍未合并任何 PR

# 第二轮（2026-09-05 下午，目标：再 4 个新 Bug）

## 候选记录（第二轮）
| # | 区域 | BUG | EVIDENCE | ROOT_CAUSE | PRIORITY | FIX_CONFIDENCE | 结论 |
|---|------|-----|----------|-----------|----------|----------------|------|
| N1 | web MetricsExplorer | 共享 requestId 被 loadAll/runCustomQuery 双写：点"刷新全部"（或切数据源、加载中跑自定义查询）同步自增两次 → loadAll 所有结果守卫失效 → 全部面板永久 loading，刷新钮永久转圈 | 刷新 handler 764-769 行先 loadAll（bump N）再 runCustomQuery（bump N+1）；490 行先置 loading；495/502/565/569 行守卫永假；100% 复现 | loadAll 与 runCustomQuery 两个独立 state 域共用一个单调计数器 | 79 | 92 | ✅ 采纳 |
| N2 | server RocketMQMessageProvider | 按 key 查消息 0 命中返回 502（真实 client queryMessage 空结果抛 MQClientException(208 NO_MESSAGE)）；trace 同理：无轨迹数据 → 502 | queryByKey catch-all → 502；isTraceTopicAbsent 仅评 TOPIC_NOT_EXIST；代码自注释"no business data → return empty"却未评 208；upstream #1161/#1275 明确期望 no match → empty | NO_MESSAGE(208) 未纳入异常分级 | 78 | 92 | ✅ 采纳 |
| N3 | web instance/acl | 用户新建/删除只改本地数组，不刷新 userTotal 也不 refetch（rules tab 有 ruleRefreshKey）：总数错、满页时最后一行被挤出、末页删空后永久空页 | acl.tsx:426/441 无 setUserTotal 无 refetch；对照 330/343 行 rules 用 refreshKey | 列表 mutation 未触发与 rules 一致的刷新 | 72 | 88 | ✅ 采纳 |
| N4 | web instance/topic | fetch 用 trim 后 search（433 行），客户端再过滤用原始值（484→178 行）：带首尾空格的搜索词使表格清空 | 前轮遗留候选；upstream #911（consumer group search 不 trim）同类已修，佐证项目认可该类缺陷 | 两处 search 语义不一致 | 72 | 90 | ✅ 采纳 |
| N5 | server LlmConfigService | saveConfig 重建 VO 漏 dingtalkWebhook/smsWebhook/emailRecipients → 存 LLM 配置抹掉通知配置 | 代码证实 | - | - | - | ❌ 放弃：开放 PR #2863 已修（preserve unmanaged settings fields on partial saves） |
| N6 | web MetricsExplorer | 实例切换 effect 用 RANGE_OPTIONS[0] 重载但不同步 rangeId → 选择器显示 24h 实际查 1h | 528 行 | - | ~73 | 85 | 留档（与 N1 同文件，避免自冲突） |
| N7 | web instance/dlq | 实例切换未清 resendInFlightRef → 旧请求结算前(≤30s)新实例重投/重发静默无效 | 230-232 行 | - | ~66 | 82 | 低于 70 门槛，仅记录 |
| N8 | web instance/consumer | settings 加载失败后 settingsGroup 已置名，tab 切换守卫阻止重试，仅关弹窗可恢复 | 493/513 行；onCancel 1562 行重置 | - | ~66 | 85 | 低于 70 门槛，仅记录 |
| N9 | web studio/BrokerCluster | 语言切换重置所选实例（t 身份变化重跑 effect；Producer 有保护模式可对照） | 236 行 | - | ~68 | 85 | 低于 70 门槛，仅记录 |
| N10 | server SettingsService | data-sources page cache key 含用户输入 search，无界 ConcurrentMapCache（#2650 引入） | 221-232 行 | - | ~65 | 70 | 低于门槛，仅记录 |

## 第二轮实施记录（2026-09-05）
4 个独立分支 × 各 1 commit × 各 1 PR，全部 base=rocketmq-studio，MERGEABLE，未合并：
- **#3299** fix(metrics): give profile panels and the custom query independent request guards — 分支 fix/metrics-explorer-split-request-guards（commit efe6e963）
  - 新回归先红（面板图表永不恢复）后绿：MetricsExplorer 20/20；全量 web 923 tests 1 失败=ConsumerPage 已知脆弱文件（隔离 29/29 过）；build ✓ tsc/eslint ✓
- **#3300** fix(acl): reload the user page after creating or deleting a user — 分支 fix/acl-user-mutation-refresh（commit 48d88c9f）
  - 2 个新回归先红（pageAclUsers 仅 1 次调用）后绿：AclPage 23/23；全量 web 两轮，末轮 924 tests 1 失败=ConsumerPage 已知脆弱；build ✓ tsc/eslint ✓
- **#3301** fix(topic): search topics with the trimmed term on the client too — 分支 fix/topic-search-trim-filter（commit 3d1224ec）
  - 新回归先红（" orders " 表格清空）后绿：TopicPage 22/22；全量 web 923 tests 1 失败=ConsumerPage 已知脆弱；build ✓ tsc/eslint ✓
- **#3302** fix(message): treat MQClientException NO_MESSAGE key queries as empty results — 分支 fix/message-key-query-no-match（commit dd2dd70c）
  - 3 个新回归先红（生产同款 CODE: 208 502 错误）后绿：RocketMQMessageProviderTest 39/39、相关模块 299/299；全量后端 mvn test 2038 tests（基线 2035+3 新），3 失败与 pristine 基线逐字相同（AuthCors ×2、Aliyun ×1）零新增失败
  - 客户端行为证据：rocketmq-client 5.5.0 MQAdminImpl.queryMessage 字节码证实无命中即抛 MQClientException(208)
- 全部 4 个 PR 附完整证据链 comment（红灯输出、套件对照、CI startup_failure 佐证）：
  - #3299 → issuecomment-5550558019；#3300 → issuecomment-5550559377；#3301 → issuecomment-5550559522；#3302 → issuecomment-5550558179
- CI：4 个分支及他人分支均 startup_failure（upstream 工作流未启动，与改动无关）；4 PR 均 MERGEABLE、非 draft、未合并

## 第二轮最终状态（2026-09-05）
本轮新增 4 个 PR：#3299、#3300、#3301、#3302（加首轮 4 个：#3145、#3151、#3154、#3156）。均已推送、建档、留证，未做任何合并操作。工作区已回到 fix/topic-search-trim-filter 分支，临时 worktree 已清理。

## PR 证据链逐个审计（2026-09-05，应用户要求；缺什么补什么）
- 首轮 4 个（#3145/#3151/#3154/#3156）：正文五要素齐全（Problem/Evidence、Root cause/Fix、Priority & scoring、Tests 含实际数字、Risk），各有一条完整 Verification evidence 评论（CI startup_failure 佐证、红灯实际输出、模块/全量/构建结果、基线对照）→ **已充分，未制造任何新 commit/comment**。
- 第二轮 4 个（#3299–#3302）：发现正文缺陷——开头残留 "# PR N —" 拆稿标签行、#3299 末尾混入 PR2 残段（分稿脚本 head -32 越界所致）、Tests 节的 "see comment below" 未内联实际全量结果 → 用 REST PATCH /pulls 更新 4 个正文：去掉标签行与残段，并内联各自的全量套件结果（含脆弱文件说明）、build/tsc/eslint 结果与 CI 不可用说明，正文现已自洽；既有证据 comment 不变。
- 修复代码本身无问题（全部先红后绿），未改任何代码、未新增 commit。

## 第二轮 PR ↔ Issue 建档（2026-09-05，应用户要求；均为 Bug 类型）
查重：按各自关键词搜索开放+已关闭 Issue，均无重复；#2680（Topic/Consumer 删除后刷新）、#911（consumer group 搜索 trim）、#1161/#1275（空结果语义）为同类先例，已在新 Issue 的 Related work 中如实引用。
- #3299 → Issue #3304（MetricsExplorer 刷新冻结；查重 metrics refresh/custom query panel/spinner 均空）
- #3300 → Issue #3306（ACL 用户列表 create/delete 不刷新；查重 acl user pagination/delete/total——#2582/#2297/#2680 范围不同）
- #3301 → Issue #3307（topic 搜索空格清空表格；查重 topic search trim/trim whitespace——仅 #911 同类不同页）
- #3302 → Issue #3305（NO_MESSAGE 502；查重 no message 208/key query empty 均空；#1161/#1275 为语义先例）
- 4 个 PR 正文顶部均已加 `Related to #NNNN.`（REST PATCH 回读验证）；Issue 未加标签/负责人/里程碑；格式沿用 #3292 先例（Problem/Evidence/Impact/Expected/Related work/PR，仅用代码与测试可验证信息）。

## PR 关联标志核查（2026-09-05，应用户反馈"看不到小圆圈"）
- 平台结论：GitHub 的 Development 关联标志仅在 **PR base = 仓库默认分支（master）** 时由关闭关键词（Fixes/Closes/Resolves）计算产生；本项目所有 PR base=rocketmq-studio（比赛规则要求，不可改），故 GraphQL `closingIssuesReferences` 对全部 8 个 PR 返回空（含首轮就写 Fixes 的 #3145/#3151）——非默认分支上正文关键词不产生该标志，"Related to" 在任何分支都只是普通文字引用。
- 已做：8 个 PR 正文统一为关闭关键词写法（#3154→Fixes #3292、#3156→Fixes #3293、#3299→Fixes #3304、#3300→Fixes #3306、#3301→Fixes #3307、#3302→Fixes #3305；#3145/#3151 原本已是 Fixes #3104/#3103）。若维护者将 base 改为默认分支或 cherry-pick 到默认分支合并，关联会自动生效。
- 剩余途径：在 base 保持 rocketmq-studio 的前提下，唯一能显示 Development 圆圈的方式是网页手动链接（PR 页右侧 Development → 齿轮 → 勾选 Issue → Link）；GitHub 无公开 API 可做手动挂接。合并到非默认分支也不会自动关闭 Issue（与 #3292/#3293 仍开放一致），需维护者手动关闭。
- 8 组 PR↔Issue：#3145→#3104、#3151→#3103、#3154→#3292、#3156→#3293、#3299→#3304、#3300→#3306、#3301→#3307、#3302→#3305。

## 后续 PR 强制标准（应用户要求，此后所有提交遵循）
1. 正文五要素缺一不可，Tests 节必须内联实际命令与实际结果（含全量套件数字与基线对照），不得只写 "see comment below"。
2. 正文下方补一条 Verification evidence 评论：CI 失败原因与佐证、红灯实际输出、模块/全量/构建结果、基线对照。
3. 修复必须先红后绿；无测试或修复有错时先补齐再提交 PR。
4. 已充分的 PR 不做无意义编辑、不制造无意义 commit。

# 第三轮（2026-09-05 晚，目标：再 3 个新 Bug）

## 候选记录（第三轮）
| # | 区域 | BUG | EVIDENCE | ROOT_CAUSE | PRIORITY | FIX_CONFIDENCE | 结论 |
|---|------|-----|----------|-----------|----------|----------------|------|
| T1 | server MybatisPlusAclRepository | 编辑 ACL 用户清空"关联集群"（或 API 层规则 actions:[]）显示成功但旧值静默保留 | 3 个新 Mockito 回归先红（update 未发显式置空）；失败输出显示 updateById 实体 clusters=null 被跳过 | 空列表→joinNormalizedCsv→null→MyBatis-Plus updateById 跳过 null 列；同类 white_remote_address 有既有 workaround | 84 | 85 | ✅ PR #3342 |
| T2 | server RocketMQMetadataProvider | 无 broker 路由的 DB topic：详情弹窗/同步流程 502，syncMissing 永远为空 | 2 个新回归先红：真实输出 CODE:17 → 502 | getTopicRoutes/getTopicConsumersPage 缺 TOPIC_NOT_EXIST→空的分级（兄弟 provider 均有） | 76 | 90 | ✅ PR #3346 |
| T3 | server AliyunConverters | Aliyun 订阅表"订阅模式"列全空（Apache/Tencent 正常） | 2 个新回归先红：expected SQL/TAG but was null | toSubscriptionEntry 漏 filterMode 映射 | 72 | 92 | ✅ PR #3349 |
| T4 | web studio/Producer | 实例切换不 bump producerGroupRequestIdRef → 旧实例 group 建议残留 | 代码证实：handleInstanceChange 只 bump query ref；对照 handleTopicChange 有 bump | in-flight 守卫未失效 | ~63 | 88 | 低于 70 门槛，留档 |
| T5 | server InstanceService | delete-batch 自调用绕过 @Transactional | Spring 自调用机制 | this.deleteInstance 不走代理 | ~64 | 80 | 低于门槛，留档 |
| T6 | server AuthService | 登录先查"禁用"后验密码：403/401 区分可枚举用户名且 403 不计限流 | 代码证实 264-273 行顺序 + 129-134 只记 401 | 检查顺序 + 限流覆盖不全 | ~76 | 85 | 可作下轮候选（本轮已达 3 个上限，留档） |
| T7 | server DuplicateInstanceName/CloudCredential | 重复名 400 vs 409 不一致；并发 create 落唯一键 500 | 代码证实 | 状态码硬编码 | ~64 | 85 | 低于门槛，留档 |
| T8 | web notificationDeliveries | 单行重试进行中时"重试当前页"静默无操作 | guard 早退无反馈 | 重叠守卫语义 | ~57 | 85 | 低于门槛，留档 |
| T9 | server MetadataProvider enrichLiveStats | 每未来 3s 顺序计时 + pageSize 无上限 → 慢 broker 时分组页长时间挂起 | 代码证实 286-307 行 | 批次无全局 deadline | ~59 | 75 | 低于门槛，留档 |
| T10 | web studio/Proxy | cleanup 将代计数器回写旧值（语言切换触发）→ 丢弃 in-flight 刷新 | 146-172 行 | 计数器只应 ++ | ~61 | 85 | 低于门槛，留档 |

## 查重（第三轮）
- T1：搜 "acl clusters/acl user update cluster/acl rule actions update" 均无覆盖；#516 为旧 ACL 页 API 集成，不相关。
- T2：#1043（merged）为 producer connections 同语义先例；#1163（closed-stale）要求真实故障报错——本修复保留 502 行为并加以区分；开放 PR #3102 为同方法不同缺陷（trim/空名短路），hunk 不重叠；#1143/#1163 均不覆盖 TOPIC_NOT_EXIST。
- T3：#3160（服务端按订阅模式过滤）、#3178（demo 数据 i18n）均非本缺陷；#1646 为分页问题。

## 第三轮实施记录（2026-09-05）
3 个独立分支 × 各 1 commit × 各 1 PR，全部 base=rocketmq-studio，非 draft、MERGEABLE，未合并：
- **#3342** fix(acl): assign cleared list columns explicitly when updating users and rules — 分支 fix/acl-clear-list-bindings（commit ad344a45）
  - 3 个新回归：2 个先红后绿 + 1 个锁定 null=保留语义；MybatisPlusAclRepositoryTest 22/22、AclServiceTest 64/64、AclControllerTest 27/27；全量 2038（基线 2035+3），3 失败=基线逐字相同 + 1 个 LLM 负载脆弱用例（隔离 9/9 过）
- **#3346** fix(metadata): return empty results for topics without a broker route — 分支 fix/topic-route-absence-empty（commit efac4f05）
  - 2 个新回归先红（CODE:17→502）后绿：RocketMQMetadataProviderTest 37/37、MetadataServiceTest 36/36、TopicControllerTest 16/16；全量 2037（基线 2035+2），3 失败=基线逐字相同
- **#3349** fix(aliyun): populate subscription filter mode like the other providers — 分支 fix/aliyun-subscription-filter-mode（commit 72e33481）
  - 2 个新回归先红（null）后绿：AliyunConvertersTest 3/3；全量 2037（基线 2035+2），3 失败=基线逐字相同
- 均附 Verification evidence 评论：#3342 → issuecomment-5552136341；#3346 → issuecomment-5552136461；#3349 → issuecomment-5552136613
- CI：与往轮一致无 checks（upstream 工作流 startup_failure，从未启动）；PR 正文已如实注明。

## 第三轮最终状态（2026-09-05）
累计 11 个开放 PR（#3145/#3151/#3154/#3156/#3299/#3300/#3301/#3302/#3342/#3346/#3349），全部未合并。工作区停在 fix/aliyun-subscription-filter-mode，无未提交改动（除 AGENTS.md/bug-hunter-progress.md 两个未跟踪的工作区文件）。下一轮候选优先级：T6（登录枚举+限流）→ T4 → T5。

## 第三轮 PR 证据链逐个审计（2026-09-05，应用户要求；缺什么补什么）
- 审计对象：#3342、#3346、#3349。对照标准=上轮确立的五要素正文 + Verification 评论（CI 佐证/红灯输出/模块/全量/构建/基线对照）。
- 正文五要素三个 PR 全部齐备（Problem/Evidence、Root cause/Fix、Priority & scoring、Tests 内联实际结果与基线、Risk）→ 正文无需改动，未制造 commit。
- 评论审计发现两项真实缺项：**构建结果**与 **git diff 自检**未写入；CI 证据原本引用的是其他 PR 的 startup_failure，未落到本 PR 自身。
- 已用 REST PATCH 编辑三条既有评论（5552136341/5552136461/5552136613）：CI 条目替换为本分支 head SHA 的精确证据（gh api 查证：三分支各有一条 pull_request 事件 "CI" run，conclusion=startup_failure；base 分支近期 run 同样 startup_failure；check-runs=0、combined status=pending 无 statuses）；新增 **Build**（编译干净、BUILD FAILURE 仅源于既有测试失败、覆盖 backend-build job）与 **Diff self-check**（2 文件/86、60、46 行插入，无无关改动）两个条目。
- 修复与测试本身无问题（全部先红后绿），未改代码、未新增 commit；评论已回读验证格式完整。
- 结论：**之后所有 PR 的 Verification 评论必须包含六项：CI 佐证（本分支 head SHA 精确状态）、红灯实际输出、模块测试、全量套件+基线对照、构建结果、diff 自检。**

## 第三轮 PR ↔ Issue 建档（2026-09-05，应用户要求）
- 类型判定：读默认分支 `.github/ISSUE_TEMPLATE.md`（tracker 仅收 bug/feature）——三个均为 Bug；格式沿用 #3292 先例（[Studio][Bug] 标题 + Problem/Evidence/Impact/Expected/Related work/PR，不加标签/负责人/里程碑）。
- 查重（open+closed，扩展关键词）：ACL 清空（#2007/#2006 为并发删除语义、#2928 为 whitelist 映射缺失——均不同缺陷）、无路由 topic（#1041/#1043 先例、#3102 trim、#3305 NO_MESSAGE——均不同）、Aliyun filterMode（#3178 等 i18n、#3231 feature——均不同）→ 无重复，不关联现有 Issue。
- 新建（仅用代码/回归测试/PR 可验证信息，无虚构）：
  - #3357 ← PR #3342（ACL 清空绑定静默丢弃）
  - #3359 ← PR #3346（无路由 topic 502）
  - #3360 ← PR #3349（Aliyun filterMode 空白）
- 挂接：REST PATCH（gh pr edit 受 Projects classic 弃用限制）在三个 PR 正文顶部加 `Fixes #NNNN.`，回读验证首行生效；与往轮一致，非默认分支 base 上该关键字仅作为文字引用，Development 圆圈需维护者在网页手动链接或改 base 后自动生效。
- 未改代码、未新增 commit。

# 第四轮（2026-09-06，目标：最多 4 个新 Feature）

## 候选记录（第四轮，Feature）
评分公式：FEATURE_PRIORITY = 项目需求 0–40 + 外部实现成熟度 0–30 + 项目契合度 0–20 + 可测试性 0–10
| # | Issue | FEATURE | SOURCE | PRIORITY | CONFIDENCE | 结论 |
|---|-------|---------|--------|----------|------------|------|
| F1 | #3568 | 云凭据清单 CSV 导出（后端 export 端点+前端按钮，仅导出脱敏元数据，永不导出 secretKey） | 仓库明确 Issue（2026-09-05 建，维护者评估 Feasible）+ 仓库内成熟先例（AuditController/AuditService exportLogs：CsvUtil+BOM+上限 400；前端 audit.tsx blob 下载）| 37+26+18+9=90 | 88 | ✅ 实现 |
| F2 | #3565 | 实例清单 CSV 导出（同一模式，type/search 过滤，非敏感字段） | 同上，#3565 明确列出期望字段；in-repo 先例同 F1 | 37+26+18+9=90 | 86 | ✅ 实现 |
| F3 | #3566 | AI 对话历史单条删除（store action+抽屉确认删除+active 重建确定性选择） | #3566 明确需求；外部：成熟对话类产品普遍具备单条删除；仓库内 store/抽屉测试基建完备 | 35+24+17+9=85 | 85 | ✅ 实现 |
| F4 | #3567 | AI 对话历史搜索（大小写不敏感 contains，匹配首条用户消息+消息文本，保序，显式空态） | #3567 明确需求；外部：对话历史搜索为标配；仓库内 getRecentAiChatConversations 纯函数先例可扩展 | 35+24+17+9=85 | 84 | ✅ 实现 |

## 查重（第四轮）
- 已被占用而放弃：#3387→PR #3398、#3400→PR #3459、#3564→PR #3937、#3559→#3770、#3558→#3753、#3165→#3166、#3163→#3164、#3295→#3296、#3206→#3207、#3134→#3135、#3136→#3137、#3138→#3139、#3057→#3058、#3169→#3170、#3146→#3147。
- #3565/#3566/#3567/#3568：gh api search PR 全文检索（标题+正文）均无任何 PR 提及；开放 PR 列表逐一无对应实现。
- 代码缺失验证：CloudCredentialController 仅有 list/create/update/delete/reveal；aiChatHistoryStore 仅有 clearHistories（全清）无单条删除；AI 抽屉（pages/ai/index.tsx Drawer 块）无搜索输入、仅渲染 getRecentAiChatConversations（默认 8 条）。
- 有意省略排查：#3568 评估意见明确"Adding a CSV export endpoint … is well-scoped"且要求脱敏（仓库已有 maskAccessKey/CredentialUtils.mask 基建）；无任何代码注释/文档表明导出被有意不支持。

## 第四轮实施记录（2026-09-06）
4 个独立分支 × 各 1 commit × 各 1 PR，全部 base=rocketmq-studio，OPEN、非 draft、MERGEABLE、未合并：
- **#3980** feat(credential): export the filtered cloud credential inventory as masked CSV — 分支 feat/cloud-credential-export（commit f2788380）
  - 新回归先红（前端 3 失败：无导出按钮/无 API；后端 5 × cannot find symbol exportMaskedCsv）后绿：CloudCredential*Test 20/20、web 两文件 15/15；全量后端 2039（基线 2035+4），4 失败=基线 AuthCors ×2 + Aliyun ×1 + 已知 LLM 负载脆弱（隔离 9/9 过）零新增；全量 web 925（922+3），1 失败=ConsumerPage 已知脆弱（隔离 29/29 过）；build/tsc/eslint ✓；diff 9 文件 +182/−4
  - 证据评论：issuecomment-5556384327
- **#3982** feat(instance): export the filtered instance inventory as CSV — 分支 feat/instance-inventory-export（commit 9c617fd5）
  - 新回归先红（前端 3 失败、后端 5 × cannot find symbol）后绿：InstanceServiceTest 84/84、InstanceControllerTest 14/14、web 两文件 32/32；全量后端 2039，4 失败=与 #3980 完全相同的基线集零新增；全量 web 925，ConsumerPage 脆弱（隔离 29/29）；build/tsc/eslint ✓；diff 9 文件 +207/−1
  - 证据评论：issuecomment-5556653036
- **#3983** feat(ai): delete individual AI chat conversations from the history drawer — 分支 feat/ai-chat-conversation-delete（commit 2b7a696f）
  - 新回归先红（3 × deleteConversation is not a function + 2 个抽屉测试无删除控件）后绿：store+page 33/33；全量 web 927（922+5），1 失败=ConsumerPage 已知脆弱；仅前端改动后端未触碰；build/tsc/eslint ✓；diff 5 文件 +209/−32（−32 为行结构从单按钮改为行容器+删除钮）
  - 证据评论：issuecomment-5556707354
- **#3984** feat(ai): search AI chat history by conversation content — 分支 feat/ai-chat-history-search（commit 51553be5）
  - 新回归先红（2 × searchAiChatConversations is not a function + 2 个抽屉测试无搜索框）后绿：store+page 32/32；全量 web 926（922+4），2 失败=AclPage+ConsumerPage 已知脆弱（隔离 50/50 过）；仅前端改动；build/tsc/eslint ✓；diff 5 文件 +178/−8
  - 证据评论：issuecomment-5556765050
- CI：4 分支 head SHA 均确认 upstream CI workflow startup_failure（0 check-runs），已如实写入各 PR 证据评论。
- PR 正文均为五要素（Problem/Evidence、What was added、Priority & scoring、Tests 内联实际结果、Risk）+ 顶部 `Fixes #NNNN.`；对应 Issue 即 #3565–#3568（tju-yxq 所建，均无 PR 认领后由本轮实现）。

## 第四轮 PR 证据链审计（2026-09-06，应用户要求逐个审查 #3980/#3982/#3983/#3984）
- 事实核查发现并修正的不实表述（仅改 PR 正文，未改代码、未新增 commit）：
  1. "maintainer-evaluated" → 四个 Issue 的 Evaluation 评论作者实为 RockteMQ-AI（机器人，association=NONE），四个 PR 正文全部改为准确表述（自动化 triage，非维护者背书）。
  2. #3980 原文 "seven in-repo export endpoints" 计数不准 → 改为只引用已亲自核验的先例（AuditController /export 通读 + web 端 downloadCsv 使用面），不再给出易错的总数。
  3. #3983 原文 "this repo's own Message Query History drawer manages records individually" 在基线分支不成立（基线该抽屉无单条删除，单条管理属未合并 PR #3296）→ 删除该声明，替换为可核验的契合理由（store 动作风格、Popconfirm 既有模式、既有 ref 同步 effect）。
- 实现核查：#3980 依赖的 MybatisPlus 分页拦截器（MyBatisConfig）未配置 maxLimit → findPage(1, 10_000) 不会被截断，bound 校验成立，无需改码；其余实现核查无误。
- 状态发现：Issue #3568 已被其作者 tju-yxq 于 2026-09-06 关闭，理由为"另一贡献者已提交 PR #3980 完整实现"；#3980 正文已如实记录该状态，保留 Fixes #3568（PR 是其指向的实现）。#3565/#3566/#3567 仍开放。
- 正文重写为八要素链（Source 含可核验链接 / Current gap 对基线 36126024 / Project fit / Scope 含不包含项 / Implementation / Tests 实际命令与结果 / Compatibility & Risk 含依赖与许可证）+ 顶部 Fixes #N；回读验证四章齐全、关键词在位、四 PR 各仍 1 commit。
- 结论：四个 PR 均无需补测试或改代码；描述性缺陷已全部补正，无关闭项。

## 第四轮 PR ↔ Issue 挂接审计（2026-09-06，应用户要求）
- 类型判定：读 master 分支 .github/ISSUE_TEMPLATE.md（tracker 仅收 bug report / feature request）；四个 PR 均为 feat: 新能力 → 对应 FEATURE REQUEST；来源 Issue 正文均为 Problem/Current behavior/Expected behavior/Why this matters 的 feature request 结构，RockteMQ-AI 评估均分类 enhancement。无 Bug/Feature 误判。
- 查重（open+closed，关键词 export/credential/instance/history/AI chat）：四个主题各自唯一对应 #3568/#3565/#3566/#3567；相近条目（#3559 通知投递导出、#3564 vendor 过滤、#3295 Message Explorer 查询历史删除、#2474 抽屉搜索可见性等）均为不同功能，不构成重复。→ 不新建 Issue，只关联现有 Issue。
- 挂接核验：四 PR 正文首行分别为 `Fixes #3568.` / `Fixes #3565.` / `Fixes #3566.` / `Fixes #3567.`（回读逐字匹配）；四个 Issue 的 timeline 均已显示对应 PR 的 cross-referenced 事件（Issue 侧可见）。
- **竞争态势（如实记录）**：Issue 作者 tju-yxq 于 2026-09-06 也为其自己的 Issue 开了实现 PR：#3970（instance export，02:03:58Z）→ #3565、#3979（ai delete，02:17:21Z）→ #3566、#3981（ai search，02:33:07Z）→ #3567，分支名 codex/*。我方实现开始于 ~02:17Z、首轮 PR 查重运行于 ~02:0xZ（当时无这些 PR）。三个主题上对方 PR 早于我方（#3982 03:33Z、#3983 03:45Z、#3984 03:59Z）；#3568 上我方 #3980（02:32Z）为唯一 PR 且作者以它为由关闭了 Issue。是否取哪份实现属维护者决定，未做任何撤回/合并操作。

## 撤销重复 PR（2026-09-06，应用户指令"如果pr重复，请撤销"）
- 撤销前复查：#3970/#3979/#3981（tju-yxq）仍 OPEN、未合并、无 review；我方 #3982/#3983/#3984 与其构成同 Issue 重复且对方更早 → 判定为重复，执行撤销。#3980 在 #3568 上为唯一 PR（作者以它为由关闭 Issue），无重复 → 保留。
- 已关闭并留说明（各一条 comment 说明撤因与指向对方 PR）：#3982（重复 #3970）、#3983（重复 #3979）、#3984（重复 #3981）；回读确认 #3982/#3983/#3984=CLOSED、#3980=OPEN。
- 已删除 fork 远端分支 feat/instance-inventory-export、feat/ai-chat-conversation-delete、feat/ai-chat-history-search（本地分支保留作实现与验证记录）；未触碰任何他人 PR，未做合并。
- 本轮有效产出：#3980（开放）+ #3982/#3983/#3984（已撤销，实现与验证证据留在本地分支与 fork git 历史）。

## 第四轮最终状态（2026-09-06）
本轮新增 4 个 Feature PR：#3980、#3982、#3983、#3984（各 1 commit、MERGEABLE、未合并；未做任何合并操作）。工作区停于 feat/ai-chat-history-search 分支，无未提交改动（除 AGENTS.md/bug-hunter-progress.md 两个未跟踪工作区文件）。历史 PR：#3112/#2835/#2833 为维护者此前自行合并，非本账号操作。

# 第五轮（2026-09-06，目标：最多 4 个新 Feature）

## 证据源排查（关键结论）
1. **Issue 证据源已耗尽**：建立完整"开放 PR ↔ Issue"映射（992 个开放 PR 逐一提取正文引用），除 Bug #2889（前轮已评 ~55 分）外，**所有开放 Issue 均已有对应开放 PR**（含 RockteMQ-AI 评估过的全部 enhancement）。
2. **父项目证据**：classic dashboard（本仓库 master 分支）↔ Studio 系统性功能差距分析（Explore 代理，逐控制器/逐对话框核对）确认的真缺口：G2 消费组设置仅 2 字段（classic 编辑 7 个）、G3 无一键跳过积压（classic 有 skipAccumulate.do + 专用对话框）、G5 broker 无今日/昨日消息数列（classic 集群页有）。G1（消息属性）已被 PR #3290 认领；G4（历史趋势图）为设计文档明确的 Non-Goal（无 TSDB）→ 放弃；per-broker runtime 详情弹窗判定为 G5 覆盖面不足故弃。
3. **设计文档排查**：docs/studio-native-alerting-design.md 将 Producer 失败率/线程池拒绝/云 broker 健康指标明确列为 **Explicitly deferred** → 按规则视为有意省略，放弃；Prometheus YAML 导出已实现。
4. **后端已备/前端未暴露排查**：全控制器端点 × web 引用交叉比对，仅 /api/acl/remote/rules、/api/acl/capabilities 未用——属已认领 PR #3137 的跨实例 ACL 对比范围，放弃；系统告警页已有导出（exportAlerts）；topic/consumer/DLQ/broker 导出均已存在。
5. **BrokerVO 每日计数器**：RocketMQClusterProvider 已逐 broker fetchBrokerRuntimeStats 但丢弃 msgPut/GetTotal* 计数键（cluster.tsx/Classic 有此四列）→ 候选 C。

## 候选记录（第五轮，Feature）
| # | Issue | FEATURE | SOURCE | PRIORITY | CONFIDENCE | 结论 |
|---|-------|---------|--------|----------|------------|------|
| A | #3990 | 消费组设置扩展 consumeEnable/consumeMessageOrderly/consumeBroadcastEnable（Apache-only 门控不变，省略字段=保留现状） | 父项目 classic ConsumerConfigItem.jsx（in-repo）+ #2512 先例（首期仅 2 字段、未排除其余）| 32+26+17+9=84 | 84 | ✅ PR #3991 |
| B | #3993 | 重置位点弹窗"跳过积压（重置到最新）"预设（timestamp=now，保留 preview/confirm 安全流） | 父项目 classic skipAccumulate.do + SkipMessageAccumulateDialog（resetTime:-1）| 28+24+17+9=78 | 88 | ✅ PR #3994 |
| C | #3996 | Broker 拓扑表四列今日/昨日写入/消费计数（provider 已取 KVTable，仅补解析） | 父项目 classic cluster.jsx 四列；同 KVTable 数据已在手 | 28+24+17+9=78 | 85 | ✅ PR #3997 |
| - | - | G1 消息属性展示 | 已被 PR #3290 认领 | - | - | ❌ 重复 |
| - | - | G4 内置历史趋势图 | 设计文档 Non-Goal（无 TSDB，Grafana/MetricsExplorer 替代） | - | - | ❌ 有意省略 |
| - | - | 生产者失败率等告警指标 | 设计文档 Explicitly deferred | - | - | ❌ 有意省略 |
| - | - | /api/acl/remote/rules + /capabilities UI | 已被 PR #3137 认领 | - | - | ❌ 重复 |
| - | - | 系统告警 CSV 导出 | 页面已有 exportAlerts | - | - | ❌ 已存在 |
| - | - | 按 broker 删除 topic | 破坏性细粒度删除，需产品决策 | - | - | ❌ 放弃 |

## 第五轮实施记录（2026-09-06）
3 个独立分支 × 各 1 commit × 各 1 PR，全部 base=rocketmq-studio、OPEN、MERGEABLE、未合并、无竞争 PR（三 Issue 均只有本账号 PR）：
- **#3991** feat(consumer): manage consumption switches in group settings — feat/consumer-group-consumption-switches（f2bd8631）+ Issue #3990
  - 红灯：stash 后 server test-compile 9 个编译错误（cannot find symbol consumeEnable(boolean) 等 + signature 不匹配 ×5）；web "Unable to find role=switch 启用消费"。绿灯：三测试类 100/100；全量后端 2038（基线 2035+3），4 失败=基线逐字（AuthCors×2+Aliyun×1+LLM 负载脆弱，隔离 9/9）；ConsumerPage 30/30；全量 web 923，2 失败=未触碰文件负载脆弱（两文件隔离 48/48）；tsc/eslint/build ✓；diff 11 文件 +219/−16。证据评论：issuecomment-5557755125
- **#3994** feat(consumer): offer a skip-accumulation preset when resetting offsets — feat/skip-accumulation-preset（2215c53e）+ Issue #3993
  - 红灯：stash 后 "Unable to find button /跳过积压/"。绿灯：ConsumerPage 30/30（全量负载首跑 20s 超时→精简测试交互后全量 923 中仅 1 失败=既有脆弱用例）；tsc/eslint/build ✓；diff 2 文件 +39/−0。证据评论：issuecomment-5557910365
  - 教训：①测试勿用 Array.prototype.at（lib 目标不含 es2022）；②新增 UI 测试交互步数须控制（全量负载下单测 20s 上限）；③提交信息引用 Issue 前先建 Issue（编号竞速：两次引用错号 #3992/#3995，均已在"无 PR 依赖"窗口内删远端分支→amend→重推修正，未改写任何 PR 历史）。
- **#3997** feat(cluster): show per-broker daily message counters — feat/broker-daily-message-counters（a5ebab5c）+ Issue #3996
  - 红灯：stash 后 server "invalid method reference: cannot find symbol" ×4；web "Unable to find text 1,234"。绿灯：RocketMQClusterProviderTest 17/17；全量后端 2037（基线 2035+2），3 失败=基线逐字零新增；BrokerCluster 16/16；全量 web 923，2 失败=未触碰 ConsumerPage（隔离 29/29）；tsc/eslint/build ✓；diff 7 文件 +143/−0。证据评论：issuecomment-5558001552
- CI：三 PR head SHA 均 0 check-runs/0 statuses（工作流 startup_failure 惯例），已写入各证据评论。
- 第 4 个特性未实现：合格候选已穷尽（上表 ❌ 项均为重复/有意省略/需产品决策），按"不得降低标准凑数"规则停止于 3 个。

## 第五轮最终状态（2026-09-06）
本轮新增 3 个 Feature PR：#3991、#3994、#3997（各 1 commit、MERGEABLE、未合并、未做任何合并操作），并新建 3 个 Feature Issue：#3990/#3993/#3996。累计开放 PR：12 个 bug + 4 个 feature（含第四轮 #3980）。工作区停于 feat/broker-daily-message-counters，无未提交改动（除两个未跟踪工作区文件）。

# 第五轮 PR 证据链逐个审计（2026-09-06，应用户要求审查 #3991/#3994/#3997）
- 事实核查：#3997 的来源声明（classic cluster.jsx 164-190 行 YESTERDAY/TODAY PRO/CUS COUNT 列，同款 msgPut/GetTotal* 键与差值算法）此前未经亲验，本次用 git show 核实无误；#3991/#3994 的 classic 证据此前已亲验。
- 发现并修复一个真实实现缺陷（#3997，第二 commit 2b347d96）：ClusterRepositoryImpl.copyBroker 逐字段重建 BrokerVO，新四字段在仓库 defensive copy 中被静默丢弃（web 列表路径不受影响，但违反"独立副本"契约）。修复：copyBroker 携带四字段 + initStubData 种子计数 + 回归测试 findByIdShouldPreserveBrokerDailyMessageCounters。红灯：expected: 1500L but was: 0L（5 tests 1 failure）；绿灯：5/5 + cluster 模块 102/102。
- 三个 PR 正文用 REST PATCH 重写为八要素链（Source 可核验链接 / Current gap / Project fit / Scope 含不包含项 / Implementation / Tests 实际命令与结果 / Compatibility & Risk / head SHA），首行统一 `Fixes #N.`（#3990/#3993/#3996）；回读验证首行生效。
- 最终状态：#3991（1 commit）、#3994（1 commit）、#3997（2 commits）均 OPEN、MERGEABLE、未合并；三个 Issue OPEN。
- 结论：#3991/#3994 无代码改动（描述补全）；#3997 修 1 处缺陷 + 1 新回归测试（红→绿）；无虚构、无越界、无未解决实现风险。

# 第五轮 PR↔Issue 挂接补全审计（2026-09-06，应用户 7 条要求逐项核验）
- 判型：#3991/#3994/#3997 均为 feat: → Feature Request；对应 Issue #3990/#3993/#3996 均为 [Studio][Feature] 标题 + FEATURE REQUEST 模板结构（requesting/use case/importance），无 Feature 误描述为 Bug。
- 查重（开放+关闭）：consumeEnable/consumption switches、skip accumulation、daily message counters broker 三组关键词搜索均仅命中本轮自建 Issue（#2512/#2634 为先例非重复），无重复。
- Issue 内容补全（REST PATCH 编辑既有正文，未新建）：原三节（requesting/use case/importance）保留，追加 "4. Expected behavior / acceptance criteria"（仅 PR 与代码可验证信息）与 "5. Related PR"（回链对应 PR）；回读验证 #3990→#3991、#3993→#3994、#3996→#3997 链接在位。
- 关键字挂接：三 PR 正文首行 `Fixes #N.` 维持（回读逐字匹配）；Issue timeline 均显示对应 PR 的 cross-referenced 事件。
- 最终状态：三组 PR↔Issue（#3991↔#3990、#3994↔#3993、#3997↔#3996）双向挂接完整，全部 OPEN、未合并。

# 第六轮（2026-09-06，目标：最多 2 个新 Feature）

## 候选记录
| # | FEATURE | SOURCE | PRIORITY | CONFIDENCE | 结论 |
|---|---------|--------|----------|------------|------|
| A | DLQ 死信消息展示用户属性（DLQMessageVO+properties/propertiesTruncated，镜像 limitProperties 契约；抽屉 expandable row） | 父项目 classic DlqMessageDetailViewDialog.jsx（properties JSON+TAGS/KEYS，git show 核实）+ in-repo #1436（重发保留属性=显示缺失的对应面）+ Issue #3998 | 32+25+18+9=84 | 85 | ✅ PR #3999 |
| B | 消息体截断/编码提示（bodyTruncated/bodyEncoding 已由 Apache displayBody 契约填充、AI 工具已暴露给用户，但 UI 不显示） | 需"两个成熟同类项目"外部证据：GitHub code search 与 raw 拉取在本环境不可靠（AKHQ/kafka-ui 检索 0 命中/404），无法可靠核实 | - | - | ❌ 放弃（证据不足，如实记录） |
| - | consumeStatsAvailable/consumptionTimestampAvailable | 全仓库无赋值点（死字段），无数据可展示 | - | - | ❌ 放弃 |
| - | propertiesTruncated 消息页展示 | 已被 PR #3290 认领 | - | - | ❌ 重复 |

## 第六轮实施记录
- **#3999** feat(dlq): show user properties on dead-letter messages — feat/dlq-message-properties（f77c10a2，1 commit）+ Issue #3998（提交信息引用恰好命中，未再纠号）
  - 红灯：stash 后 server test-compile `cannot find symbol` ×4（getProperties/isPropertiesTruncated）；web "Unable to find role button /expand/i"。绿灯：RocketMQDLQProviderTest 34/34；DLQPage 19/19；全量后端 2037（基线 2035+2），3 失败=基线逐字（AuthCors×2+Aliyun×1）零新增；全量 web 923（5 失败=后端并发跑时未触碰文件 ClusterPage×1/ConsumerPage×3/TopicPage×1，隔离 91/91 过）；tsc/eslint/build ✓（dlq.tsx:81 警告为基线既有，stash 复核）；diff 6 文件 +166/−0。证据评论：issuecomment-5558347112
  - 第二特性槽位：合格候选穷尽（B 证据不可验证、死字段不可实现、其余已被认领），按"不得降低标准"规则交付 1 个。
- PR #3999 首行 `Fixes #3998.`，Issue timeline cross-referenced 事件已核验；八要素正文在创建时即写好。
- 过程教训：`gh pr create` 无 `--input` 参数（那是 gh api 的）；用 `--body-file` 传 markdown。

## 第六轮最终状态（2026-09-06）
本轮新增 1 个 Feature PR：#3999（OPEN、MERGEABLE、1 commit、未合并）+ Issue #3998。累计：12 bug + 5 feature PR 全部开放未合并。工作区停于 feat/dlq-message-properties，无未提交改动（除两个未跟踪工作区文件）。

# 第六轮 PR 证据链审计（2026-09-06，应用户要求审查 #3999）
- 八要素回读：正文首行 `Fixes #3998.` 正确；Source/Current gap/Project fit/Scope/Implementation/Tests/Compatibility & Risk 七节齐备；head SHA 注记在位。正文所有事实声明与本轮实际观察一致（来源链接经 git show 核实；测试数字与实际运行一致；CI 不可执行已如实注明）。
- 实现核查：limitProperties/propertiesTruncated 语义与 RocketMQMessageProvider 逐条一致；无越界、无越权改动 → 已充分，未改代码、未制造 commit。
- Issue #3998 补全（REST PATCH）：追加 "4. Expected behavior / acceptance criteria" 与 "5. Related PR #3999"，回读验证在位；竞争复核 #3998 上仅 #3999 一个 PR。
- 结论：#3999 证据链完整，无未解决实现风险。

# 第六轮 PR↔Issue 挂接核验（2026-09-06，应用户 7 条要求）
- #3999↔#3998 双向挂接核验（基于当前状态回读，非记忆）：PR 标题 feat: → Feature Request 判型；正文首行 `Fixes #3998.`；Issue 标题 [Studio][Feature] + FEATURE REQUEST 模板结构（1 需求+来源 / 2 use case+影响 / 3 importance / 4 验收标准 / 5 Related PR #3999）；#3998 timeline 有 1 条 cross-referenced 事件（来自 #3999）。
- 查重复核：开放+关闭搜索 "DLQ properties" 仅 #3998（本特性）与 #1415（closed Bug，重发语义，不同范围，已作为先例引用）；无重复。
- 结论：本轮唯一 PR 的 Issue 挂接完整合规，无遗留动作。

# 第七轮（2026-09-07，目标：至少 4 个新 Bug）

## 候选记录（第七轮）
| # | 区域 | BUG | EVIDENCE | ROOT_CAUSE | PRIORITY | FIX_CONFIDENCE | 结论 |
|---|------|-----|----------|-----------|----------|----------------|------|
| R1 | server RocketMQClientProvider | 全部消费组离线（正常态）时 /api/clients 502 且丢弃同请求已取的 producer 结果；显式 producerGroup 离线同理 502 | rocketmq-tools 5.5.0 字节码：examineConsumerConnectionInfo 空连接集抛 MQClientException(206)；examineProducerConnectionInfo 抛 "Not found the producer group connection"；332 行 all-failed 守卫误判 | 206/离线组消息被计为扫描失败 | 76 | 95 | ✅ PR #4002 + Issue #4006 |
| R2 | server QueryHistoryService | getMessageQueryResults 按 id 直查无 queried_by 过滤 → 认证用户可枚举读取他人查询快照（含 bornHost/storeHost） | selectById(:135) vs 同服务 184/204/221 全部 eq(queried_by)；#2265 契约由 #2365 落地、#2839 新端点遗漏 | 按 id 读路径漏归属边界 | 72 | 95 | ✅ PR #4003 + Issue #4007 |
| R3 | web AlertsPage | /ops/alerts ↔ /ops/business-alerts 同组件实例：page/search/enabledFilter 跨域残留 → 空表（rc-pagination 夹持显示 1）或静默缩窄 | App.tsx:187-188 无 key；alerts.tsx:159-162 无重置；测试红灯复现 | 路由元素复用无域状态重置 | 78 | 92 | ✅ PR #4004 + Issue #4008 |
| R4 | web message | 分页 onChange 用渲染期重建的 currentQueryParams（实时输入）→ 改输入不重查再翻页 = 两次查询拼一个列表 | message.tsx 429-434 + 1206-1214；红灯：page2 取到未提交 topic | 分页未对"已提交查询"做快照 | 71 | 88 | ✅ PR #4005 + Issue #4009 |
| S1 | server AlertSilenceSchedule | 周静默窗 >6 天在最后 1-2 天不再抑制通知 | daysToInspect 硬编码 6（:50-52）；validateRecurrence 允许 168h | 回看窗按最小周期而非最大时长 | ~68 | 96 | 边缘分，留档 |
| S2+S3 | server AlertRepository/NameserverRegistry | replace-style updateById + NOT_NULL 策略 → 清空字段静默保留（同 #3342 已修的 ACL 类） | 代码证实 | 同 ACL 类缺陷 | ~62 | 88-92 | 低于 70，留档 |
| S4 | NotificationOutboxService | test-notification 对 email/sms 也说 "DingTalk ... working" | :153-168 硬编码 | 文案未参数化 | 40 | 100 | 低于 70，仅记录 |
| F3 | web consumer | ?group= 深链与 URL 脱同步（组件跨兄弟路由存活） | consumer.tsx:266 useState initializer 只读一次 | 同 R3 类 | 59 | 82 | 低于 70，留档 |
| F4 | backend message provider | queryByTopic/getQueueOffsets TOPIC_NOT_EXIST → 502 | RocketMQMessageProvider:251-330/219-222 | 与 #3346/#3302 同类、触发面窄 | 48 | 85 | 低于 70，留档 |

## 查重（第七轮）
- R1：#1142(closed，#1143 引入现启发式——本修复为其未考虑"全离线"正常态的精化)；开放 #3291/#3294 为过滤器缺陷，不同；#3891/#3462 为测试扩展 PR。
- R2：#2265(closed，merged=22c26d25 只覆盖 list/summarize)、#3388(开放，仅 happy-path 测试)、#3295(删除功能)——results 端点归属缺口无覆盖。
- R3：#2570/#2589/#2430 均为不同缺陷；域切换残留无任何 Issue/PR。
- R4：#2601/#2529/#2531/#1805/#1810 均不同；live-input 重查无覆盖。

## 第七轮实施记录（2026-09-07）
4 个独立分支 × 各 1 commit × 各 1 PR，全部 base=rocketmq-studio、OPEN、非 draft、MERGEABLE、未合并：
- **#4002** fix(client): treat offline group connections as empty results — fix/offline-group-connections-empty（cd33f79d）
  - 2 新回归先红（"Failed to query consumer connections from all groups" / producer 同理）后绿：RocketMQClientProviderTest 27/27；全量后端 2038（基线 2035+3），3 失败=基线逐字（AuthCors×2+Aliyun×1）零新增；diff 2 文件 +66/−3。证据评论 5560942495
- **#4003** fix(message): scope stored query results to the authenticated owner — fix/query-results-ownership（987003b2）
  - 新回归先红（外部用户记录被返回）后绿：QueryHistoryServiceTest 12/12 + ControllerTest 4/4；全量 2037（基线+2），3 失败=基线逐字；diff 2 文件 +44/−2。证据评论 5560945490
- **#4004** fix(alerts): reset list state when the alerts domain route switches — fix/alerts-domain-state-reset（7c9b4673）
  - 教训：effect 内同步 setState 违反仓库 `react-hooks/set-state-in-effect` error 级规则 → 改用 React 官方"prop 变化时 render 期调整 state"模式，lint 0 error（5 警告=基线既有）。
  - 新回归先红（BUSINESS 拉取仍带 page:2/search:disk）后绿：AlertsPage 23/23；全量 web 923（首跑 3 失败=未触碰文件负载脆弱，二跑 115 文件全过）；tsc/eslint/build ✓；diff 2 文件 +59/−0。证据评论 5560945596
- **#4005** fix(message): paginate against the committed query — fix/message-pagination-committed-query（89895053）
  - 教训：查询后表格出现 pageSize combobox，`lastElement(getAllByRole('combobox'))` 会选中它；antd Select 二次打开时 userEvent 点击可见选项受 pointer-events 限制 → 改 `getAllByRole('combobox')[1]` + fireEvent 点可见选项，并用临时诊断测试确认选择器行为（已删）。
  - 新回归先红（page2 取未提交 topic）后绿：MessagePage+AsyncState 28/28；全量 web 923/923 全过；tsc/eslint/build ✓（10.06s）；diff 2 文件 +61/−3。证据评论 5560945694
- CI：4 个 head SHA 均查证 check-runs=0/statuses=0 且各自 workflow run=startup_failure（base 分支近期同状），已写入各证据评论（六项标准：CI/红灯/模块/全量+基线/构建/diff 自检）。
- PR 正文首行先建 Issue 后回填：#4002→Fixes #4006、#4003→Fixes #4007、#4004→Fixes #4008、#4005→Fixes #4009（REST PATCH，回读验证；4 个 Issue timeline 各 1 条 cross-referenced 事件）。

## 第七轮最终状态（2026-09-07）
本轮新增 4 个 Bug PR：#4002/#4003/#4004/#4005（各 1 commit、MERGEABLE、未合并、无合并操作）。工作区停于 fix/message-pagination-committed-query，无未提交改动（除 AGENTS.md/bug-hunter-progress.md 两个未跟踪文件）。累计开放 PR：16 bug + 5 feature。

# 第七轮 PR 证据链逐个审计（2026-09-07，应用户要求逐个审查 #4002-#4005）
- 审计标准：正文五要素（Problem/Evidence、Root cause/Fix、Priority & scoring、Tests 内联实际结果、Risk）+ 证据评论六项（CI 佐证含 head SHA 精确状态、红灯实际输出、模块测试、全量+基线对照、构建结果、diff 自检）。
- 正文回读：四个 PR 正文五要素齐备、首行 Fixes #N 在位、所有事实声明与实际运行一致 → 正文无需改动，未制造 commit。
- 评论审计发现并修正三类不实/不精确表述（仅 PATCH 既有评论 5560942495/5560945490/5560945596/5560945694，未新增评论、未改代码、未新增 commit）：
  1. **numstat 错误**：#4002 主文件实为 +23/−3（误写 26/−3）、#4003 实为 +6/−2（误写 8/−2）、#4005 实为 message.tsx +22/−2 与 test +39/−1（误写 24/−3 与 40/−1，且含混文本 "…+39/−1 net"）——三处均来自误读 diffstat 条宽，已按 `git show <sha> --numstat` 逐一改正并注明出处。
  2. **CI 断言不精确**：#4003/#4004/#4005 原写 "(no workflow started for this head)"，实际逐 SHA 查 `actions/runs?head_sha=` 均有一条 "CI"（pull_request）run 且 conclusion=startup_failure（17:00:44Z/17:17:06Z/17:29:53Z）——已改为与 #4002 一致的精确表述。
  3. **#4004 两处补实证**：① eslint "5 warnings 为基线既有"——本轮用 `git show origin/rocketmq-studio:<file>` 提取基线版实跑 eslint 证实（相同规则/位置，行号仅因 +11 行偏移），声明由"未验证"变为已验证；② 首轮全量 3 失败表述改为精确（仅 NotificationDeliveriesPage 一例从日志确认，即时二跑 923/923 覆盖本变更文件）；Build 项为 #4003 补齐 backend-build job 覆盖说明（与 #4002 对齐）。
- 修复与测试本身复核无问题（四者均先红后绿、模块+全量+构建齐备）→ 未补测试、未改代码、未新增 commit。
- **此后 PR 强制标准 reaffirmed**（用户要求"之后提交的 pr 也需遵循"）：正文五要素 + 评论六项；评论中所有数字（含逐文件增删行）必须以 numstat/实际输出为准，不得从 diffstat 条宽转抄；CI 佐证必须落到本 PR head SHA 的实际 workflow run 记录；任何"基线对照/既有"声明必须当时实测，不得凭推断表述为"已验证"。

# 第七轮 PR↔Issue 挂接核验（2026-09-07，应用户 7 条要求逐项核验）
- 判型：读 origin/master `.github/ISSUE_TEMPLATE.md`（tracker 仅收 bug report / feature request，无 CONTRIBUTING 限制事后建档；#3292/#3998 为既有先例）。4 个 PR 均为 fix: 修正客观错误行为 → 全部 BUG，无 Feature 误判；4 个 Issue 标题均 [Studio][Bug]。
- 结构回读：#4006/#4007/#4008/#4009 均 OPEN，六节齐全（Problem/Evidence/Impact/Expected behavior/Related work/PR），内容仅含代码行号、字节码、测试输出与 PR/Issue 引用等可验证信息。
- 查重复核（建档前已做 + 本轮重查 open+closed 多组关键词）：四主题各自唯一对应本轮自建 Issue（#404 为端口文档不一致，无关），建档后至今无他人新建相同 Issue。
- 关键字挂接：4 个 PR 正文首行 `Fixes #4006/#4007/#4008/#4009.`（回读逐字验证）；4 个 Issue timeline 各 1 条 cross-referenced 事件。
- 平台限制（实时 GraphQL 复证）：4 个 PR `closingIssuesReferences.totalCount=0` —— base=rocketmq-studio 非默认分支，GitHub 不据关闭关键词计算 Development 关联，合并也不会自动关闭 Issue；关键字仍产生文字引用与 timeline 事件，维护者改 base 或网页手动链接后自动生效（与往轮 #3292/#3293 结论一致，无 API 途径可替代）。
- 结论：本轮 4 组 PR↔Issue 建档与挂接全部合规，无缺项、无虚构。

# 第八轮（2026-09-08/09，目标：至少 4 个新 Bug）

## 环境要点
- 上游 base 已推进：本地 rocketmq-studio 同步至 0a596661（#3091 恰好修掉留档候选 T5 的自调用事务 → T5 作废）。
- 重要状态：首轮 8 个 PR 中 #4003 被维护者以"与 #3213 重复（同文件同行同方案，对方早一天）"关闭；其余 20 个已 MERGED。故本轮查重基线以最新上游为准。
- 网络：gh/git 均需走 127.0.0.1:7897 代理；不带代理的 mvn 会卡死在 fake-ip TLS 连接（198.18.x），须 `https_proxy=http://127.0.0.1:7897` 或 `-o` 离线模式。
- Maven 在本机以 `mvn -o`（离线）运行；全量后端 2132 基线 +3 新回归；web 全量 945。基线失败：AuthCorsIntegrationTest ×2、AliyunInstanceProviderTest ×1（后端）；AclPage/ConsumerPage/NotificationDeliveries/ClusterPage 为全量并行下的已知负载脆弱文件（隔离全过）。

## 候选记录（第八轮）
| # | 区域 | BUG | EVIDENCE | ROOT_CAUSE | PRIORITY | FIX_CONFIDENCE | 结论 |
|---|------|-----|----------|-----------|----------|----------------|------|
| U1 | server auth | 禁用账户先验 403 后验密：未认证方可枚举被封禁用户名，且这些尝试不计入限流器（只记 401）→ 可无限爆破 | 2 个新回归先红：错密码得 "User account is disabled"(403)；5 次失败后第 6 次仍 403 而非 429 | loginDatabaseUser 检查顺序 + login() 只对 401 recordFailure | 80 | 95 | ✅ PR #4160 + Issue #4159 |
| U2 | web cluster | Broker 配置预览无代际守卫（同文件 6 个 ref 守卫独缺此路径）：慢预览跨会话覆盖新预览、loading 卡死、过期 toast | 新回归先红：最终面板显示 defaultTopicQueueNums=16（过期响应）而非 24 | handleConfigPreview 无 requestId 比对；弹窗开/关/表单变更未失效 | 75 | 95 | ✅ PR #4166 + Issue #4165 |
| U3 | web studio/Producer | 切 Topic 只清 group 建议，连接表/readiness 保留旧 Topic 数据；导出用当前表单 Topic 给旧行贴标签 | 2 个新回归先红：切 Topic 后 producer-1 行仍在；导出按钮仍可用 | handleTopicChange 缺 setConnectionList/Summary（handleInstanceChange 有） | 73 | 92 | ✅ PR #4169 + Issue #4168 |
| U4 | web+server consumer | 订阅模式筛选仅客户端过滤当前页行，分页器/总数仍为未过滤值；导出路径却是服务端过滤 → 列表与导出矛盾 | 新回归先红：选 Pop 后 /groups/page 请求无 subscriptionMode | visibleConsumerGroups 客户端切片 + 端点缺参数 | 76 | 85 | ✅ PR #4172 + Issue #4171 |
| X1 | server DLQ | 选中重投全部 msgId 失效时 outcome=PARTIAL（前端报"重发完成：成功 0 条"）而非 NO_MESSAGES | 新回归红：[0,0,0,PARTIAL,true] | classifyOutcome 顺序 | ~70 | 90 | ❌ 放弃：#2635（PR #2636 已合并）明确 "Missing IDs should still produce a partial result"——有意设计 |
| X2 | server Aliyun | 缺 queryMessagesDetailed 覆写，100 条静默截断且 mayBeTruncated 永不为 true（两层兜底都够不到 200 阈值） | 代码证实（456-503 行），Tencent #3048 有先例 | 未实现 detailed 接口 | ~78 | 85 | ❌ 放弃：开放 PR #4164（2026-09-08 创建，同文件同方案）已认领 |
| X3 | web consumer 配置 | 跨组残留保存 A 配置到 B | 弹窗 onCancel 已 resetFields+清 settingsGroup（1600-1608 行），主路径不成立 | - | - | - | ❌ 复核后否定（代理报告 #3 与代码不符，仅余快照 ref 残留，触发面窄） |

## 查重（第八轮）
- U1：#3045/#3046（限流器容量机制，已修）、#2876（trim）、#823/#825（DTO toString）、#2351（浏览器状态）——均不同；403-before-password 顺序无任何 Issue/PR。
- U2：#3292/#3154（Broker diff 守卫，同文件孪生先例）；preview/submit 路径无覆盖。
- U3：#1593/#2200（提交新查询时清空旧结果）、#1285/#1286（实例维度 scope）——topic 切换路径从未覆盖（c7c8e861 引入 handleTopicChange 时即如此）。
- U4：#911（search trim 同族先例）、#2680/#2297（刷新/分页不同缺陷）；mode filter 无覆盖。

## 第八轮实施记录（2026-09-08/09）
4 个独立分支 × 各 1 commit × 各 1 PR，全部 base=rocketmq-studio、OPEN、MERGEABLE、各 1 commit、未合并：
- **#4160** fix(auth): verify the password before revealing disabled accounts — fix/login-disabled-account-enumeration（733b6669）+ Issue #4159
  - 红灯：`Tests run: 23, Failures: 2`（403 vs 通用消息；无 429 锁定）；绿灯 23/23；auth 模块 60/60；全量后端 2134（基线+2），3 失败=基线逐字零新增；diff +43/−3
- **#4166** fix(cluster): discard superseded broker config preview responses — fix/cluster-config-preview-request-guard（fb3cbcf5）+ Issue #4165
  - 红灯：面板被过期响应覆盖（24→16）；绿灯 ClusterPage 26/26；全量 web 945（首轮 2 失败=未触碰文件负载脆弱，隔离 54/54 + stash 基线复核）；tsc/eslint/build ✓；diff +87/−2
- **#4169** fix(producer): clear connection results when the topic changes — fix/producer-topic-change-connection-stale（29c0f4f0）+ Issue #4168
  - 红灯 2 失败（stale 行存活 + 导出未禁用）；绿灯 15/15；全量 web 3 失败=3 个未触碰文件（隔离 59/59）；build ✓；diff +80/−0
  - 教训：lint-staged pre-commit 会跑 eslint --fix，提交前需自查；antd Modal footer 按钮在测试环境为英文 "Cancel"/"OK"（未配 locale）
- **#4172** fix(consumer): filter groups by subscription mode on the server — fix/consumer-mode-filter-server-paged（2ed2f410）+ Issue #4171
  - 前后端贯通：/groups/page 新增可选 subscriptionMode → MetadataService → InstanceProvider（默认内存过滤）→ RocketMQMetadataProvider（messageModel 列 DB 过滤）；前端发参+移除客户端切片+切模式复位页码；AI tool 显式传 null 保持行为
  - 红灯：请求无 subscriptionMode；绿灯 ConsumerPage 32/32 + 后端 132/132（4 测试类，含新 DB 断言）；全量 web 945/945；tsc/eslint/build ✓；diff 14 文件 +146/−35
  - 教训：改接口签名时全部调用点（含测试、AI tool handler）要一次找全；MetadataProvider 6 参数签名与既有 (instanceId, clusterId, search, page, pageSize) 不冲突，与 5 参数 (instanceId, search, ...) 才是区分点
- CI：4 个 head SHA 均查证唯一 "CI (pull_request)" run = startup_failure、check-runs=0，已写入各证据评论（六项标准）。

## 第八轮 PR 证据链逐个审计（2026-09-09，应用户要求逐个补全）
- 审计标准：正文五要素（Problem/Evidence、Root cause/Fix、Priority & scoring、Tests 内联实际结果、Risk）+ 证据评论六项（CI 佐证含本 PR head SHA 精确状态、红灯实际输出、模块测试、全量+基线对照、构建结果、diff 自检）。
- 正文回读：4 个 PR 五要素齐备、首行 `Fixes #NNNN.` 在位、正文数字与实际运行逐条比对一致 → 正文无需改动，未制造 commit。
- 评论审计发现 2 处逐文件数字错误（本轮强制标准：逐文件增删必须以 numstat 为准）：
  1. #4160：`AuthService.java` 误写 +4/−2，numstat 实为 **+3/−3**（总量 43/3 正确）→ PATCH 修正评论 5585844322。
  2. #4172：web page 误写 "+32/−16 net"，numstat 实为 **+8/−16**（总量 146/35 与其余文件正确）→ PATCH 修正评论 5588026773。
- #4166（+17/−2、+70/−0）与 #4169（+2/−0、+78/−0）与 numstat 逐字一致，无需改动。
- 修复代码与测试本身复核无问题（4 者均先红后绿、模块+全量+构建齐备）→ 未补测试、未改代码、未新增 commit；仅按"缺说明补说明"PATCH 既有评论。

## 第八轮 PR↔Issue 挂接核验（2026-09-09）
- 判型：4 个 PR 均为 fix: 修正客观错误行为 → BUG；4 个 Issue 标题均 [Studio][Bug]，结构六节（Problem/Evidence/Impact/Expected behavior/Related work/PR）。
- 查重复核：建档前后均无他人同类 Issue/PR（#4164 为 X2 的认领 PR，已按规则放弃该候选）。
- 挂接：4 个 PR 正文首行 `Fixes #4159/#4165/#4168/#4171.`（回读逐字验证）；timeline 实测各 1 条 cross-referenced 事件（#4159→4160、#4165→4166、#4168→4169、#4171→4172）。
- 平台限制（同往轮）：base=rocketmq-studio 非默认分支，closingIssuesReferences 为空，Development 圆圈需维护者网页手动链接或改 base 后自动生效；合并到非默认分支不会自动关闭 Issue。

## 第八轮最终状态（2026-09-09）
本轮新增 4 个 Bug PR：#4160/#4166/#4169/#4172（各 1 commit、MERGEABLE、未合并、未做任何合并操作）+ 4 个自建 Issue（#4159/#4165/#4168/#4171）。工作区停于 fix/consumer-mode-filter-server-paged，无未提交改动（除 AGENTS.md/bug-hunter-progress.md 两个未跟踪工作区文件）。放弃候选均已记录理由（X1 有意设计、X2 已被 #4164 认领、X3 复核否定）。

# 第八轮 PR↔Issue 挂接与查重复核（2026-09-09，应用户挂接要求执行）

## 执行摘要
- 判型：读 origin/master .github/ISSUE_TEMPLATE.md（tracker 仅收 bug/feature）。4 个 PR 均为 fix: → 全部 BUG，无 Feature 误判。
- 更宽查重（含 PR 全文检索）：U1（403 登录枚举）、U2（预览代际守卫）、U3（Producer 切 Topic 残留）确认无既有 Issue/PR 覆盖；**U4 查重遗漏被发现**——Issue #3150（2026-09-04，tju-yxq，"Consumer Group Push/Pop filter only filters the current page"）与本轮 #4171 同一缺陷，且 PR #3160（09-04）/ #4053（09-07）为同一修复且更早。原查重只搜了 issue 且关键词未含 "Push/Pop filter"，教训已记录。
- 处理（沿用第四轮"对方更早则撤回己方"的既定先例）：
  - #4172 关闭留说明（重复 #3160/#4053，指向 #3150）；fork 远端分支已删，本地分支与验证证据保留。
  - 自建 #4171 关闭留说明指向 #3150，并在 #3150 留下 cross-reference comment（补充"列表 vs 导出不一致"细节）。
  - U1-U3 三组维持：#4160↔#4159、#4166↔#4165、#4169↔#4168 全部 OPEN、MERGEABLE。

## 挂接动作
- PR 侧：#4160/#4166/#4169 正文首行 `Fixes #4159/#4165/#4168.`（建 PR 时已挂，回读验证）；Issue 侧 "## PR" 一节由 "Fix incoming." 回填为实际编号 `Fix: #4160/#4166/#4169.`（REST PATCH，回读验证）。
- 平台限制（同往轮）：base=rocketmq-studio 非默认分支，Fixes 关键字不产生 Development 圆圈、合并不自动关 Issue；timeline cross-referenced 事件已实测存在。

## 事故与恢复（如实记录）
- 回填 Issue 正文时误用 `gh api -f body=@/file`（-f 传字面量，不读文件），把 "@ /tmp/issue-4159.md" 等字面字符串写入了 #4159/#4165/#4168 三个 Issue 正文。立即用本地备份文件经 `--input`（JSON 包裹 body 字段）恢复，回读验证六节结构与 `Fix: #N.` 回填完整无损。教训：gh api 修改正文一律 `--input` + JSON；patch 后必须回读验证。

# 第九轮（2026-09-09，3 个 Bug：#4175/#4177/#4189）

## 候选与实施（会话记录晚于本文件，此处补记要点）
- **#4175** fix(alert): interpret lastTriggered as UTC like the system alerts page — fix/alerts-last-triggered-utc（abef8546）+ Issue #4174。alerts.tsx 对后端无时区后缀的 UTC lastTriggered 用 formatDateTime 本地解析 → 换 formatUtcDateTime（systemAlerts 同契约先例）；diff alerts.tsx +4/−2、test +13/−2。
- **#4177** fix(instance): keep cloud instance endpoints read-only in the edit dialog — fix/cloud-instance-endpoint-edit（787f8158）+ Issue #4176。云厂商实例 endpoint 后端不落库（updateInstance 的 !cloudInstance 分支）但前端可编辑且报成功 → 编辑弹窗对非 APACHE vendor 禁用 endpoint Input；diff index.tsx +3/−0、test +33/−1。
- **#4189** fix(metrics): keep the selected range when the instance changes — fix/metrics-explorer-instance-range-sync（29873660）+ Issue #4188。仪表盘 MetricsExplorer 实例切换重载路径硬编码 RANGE_OPTIONS[0]（1h）→ rangeIdRef 同步已选 range；diff MetricsExplorer.tsx +12/−1、test +28/−0。
- 三者均 base=rocketmq-studio @ 0a596661、各 1 commit、MERGEABLE、OPEN、未合并；正文五要素 + 首行 Fixes #N + 六项证据评论在创建时即齐备。

## 第九轮 PR 证据链逐个审计（2026-09-09，应用户要求逐个补全）
- 审计标准：正文五要素 + 六项证据评论（CI/head SHA、红灯输出、模块、全量+基线、构建、diff 自检）。
- 静态核查（全部通过，零改动）：三个正文五要素齐备、数字与实际一致；numstat 逐文件（+12/−1、+28/−0；+3/−0、+33/−1；+4/−2、+13/−2）与三份评论逐字一致——本轮未再现第七/八轮的 numstat 抄写错误；CI 佐证逐一以 API 复证：三个 head SHA 各恰好一条 CI(pull_request) run、conclusion=startup_failure、check-runs=0、combined status pending（runs 34317929611/34291965413/34290553365），与评论表述精确相符；Issue #4174/#4176/#4188 均 OPEN、六节结构、PR 互链在位。
- 实测复核（本轮新增的实质验证）：
  - 红灯复现 ×3（`git checkout 0a596661 -- <修复源文件>` 保留新测试）：#4189 期望 6h 窗口 start 1799978400/step 2m 实得 1h；#4177 `expect(element).toBeDisabled()` 失败；#4175 两个用例 ×（formatUtcDateTime 期望 vs 本地渲染）——三者均与记录的红灯输出一致。
  - 绿灯：#4189 MetricsExplorer 21/21；#4177 InstancePage 24/24；#4175 AlertsPage 24/24——均与正文一致。
  - 全量 web（npx vitest run）：#4189 分支 945 tests/944 过/1 失败（本轮 ClusterPage 负载超时，隔离 25/25 过）；#4177 分支 945/944/1 失败（ConsumerPage，隔离 31/31 过）；#4175 分支 945/945 全过——总数 945 与三份正文/评论声明完全一致，失败仅见于已知负载脆弱文件（第八轮基线记载的同名单）。
  - #4189 上 RockteMQ-AI 机器人独立验证并 APPROVE；其指出 "21/21 vs 20" 疑点经实测裁定：机器人用旧基线 3612602（该基线文件 19+1=20），本 PR 基线 0a596661 实测 21/21——正文数字正确，无需更正。
- 结论：三个 PR 证据链完整且全部声明经实测成立——**已充分，未改代码、未新增 commit、未编辑正文/评论**（遵守"已充分不制造无意义改动"）。
- 留档候选（机器人评审附带发现，未评分）：MetricsExplorer 自定义查询面板在实例切换时不随 loadAll 重跑，旧实例 PromQL 结果残留（刷新按钮会重跑）；属 #3299 修复类的邻近缺口，pre-existing、越出 #4189 范围，后续轮次可评估。

## 第九轮最终状态
工作区停于 fix/alerts-last-triggered-utc，无未提交改动（除 AGENTS.md/bug-hunter-progress.md 两个未跟踪文件）。当前开放未合并 PR 实测 7 个（全为 bug）：#3151/#4160/#4166/#4169/#4175/#4177/#4189（#4004/#4005 等往轮 PR 已被维护者 MERGED；#4003、#4172 已被关闭，理由分别为与 #3213、#3160/#4053 重复）。之后提交的 PR 继续执行既定标准（五要素正文 + 六项证据评论 + 先红后绿 + numstat/CI 以实际查询为准）。

# 第九轮 PR↔Issue 挂接核验（2026-09-09，应用户 7 条要求逐项执行）
- 判型：重读 origin/master `.github/ISSUE_TEMPLATE.md`（tracker 仅收 bug/feature）。#4189/#4177/#4175 均为 fix: 修正客观错误行为 → 全部 BUG；三个 Issue 标题均 [Studio][Bug]，无 Feature 误判。
- 查重（开放+关闭 Issue 与 PR 全文检索，含变体关键词 metrics explorer/range selector/endpoint editable/接入地址/lastTriggered/UTC timestamp alert）：#4188、#4176 主题均无他人 Issue/PR 覆盖。**发现一个疑点并查明**：已关闭 #4173 与 #4174 标题逐字相同——经查为自身重复（此前会话创建 #4174 的 API 调用误报失败导致 6 秒后重复建档），RockteMQ-AI 已评估 duplicate、账号已留说明关闭，#4174 为正本且挂 PR #4175，非他人抢先。
- 挂接核验（基于回读非记忆）：三个 PR 正文首行 `Fixes #4188/#4176/#4174.` 逐字在位；#4188/#4176 timeline 各 1 条 cross-referenced 事件，#4174 有 2 条（来自 PR #4175 与重复 #4173）；三个 Issue 的 "## PR" 节均回链对应 PR。平台限制同往轮：base=rocketmq-studio 非默认分支，Fixes 不产生 Development 圆圈、合并不自动关 Issue，需维护者改 base 或网页手动链接。
- 本轮三处补全（均 `--input` + JSON PATCH、回读验证；未改代码、未新增 commit）：
  1. #4174 Related work 补引 #2763（closed）——同一 formatUtcDateTime 工具上的 padded 时间戳 trim 缺陷，与本地时区误解析是不同缺陷；
  2. #4188 Related work 补引 #1603（closed）——同组件同实例切换触发下数据源 ref 残留，时间范围回退是另一缺口；
  3. #4173 关闭理由由 completed 修正为 not_planned（重复关闭的 GitHub 语义；bot 评估与关闭说明均为 duplicate）。
- 结论：三组 PR↔Issue（#4189↔#4188、#4177↔#4176、#4175↔#4174）建档与关键字挂接全部合规；唯一重复 #4173 已妥善处置；#4176 无需补引（查重未发现同族先例）。

## #4189 合并冲突诊断（2026-09-09，应用户要求）
- 事实：base rocketmq-studio 由 0a596661 推进至 0c54d985（5 个新合并提交）；GitHub 实测 #4189 mergeable=CONFLICTING / mergeState=DIRTY（其余开放 PR 当时 UNKNOWN，经本地 merge-tree 实测：#4175/#4177/#4160/#4166/#4169 全部 CLEAN，**#3151 CONFLICT**——cluster/index.tsx 与 ClusterPage.test.tsx 两个文件，上游 cluster 改动所致）。
- #4189 冲突根因：上游合并 PR #2677（d7cbf3ed，Metrics Explorer 历史/序列详情/CSV 导出）重写 MetricsExplorer.tsx（835 行变更）与测试文件（291 行），与 #4189 的两个改动文件重叠。本地 worktree 实际合并复现：**仅 1 个冲突 hunk**（声明区：我们的 rangeIdRef 声明+同步 effect vs 上游新增 dataSourceNamesRef/pendingAuthReplayRef 两个 ref），effect 修复主体自动合并成功。
- 冲突解法 = keep both（两块声明共存）。已在合并结果上验证：MetricsExplorer 26/26（含上游新增测试+我们的回归测试）、tsc 干净。且新 base 652 行仍是 `loadAll(initialProfile, RANGE_OPTIONS[0])`——#4189 修的缺陷在新代码中依然存在，PR 仍然必要。
- 解决途径（未执行，待用户指示）：按规则不 force-push/不改写历史 → 将 origin/rocketmq-studio 以普通 merge commit 并入 PR 分支（解决 keep-both hunk 后正常 push），并补一条 PR 评论记录冲突解决与重新验证结果。
- 关联影响：早前"自定义查询面板实例切换不重跑"的最小修复分析基于旧代码；上游重写引入了 auth replay 与 loadAll 的 customPromqlToRun 参数、以及自定义查询重跑 effect（新文件 826-827 行）——该候选在动手前需按新版本重新核实。
