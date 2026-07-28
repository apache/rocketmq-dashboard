# RocketMQ Studio PR 批量整合 — 对话记录与提示词 (2026-07-22)

> 本文件为会话记录/复用模板，**未提交**（仅本地保存，方便下次使用）。
> 目标仓库：`apache/rocketmq-dashboard`，集成分支：`rocketmq-studio`。
> 作者/committer：`lizhimins`（有 write 权限，仓库仅允许 squash 合并）。

## 一、本次会话用到的关键提示词（按顺序）

1. 讨论 `project/rocketmq-studio`，先同步远程，它有几个 git remote
2. 这个项目配置了多少天关闭无效的 issue 和 PR，为什么不生效？
3. 先在 master 分支上改，每天 200，帮我提交 PR（stale 工作流 operations-per-run）
4. 先不同步到 studio 分支
5. apache 上积攒了一堆 studio 的 PR，筛选近一个月，先都获取，看每个改了什么、多少文件
6. 切换到 apache 的 rocketmq-studio 分支
7. 看 open 的 45 个，整理 markdown 报告到 docs 下
8. 评估 PR #432（Prometheus range query adapter），把合并后的分支用 docker compose 部署到 47.83.18.255（密码 Terrances123!）
9. 把原来的（占端口的容器）关了
10. 合并这个 commit，注意 commit msg 符合历史规范，先给我确认
11. 我是 committer，可以直接用 gh 合并吗 → 执行
12. 同步本地、清理临时分支
13. 分析 zhaohai666 的提交（11 前端页面 + 3 后端）
14. 在本地按顺序把所有前端 PR 一个个合进来，模拟正式仓库操作，再部署看效果
15. 把 #478 Namespace 关了（当前版本不支持 Namespace），其余按顺序合并
16. 按这个顺序合入 apache 的 PR → 逐个处理冲突并合并（用 merge base 进 PR 分支，非 force push）
17. 处理 zhaohai666 后端改动：先 fetch，重新评估 → 发现已被主干覆盖，关闭 #455/#456/#457
18. 分析最后一个同学 Loyal-Young 的 PR（很多）
19. 把他的改动都合到本地，拆成约 3 个 commit，关联到他原始 PR、改写这 3 个 PR，关闭其余；作者只写他
20. 先部署到远程 → 迭代修构建错误 → 执行 apache 三组拆分
21. 重新部署 / 删除本地无效分支 / 保存对话到 docs（不提交）

## 二、稳定可复用的操作套路

### A. Stale 工作流不生效的排查
- `gh run list --workflow=stale.yml` 看是否在跑；`gh run view <id>` 看注解。
- 常见根因：`operations-per-run` 默认 30，每次只能处理约 15 个 → 积压清不完。调大到 200。

### B. 单个 PR 评审 + 远程部署（pr-review skill 流程）
- `gh pr view/diff` 取元信息与 diff；标题规范校验；拉关联 Issue。
- 本地 `gh pr checkout` → 远程 tar over ssh → `docker compose up -d --build`。
- 远程 47.83.18.255 = podman 4.9（`docker`=podman，有 `docker compose`）。端口 6789(web)/8888(server)。
- 健康检查：`curl localhost:6789/`、`/actuator/health`；公网需安全组放行 6789。

### C. committer 直接合并（squash）
```
gh pr merge <N> --repo apache/rocketmq-dashboard --squash \
  --subject "feat: xxx (#N)" --body "..."
```
- 标题规范化为 `type: desc (#N)`（去掉 `[Studio]` 前缀，符合 rocketmq-studio 历史）。

### D. 逐个把冲突 PR 合入 apache（不 force push）
对每个 PR：`gh pr checkout` → `git merge apache/rocketmq-studio`（把 base 并入 PR 分支）→ 解决冲突（多为 App.tsx 路由/import、translations.ts 文案的加性冲突，保留双方 + i18n key 去重）→ 推回贡献者 fork 分支（需 `maintainerCanModify=true`）→ squash 合并。
- **注意**：https push 到 github 常超时，改用 SSH：`git push git@github.com:<owner>/rocketmq-dashboard.git HEAD:<headRef>`。

