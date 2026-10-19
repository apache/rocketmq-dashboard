# rmqctl：独立命令行与 MCP 桥接

[English](README.md) · [Studio 启动指南](../README_zh.md#快速开始)

`rmqctl` 调用 Studio 服务端，不直接连接 Broker。同一个可执行文件既可在终端运行
只读查询和 Catalog 命令，也可把 Studio 工具提供给外部 MCP 客户端。

## 构建与离线查看

以下示例使用 Linux 或 macOS 的 POSIX shell，工作目录始终为仓库根目录。
先安装 Go **1.27.1 或更新版本**和 Make。构建本机要执行的程序时，清除交叉编译覆盖值：

```sh
unset GOOS GOARCH
make -C rmqctl build
./rmqctl/bin/rmqctl version
./rmqctl/bin/rmqctl --help
./rmqctl/bin/rmqctl explain topic
./rmqctl/bin/rmqctl topic list --help
```

`version`、`explain` 和 `--help` 不需要运行 Studio，也不需要凭据。
构建还会把二进制的许可证材料写入 `rmqctl/bin/legal/native`。
`make -C rmqctl build-all` 可构建 [Makefile](Makefile) 中的六种目标平台；
交叉编译产物不一定能在构建机器上直接运行。

## 配置 Studio 访问上下文

按照[部署指南](../deploy/README.md)启动 Studio，注册要查看的实例，并从管理员处
取得为该实例配置的 AccessKey/SecretKey。Apache 实例使用 `adminCredentialRef`
引用的管理凭据，云实例使用其引用的云凭据。Studio 浏览器登录的用户名、密码或
会话 Cookie 不是这组 HMAC 凭据。

通过 shell 或密钥管理器提供环境变量 `RMQ_ACCESS_KEY` 和 `RMQ_SECRET_KEY`。
不要把真实密钥写入命令参数、此仓库或 MCP 配置文件。以下参数接收的是
**环境变量名**，不是密钥值：

```sh
./rmqctl/bin/rmqctl config set-context local \
  --server http://127.0.0.1:6789 \
  --access-key-env RMQ_ACCESS_KEY \
  --secret-key-env RMQ_SECRET_KEY
./rmqctl/bin/rmqctl config use-context local
./rmqctl/bin/rmqctl config get-contexts
```

上下文仅保存服务地址及 `env:RMQ_ACCESS_KEY` / `env:RMQ_SECRET_KEY` 引用，
不保存解析后的密钥或默认实例。配置路径优先级为 `--config` > `RMQCTL_CONFIG` >
`~/.rmqctl/config.yaml`；后续调用必须使用同一个配置文件。
`--context local` 可仅为当前调用覆盖已选中的上下文。

示例地址是随部署提供的前端代理，也转发 `/api`。远程部署应使用客户端可达的
Studio 基础地址，**不要**追加 `/api` 或 `/api/mcp`。公网地址必须使用 HTTPS；
HTTP 仅允许回环、私有、链路本地 IP 地址及 `localhost`。

## 发出只读请求

把 `my-instance` 替换为注册的 **Studio 实例名称**（`rmq_instance.name`），
而不是数据库数字主键或云厂商实例 ID：

```sh
./rmqctl/bin/rmqctl --instance-id my-instance topic list --output json
```

每次工具调用都需要 `--instance-id`。即使某个平台级工具没有 `instanceId` 入参，
认证请求头仍需携带实例名称，所用密钥也必须与该实例匹配。
默认输出表格，也支持 `--output json` 和 `--output yaml`。
用 `explain <resource>` 及各命令的 `--help` 查看当前 Catalog、必填参数、能力和风险级别。

本入门指南不执行变更操作。L2/L3 变更采用服务端的预览与确认令牌流程。
`--yes` 只跳过终端确认提示，不绕过服务端能力、权限或 L3 策略检查。

## 连接外部 MCP 客户端

仍在仓库根目录，指定可执行文件的绝对路径、上下文及实例，生成配置片段：

```sh
./rmqctl/bin/rmqctl --context local --instance-id my-instance mcp config \
  --command "$PWD/rmqctl/bin/rmqctl"
```

把输出的 `mcpServers` 条目复制到客户端的 MCP 配置。此命令只生成配置，
不会连接服务端或验证凭据。客户端启动该程序时，自身环境中必须有引用的凭据变量。
单独启动的桌面客户端不一定继承当前终端的环境。生成的片段不包含密钥值。

如果使用自定义配置文件，生成片段时也传入 `--config /absolute/path/config.yaml`。
`mcp stdio` 的标准输出专用于协议消息，应由 MCP 客户端连接，而不是当作交互式 shell 使用。

## 常见问题

- `--instance-id is required`：显式传入 Studio 实例名称；切换上下文不会选定实例。
- `no current context is selected`：使用与 `set-context` 相同的配置文件，运行
  `config use-context local` 或传入 `--context local`。
- `environment variable ... is empty`：为实际启动 `rmqctl` 的进程提供该环境变量，
  包括外部 MCP 客户端。
- MCP 认证失败：核对 Studio 实例名称、为它配置的密钥及客户端与服务端的时钟同步。
  浏览器登录不能替代这些签名请求的认证。
