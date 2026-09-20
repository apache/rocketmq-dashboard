# AI Agent 托管架构

> 本文记录 RocketMQ Studio 的 AI 对话系统**为什么**是现在这个形状。面向 reviewer 与后续维护者：
> 实现细节看代码，这里只讲取舍与实测依据。所有标「实测」的结论都来自 2026-09-17 在真实环境里
> 跑 `claude` + `rmqctl` 抓到的原始 `stream-json`（fixture 见
> `server/src/test/resources/ai/claude-stream-capture.jsonl`，278 行 / 2 个 run，字节精确未改写）。

## 1. 一句话

**Studio 只负责托管一个通用 Agent CLI，并把 RocketMQ 的工具能力经 `rmqctl` 这个独立进程提供给它。**
Studio 自己不再实现工具调用循环，也不把工具直接塞进 JVM 内的对话逻辑。

## 2. 运行时环路

```
浏览器 ──SSE──> Studio Server（托管方）
                  │  ProcessBuilder 拉起
                  ├─> claude -p <prompt> --model <m> [--resume <sid>]
                  │     --mcp-config <ws>/mcp.json --strict-mcp-config
                  │     --allowedTools mcp__rocketmq-studio
                  │     --output-format stream-json --verbose --include-partial-messages
                  │        │ 按 mcp.json 拉起 stdio MCP server
                  │        └─> rmqctl mcp stdio --config <ws>/rmqctl.yaml --instance-id <id>
                  │               │ rmq-hmac-sha256 签名
                  │               └─HTTP─> http://127.0.0.1:8888/api/mcp
                  │                          │ ToolExecutionService + 过滤器链
                  │                          └─> RuntimeAdminClientResolver ─> RocketMQ
                  └─ 解析 stream-json → 事件 → 落库 + 推给 SSE 观察者
```

## 3. 诚实定性：`rmqctl` 不是独立的 RocketMQ Agent

这一点必须讲清楚，否则 reviewer 会按错误的模型理解这套设计。

`rmqctl` 只认两个 HTTP 路径——`/api/mcp` 与 `/api/mcp/tools/call`
（`rmqctl/internal/studio/client.go`），它的 `go.mod` 里没有任何 RocketMQ 依赖，没有 nameserver
参数，没有 remoting 客户端。**它自己连不上任何 RocketMQ 集群。**

所以准确的描述是：`rmqctl` 是一个**带签名、绑定实例、带风险闸门的 MCP 传输壳**，Studio 托管的
Agent 只是它的第一个客户。环路里最后一跳又回到了 Studio 自己。

### 这个形状真实买到了什么

1. **工具路径唯一。** 同一套能力面、身份模型、风险闸门与审计链路，同时服务于：Studio 托管的
   Agent、开发者手敲的 `rmqctl topic list`、以及任何外部 Agent（Claude Desktop / Cursor / Qoder，
   直接粘 `rmqctl mcp config` 的输出即可）。Studio 不需要第二套进程内工具桥。
2. **可替换性是真的。** 把 `claude` 绑到 Studio 上的东西只有一个 MCP config JSON 加两个环境变量。
   「Studio 托管一个 Agent」与「别的系统托管一个 Agent」因此是**同一种部署形态**——这正是这套设计
   的目的，而且它确实达成了，只是达成方式不是「rmqctl 独立于 Studio」。
3. **零新增鉴权面。** `rmqctl/internal/studio/auth.go` 已经实现了服务端 `McpAuthenticator` 要求的
   `rmq-hmac-sha256` 六行 canonical request；`isLoopbackHost` 已经放行 `http://127.0.0.1:8888`。
   容器内回连不需要任何传输层改动。
4. **进程级爆炸半径。** 工具访问由一个独立进程中介，该进程有自己的凭据作用域与实例绑定
   （`--instance-id` 烘进 argv，Agent 改不了），而不是 JVM 内部调用——后者可能被诱导越权。

### 它没有买到什么