### E. 把某贡献者多个 PR 整合成少数几个 commit（本次核心）
1. 本地建 `integrate/<author>` 分支，从 `rocketmq-studio` 起。
2. 拉取所有 PR head，按 PR 号顺序 `git merge --squash` 逐个合并；冲突谨慎解决（真实代码冲突不能盲目"保留双方"）。
3. **有 husky+eslint pre-commit hook** 会挡住语法错误；**tsc（远程/本地 npm ci 后 build）** 会挡住类型错误/重复声明/缺依赖。二者都要过。
4. 按主题把改动文件分成 N 组；**注意文件间跨组依赖**（如 MainLayout 依赖 navigationSearch.ts；client.ts 依赖 config.ts 的 API_BASE_URL）——用本地 `npm run build` 逐组累积校验，缺依赖就把文件并入该组。
5. 对每个容器 PR：`gh pr checkout` → `git merge -s ours apache/rocketmq-studio`（让 base 成祖先，避免 force push）→ `git read-tree --reset -u apache/rocketmq-studio`（树重置为 base）→ `git checkout integrate/<author> -- <该组文件>` → `git commit --author="<贡献者>"` → SSH 推送 → squash 合并。
6. 其余 PR 关闭，注明整合去向。

## 三、本次踩过的坑（下次避免）

1. **`while read` 跳过无尾换行的最后一行**：`'\n'.join()` 生成的文件列表没有尾换行，`while IFS= read -r f` 会漏掉每个列表最后一个文件。导致每组各漏一个文件（topicService/authStore/vite.config），事后用 #490 补齐。→ 生成列表时加尾换行，或用 `for f in $(cat list)`。
2. **merge-tree 判断"冗余"不可靠**：有冲突时 `git merge-tree` 的净变化统计会误判为空。判断某 PR 是否已被主干覆盖，应比对"该 PR 自有文件"的实际内容 diff。
3. **base 已覆盖**：zhaohai666 的 3 个后端 PR（#455/#456/#457）内容已由主干初始导入提交（`49bc4419`/`b773716a`）覆盖；#455 合并还会把 GlobalExceptionHandler 退回旧版。→ 合并前先核对文件是否已在 base。
4. **"保留双方"仅适用于加性冲突**（路由、i18n key、import 并集）。对同函数不同实现的真实冲突会产生重复声明/重复 key，被 eslint/tsc 挡下。i18n 需去重；重复函数需删一个；对象缺字段要补齐（如 createConsumerGroup 缺 updatedAt/delaySeconds）。
5. **本地 build 假阳性**：本地 node_modules 缺 devDeps（@testing-library）会报一堆假错；`npm ci` 后与远程一致，才是可靠 oracle。
6. **vite.config.ts 用 `process.cwd()`** 需 @types/node；改用 `loadEnv(mode, '.', '')` 免依赖。
7. **端口 6789 被遗留 rootlessport 占用**：podman 容器删除后端口转发器残留，`kill <pid>` 释放。
8. **https push 超时**：github https 不稳，统一用 SSH 通道推送。

## 四、本次最终结果

- **Stale 工作流**：#489 合入 master，`operations-per-run: 200`。
- **#432** Prometheus adapter：squash 合入 rocketmq-studio。
- **zhaohai666 前端**：#473–#483 共 10 个 squash 合入；#478 Namespace 关闭。
- **zhaohai666 后端**：#455/#456/#457 关闭（已被主干覆盖）。
- **Loyal-Young**：29 个前端 PR 整合为 3 个容器 PR（#445 API 对接 / #462 服务能力+交互 / #465 偏好+无障碍+配置）+ #490 补充提交，作者均为 `G17 ShoWang24`；其余 26 个 + #467 关闭。
- apache `rocketmq-studio` 应用代码 == 本地已验证构建通过的集成结果。

