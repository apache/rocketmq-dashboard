# RocketMQ Studio (Dashboard) 待合并 PR 报告

> 数据来源: `apache/rocketmq-dashboard`，筛选条件 `state:open created:>=2026-06-22`，共 **45** 个 PR。
> 生成日期: 2026-07-22

## 总览

- **PR 总数**: 45
- **改动文件合计**: 203 个文件，`+14042 / -846` 行
- **主要贡献者**:
 - `Loyal-Young`: 30 个 PR
 - `zhaohai666`: 14 个 PR
 - `Kris20030907`: 1 个 PR

### 主题分类

- **新增前端页面 (zhaohai666)**: 以 `feat: add ... page` 为主，多为全新页面，改动大、几乎无删除。覆盖 LLM 设置、Proxy 管理、lite topic、消费组、broker 概览、namespace、SSL、告警、Producer、Ops、登录鉴权、公共基础模块等。
- **前后端 API 对接与修复 (Loyal-Young)**: 以 `fix: connect ... to APIs` / `feat: ...` 为主，将各页面接入后端接口，并做无障碍、主题跟随、多语言、会话恢复等体验优化。
- **监控适配 (Kris20030907)**: Prometheus range query adapter。

## PR 明细

### #488 [Studio] feat: add skip navigation link

- 作者: `Loyal-Young` ｜ 文件: 1 ｜ 改动: `+24 / -0`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/488
- 说明: Summary: adds a keyboard-visible skip link so users can move past navigation directly to the main content. Validation: git diff --check
- 改动文件 (1):
 - `web/src/layouts/MainLayout.tsx` `+24/-0`

### #487 [Studio] fix: use semantic page headings

- 作者: `Loyal-Young` ｜ 文件: 1 ｜ 改动: `+3 / -2`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/487
- 说明: Summary: use an h1 for page headers by default while preserving the established visual size and allowing nested pages to opt into another level. Validation: git diff --check
- 改动文件 (1):
 - `web/src/components/PageHeader.tsx` `+3/-2`

### #486 [Studio] fix: announce status badge labels

- 作者: `Loyal-Young` ｜ 文件: 1 ｜ 改动: `+2 / -2`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/486
- 说明: Summary: exposes status labels to screen readers and hides the decorative color dot. Validation: git diff --check
- 改动文件 (1):
 - `web/src/components/StatusBadge.tsx` `+2/-2`

### #485 [Studio] fix: handle empty mini bar data

- 作者: `Loyal-Young` ｜ 文件: 1 ｜ 改动: `+8 / -1`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/485
- 说明: Summary: renders a safe empty state for an empty series and adds an accessible trend label. Validation: git diff --check
- 改动文件 (1):
 - `web/src/components/MiniBar.tsx` `+8/-1`

### #484 [Studio] feat: configure API endpoint from env

