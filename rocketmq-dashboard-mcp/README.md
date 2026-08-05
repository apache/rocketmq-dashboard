# rocketmq-dashboard-mcp

Lightweight **Model Context Protocol (MCP) server** for RocketMQ Studio (RIP-3),
implemented in TypeScript with [`@modelcontextprotocol/sdk`](https://github.com/modelcontextprotocol/typescript-sdk).

## Why TypeScript (not Java)

The Java MCP server on the client side was rejected as too heavyweight (reviewer
feedback on pr-15). This server is a **thin adapter**: it speaks the MCP protocol
(JSON-RPC 2.0 over stdio) to AI clients and forwards every tool call to the
RocketMQ Studio server over plain JSON (`GET/POST /api/ai`). The Studio server
remains the single source of truth for tool discovery and execution —
"keep only protocol translation on the server / Studio side".

## Architecture

```
AI client (Claude / Cursor / ...)
   │  MCP protocol (stdio, JSON-RPC 2.0)
   ▼
rmq-mcp  (this TypeScript process)
   │  plain JSON over HTTP  (Bearer auth)
   ▼
RocketMQ Studio server   /api/ai/tools , /api/ai/tools/{name}/execute
   │
   ▼
RocketMQ broker / NameServer
```

## Usage

```
rmq-mcp [options]
  -s, --server <url>   Studio base URL (default $RMQ_STUDIO_URL or http://localhost:8080)
  -u, --user <user>    Studio username ($RMQ_USER)
  -p, --password <pw>  Studio password ($RMQ_PASSWORD)
  -t, --token <token>  Bearer token ($RMQ_TOKEN); if omitted, auto-login is attempted
  -c, --cluster <name> Default cluster to scope tools to
  -h, --help           Show help and exit
```

Wire it to an MCP-aware client over stdio, e.g. `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "rocketmq-studio": {
      "command": "npx",
      "args": ["tsx", "/path/to/rocketmq-dashboard-mcp/src/index.ts", "-s", "http://localhost:8080", "-u", "admin", "-p", "admin"]
    }
  }
}
```

## How it works

- **`tools/list`** — fetches `GET /api/ai/tools[?cluster=]` and maps each
  `AiToolVO` to an MCP tool, passing the server's `inputSchema` through verbatim
  (so the AI client sees the exact contract the server enforces).
- **`tools/call`** — forwards `params.arguments` to
  `POST /api/ai/tools/{name}/execute` and returns the result as `content`
  (text). Failures return `isError: true` rather than aborting the protocol.
- **Auth** — sends `Authorization: Bearer <token>`; if no token is configured it
  logs in via `/api/auth/login` and retries once on `401`.

## Development

```bash
npm install
npm run typecheck
npm test
npx tsx src/index.ts --help
```

## Relation to the `rmqctl` CLI

`rocketmq-dashboard-mcp` and `rocketmq-dashboard-cli` are sibling RIP-3 client
modules. They share the same contract (the Studio `/api/ai` HTTP API) and the
same `StudioClient` shape, but serve different surfaces: the CLI is for humans
on the command line; this MCP server is for AI assistants.