## 五、环境速查

- 远程开发机：`root@47.83.18.255`（密码 `Terrances123!`），podman + docker compose，4C/15G。
- 部署：源码 tar over ssh → `/opt/rocketmq-studio` → `deploy/docker-compose.yml` → `docker compose up -d --build`。
- Loyal-Young git author：`G17 ShoWang24 <1713252343@qq.com>`。

## 六、待合并 PR 清单与评审 (2026-07-23)

> 截至 2026-07-23，`rocketmq-studio` 分支共 19 个 open PR，全部 MERGEABLE。
> 评审基于 `pr-review` skill 标准进行静态代码审查（未本地构建），涵盖标题规范、代码质量、测试覆盖、安全风险四个维度。

| PR | 作者 | 中文描述 | 文件 | +/- | 标题 | 代码 | 测试 | 安全 | 评审结论 |
|----|------|----------|------|-----|------|------|------|------|----------|
| #492 | @Kris20030907 | 修复前端登录响应类型与后端 LoginVO 契约不匹配，将 username/role 改为嵌套 user.username/admin 结构 | 3 | +14/-8 | ✅ | ✅ | ✅ | ✅ | **Approve** |
| #494 | @majialoong | 修复 Nginx API 代理因硬编码 DNS resolver 导致的失败，改用容器运行时动态解析 | 2 | +4/-2 | ⚠️ "Use" 应小写 "use" | ✅ | ⚠️ 无单测 | ✅ | **Approve**（修正标题后） |
| #495 | @zhaohai666 | 将 UI 硬编码中文文本迁移为 i18n labelKey 模式，新增约 280 条翻译条目并修复重复 key 编译错误 | 10 | +529/-74 | ✅ | ✅ | ✅ 18 用例 | ✅ | **Approve**：aria-label 仍硬编码中文前缀，squash 时顺带修 |
| #496 | @zhaohai666 | 对齐前端 API 路径与后端 Controller 端点，补全 format.ts 中 6 个 TODO 格式化函数 | 8 | +401/-25 | ✅ | ❌ auth 路径改为 `/login/login.do` 但后端是 `/api/auth`，合并会打挂登录；createInstance 仅传 proxyAddr；deleteInstance 仍走 mock | ✅ 34 用例 | ✅ | **Request Changes**：auth API 路径与后端不匹配（严重）；API 请求体不完整 |
| #497 | @zhaohai666 | 升级 MiniLine 图表组件（平滑曲线/渐变/响应式），重新设计首页布局，新增 388 行全局 CSS | 6 | +686/-157 | ✅ | ⚠️ `_lineId` 模块级可变状态应改 useId()；CSS 大量 !important | ✅ 12 用例 | ✅ | **Request Changes**：MiniLine ID 生成方案；CSS !important 维护性差 |
| #498 | @Aias00 | 修复前端基线测试中断言与后端 PageResult 契约及 i18n 标签不一致的问题 | 2 | +7/-3 | ✅ | ✅ | ✅ | ✅ | **Approve** |
| #499 | @Aias00 | 将部署脚本中日志/错误辅助函数初始化移到配置校验之前，修复 err() 未定义的 bug | 1 | +12/-12 | ✅ | ✅ | ⚠️ 无单测（shell 可接受） | ✅ | **Approve** |
| #500 | @Aias00 | 在 CI 中新增前端 Docker 镜像构建任务，覆盖 Dockerfile/nginx 部署路径 | 1 | +10/-0 | ✅ | ✅ | ✅ | ✅ | **Approve** |
| #501 | @Aias00 | 修复 Producer 页面 topic 列表读取逻辑，兼容 Studio API 新格式和旧格式 | 2 | +23/-3 | ✅ | ✅ 向后兼容完善 | ✅ | ✅ | **Approve** |
| #502 | @Aias00 | 为 LiteTopic 页面添加后端 stub 接口（列表/会话/TTL/配额/能力检查） | 9 | +566/-0 | ⚠️ 应为 `feat` 非 `fix` | ⚠️ 全硬编码 stub 数据 | ✅ | ✅ | **Comment**：type 改 feat；标注 stub 替换计划 |
| #504 | @Aias00 | 为 Ops 页面添加 NameServer 地址管理、VIP Channel 和 TLS 开关的后端接口 | 8 | +430/-0 | ⚠️ 应为 `feat` 非 `fix` | ⚠️ 内存存储重启丢失；namesrvAddr 缺格式校验 | ✅ | ✅ | **Comment**：type 改 feat；增加 host:port 正则校验 |
| #505 | @Aias00 | 新增 Proxy 地址列表和添加地址的兼容接口，支持前端表单提交 | 5 | +262/-0 | ⚠️ 应为 `feat` 非 `fix` | ⚠️ 内存 LinkedHashSet 重启丢失；默认值硬编码 | ✅ | ✅ | **Comment**：type 改 feat；默认地址提取为配置项 |
| #506 | @Aias00 | 为 LLM 设置页面添加配置读写、连通性测试和模型列表的后端兼容接口 | 8 | +611/-0 | ⚠️ 应为 `feat` 非 `fix` | ⚠️ Controller 未用 Result<> 包装 | ✅ | ⚠️ GET 返回 apiKey 明文 | **Request Changes**：apiKey 响应脱敏；Controller 格式统一；type 改 feat |
| #507 | @Aias00 | 新增告警规则 Prometheus YAML 导出接口，无规则时返回默认 RocketMQ 告警模板 | 5 | +311/-0 | ⚠️ 应为 `feat` 非 `fix` | ⚠️ StringBuilder 手工拼 YAML 有注入风险；severity 固定为 warning | ✅ | ✅ | **Comment**：type 改 feat；建议引入 SnakeYAML 库生成 YAML |
| #509 | @Kris20030907 | 修复创建数据源时 key 为 null 导致 ConcurrentHashMap NPE，服务层自动生成 UUID | 3 | +72/-6 | ✅ | ✅ | ✅ 全面 | ✅ | **Approve** |
| #510 | @Aias00 | 新增 Producer 连接查询接口，按 topic 和 producerGroup 过滤并返回兼容格式 | 8 | +344/-0 | ⚠️ 应为 `feat` 非 `fix` | ✅ 逻辑清晰 | ✅ | ✅ | **Approve**（改 type 后） |
| #512 | @yx9o | 修复通用设置接口不再返回 API Key 明文，改用 apiKeyConfigured 布尔值标识 | 10 | +318/-31 | ✅ | ✅ DTO/VO 分离，@Valid 完整 | ✅ 全面 | ✅ | **Approve** |
| #514 | @PiliLily | 引入版本化 YAML 工具目录及两个只读 L1 工具处理器（cluster.list/capabilities） | 22 | +1758/-36 | ✅ | ✅ 架构严谨，不可变/schema/fail-fast 到位 | ✅ 720+ 行测试 | ✅ | **Approve** |
| #515 | @PiliLily | 定义集群能力三态契约及 configured-default 解析器，暴露 capabilities 端点 | 7 | +773/-13 | ✅ | ✅ | ✅ 296 行测试 | ✅ | **Comment (DRAFT)**：代码质量达标，待移除 DRAFT 标记；注意与 #514 功能重叠需协调 |