- **延迟**：每次工具调用多一个进程 + 一次容器内 HTTP 跳。
- **独立性**：Studio 挂了 `rmqctl` 就无用。
- **隔离**：工具最终仍在拉起 Agent 的同一个 JVM 与同一个容器里执行，没有租户或网络隔离。

### 已否决的替代方案

把 MCP server 直接以 `{"type":"http","url":"http://127.0.0.1:8888/api/mcp"}` 注册进
`--mcp-config`，配一个会话级 bearer token。这样少一跳、少一个 Go 构建阶段，但：

- 要在 `/api/mcp` 上开**第二条鉴权路径**（现在 `McpAuthenticationFilter` 只认 HMAC）；
- 丢掉 `rmqctl/cmd/catalog.go` 里 confirm-token 那套 L2/L3 风险闸门。

两条都比省掉的一跳更贵，所以没做。记录在此，免得后来人重新提。

## 4. 实测约束（实现必须遵守，全部来自真实抓取）

| 约束 | 实测依据 | 不遵守的后果 |
|------|---------|-------------|
| **`--allowedTools mcp__rocketmq-studio` 必需** | 不加时工具调用被**静默自动拒绝**：无提示、无报错、`is_error:false`、进程 exit 0，只在 `result.permission_denials` 里留一条记录，模型转而请人类批准 | 最危险的失败模式：日志全绿、UI 显示「agent 说它没权限」。因此解析器必须把非空 `permission_denials` 投影成一条 WARN 事件 |
| **`--model` 必须总是传** | 不传时 claude 默认 `claude-opus-4-8[1m]`，网关返回 HTTP 400 `Model not exist.`，表现为 `result` 帧 `is_error:true, api_error_status:400` + exit 1 | 整个 run 失败，且错误信息藏在一个合成 assistant 帧里 |
| **工具名被清洗，点号不存活** | `rmq.topic.list` 暴露为 `mcp__rocketmq-studio__rmq_topic_list`；40 个 MCP 工具里含点号的数量 = **0** | 见下一行 |
| **规范名从 `tool_use_meta` 取，不要反推下划线** | assistant 帧带 `"tool_use_meta":[{"id":"toolu_…","display_name":"rmq.instance.capabilities","server_display_name":"rocketmq-studio"}]` | 反推有歧义：`rmq_message_query_by_topic` 既可能是 `rmq.message.query.by.topic` 也可能是 `rmq.message.query_by_topic`；且 `-` 也被清洗成 `_`（`rmq.group.reset-offset` → `rmq_group_reset_offset`），反推必然出错 |
| **`--resume` 需要稳定的 HOME *和* cwd** | 状态文件在 `$HOME/.claude/projects/<cwd 把 / 换成 ->/<session-id>.jsonl`。同 sid 换 cwd → exit 1；同 sid 同 cwd 换 HOME → exit 1 | resume 静默失效，每轮都退化成单轮 |
| **resume 失败的精确判据** | exit 1；stderr 单行 `No conversation found with session ID: <id>`；stdout 无 init 帧、只有一个 `subtype=error_during_execution` 的 result 帧且带 `errors[]` | 容器重启会清 `/tmp`，不识别这个错误会话就永久坏掉。识别后应不带 `--resume` 重试一次并清空 `runtime_session_id` |
| **`session_id` 第一帧就有** | `system`/`init` 帧即携带，`result` 帧里还有一份 | 只从 `result` 取的话，run 在 `result` 之前被停止就丢了 sid，下一轮无法 resume |
| **两种非 Anthropic 的 `system` 子类型要容忍** | `status`（`{"status":"requesting"}`）与 `thinking_tokens`（`{"estimated_tokens":N,…}`，run1 里出现 **51 次**） | 会全部命中「未知消息类型」守卫，一次 run 刷 51 条 WARN |
| **`tool_result` 有两份** | `type:"user"` 帧里既有 `message.content[].tool_result`，又有顶层 `tool_use_result` 重复字段 | 只取一份，否则每次工具调用落两条事件 |
| **`tool_result.is_error` 成功时字段缺失** | 抓取里两条 tool_result 的 `is_error` 都是缺失而非 `false` | 判定必须写成「缺失或 false 即成功」 |
| **`--config` 必需、yaml 必须 0600** | 容器内无 `~/.rmqctl/config.yaml`，省略 `--config` 得到 `no current context is selected`；更宽的权限被 `permissions_unix.go` 拒绝 | rmqctl 起不来 |
| **mcp.json 不需要 `env` 块** | 在 claude 进程环境里导出的变量被 rmqctl 子进程继承（MCP stdio server 继承 agent 环境） | 少写一处配置；但 yaml 里的 `env:NAME` 引用名必须与实际导出的变量名一致 |
| **`--strict-mcp-config` 必需** | — | 否则 claude 还会加载 `~/.claude.json` 与 user scope 的 MCP server，在容器里是不可控暴露面 |

