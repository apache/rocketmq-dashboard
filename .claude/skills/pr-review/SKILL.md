---
name: pr-review
description: RocketMQ Studio 的 PR 评审助手。输入一个 GitHub PR 链接，自动用 gh 拉取 PR 及其关联 Issue，切出本地分支，编译前端与后端，用 docker compose 拉起整个项目（不改端口），并产出结构化的 PR 分析总结。当用户提到 评审 PR、review PR、看一下这个 PR、PR 分析、拉 PR 编译、检查 PR、pr review、审查合并请求 等场景时触发；即使用户只贴了一个 GitHub PR 链接，也应触发此 skill。
---

# RocketMQ Studio PR 评审

对 RocketMQ Studio（`apache/rocketmq-dashboard`，分支 `rocketmq-studio`）的一个 GitHub PR 做端到端评审：拉取 PR 与关联 Issue → 本地切分支 → 编译前后端 → docker compose 拉起 → 输出结构化分析总结。

## 前置条件

- 已安装并登录 `gh`（`gh auth status` 确认）。
- 已安装 `docker`（含 `docker compose`）、`node`(>=20)、`npm`、`mvn`(JDK 21)。
- 当前工作目录为 `project/rocketmq-studio`（项目根，含 `server/`、`web/`、`deploy/`）。

> 端口约定（**禁止修改**）：前端 6789（Nginx）、后端 8888（Spring Boot）、NameServer 9876、Broker 10911、Proxy 8080/8081。

## 输入

一个 PR 链接，例如：`https://github.com/apache/rocketmq-dashboard/pull/123`

从链接中解析出 PR 编号 `<PR>`（URL 最后一段数字）。

## 执行步骤

### 1. 拉取 PR 元信息

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

### 1.5 检查 PR 标题是否规范

规范来源：`docs/contributing.md`「提交规范」+ `README.md`「Commit Format」+ 本仓库既有 commit 历史，统一遵循 [Conventional Commits](https://www.conventionalcommits.org/)。

**格式**：`<type>: <description>`

- **type** 必须为以下之一（小写）：`feat`（新功能）、`fix`（修复 Bug）、`docs`（文档）、`refactor`（重构）、`test`（测试）、`chore`（构建/工具）、`perf`（性能）。
- type 后紧跟半角冒号 `:` 加一个空格，再接 **description**。
- description 用英文小写祈使句，简洁描述改动，结尾不加句号。
- 可选 scope：`type(scope): description`（scope 为小写模块名，如 `fix(web):`）。
- squash 合并后 GitHub 会在标题末尾追加 ` (#PR号)`，属正常现象，检查时应先剥离该后缀。

校验正则（先去掉可能存在的 ` (#N)` 尾巴）：

```bash
TITLE=$(gh pr view <PR> --repo apache/rocketmq-dashboard --json title -q .title)
CLEAN=$(printf '%s' "$TITLE" | sed -E 's/ \(#[0-9]+\)$//')
if printf '%s' "$CLEAN" | grep -Eq '^(feat|fix|docs|refactor|test|chore|perf)(\([a-z0-9-]+\))?: .+'; then
  echo "标题规范 ✅: $TITLE"
else
  echo "标题不规范 ❌: $TITLE"
fi
```

对照参考（本仓库历史）：`fix: align frontend API success response handling (#429)`、`feat: implement backend cluster management module`、`chore: initialize project with license and git config`。

不规范时在总结中明确指出问题（type 非法 / 缺冒号或空格 / 用了中文或首字母大写 / 结尾多余标点等）并给出建议标题。

### 2. 拉取关联 Issue（若有）

若 `closingIssuesReferences` 非空，或 PR 正文中出现 `#N` / `Closes #N` / `Fixes #N` 引用，逐个拉取：

```bash
gh issue view <ISSUE> --repo apache/rocketmq-dashboard \
  --json number,title,state,body,labels,url
```

用于判断 PR 是否真正解决了 Issue 描述的问题（需求对齐度）。

### 3. 本地切出评审分支

不要污染当前分支。用 `gh` 直接 checkout PR 分支（会自动创建本地分支）：

```bash
# 记录当前分支以便复原
git rev-parse --abbrev-ref HEAD
git stash push -u -m "pr-review-stash" 2>/dev/null || true

gh pr checkout <PR> --repo apache/rocketmq-dashboard --branch pr-review-<PR>
```

若因权限/fork 无法直接 checkout，退化为手动 fetch：

```bash
git fetch apache pull/<PR>/head:pr-review-<PR>
git checkout pr-review-<PR>
```

### 4. 编译后端

严格对齐 `server/Dockerfile` 的构建方式（`mvn package -DskipTests`，含 checkstyle 校验）：

```bash
cd server
mvn -B -ntp clean package -DskipTests
cd ..
```

- 编译失败 → 记录报错（编译错误 / checkstyle 违规），标记后端为 ❌，仍继续后续步骤并如实汇报。
- 通过 → 标记 ✅。

### 5. 编译前端

对齐 `web/Dockerfile`（`npm ci && npm run build`，即 `tsc -b && vite build`）：

```bash
cd web
npm ci
npm run build
cd ..
```

- 出现 TypeScript 类型错误或构建失败 → 标记前端 ❌，记录关键报错。
- 通过 → 标记 ✅。

### 6. docker compose 拉起项目

使用项目自带 compose 文件，**不修改任何端口**：

```bash
cd deploy
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

### 7. 复原工作区

评审完成后切回原分支并恢复暂存：

```bash
git checkout <原分支>
git stash pop 2>/dev/null || true
# 如需删除评审分支：git branch -D pr-review-<PR>
```

## 输出：PR 分析总结

用中文输出一份结构化 Markdown 总结，包含以下部分：

### 1. 概览
- 标题、作者、状态、源分支 → 目标分支、PR 链接。
- **PR 标题规范**：✅/❌（不规范时标出具体问题并附建议标题）。
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
- 提交规范：commit message 是否符合 Conventional Commits。

### 5. 风险与建议
- 潜在逻辑问题、边界情况、安全风险（如凭据明文、公网暴露）。
- 缺失的测试 / 文档 / i18n。
- 明确的改进建议（可执行、可定位到文件）。

### 6. 评审结论
给出倾向：**Approve** / **Request Changes** / **Comment**，并用一句话说明理由。

## 自由发挥补充能力

- **变更规模自适应**：大 PR（改动文件多）先按目录聚合概述再抽样精读核心文件；小 PR 可逐文件过。
- **checkstyle / lint 单独复核**：后端 `mvn checkstyle:check`，前端 `npm run lint`，把风格问题与逻辑问题分开汇报。
- **失败即止但汇报完整**：任一步失败不阻断总结，如实记录并继续能做的检查。
- **可选发布评审意见**：用户明确要求时，可用 `gh pr comment <PR> --repo apache/rocketmq-dashboard --body-file <file>` 或 `gh pr review <PR> --comment/--approve/--request-changes` 提交（默认只本地产出，不自动发布）。

## 注意事项

- **不修改端口**：任何环节都使用既有端口，不得改 compose / nginx / env 中的端口映射。
- **不污染主分支**：评审在独立分支进行，结束后复原。
- **只读默认**：默认不向 GitHub 写入评论/评审，除非用户明确要求。
- **凭据安全**：分析 diff 时若发现 AK/SK、password、token 等明文凭据，作为高风险项在总结中显著标注。