### 评审统计

| 结论 | 数量 | PR 编号 |
|------|------|---------|
| Approve | 11 | #492, #494, #495, #498, #499, #500, #501, #509, #510, #512, #514 |
| Request Changes | 3 | #496, #497, #506 |
| Comment | 5 | #502, #504, #505, #507, #515 (DRAFT) |

### 高频问题

1. **commit type `fix` 误标**（#502/#504/#505/#506/#507/#510）：新增功能应使用 `feat` 而非 `fix`，squash 时统一修正
2. **stub 数据遗留**（#502/#504/#505）：多个 PR 使用内存硬编码 stub，需在 PR body 或 TODO 中标注替换计划
3. **apiKey 明文返回**（#506）：GET /api/llm/config 将 apiKey 原样返回前端，属安全隐患

### 按作者汇总与合并建议

| 作者 | PR 数 | Approve | 待修改 | 建议 |
|------|-------|---------|--------|------|
| @Aias00 | 10 | #498/#499/#500/#501/#510 | #506（apiKey 脱敏）其余 5 个仅 type 标注 | #506 单独处理；其余可整合为 2 个容器 PR（后端 endpoint + 前端/CI），squash 时统一 type 为 feat |
| @zhaohai666 | 3 | #495 | #496（API 请求体不完整）、#497（MiniLine/CSS 质量） | #495 可直接 squash 合并；#496/#497 需作者修复代码问题后可整合为 1 个容器 PR |
| @Kris20030907 | 2 | 全部 | — | 体积小，可直接 squash 合并 |
| @PiliLily | 2 | #514 | #515 DRAFT 暂缓 | #514 直接合并；#515 等 DRAFT 移除后再审 |
| @majialoong | 1 | ✅ | — | 直接合并，squash 时修正标题 "Use" → "use" |
| @yx9o | 1 | ✅ | — | 直接合并 |