delta 实测分布（run1 + run2）：`thinking_delta` 67、`text_delta` 80、`input_json_delta` 10、
`signature_delta` 4。`signature_delta` 是密码学签名，**永不渲染、永不落库**。`thinking_delta` 的存在
就是「思维链」需求能真实满足的依据——它来自模型，不是 prompt 改写。

## 5. 数据模型：Conversation ⊃ Run ⊃ Event

三层结构与 OpenAI Assistants 的 Thread ⊃ Run、LangGraph 的 thread ⊃ run 同形：会话容器 ⊃ 一次
Agent 循环 ⊃ 一条时间线条目。不带多租户 `tenant_id`、`shareScope`、`ownerPod`/`leaseUntil`
——Studio 是单实例开源部署，用不上。

| 表 | 一行代表 | 关键列 |
|----|---------|-------|
| `rmq_ai_conversation` | 一个对话容器 | `owner`、`instance_id`（= `rmqctl --instance-id`）、`runtime_session_id`（供 `--resume`）、`last_seq`（**只是缓存**，权威值是 `MAX(rmq_ai_event.seq)`） |
| `rmq_ai_run` | 一次 Agent 循环（= 一轮） | `turn`、`status`、`stop_reason`、**准入时固化的 `engine`/`model` 快照**、`runtime_session_id`/`resumed_from`、token 计数、`start_seq`/`end_seq` |
| `rmq_ai_event` | 一条时间线条目 | `seq`（会话内严格单调，同时是重连游标）、`type`、`payload`（`TimelineEvent` 的 JSON） |

**没有独立的 message 表**——用户消息是 `{type:"user"}` 事件，助手文本是 `{type:"text"}`，推理是
`{type:"thinking"}`，全部是 event 行。这是这套数据模型里最重要的一个决定。

`rmq_ai_event` 上的 `conversation_id` 与 `turn` 都是**故意的冗余**：让整会话时间线成为一次带索引的
过滤查询，不必先列 run 再逐 run 聚合，也不必 join。

`engine`/`model` 快照在 run 上而不是只在 conversation 上，是为了让历史**保持真实**：用户改了设置之后，
回看旧对话仍能看到当时实际用的是什么。

`payload` 用 `MEDIUMTEXT` 不用 MySQL `json`：`json` 存二进制 blob，任何走不到索引的 `ORDER BY` 都会把
整个值物化进 `sort_buffer_size`。同理，时间线查询**故意不用 SQL `ORDER BY`**，
而是走 `uk_ai_event_conversation_seq` 过滤后在内存里排序。

## 6. 事件契约：三种词汇，一个渲染目标

| 层 | 用途 | 特点 |
|----|------|------|
| `AgentEvent` | CLI provider 的 SPI 输出 | provider 中立，**永不序列化** |
| `LiveEvent` | SSE 线上载荷 | delta 导向、瞬时、高频 |
| `TimelineEvent` | 落库载荷 | 已合并、忠实记录「当时显示了什么」 |

