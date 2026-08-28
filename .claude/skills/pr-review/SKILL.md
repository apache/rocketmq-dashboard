---
name: pr-review
description: RocketMQ Studio 的 PR 评审助手。输入一个 GitHub PR 链接，用 gh 只读拉取 PR 及关联 Issue，在 detached 临时 worktree 中检查和构建，并产出结构化分析总结。当用户提到评审 PR、review PR、看一下这个 PR、PR 分析、拉 PR 编译、检查 PR、pr review、审查合并请求等场景时触发；即使用户只贴了一个 GitHub PR 链接，也应触发此 skill。
---

# RocketMQ Studio PR 评审

对 RocketMQ Studio（`apache/rocketmq-dashboard`，分支 `rocketmq-studio`）的一个 GitHub PR 做端到端评审：拉取 PR 与关联 Issue → 创建隔离 worktree → 编译前后端 → 按需启动 docker compose → 输出结构化分析总结。默认只读，不修改贡献者分支、当前工作树或 GitHub 状态。

## 前置条件

- 已安装并登录 `gh`（`gh auth status` 确认）。
- 已安装 `docker`（含 `docker compose`）、`node`(>=20)、`npm`、`mvn`(JDK 21)。
- 当前工作目录为项目根目录（含 `server/`、`web/`、`deploy/`）。
- 后端构建需要 JDK 21（`JAVA_HOME` 指向 JDK 21，或使用 `mvn -B -ntp` 配合系统 JDK 21）。

> 端口约定（**禁止修改**）：前端 6789（Nginx）、后端 8888（Spring Boot）、NameServer 9876、Broker 10911、Proxy 8080/8081。

## 输入

一个 PR 链接，例如：`https://github.com/apache/rocketmq-dashboard/pull/123`

从链接中解析出 PR 编号 `<PR>`（URL 最后一段数字）。

## 标准流程（Pipeline）

评审按以下 8 个阶段顺序执行。每个阶段独立产出结果，任一阶段失败不阻断后续步骤，但需在总结中标记 ❌。

```
Stage 1  拉取元信息        gh pr view → JSON + diff
Stage 2  标题一致性检查     仓库显式规则 + 近期合并历史
Stage 3  关联 Issue        gh issue view（若有 Closes/Fixes 引用）
Stage 4  创建隔离工作树     fetch PR head → detached 临时 worktree
Stage 5  只读预检           检查 diff、构建输入与工作树状态
Stage 6  编译后端           mvn package -DskipTests（含 checkstyle）
Stage 7  编译前端           npm ci && npm run build（tsc + vite）
Stage 8  Docker 部署        docker compose up -d --build + 健康检查
```

**完成后**：移除干净的临时 worktree；当前工作树从始至终不切分支、不 stash。

---

## 各阶段详细步骤

### Stage 1: 拉取 PR 元信息

```bash
gh pr view <PR> --repo apache/rocketmq-dashboard \
  --json number,title,author,state,baseRefName,headRefName,url,body,additions,deletions,changedFiles,labels,commits,files,closingIssuesReferences
```

重点关注：
- `baseRefName` 是否为 `rocketmq-studio`（目标分支应为它，否则在总结中提示）。
- `files` / `changedFiles` / `additions` / `deletions`：变更范围与体量。
- `closingIssuesReferences`：PR 声明会关闭的 Issue（`Closes #N`）。

同时拉取 diff 供后续分析：

```bash
gh pr diff <PR> --repo apache/rocketmq-dashboard > /tmp/pr-<PR>.diff
```

### Stage 2: 检查 PR 标题一致性

先读取仓库实际存在的 `CONTRIBUTING*`、`.github/pull_request_template*`、`README*` 和目标分支说明。不要引用不存在的文件，也不要把 README 中的 commit 约定直接宣称为 PR 标题硬规则。

若没有明确 PR 标题规则，再只读抽样近期合并记录：

```bash
gh pr list --repo apache/rocketmq-dashboard --base rocketmq-studio \
  --state merged --limit 30 --json number,title,mergedAt,url
```

当前常见形式为关联 Issue 后使用 Conventional Commit 风格，例如
`[ISSUE #123] fix(scope): concise description`；历史一致性只能作为建议，不能伪装成强制政策。
检查 type、可选 scope、描述清晰度，以及标题中的 Issue 是否与正文或
`closingIssuesReferences` 一致。只有违反仓库显式规则时标记 ❌；无显式规则但偏离近期惯例时标记 ⚠️，并给出有证据的建议标题。

### Stage 3: 拉取关联 Issue（若有）

若 `closingIssuesReferences` 非空，或 PR 正文中出现 `#N` / `Closes #N` / `Fixes #N` 引用，逐个拉取：

```bash
gh issue view <ISSUE> --repo apache/rocketmq-dashboard \
  --json number,title,state,body,labels,url
```

用于判断 PR 是否真正解决了 Issue 描述的问题（需求对齐度）。

### Stage 4: 创建隔离 worktree

记录当前工作树状态但不要修改它。将 PR head 拉到临时引用，并以 detached HEAD 创建 worktree：

```bash
git status --short
REVIEW_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/rocketmq-pr-<PR>.XXXXXX")
git fetch upstream pull/<PR>/head
git worktree add --detach "$REVIEW_ROOT" FETCH_HEAD
cd "$REVIEW_ROOT"
```

若仓库没有 `upstream`，先从 `git remote -v` 选择指向 `apache/rocketmq-dashboard` 的只读远端。不要 fetch 到贡献者分支，不要使用 `gh pr checkout`、`git checkout`、stash 或 reset 改动当前工作树。