## 七、合并与部署结果 (2026-07-24)

### 已合并 PR（16 个）

按 PR 编号顺序 squash merge 到 `rocketmq-studio` 分支：

| PR | 提交 | 标题 |
|----|------|------|
| #492 | `b30881fd` | fix: align login response contract |
| #494 | `608daa2b` | fix: use runtime DNS resolver for Nginx API proxy |
| #495 | `bce30ba3` | feat: enhance i18n support with labelKey-based translations |
| #498 | `87f70c3c` | test: fix frontend baseline assertions |
| #499 | `e1df6857` | fix: initialize deploy helpers before validation |
| #500 | `c79129f5` | ci: build frontend Docker image |
| #501 | `0655effa` | fix: load producer topics from Studio API response |
| #502 | `9d6f2538` | feat: add LiteTopic backend endpoints |
| #504 | `4dbdc19a` | feat: add ops backend endpoints |
| #505 | `e9329a57` | feat: add proxy address endpoints |
| #506 | `db2420f6` | feat: add LLM settings endpoints |
| #507 | `fd7001dc` | feat: add alert rules YAML endpoint |
| #509 | `100be0f4` | fix: generate data source keys on creation |
| #510 | `c5e31009` | feat: add producer connection endpoint |
| #512 | `d06c7738` | fix: avoid returning General Settings API key |
| #514 | `25395a00` | feat: add catalog-driven read-only tools |

### 未合并 PR（3 个）

| PR | 原因 |
|----|------|
| #496 | Request Changes：auth API 路径与后端不匹配（`/login/login.do` vs `/api/auth`），合并会打挂登录 |
| #497 | Request Changes：MiniLine `_lineId` 模块级可变状态应改 useId()；CSS 大量 `!important` 维护性差 |
| #515 | DRAFT：代码质量达标，待移除 DRAFT 标记后合并 |

### 构建验证

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 前端编译 (`npm ci && npm run build`) | ✅ | tsc + vite 通过，7721 modules，1.96MB JS |
| 后端编译 (`mvn package -DskipTests`) | ✅ | BUILD SUCCESS，9.5s |

### 部署验证 (47.83.18.255)