`LiveEvent` 与 `TimelineEvent` 都归约成同一个 `RenderBlock[]`：直播走 `reduceLiveBlocks`，回看走
`foldTimelineBlocks`。**UI 因此永远不知道自己在看直播还是回放历史**——这是「思维链」需求能同时
在两种模式下正确显示的结构性原因，也是 `render/equivalence.test.ts` 要钉住的性质。

名字位移：`text_delta`→`text`、`tool_start`→`tool_use`、`tool_done`→`tool_result`。
`run_started`/`run_finished` 只在 live 侧（run 行才是真相），但终态 `run_status` **要**落库，
这样重载的会话不必 join run 表也能显示「已停止」。

**一个解析器，两种输出**：`AgentEventProjector` 对每个 `AgentEvent` 产出「一个可空的 `LiveEvent` +
一个可空的 `TimelineEvent` 列表」。同一条上游消息可能只产 live、只产落库、两者都产或都不产，而且
两侧内容**故意不同**：delta 只走 live（落库侧由缓冲写在合并边界产出一条完整 `text`）；工具输出在
落库侧截到 32 KiB 并保留 `outputBytes`/`truncated`。它对 sealed 类型做穷尽 switch 且不写 `default`，
新增子类型即编译错误。

`ThinkingSource = {MODEL, ENHANCE}` 这一个字段修掉了一个真实的错标 bug：改造前 UI 把 **prompt 增强
改写**渲染在标题为「思维链」的折叠块里。两种来源的 thinking **只在 source 相同时才合并**，否则就是
把改写混进模型推理，正好复刻原 bug。

跨语言防漂移不靠 codegen 也不靠共享模块（对 ASF 项目都太重），而靠**一份提交的 fixture**
`server/src/test/resources/ai/ai-event-contract.json`：Java 的 `AiEventContractTest` 与 TS 的
`aiEvents.contract.test.ts` 读同一个文件，各自断言类型集合与示例。任一侧漂移，另一侧 CI 变红。

## 7. 生成与 HTTP 连接解耦

SSE 流是**观察者，不是所有者**。今天（改造前）`LlmSseSession` 把 `emitter.onCompletion/onTimeout/
onError` 接到 `cancel()`，TCP 一断就杀 worker。改造后：事件先落库，run 在自己的 executor 上跑完，
客户端断开不影响生成，重连用 `GET /api/ai/runs/{id}/stream?after=<seq>` 从 DB 回放再接着 tail。

代价要说清楚：关掉标签页后 Agent 会跑到完，由 CLI 超时与孤儿 run 清扫兜住。换来的是刷新不丢、
崩溃可恢复、以及「run 在没有客户端时也能完成」这个可验证性质。

## 8. 取消

三层，全部落在 `status=STOPPED/FAILED`：

1. **显式进程注册**——`CliAgentProvider` 在 `start()` 后把 `Process` 交给 `AgentRunHandle`，停止时
   直接杀进程，不依赖线程中断恰好落在可中断点上（改造前的缺口：stdout drain 跑在不被中断的虚拟线程上）。
2. **`ProcessHandle.descendants()` 杀孙进程**——`rmqctl` 是 `claude` 的子进程，`destroyForcibly()`
   打在 `claude` 上杀不到它。顺序必须**先 descendants 后 parent**，否则 `claude` 会重拉。
3. **先优雅**——`destroy()`(SIGTERM) → 等 `stop-grace`（默认 3s）→ 硬杀。`claude` 收到 SIGTERM 会
   flush 它的 `result` 帧，于是 `session_id` 保得住，**停止之后下一轮 `--resume` 仍然可用**。这是
   相对硬杀的真实收益，也是优先优雅的理由。

`AbortReason` 是判别的（`USER_STOP` / `SHUTDOWN` / `TIMEOUT` / `ORPHANED`），好让 `@PreDestroy` 的
drain 写 `SHUTDOWN` 而不是把一次重新部署误标成用户取消。停止接口带 `expectedRunId` 语义：已终态则
200 空操作，当前活跃的是另一个 run 则 409 —— 陈旧的停止请求必须 fail closed，绝不能杀掉新 run。