- 作者: `Loyal-Young` ｜ 文件: 4 ｜ 改动: `+28 / -16`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/484
- 说明: - support configurable browser API base URLs and Vite development proxy targets - retain `/api` and `http://localhost:8888` as backwards-compatible defaults - document the new environment variables for reverse-proxy and container deployments - `npm.cmd test -- client.test.ts` (6 
- 改动文件 (4):
 - `web/.env` `+4/-0`
 - `web/src/api/client.ts` `+2/-1`
 - `web/src/config.ts` `+3/-0`
 - `web/vite.config.ts` `+19/-15`

### #483 [Studio] feat: add LLM Settings configuration page

- 作者: `zhaohai666` ｜ 文件: 5 ｜ 改动: `+1085 / -0`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/483
- 说明: `[studio] feat: add LLM Settings configuration page` ```markdown - Branch: feature/studio-llmsettings-page (target repo: rocketmq-studio) - Total commits: 3 - Code changes: 5 files, +1086 lines | File | Line Changes | Description | |------|--------------|-------------| | llm.ts |
- 改动文件 (5):
 - `web/src/App.tsx` `+2/-0`
 - `web/src/api/llm.test.ts` `+135/-0`
 - `web/src/api/llm.ts` `+67/-0`
 - `web/src/i18n/translations.ts` `+100/-0`
 - `web/src/pages/studio/LlmSettings.tsx` `+781/-0`

### #482 [Studio] feat: add Proxy management page

- 作者: `zhaohai666` ｜ 文件: 5 ｜ 改动: `+568 / -0`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/482
- 说明: `[studio] feat: add Proxy management page` ```markdown - Branch: feature/studio-proxy-page (target repo: rocketmq-studio) - Total changed files: 5, +568 lines - Total commits: 3 | File | Line Changes | Description | |------|--------------|-------------| | proxy.ts | +53 | Proxy A
- 改动文件 (5):
 - `web/src/App.tsx` `+2/-0`
 - `web/src/api/proxy.test.ts` `+76/-0`
 - `web/src/api/proxy.ts` `+53/-0`
 - `web/src/i18n/translations.ts` `+6/-0`
 - `web/src/pages/studio/Proxy.tsx` `+431/-0`

### #481 [Studio] feat: add lite topic management page

- 作者: `zhaohai666` ｜ 文件: 5 ｜ 改动: `+1020 / -0`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/481
- 说明: - Branch: feature/studio-litetopic-page (target repo: rocketmq-studio) - Total commits: 3 1. 4004284 [studio] feat: add LiteTopic page 2. 39a75da [studio] test: add LiteTopic API unit tests 3. 7bcda6b [studio] fix: remove unsupported icon prop from PageHeader in LiteTopic - Code 
- 改动文件 (5):
 - `web/src/App.tsx` `+2/-0`
 - `web/src/api/liteTopic.test.ts` `+156/-0`
 - `web/src/api/liteTopic.ts` `+105/-0`
 - `web/src/i18n/translations.ts` `+14/-0`
 - `web/src/pages/studio/LiteTopic.tsx` `+743/-0`

### #480 [Studio] feat: add consumer group management page

- 作者: `zhaohai666` ｜ 文件: 4 ｜ 改动: `+709 / -0`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/480
- 说明: - Branch: feature/studio-group-management-page (target repo: rocketmq-studio) - Total commits: 2 - 9fd182f [studio] feat: add GroupManagement page - b05f6d7 [studio] test: add GroupManagement page component tests - Code changes: 4 files changed, +709 lines | File | Changes | Desc
- 改动文件 (4):
 - `web/src/App.tsx` `+2/-0`
 - `web/src/i18n/translations.ts` `+59/-0`
 - `web/src/pages/studio/GroupManagement.tsx` `+551/-0`
 - `web/src/pages/studio/__tests__/GroupManagement.test.tsx` `+97/-0`

### #479 [Studio] feat: add broker cluster overview page

- 作者: `zhaohai666` ｜ 文件: 4 ｜ 改动: `+690 / -0`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/479
- 说明: `[studio] feat: add broker cluster overview page` Adds a new Broker Cluster overview page with tab-based management for NameServer, Broker, and Proxy nodes. The page provides visualized cluster status, resource usage statistics and basic operation entries. This implementation cur
- 改动文件 (4):
 - `web/src/App.tsx` `+2/-0`
 - `web/src/i18n/translations.ts` `+25/-0`
 - `web/src/pages/studio/BrokerCluster.tsx` `+551/-0`
 - `web/src/pages/studio/__tests__/BrokerCluster.test.tsx` `+112/-0`

### #478 [Studio] feat: add namespace management page

- 作者: `zhaohai666` ｜ 文件: 4 ｜ 改动: `+711 / -0`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/478
- 说明: Branch: feature/studio-namespace-page (target repo: rocketmq-studio) Total commits: 2 1. 9d7ca83 [studio] feat: add Namespace page 2. 28e69b5 [studio] test: add Namespace API unit tests Total 4 files modified, +711 lines of code | File | Line Changes | Description | |------|-----
- 改动文件 (4):
 - `web/src/App.tsx` `+2/-0`
 - `web/src/api/namespace.test.ts` `+116/-0`
 - `web/src/api/namespace.ts` `+68/-0`
 - `web/src/pages/studio/Namespace.tsx` `+525/-0`

### #477 [Studio] feat: add SslSettings page for SSL/TLS configuration management

- 作者: `zhaohai666` ｜ 文件: 3 ｜ 改动: `+427 / -0`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/477
- 说明: Add an SSL/TLS configuration management page with route `/studio/ssl-settings`. It supports SSL toggle, TLS protocol version selection, KeyStore & TrustStore configuration, client authentication mode setup and certificate file upload. | File | Change Type | Description | |------|
- 改动文件 (3):
 - `web/src/App.tsx` `+2/-0`
 - `web/src/pages/studio/SslSettings.tsx` `+327/-0`
 - `web/src/pages/studio/__tests__/SslSettings.test.tsx` `+98/-0`

### #476 [Studio] feat: add AlertManagement page for alert rule operations

- 作者: `zhaohai666` ｜ 文件: 5 ｜ 改动: `+800 / -0`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/476
- 说明: Add an alert rule management page accessible via route `/studio/alert`. The page supports parsing and rendering Prometheus AlertManager YAML rules, with search & filter, enable/disable toggle, create/edit/delete operations and YAML export capabilities. | File | Change Type | Desc
- 改动文件 (5):
 - `web/src/App.tsx` `+2/-0`
 - `web/src/api/alertManagement.test.ts` `+52/-0`
 - `web/src/api/alertManagement.ts` `+27/-0`
 - `web/src/i18n/translations.ts` `+45/-0`
 - `web/src/pages/studio/AlertManagement.tsx` `+674/-0`

### #475 [Studio] feat: add Producer page

- 作者: `zhaohai666` ｜ 文件: 5 ｜ 改动: `+291 / -4`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/475
- 说明: Add a new page to query producer client connections, supporting lookup by specified Topic and Producer Group. The page route is `/studio/producer`. | File | Change Type | Description | |------|-------------|-------------| | web/src/pages/studio/Producer.tsx | New | Producer conne
- 改动文件 (5):
 - `web/src/App.tsx` `+2/-0`
 - `web/src/api/producer.test.ts` `+84/-0`
 - `web/src/api/producer.ts` `+45/-0`
 - `web/src/i18n/translations.ts` `+12/-4`
 - `web/src/pages/studio/Producer.tsx` `+148/-0`

### #474 [Studio] feat: add Ops page (NameServer management, VIPChannel, TLS)

- 作者: `zhaohai666` ｜ 文件: 5 ｜ 改动: `+487 / -0`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/474
- 说明: Add a new Ops management page accessible via route `/studio/ops`. This page supports NameServer address management, VIP channel toggle and TLS switch configurations. | File | Change Type | Description | |------|-------------|-------------| | web/src/pages/studio/Ops.tsx | New | O
- 改动文件 (5):
 - `web/src/App.tsx` `+2/-0`
 - `web/src/api/ops.test.ts` `+248/-0`
 - `web/src/api/ops.ts` `+29/-0`
 - `web/src/i18n/translations.ts` `+9/-0`
 - `web/src/pages/studio/Ops.tsx` `+199/-0`

### #473 [Studio] feat: Implement login page, auth & AI modules, simplify theme management

- 作者: `zhaohai666` ｜ 文件: 8 ｜ 改动: `+316 / -69`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/473
- 说明: Branch: feature/studio-login-page Implement the login page for RocketMQ Studio, including full user authentication workflow, Auth API module, AI API module, and Zustand state management integration. Meanwhile, remove ThemeContext to simplify global theme handling logic. | File | 
- 改动文件 (8):
 - `web/package.json` `+1/-1`
 - `web/src/App.tsx` `+2/-0`
 - `web/src/api/ai.test.ts` `+134/-57`
 - `web/src/api/auth.test.ts` `+74/-0`
 - `web/src/layouts/MainLayout.tsx` `+1/-9`
 - `web/src/pages/login/index.tsx` `+102/-0`
 - `web/src/theme/ThemeContext.tsx` `+1/-1`
 - `web/src/theme/__tests__/ThemeContext.test.tsx` `+1/-1`

### #472 [Studio] fix: restore persisted auth sessions

- 作者: `Loyal-Young` ｜ 文件: 4 ｜ 改动: `+115 / -6`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/472
- 说明: - persist both authentication token and username, then restore the session when the application reloads - clear the complete persisted session from the user action and from API 401 handling - centralize session-storage behavior and cover persistence, orphaned data, and cleanup - 
- 改动文件 (4):
 - `web/src/api/client.ts` `+3/-2`
 - `web/src/stores/authStorage.test.ts` `+51/-0`
 - `web/src/stores/authStorage.ts` `+54/-0`
 - `web/src/stores/authStore.ts` `+7/-4`

### #471 [Studio] feat: pass home prompts to AI chat

- 作者: `Loyal-Young` ｜ 文件: 4 ｜ 改动: `+94 / -2`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/471
- 说明: - preserve a question typed on the home page when navigating to AI Chat - forward the selected model with the prompt, focus the AI input, and clear one-time router state after consumption - make the home input controlled so Enter and the send button share the same behavior - `npm
- 改动文件 (4):
 - `web/src/pages/ai/chatDraft.test.ts` `+34/-0`
 - `web/src/pages/ai/chatDraft.ts` `+32/-0`
 - `web/src/pages/ai/index.tsx` `+18/-0`
 - `web/src/pages/home/index.tsx` `+10/-2`

### #470 [Studio] feat: add navigation search shortcut

- 作者: `Loyal-Young` ｜ 文件: 3 ｜ 改动: `+114 / -13`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/470
- 说明: - make the advertised Command/Control-K navigation search shortcut functional - support Enter to navigate to the first match and show an explicit empty state for unmatched queries - isolate and test search filtering and shortcut recognition logic - `npm.cmd test -- navigationSear
- 改动文件 (3):
 - `web/src/layouts/MainLayout.tsx` `+34/-13`
 - `web/src/layouts/navigationSearch.test.ts` `+38/-0`
 - `web/src/layouts/navigationSearch.ts` `+42/-0`

### #469 [Studio] feat: add cluster operation services

- 作者: `Loyal-Young` ｜ 文件: 2 ｜ 改动: `+125 / -1`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/469
- 说明: - add service-layer access to NameServer restart, upgrade, create, update, and delete operations - add Proxy restart support and realistic in-memory behavior for every operation in mock mode - cover request payloads for all new backend operation endpoints - `npm.cmd test -- clust
- 改动文件 (2):
 - `web/src/api/cluster.test.ts` `+45/-1`
 - `web/src/services/clusterService.ts` `+80/-0`

### #468 [Studio] feat: complete consumer group service APIs

- 作者: `Loyal-Young` ｜ 文件: 3 ｜ 改动: `+133 / -8`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/468
- 说明: - expose consumer-group detail, typed offset-reset, and created-record responses through the frontend service layer - make mock-mode creation produce a realistic, immediately queryable group - add request-contract coverage for group detail, create, and reset-offset operations - `
- 改动文件 (3):
 - `web/src/api/metadata.test.ts` `+78/-0`
 - `web/src/api/metadata.ts` `+14/-5`
 - `web/src/services/consumerService.ts` `+41/-3`

### #467 [Studio] fix: implement namespace listing

- 作者: `Loyal-Young` ｜ 文件: 2 ｜ 改动: `+54 / -1`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/467
- 说明: - implement the previously unsupported namespace listing operation behind `GET /api/namespaces` - derive unique namespaces from topic metadata while retaining cluster identity and skipping blank namespaces - return namespace records in deterministic name and cluster order, with s
- 改动文件 (2):
 - `server/src/main/java/com/rocketmq/studio/instance/topic/MetadataService.java` `+23/-1`
 - `server/src/test/java/com/rocketmq/studio/instance/topic/MetadataServiceTest.java` `+31/-0`

### #466 [Studio] feat: follow system theme changes

- 作者: `Loyal-Young` ｜ 文件: 3 ｜ 改动: `+117 / -24`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/466
- 说明: - follow operating-system theme changes when the user has not explicitly chosen a theme - preserve explicit light or dark choices and stop automatic synchronization after a manual toggle - isolate browser-storage helpers and cover stored preference validation - `npm.cmd test -- t
- 改动文件 (3):
 - `web/src/theme/ThemeContext.tsx` `+33/-24`
 - `web/src/theme/themePreference.test.ts` `+43/-0`
 - `web/src/theme/themePreference.ts` `+41/-0`

### #465 [Studio] feat: persist language preference

- 作者: `Loyal-Young` ｜ 文件: 3 ｜ 改动: `+82 / -1`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/465
- 说明: - persist the user's Chinese or English selection in browser storage - restore only supported language values and safely default to Chinese for missing, invalid, or unavailable storage - add focused unit coverage for preference restoration and persistence - `npm.cmd test -- langu
- 改动文件 (3):
 - `web/src/i18n/LangContext.tsx` `+7/-1`
 - `web/src/i18n/languagePreference.test.ts` `+38/-0`
 - `web/src/i18n/languagePreference.ts` `+37/-0`

### #464 [Studio] feat: stream AI chat responses

- 作者: `Loyal-Young` ｜ 文件: 2 ｜ 改动: `+74 / -95`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/464
- 说明: - replace the AI page's simulated delayed reply with the backend SSE chat stream - append streamed chunks to the in-progress assistant response and retain a stable conversation identifier - add a stop action, cleanup on unmount, and user-visible failure feedback - align the chat 
- 改动文件 (2):
 - `web/src/api/ai.ts` `+8/-1`
 - `web/src/pages/ai/index.tsx` `+66/-94`

### #463 [Studio] fix: connect user logout to API

- 作者: `Loyal-Young` ｜ 文件: 2 ｜ 改动: `+76 / -0`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/463
- 说明: - wire the account dropdown's logout action to the existing backend logout endpoint - clear the local authentication state even when the backend is unreachable, preventing a stale client session - make the profile menu entry open the settings page and add auth request-contract co
- 改动文件 (2):
 - `web/src/api/auth.test.ts` `+54/-0`
 - `web/src/layouts/MainLayout.tsx` `+22/-0`

### #462 [Studio] feat: add K8s certificate renewal

- 作者: `Loyal-Young` ｜ 文件: 3 ｜ 改动: `+63 / -3`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/462
- 说明: - expose the existing K8s certificate renewal endpoint in the certificate management page - refresh the renewed record in place and provide confirmation, progress, and failure feedback - implement deterministic renewal behavior for the frontend mock service - add API-contract cov
- 改动文件 (3):
 - `web/src/api/cluster.test.ts` `+11/-1`
 - `web/src/pages/cluster/certs.tsx` `+34/-2`
 - `web/src/services/clusterService.ts` `+18/-0`

### #461 [Studio] fix: show dashboard loading failures

- 作者: `Loyal-Young` ｜ 文件: 2 ｜ 改动: `+112 / -5`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/461
- 说明: - keep the dashboard visible while data is loading instead of rendering a blank page - provide a clear error state with a retry action when the dashboard request fails - add request-contract coverage for the dashboard and metrics endpoints - `npm.cmd test -- metrics.test.ts` - `n
- 改动文件 (2):
 - `web/src/api/metrics.test.ts` `+67/-0`
 - `web/src/pages/home/dashboard.tsx` `+45/-5`

### #460 [Studio] fix: connect instance management to APIs

- 作者: `Loyal-Young` ｜ 文件: 2 ｜ 改动: `+130 / -113`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/460
- 说明: - replace the instance management page's local fixture state with the existing instance service - add loading, failure, and submission feedback for list, create, update, and delete operations - cover the instance API request and response contracts with focused tests - `npm.cmd te
- 改动文件 (2):
 - `web/src/api/instance.test.ts` `+55/-0`
 - `web/src/pages/instance/index.tsx` `+75/-113`

### #459 [Studio] fix: connect data sources to APIs

- 作者: `Loyal-Young` ｜ 文件: 3 ｜ 改动: `+180 / -69`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/459
- 说明: - replace mock-only data-source management with the existing settings APIs - persist create, edit, delete, and connection-test operations with loading and error feedback - align the deletion request with the controller's `key` query parameter and add API contract tests - `npm.cmd
- 改动文件 (3):
 - `web/src/api/dataSources.test.ts` `+61/-0`
 - `web/src/api/settings.ts` `+9/-7`
 - `web/src/pages/settings/index.tsx` `+110/-62`

### #458 [Studio] fix: connect general settings to APIs

- 作者: `Loyal-Young` ｜ 文件: 3 ｜ 改动: `+108 / -18`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/458
- 说明: - load the general-settings form from `/api/settings/general` and persist changes through `/api/settings/general/save` - align the frontend settings type with `GeneralSettingsVO` - show loading, saving, and request-error feedback; add API contract coverage - `npm.cmd test -- gene
- 改动文件 (3):
 - `web/src/api/generalSettings.test.ts` `+63/-0`
 - `web/src/api/settings.ts` `+8/-5`
 - `web/src/pages/settings/index.tsx` `+37/-13`

### #457 [Studio] feat: add cluster broker overview page

- 作者: `zhaohai666` ｜ 文件: 12 ｜ 改动: `+1141 / -0`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/457
- 说明: Implement cluster and broker management module, providing core APIs including cluster list query, cluster detail retrieval, cluster configuration update, and broker restart. The module adopts a layered architecture (Controller → Service → Repository/Provider), with in-memory stub
- 改动文件 (12):
 - `server/src/main/java/com/rocketmq/studio/cluster/broker/BrokerVO.java` `+37/-0`
 - `server/src/main/java/com/rocketmq/studio/cluster/broker/ClusterController.java` `+64/-0`
 - `server/src/main/java/com/rocketmq/studio/cluster/broker/ClusterProvider.java` `+27/-0`
 - `server/src/main/java/com/rocketmq/studio/cluster/broker/ClusterProviderStub.java` `+85/-0`
 - `server/src/main/java/com/rocketmq/studio/cluster/broker/ClusterRepository.java` `+31/-0`
 - `server/src/main/java/com/rocketmq/studio/cluster/broker/ClusterRepositoryImpl.java` `+192/-0`
 - `server/src/main/java/com/rocketmq/studio/cluster/broker/ClusterService.java` `+157/-0`
 - `server/src/main/java/com/rocketmq/studio/cluster/broker/ClusterVO.java` `+53/-0`
 - `server/src/main/java/com/rocketmq/studio/cluster/config/ClusterConfigVO.java` `+40/-0`
 - `server/src/main/java/com/rocketmq/studio/cluster/config/UpdateConfigDTO.java` `+38/-0`
 - `server/src/test/java/com/rocketmq/studio/cluster/broker/ClusterControllerTest.java` `+165/-0`
 - `server/src/test/java/com/rocketmq/studio/cluster/broker/ClusterServiceTest.java` `+252/-0`

### #456 [Studio] feat: add login and auth page

- 作者: `zhaohai666` ｜ 文件: 7 ｜ 改动: `+427 / -0`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/456
- 说明: Add login and authentication module for RocketMQ Studio, providing user login/logout REST API with JWT token support and Spring Security configuration placeholder. 7 files modified, total 427 line insertions | File | Lines | Description | |------|-------|-------------| | auth/Aut
- 改动文件 (7):
 - `server/src/main/java/com/rocketmq/studio/auth/AuthController.java` `+44/-0`
 - `server/src/main/java/com/rocketmq/studio/auth/AuthService.java` `+60/-0`
 - `server/src/main/java/com/rocketmq/studio/auth/LoginDTO.java` `+26/-0`
 - `server/src/main/java/com/rocketmq/studio/auth/LoginVO.java` `+42/-0`
 - `server/src/main/java/com/rocketmq/studio/auth/SecurityConfig.java` `+25/-0`
 - `server/src/test/java/com/rocketmq/studio/auth/AuthControllerTest.java` `+115/-0`
 - `server/src/test/java/com/rocketmq/studio/auth/AuthServiceTest.java` `+115/-0`

### #455 [Studio] feat: add core common foundation module with unified response, base entity, enums and global exception handling

- 作者: `zhaohai666` ｜ 文件: 31 ｜ 改动: `+1328 / -0`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/455
- 说明: `[studio] feat: add core common foundation module with unified response, base entity, enums and global exception handling` This module serves as the foundational common component of the project, delivering standardized response wrappers (`Result` / `PageResult`), base entity `Bas
- 改动文件 (31):
 - `server/src/main/java/com/rocketmq/studio/common/config/CorsConfig.java` `+33/-0`
 - `server/src/main/java/com/rocketmq/studio/common/config/WebConfig.java` `+25/-0`
 - `server/src/main/java/com/rocketmq/studio/common/domain/BaseEntity.java` `+27/-0`
 - `server/src/main/java/com/rocketmq/studio/common/domain/PageResult.java` `+47/-0`
 - `server/src/main/java/com/rocketmq/studio/common/domain/Result.java` `+47/-0`
 - `server/src/main/java/com/rocketmq/studio/common/domain/enums/AlertLevel.java` `+22/-0`
 - `server/src/main/java/com/rocketmq/studio/common/domain/enums/BrokerStatus.java` `+22/-0`
 - `server/src/main/java/com/rocketmq/studio/common/domain/enums/CertStatus.java` `+22/-0`
 - `server/src/main/java/com/rocketmq/studio/common/domain/enums/CertType.java` `+22/-0`
 - `server/src/main/java/com/rocketmq/studio/common/domain/enums/ClientLanguage.java` `+22/-0`
 - `server/src/main/java/com/rocketmq/studio/common/domain/enums/ClientType.java` `+22/-0`
 - `server/src/main/java/com/rocketmq/studio/common/domain/enums/ClusterStatus.java` `+22/-0`
 - `server/src/main/java/com/rocketmq/studio/common/domain/enums/ClusterType.java` `+22/-0`
 - `server/src/main/java/com/rocketmq/studio/common/domain/enums/ConsumeType.java` `+22/-0`
 - `server/src/main/java/com/rocketmq/studio/common/domain/enums/DeliveryStatus.java` `+22/-0`
 - `server/src/main/java/com/rocketmq/studio/common/domain/enums/FlushDiskType.java` `+22/-0`
 - `server/src/main/java/com/rocketmq/studio/common/domain/enums/InstanceType.java` `+22/-0`
 - `server/src/main/java/com/rocketmq/studio/common/domain/enums/Protocol.java` `+22/-0`
 - `server/src/main/java/com/rocketmq/studio/common/domain/enums/SubscriptionMode.java` `+22/-0`
 - `server/src/main/java/com/rocketmq/studio/common/domain/enums/TopicPerm.java` `+22/-0`
 - `server/src/main/java/com/rocketmq/studio/common/domain/enums/TopicType.java` `+22/-0`
 - `server/src/main/java/com/rocketmq/studio/common/exception/BusinessException.java` `+29/-0`
 - `server/src/main/java/com/rocketmq/studio/common/exception/GlobalExceptionHandler.java` `+45/-0`
 - `server/src/test/java/com/rocketmq/studio/common/config/CorsConfigTest.java` `+51/-0`
 - `server/src/test/java/com/rocketmq/studio/common/config/WebConfigTest.java` `+45/-0`
 - `server/src/test/java/com/rocketmq/studio/common/domain/BaseEntityTest.java` `+73/-0`
 - `server/src/test/java/com/rocketmq/studio/common/domain/PageResultTest.java` `+75/-0`
 - `server/src/test/java/com/rocketmq/studio/common/domain/ResultTest.java` `+87/-0`
 - `server/src/test/java/com/rocketmq/studio/common/domain/enums/EnumsTest.java` `+274/-0`
 - `server/src/test/java/com/rocketmq/studio/common/exception/BusinessExceptionTest.java` `+54/-0`
 - `server/src/test/java/com/rocketmq/studio/common/exception/GlobalExceptionHandlerTest.java` `+64/-0`

### #454 [Studio] fix: align consumer group API contract

- 作者: `Loyal-Young` ｜ 文件: 3 ｜ 改动: `+150 / -19`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/454
- 说明: - align consumer-group query parameters and detail fields with `ConsumerGroupController` and its DTOs - return created groups and expose detail/reset-offset operations through the service layer - use numeric epoch milliseconds for reset-offset requests and add API contract tests 
- 改动文件 (3):
 - `web/src/api/consumerGroups.test.ts` `+73/-0`
 - `web/src/api/metadata.ts` `+24/-9`
 - `web/src/services/consumerService.ts` `+53/-10`

### #453 [Studio] fix: align ACL API contract

- 作者: `Loyal-Young` ｜ 文件: 3 ｜ 改动: `+111 / -37`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/453
- 说明: - align ACL rule query fields and ACL version types with `AclController` and `AclRuleVO` - return persisted ACL rules and users from creation APIs, including in mock mode - add request and response contract coverage for ACL endpoints - `npm.cmd test -- acl.test.ts` - `npm.cmd run
- 改动文件 (3):
 - `web/src/api/acl.test.ts` `+55/-0`
 - `web/src/api/acl.ts` `+11/-8`
 - `web/src/services/aclService.ts` `+45/-29`

### #452 [Studio] fix: align cluster API response contract

- 作者: `Loyal-Young` ｜ 文件: 3 ｜ 改动: `+81 / -25`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/452
- 说明: - align the frontend cluster API types with the backend `ClusterVO` response - preserve broker, proxy, NameServer, configuration, and aggregate fields in mock mode as well - add a contract test for both cluster list and detail responses - `npm.cmd test -- clusterContract.test.ts`
- 改动文件 (3):
 - `web/src/api/cluster.ts` `+12/-19`
 - `web/src/api/clusterContract.test.ts` `+60/-0`
 - `web/src/services/clusterService.ts` `+9/-6`

### #451 [Studio] fix: connect topic page to APIs

- 作者: `Loyal-Young` ｜ 文件: 4 ｜ 改动: `+187 / -47`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/451
- 说明: - replace the Topic page's mock-only state with existing Topic APIs - persist create, delete, batch delete, and message sending; load routes and consumers on demand - align Topic query and create field names with the backend contract and add API tests - `npm.cmd test -- metadata.
- 改动文件 (4):
 - `web/src/api/metadata.test.ts` `+79/-0`
 - `web/src/api/metadata.ts` `+9/-2`
 - `web/src/pages/instance/topic.tsx` `+90/-34`
 - `web/src/services/topicService.ts` `+9/-11`

### #450 [Studio] fix: connect message query page to APIs

- 作者: `Loyal-Young` ｜ 文件: 4 ｜ 改动: `+149 / -55`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/450
- 说明: - replace mock-only message searches and traces with `/api/messages` requests - align query timestamps and message/trace response types with the backend's epoch-millisecond contract - provide request loading and failure feedback, with API contract coverage - `npm.cmd test -- mess
- 改动文件 (4):
 - `web/src/api/message.test.ts` `+75/-0`
 - `web/src/api/message.ts` `+16/-17`
 - `web/src/pages/instance/message.tsx` `+55/-33`
 - `web/src/services/messageService.ts` `+3/-5`

### #449 [Studio] fix: connect clients page to APIs

- 作者: `Loyal-Young` ｜ 文件: 4 ｜ 改动: `+102 / -29`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/449
- 说明: - load the clients page from the existing `/api/clients` endpoint instead of direct mock imports - derive cluster filters from the returned connection data and show request failures in the UI - align the API wrapper and mock service with the backend's supported `clusterId` and `t
- 改动文件 (4):
 - `web/src/api/connections.test.ts` `+58/-0`
 - `web/src/api/connections.ts` `+5/-4`
 - `web/src/pages/cluster/clients.tsx` `+34/-10`
 - `web/src/services/connectionsService.ts` `+5/-15`

### #448 [Studio] fix: connect DLQ page to APIs

- 作者: `Loyal-Young` ｜ 文件: 4 ｜ 改动: `+120 / -14`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/448
- 说明: - load DLQ group data from the existing `/api/dlq` endpoint instead of direct mock imports - submit resends to `/api/dlq/resend`, refresh the list after success, and report request failures - align resend timestamps with the backend's epoch-millisecond contract and cover it with 
- 改动文件 (4):
 - `web/src/api/dlq.test.ts` `+65/-0`
 - `web/src/api/message.ts` `+2/-2`
 - `web/src/pages/instance/dlq.tsx` `+51/-10`
 - `web/src/services/messageService.ts` `+2/-2`

### #447 [Studio] fix: connect alert rules page to APIs

- 作者: `Loyal-Young` ｜ 文件: 4 ｜ 改动: `+246 / -30`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/447
- 说明: - replace the alert-rules page's mock-only state with the existing alert-rules API - persist rule creation, editing, enabling/disabling, and deletion; display API failures to the operator - return the server's canonical rule after mutations and add API contract coverage - `npm.cm
- 改动文件 (4):
 - `web/src/api/alertRules.test.ts` `+90/-0`
 - `web/src/api/ops.ts` `+7/-4`
 - `web/src/pages/ops/alerts.tsx` `+113/-16`
 - `web/src/services/opsService.ts` `+36/-10`

### #446 [Studio] fix: connect audit logs page to APIs

- 作者: `Loyal-Young` ｜ 文件: 4 ｜ 改动: `+191 / -55`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/446
- 说明: - load audit records from the existing `/api/audit-logs` endpoint, including server-side filters and pagination - use the backend `PageResult` (`items`, `total`, `page`, `size`) and cleanup response (`deleted`) contracts - persist cleanup operations through `/api/audit-logs/clean
- 改动文件 (4):
 - `web/src/api/audit.test.ts` `+52/-0`
 - `web/src/api/ops.ts` `+21/-3`
 - `web/src/pages/ops/audit.tsx` `+80/-46`
 - `web/src/services/opsService.ts` `+38/-6`

### #445 [Studio] fix: connect system alerts page to APIs

- 作者: `Loyal-Young` ｜ 文件: 4 ｜ 改动: `+109 / -12`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/445
- 说明: - load system alerts through the existing `opsService` instead of page-level mock data; - persist acknowledgement and clearing actions through `/api/system-alerts`; - preserve the existing mock-mode workflow while adding loading and request-failure states; - add an API contract t
- 改动文件 (4):
 - `web/src/api/ops.test.ts` `+39/-0`
 - `web/src/api/ops.ts` `+2/-1`
 - `web/src/pages/ops/systemAlerts.tsx` `+57/-11`
 - `web/src/services/opsService.ts` `+11/-0`

### #432 [ISSUE #431] Add Prometheus range query adapter

- 作者: `Kris20030907` ｜ 文件: 14 ｜ 改动: `+954 / -70`
- 链接: https://github.com/apache/rocketmq-dashboard/pull/432
- 说明: RocketMQ Studio currently returns generated sample data from its metrics API, so it cannot be used to query real RocketMQ monitoring data. This change introduces a real Prometheus-compatible range query adapter as the foundation for the observability features proposed in #431. It
- 改动文件 (14):
 - `docs/api-spec.md` `+71/-5`
 - `server/src/main/java/com/rocketmq/studio/cluster/metrics/MetricDataVO.java` `+33/-2`
 - `server/src/main/java/com/rocketmq/studio/cluster/metrics/MetricQueryDTO.java` `+23/-0`
 - `server/src/main/java/com/rocketmq/studio/cluster/metrics/MetricsController.java` `+16/-1`
 - `server/src/main/java/com/rocketmq/studio/cluster/metrics/MetricsService.java` `+2/-2`
 - `server/src/main/java/com/rocketmq/studio/cluster/metrics/PrometheusException.java` `+34/-0`
 - `server/src/main/java/com/rocketmq/studio/cluster/metrics/PrometheusMetricsSource.java` `+235/-25`
 - `server/src/main/java/com/rocketmq/studio/cluster/metrics/PrometheusProperties.java` `+37/-0`
 - `server/src/main/java/com/rocketmq/studio/common/exception/GlobalExceptionHandler.java` `+28/-0`
 - `server/src/main/resources/application.yml` `+10/-0`
 - `server/src/test/java/com/rocketmq/studio/cluster/metrics/MetricsControllerTest.java` `+55/-0`
 - `server/src/test/java/com/rocketmq/studio/cluster/metrics/MetricsServiceTest.java` `+52/-28`
 - `server/src/test/java/com/rocketmq/studio/cluster/metrics/PrometheusMetricsSourceTest.java` `+321/-0`
 - `web/src/api/metrics.ts` `+37/-7`