### Stage 5: 只读预检

核对 PR diff、构建文件和目标分支的基线差异。发现基线本身存在问题时，将其作为“非本 PR 引入”的证据记录；禁止用 `sed` 或其他命令修改被评审内容以换取构建通过。

```bash
git status --short
git diff --check "upstream/rocketmq-studio...HEAD"
git diff --stat "upstream/rocketmq-studio...HEAD"
```

### Stage 6: 编译后端

严格对齐 `server/Dockerfile` 的构建方式（`mvn package -DskipTests`，含 checkstyle 校验）：

```bash
cd server
mvn -B -ntp clean package -DskipTests
cd ..
```

- 编译失败 → 记录报错（编译错误 / checkstyle 违规），标记后端为 ❌，仍继续后续步骤并如实汇报。
- 通过 → 标记 ✅。

### Stage 7: 编译前端

对齐 `web/Dockerfile`（`npm ci && npm run build`，即 `tsc -b && vite build`）：

```bash
cd web
npm ci
npm run build
cd ..
```

- 出现 TypeScript 类型错误或构建失败 → 标记前端 ❌，记录关键报错。
- 通过 → 标记 ✅。

### Stage 8: Docker 部署

使用项目自带 compose 文件，**不修改任何端口**：

```bash
cd deploy
docker compose down 2>/dev/null || true
docker compose up -d --build
cd ..
```

启动后做健康检查（不改端口）：

```bash
# 前端（Nginx）
curl -fsS -o /dev/null -w "web=%{http_code}\n" http://127.0.0.1:6789/ || echo "web 未就绪"
# 后端 actuator（经前端 nginx 反代或容器内），若暴露则直接探测
curl -fsS -o /dev/null -w "api=%{http_code}\n" http://127.0.0.1:6789/actuator/health || echo "api 未就绪"
docker compose -f deploy/docker-compose.yml ps
```

评审结束后清理（询问用户或默认保留，视会话上下文而定）：

```bash
docker compose -f deploy/docker-compose.yml down
```

---

## 清理隔离 worktree

完成评审后确认没有人为源码修改，再从原仓库移除临时 worktree。若构建只产生 ignored 制品，`git worktree remove` 可清理该隔离目录；若存在非预期源码差异，先报告并保留现场，不要强制删除。

```bash
git status --short
cd -
git worktree remove "$REVIEW_ROOT"
```

---

## 输出：PR 分析总结

用中文输出一份结构化 Markdown 总结，包含以下部分：

### 1. 概览
- 标题、作者、状态、源分支 → 目标分支、PR 链接。
- **PR 标题一致性**：✅/⚠️/❌（区分显式规则和历史惯例，并附证据）。
- 变更体量：`+additions / -deletions`，改动文件数。
- 关联 Issue：编号、标题、链接（若有）。

### 2. 需求对齐
- 简述 PR 目标（来自正文）。
- 若有关联 Issue，逐条比对 Issue 诉求与 PR 实现，判断是否覆盖 / 部分覆盖 / 偏离。

### 3. 构建与运行结果
用表格汇总：

| 检查项 | 结果 | 说明 |
|---|---|---|
| 后端编译 (`mvn package`) | ✅/❌ | 关键报错摘要 |
| 前端编译 (`npm run build`) | ✅/❌ | 关键报错摘要 |
| docker compose 启动 | ✅/❌ | 容器状态 / 端口 6789、8888 |
| 前端健康检查 | ✅/❌ | HTTP 状态码 |

### 4. 变更分析
- 按模块归类改动（前端页面/组件、后端 controller/service/domain、部署、文档等）。
- 结合六边形架构（server 用 ArchUnit 约束）判断分层是否合理。
- i18n：新增前端文案是否中英文双语（`web/src/i18n/`）。
- 提交规范：commit message 是否符合仓库 README 的 Conventional Commits 约定；若标题关联 Issue，同时核对编号一致性。

### 5. 风险与建议
- 潜在逻辑问题、边界情况、安全风险（如凭据明文、公网暴露）。
- 缺失的测试 / 文档 / i18n。
- 明确的改进建议（可执行、可定位到文件）。

### 6. 评审结论
给出倾向：**Approve** / **Request Changes** / **Comment**，并用一句话说明理由。

---

## 自由发挥补充能力

- **变更规模自适应**：大 PR（改动文件多）先按目录聚合概述再抽样精读核心文件；小 PR 可逐文件过。
- **checkstyle / lint 单独复核**：后端 `mvn checkstyle:check`，前端 `npm run lint`，把风格问题与逻辑问题分开汇报。
- **失败即止但汇报完整**：任一步失败不阻断总结，如实记录并继续能做的检查。
- **可选发布评审意见**：用户明确要求时，可用 `gh pr comment <PR> --repo apache/rocketmq-dashboard --body-file <file>` 或 `gh pr review <PR> --comment/--approve/--request-changes` 提交（默认只本地产出，不自动发布）。

## 注意事项

- **不修改端口**：任何环节都使用既有端口，不得改 compose / nginx / env 中的端口映射。
- **不污染任何现有分支**：评审在 detached 临时 worktree 进行；禁止 stash、checkout、reset 或自动修复被评审内容。
- **只读默认**：默认不向 GitHub 写入评论/评审，除非用户明确要求。
- **凭据安全**：分析 diff 时若发现 AK/SK、password、token 等明文凭据，作为高风险项在总结中显著标注。