## 9. 安全不变量

- 凭据**只**出现在子进程环境里（`RMQ_AI_ACCESS_KEY` / `RMQ_AI_SECRET_KEY`）。绝不在 argv、绝不在
  `rmqctl.yaml`（那里只写 `env:NAME` 引用）、绝不在任何 DB 列、绝不在任何日志行。
- 实例绑定靠 `--instance-id` **烘进 mcp.json 的 argv**：Agent 能调工具但改不了 argv，而 rmqctl
  拒绝从任何地方默认这个值。
- `CliProcessEnvironment` 的白名单**一个都不加**。provider 提供的 env 本来就绕过白名单且后应用故胜出
  （这是既有行为，已补 javadoc 说明，否则读起来像个洞）；`PATH`/`HOME`/`TMPDIR`/`SSL_CERT_*`/
  `NODE_EXTRA_CA_CERTS`/代理变量都已在白名单里。不加 `RMQCTL_CONFIG`——我们显式传 `--config`。
- 内置工具（`Bash`/`Read`/`Write`/`Edit`/`Glob`/`Grep`/`Task`/`WebFetch`/`WebSearch`/`TodoWrite`/
  `NotebookEdit`）**继续全部禁用**。放行的只有 MCP 工具。Agent 不该在服务端容器里跑 shell 或读文件系统。
- L2/L3 变更走工具协议自身的两步：dry-run 返回 `confirm_token`，apply 消费它。服务端由
  `ToolMutationFilter` + `ToolTokenService` + `studio.ai.allow-l3-tools`（默认 false）强制。
  **因此托管 Agent 默认无法执行 L3 工具**，除非部署方显式打开开关；系统提示词已交代它必须先预览、
  展示计划、再带 token 执行。
- **发起 run 的端点必须是 admin-only**，因为一次 run 会跨越权限边界（见 §9.1）。

### 9.1 为什么「发起 run」是一道权限边界

上面几条讲的都是**工具级**闸门。还有一层是**调用方角色**，两者不能互相替代。

Studio 只有两个角色：admin 与非 admin（reader）——`AuthInterceptor.requiresAdmin(...)` 返回真即需要
admin，没有独立的 writer 角色。既有的角色门控长这样：

| 路径 | 角色校验 | 结果 |
|:--|:--|:--|
| reader 走 Tool Playground → `POST /api/ai/tools/{name}/execute` | `requiresAdmin` → `isReaderAccessibleToolPath` 只放行 `ToolDefinition.isLowRiskReadOnly()` 的工具 | reader **不能**执行 L2/L3 变更 |
| 任何持有实例 AK/SK 的调用方 → `POST /api/mcp/tools/call` | `AuthWebConfig` 把 `/api/mcp/**` **整个排除**在 `AuthInterceptor` 外，只剩 `ToolMutationFilter` | L3 需 `allow-l3-tools` + break-glass + reason；**L2 只需 preview→apply，无角色校验** |

MCP 那条路没有角色校验是**设计如此**：它靠实例 AK/SK 鉴权，持有该密钥即被视为对该实例可信，
与开发者在本机跑 `rmqctl` 是同一套信任模型。

问题出在托管 Agent 恰好把这两条路接了起来：run 的工具调用是用 `InstanceCredentialResolver`
**在服务端解析出的实例 AK/SK** 去签名的，**不是发起者的身份**。于是——

> 一个在 UI 上被 `isLowRiskReadOnly()` 挡住、无法执行 L2 工具的 reader，
> 可以让托管 Agent 替他执行 L2 变更。

