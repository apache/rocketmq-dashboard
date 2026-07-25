# RocketMQ Dashboard 前端页面 PR 粒度分析

基于对 `rocketmq-dashboard/frontend-new/src/` 的完整分析，以下是可独立提交的页面 PR 划分方案。

---

## 一、基础设施层 PR（优先提交，其他页面依赖）

| PR 编号 | 标题 | 涉及文件数 | 文件清单 |
|---------|------|-----------|---------|
| PR-0a | `[studio] feat: add i18n language context` | 2 | `i18n/index.js`, `i18n/LanguageContext.js` |
| PR-0b | `[studio] feat: add theme context and store` | 4 | `store/index.js`, `store/context/ThemeContext.js`, `store/reducers/themeReducer.js`, `store/actions/themeActions.js` |
| PR-0c | `[studio] feat: add StudioLayout component` | 3 | `components/StudioLayout/StudioLayout.jsx`, `components/StudioLayout/StudioLayout.css`, `components/Navbar.jsx` |

---

## 二、独立页面 PR（赛道一，每个 PR 聚焦单个页面）

### 低耦合页面（2-4 个文件，可直接独立提交）

| PR 编号 | 页面 | 路由 | 标题建议 | 文件数 |
|---------|------|------|---------|--------|
| PR-1 | Login | `/login` | `[studio] feat: add login page` | 1 (`pages/Login/login.jsx`) |
| PR-2 | Ops | `/ops` | `[studio] feat: add ops page` | 1 (`pages/Ops/ops.jsx`) |
| PR-3 | Producer | `/producer` | `[studio] feat: add producer page` | 1 (`pages/Producer/producer.jsx`) |
| PR-4 | AlertManagement | `/alert` | `[studio] feat: add alert management page` | 1 (`pages/Alert/AlertManagement.jsx`) |
| PR-5 | SslSettings | `/ssl-settings` | `[studio] feat: add SSL settings page` | 2 (`pages/SslSettings/SslSettings.jsx` + `.css`) |
| PR-6 | Namespace | `/namespace` | `[studio] feat: add namespace management page` | 2 (`pages/Namespace/Namespace.jsx` + `.css`) |
| PR-7 | LlmSettings | `/llm-settings` | `[studio] feat: add LLM settings page` | 2 (`pages/LlmSettings/LlmSettings.jsx` + `AiConfigPage.css`) |
| PR-8 | BrokerCluster | `/cluster` | `[studio] feat: add broker cluster overview page` | 2 (`pages/BrokerCluster/BrokerCluster.jsx` + `.css`) |
| PR-9 | GroupManagement | `/consumer` | `[studio] feat: add consumer group management page` | 2 (`pages/GroupManagement/GroupManagement.jsx` + `.css`) |
| PR-10 | LiteTopic | `/liteTopic` | `[studio] feat: add lite topic page` | 2 (`pages/LiteTopic/LiteTopic.jsx` + `.css`) |
| PR-11 | Proxy | `/proxy` | `[studio] feat: add proxy cluster page` | 3 (`pages/Proxy/proxy.jsx` + `ProxyCluster.jsx` + `ProxyCluster.css`) |

### 中等耦合页面（3-6 个文件，含专属组件）

| PR 编号 | 页面 | 路由 | 标题建议 | 文件数 |
|---------|------|------|---------|--------|
| PR-12 | Message | `/message` | `[studio] feat: add message query page` | 3 (`pages/Message/message.jsx` + `components/MessageDetailViewDialog.jsx`) |
| PR-13 | DlqMessage | `/dlqMessage` | `[studio] feat: add DLQ message page` | 3 (`pages/DlqMessage/dlqmessage.jsx` + `components/DlqMessageDetailViewDialog.jsx`) |
| PR-14 | MessageTrace | `/messageTrace` | `[studio] feat: add message trace page` | 3 (`pages/MessageTrace/messagetrace.jsx` + `components/MessageTraceDetailViewDialog.jsx`) |
| PR-15 | Acl | `/acl` | `[studio] feat: add ACL management page` | 4 (`pages/Acl/acl.jsx` + `components/acl/` 下3个文件) |
| PR-16 | Consumer | `/consumer`(旧版) | `[studio] feat: add consumer group list page` | 5 (`pages/Consumer/consumer.jsx` + `components/consumer/` 下4个文件) |
| PR-17 | Cluster | `/cluster`(旧版) | `[studio] feat: add cluster page` | 1 (`pages/Cluster/cluster.jsx`) |

### 高耦合页面（需拆分为多个子 PR）

#### Topic 页面（依赖 10+ 专属组件，必须拆分）

| 子 PR | 标题建议 | 文件数 | 文件清单 |
|-------|---------|--------|---------|
| PR-18a | `[studio] feat: add topic page core` | 2 | `pages/Topic/topic.jsx`, `pages/Topic/CapabilityTopicPage.jsx` |
| PR-18b | `[studio] feat: add topic dialog components` | 8 | `components/topic/` 下 8 个 Dialog 组件（ConsumerResetOffsetDialog, ConsumerViewDialog, ResetOffsetResultDialog, RouterViewDialog, SendResultDialog, SendTopicMessageDialog, SkipMessageAccumulateDialog, StatsViewDialog） |
| PR-18c | `[studio] feat: add topic modify and capability components` | 4 | `components/topic/TopicModifyDialog.jsx`, `components/topic/TopicSingleModifyForm.jsx`, `components/topic/CapabilityAwareTopic.jsx` + `.test.jsx` |