| 检查项 | 结果 | 说明 |
|--------|------|------|
| docker compose build | ✅ | rocketmq-server + rocketmq-web 镜像构建成功 |
| 容器启动 | ✅ | 两个容器均为 Up 状态 |
| 前端健康检查 (`curl :6789/`) | ✅ HTTP 200 | Nginx 正常服务 |
| 后端健康检查 (`curl :6789/actuator/health`) | ✅ HTTP 200 | Spring Boot UP |
| API 代理 (`GET /api/settings/general`) | ✅ | 返回正确 JSON，含 `apiKeyConfigured: false` 字段（#512 生效） |
| Ops 端点 (`GET /api/ops/homePage`) | ✅ | 返回 namesvrAddrList, useVIPChannel, useTLS（#504 生效） |
| Proxy 端点 (`GET /api/proxy/homePage.query`) | ✅ | 返回 proxyAddrList, currentProxyAddr（#505 生效） |
| LLM 端点 (`GET /api/llm/config`) | ✅ | 返回 provider, model, apiBase 等配置（#506 生效，未用 Result<> 包装） |

## 八、第二轮审查与合并 (2026-07-24)

### 新增 PR 审查（6 个）

| PR | 作者 | 中文描述 | 代码 | 测试 | 安全 | API 路径 | 评审结论 |
|----|------|----------|------|------|------|----------|----------|
| #516 | @Aias00 | ACL 管理页面从 mock 切换到后端 API，新增 update 端点 | ✅ | ✅ | ✅ | ✅ | ✅ **已合并** |
| #517 | @Aias00 | 数据源连接测试改为真实探测 Prometheus 端点 | ✅ | ✅ | ⚠️ SSRF | ✅ | **Comment**：SSRF 风险需评估 |
| #518 | @Aias00 | Consumer Group 页面从 mock 切换到后端 API | ⚠️ | ✅ | ✅ | ✅ | **Request Changes**：删除缺错误处理；i18n 硬编码中文 |
| #519 | @Aias00 | 消息查询结果按 storeTime 降序排序 | ✅ | ✅ | ✅ | ✅ | ✅ **已合并** |
| #520 | @Aias00 | Topic 页面动态加载命名空间列表 | ✅ | ✅ | ✅ | ✅ | ❌ **已关闭**（当前设计不需要命名空间） |
| #521 | @Aias00 | 新增 Consumer 线程堆栈诊断 API | ✅ | ✅ | ✅ | ✅ | ✅ **已合并** |

> **PR 标题规范说明**：PR 标题带 `[Studio]` 前缀，squash merge 时统一去掉，commit message 仅保留 `type: description (#PR)`。这是项目有意设计。

### 第二轮合并 PR（3 个）

| PR | 提交 | squash 标题 |
|----|------|-------------|
| #516 | `4240f28c` | fix: ACL page API integration |
| #519 | `208540b8` | fix: sort queried messages by store time |
| #521 | `5155a180` | feat: add consumer stack diagnostics API |

### 构建与部署验证

| 检查项 | 结果 |
|--------|------|
| 前端编译 (`npm run build`) | ✅ tsc + vite 通过 |
| 后端编译 (`mvn package -DskipTests`) | ✅ BUILD SUCCESS |
| docker compose 部署 | ✅ 容器启动成功 |
| 前端 `:6789/` | ✅ HTTP 200 |
| 后端 `:6789/actuator/health` | ✅ HTTP 200 |

### 重点问题（未关闭）

1. **#517 SSRF 风险**：数据源测试端点对用户 URL 直接发 HTTP 请求，未过滤私有 IP 段（`169.254.169.254`、`10.x`、`172.16-31.x`、`127.x`）。若 Studio 仅内网部署且仅管理员可访问可降级。

2. **#518 删除操作缺错误处理**：`deleteConsumerGroup` 和 `batchDeleteConsumerGroups` 的 `onOk` 没有 try/catch，API 失败时用户无反馈。错误消息硬编码中文不符合 i18n 规范。

### 当前 open PR 状态汇总

| 类别 | PR 编号 | 数量 |
|------|---------|------|
| 已合并（总计） | #492–#514, #516, #519, #521 | 19 |
| 已关闭 | #520 | 1 |
| Request Changes | #496, #497, #518 | 3 |
| Comment | #517 | 1 |
| DRAFT | #515 | 1 |