所以 `POST /api/ai/conversations`、`POST /api/ai/conversations/{id}/messages`、
`POST /api/ai/runs/{id}/stop`、`PATCH /api/ai/conversations/{id}`、`DELETE /api/ai/conversations/{id}`
一律要求 admin；所有 GET（会话列表、详情、时间线、`agent-capabilities`、`rmqctl-config`，
以及只读性质的 `GET /api/ai/runs/{id}/stream` attach/重连）对任何已登录用户开放，仍受 owner 过滤。

**「旧的 `POST /api/ai/chat` 是 reader 可用的，所以它的替代端点也该 reader 可用」——这个推理是错的。**
旧 chat 跑的 claude 被 `--disallowedTools` 禁掉了全部内置工具，也没有 MCP，是个纯文本生成器；
它无法触达 RocketMQ，所以对 reader 开放是安全的。新端点会驱动工具，二者不等价。

**接受的代价**：reader 从此不能发起 AI 对话（只能读自己已有的会话与时间线）。这是有意的取舍——
另一条路是把发起者角色一路透传到工具过滤链、让 reader 的 run 只暴露只读工具，那能同时保住
「reader 可对话」与「reader 不可变更」，但要在本次重构里新增一套角色透传设计。前端据
`admin` 字段提前禁用 composer 并说明原因，避免让人撞上一个神秘的 403。

**一个容易踩的实现细节**：不能靠把新端点加进 `READER_POST_PATHS` 来表达「对 reader 开放」。
那个集合匹配**整条路径**，而 `/api/ai/conversations/{id}/messages` 带 id，永远进不去；
且 `requiresAdmin` 对**非 POST 且非 GET** 的动词（`PATCH`/`DELETE`）一律要求 admin。
反过来，若为此加一个「整个 conversation/run 前缀都免角色校验」的豁免，就会把上面那道边界一起抹掉。
正确做法是让 GET 走既有的 `isAdminOnlyGetPath` 分支（AI 的读端点不在那份名单里，天然对 reader 开放），
变更类动词落到默认的 admin 要求上。这一层必须由 `AuthInterceptorTest` 钉住，不能只写在文档里。

## 10. 降级

有**两条**降级路径，都由 `RmqctlWorkspace` 决定、都把会话退化成纯聊天：

| 触发条件 | 常量 / 工厂方法 | run 的首个事件 |
|:--|:--|:--|
| `rmqctl` 不在镜像里（例如本地开发没装 Go），或 `studio.ai.conversation.rmqctl-enabled=false` | `DEGRADED_NOTICE_MESSAGE` / `degradedNotice()` | WARN Notice：`rmqctl 不可用，本次会话已禁用 RocketMQ 工具` |
| 会话没有绑定实例（`instance_id` 为空） | `UNBOUND_NOTICE_MESSAGE` / `unboundNotice()` | WARN Notice：`本次会话未绑定实例，已禁用 RocketMQ 工具` |

两种情况下都省略 `--mcp-config`/`--strict-mcp-config`，内置工具禁用列表保持不变（即改造前的行为）。
第一种还会让 `GET /api/ai/agent-capabilities` 报 `rmqctlAvailable:false`，前端据此禁用实例选择器并说明原因。

**会话仍然能当纯聊天用**——这是硬要求，降级是可发现的，不是神秘的。

两条提示的可读中文原文放在 `server/src/test/resources/ai/agent-notices.txt`，生产代码里用 `\uXXXX`
转义写（`style/rmq_checkstyle.xml` 拒绝 Java 源文件里的非 ASCII 字符），`RmqctlWorkspaceTest` 读这个文件
断言「转义确实解码成这句话」——测试里重复一遍转义等于什么都没断言。该文件用 `.txt` 而不是 `.properties`
是为了避开 checkstyle 的 `resourceIncludes (**/*.properties)`，不要重命名。

UI 上两条都必须渲染成 `InfoBanner` 风格的**中性灰**块（`#fafafa` 底 + `#f0f0f0` 边框、圆角 8、14px），
**不是**黄色 antd `Alert`——项目规则是 `Alert` 只用于带语义状态的错误/警告/成功提示，而「本会话没接工具」
是常驻说明而非故障。