#### Dashboard 页面（依赖 LLM 组件）

| 子 PR | 标题建议 | 文件数 | 文件清单 |
|-------|---------|--------|---------|
| PR-19a | `[studio] feat: add dashboard page core` | 2 | `pages/Dashboard/DashboardPage.jsx`, `pages/Dashboard/chartTheme.js` |
| PR-19b | `[studio] feat: add dashboard CommandBar component` | 1 | `components/llm/CommandBar.jsx` |

#### Home 页面（依赖 LLM 组件）

| 子 PR | 标题建议 | 文件数 | 文件清单 |
|-------|---------|--------|---------|
| PR-20a | `[studio] feat: add home page core` | 2 | `pages/Home/HomePage.jsx`, `pages/Home/HomePage.css` |
| PR-20b | `[studio] feat: add ChatMessage component for home` | 1 | `components/llm/ChatMessage.jsx` |

---

## 三、LLM 基础设施 PR（赛道三，需先发 Issue 讨论）

> ⚠️ 赛道二/三涉及底层架构，**必须先发 Issue 讨论方案**，不可直接提交 PR

| PR 编号 | 标题建议 | 文件数 | 说明 |
|---------|---------|--------|------|
| PR-LLM-1 | `[studio] feat: add LLM context provider` | 1 | `store/context/LlmContext.js` |
| PR-LLM-2 | `[studio] feat: add OperationEvent context` | 1 | `store/context/OperationEventContext.js` |
| PR-LLM-3 | `[studio] feat: add ClusterCapabilities context` | 2 | `store/context/ClusterCapabilitiesContext.js` + `.test.jsx` |
| PR-LLM-4 | `[studio] feat: add LLM sidebar and overlay components` | 5 | `components/llm/SidebarChat.jsx` + `.css`, `components/llm/CommandBarOverlay.jsx` + `.css`, `components/llm/OperationEventBridge.jsx` |
| PR-LLM-5 | `[studio] feat: add LLM result display components` | 5 | `components/llm/ResultCard.jsx`, `DetailCard.jsx`, `GroupDetailCard.jsx`, `TopicDetailCard.jsx`, `TableResult.jsx` |
| PR-LLM-6 | `[studio] feat: add LLM timeseries and dry-run components` | 3 | `components/llm/TimeseriesResult.jsx`, `DryRunCard.jsx`, `ClusterChip.jsx` |

---

## 四、提交顺序建议

```
1. 基础设施层 (PR-0a → PR-0b → PR-0c)
2. 低耦合独立页面 (PR-1~PR-11，可并行)
3. 中等耦合页面 (PR-12~PR-17，可并行)
4. 高耦合页面拆分 (PR-18a→18b→18c, PR-19a→19b, PR-20a→20b)
5. LLM 基础设施 (需先 Issue 讨论，PR-LLM-1~6)
```

---

## 五、自查清单（每次提交前必检）

- [ ] PR 标题以 `[studio]` 开头
- [ ] 修改文件数控制在 5-10 个以内
- [ ] 赛道一改动仅聚焦于单个页面的功能完整性
- [ ] 赛道二/三已提前发起 Issue 并获得社区确认
- [ ] 已补充必要的单元测试 / E2E 测试

---

## 六、页面依赖关系总览

```
pages/Login/login.jsx
  └── remoteApi, i18n

pages/Ops/ops.jsx
  └── remoteApi

pages/Producer/producer.jsx
  └── remoteApi

pages/Alert/AlertManagement.jsx
  └── remoteApi, i18n

pages/SslSettings/SslSettings.jsx
  └── i18n

pages/Namespace/Namespace.jsx
  └── i18n, remoteApi

pages/LlmSettings/LlmSettings.jsx
  └── remoteApi

pages/BrokerCluster/BrokerCluster.jsx
  └── (无外部依赖，使用模拟数据)

pages/GroupManagement/GroupManagement.jsx
  └── (无外部依赖，使用模拟数据)

pages/LiteTopic/LiteTopic.jsx
  └── i18n, remoteApi

pages/Proxy/ProxyCluster.jsx
  └── i18n, remoteApi

pages/Message/message.jsx
  └── i18n, remoteApi, MessageDetailViewDialog

pages/DlqMessage/dlqmessage.jsx
  └── i18n, remoteApi, DlqMessageDetailViewDialog

pages/MessageTrace/messagetrace.jsx
  └── i18n, remoteApi, MessageTraceDetailViewDialog

pages/Acl/acl.jsx
  └── remoteApi, i18n, ResourceInput, SubjectInput

pages/Consumer/consumer.jsx
  └── i18n, remoteApi, OperationEventContext, ClientInfoModal, ConsumerDetailModal, ConsumerConfigModal, DeleteConsumerModal

pages/Cluster/cluster.jsx
  └── i18n, remoteApi, OperationEventContext

pages/Topic/topic.jsx
  └── i18n, remoteApi, OperationEventContext, 8个topic组件

pages/Topic/CapabilityTopicPage.jsx
  └── i18n, remoteApi, ClusterCapabilitiesContext, CapabilityAwareTopic, 8个topic组件

pages/Dashboard/DashboardPage.jsx
  └── i18n, remoteApi, CommandBar

pages/Home/HomePage.jsx
  └── remoteApi, LlmContext, ChatMessage