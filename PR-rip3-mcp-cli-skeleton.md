# RIP-3 — `rmqctl` CLI + `rmq-mcp` MCP server (lightweight TypeScript)

## Summary

Rework of pr-15 per review feedback: implementing the CLI **and** the MCP server in
Java was too heavyweight. The RocketMQ Studio server already owns all protocol
translation via `/api/ai` (tool catalog + execution gateway), so this PR delivers
both RIP-3 client surfaces as **lightweight TypeScript (tsx) thin clients** that
speak **plain JSON over HTTP** to the server. The server remains the single source
of truth for the tool surface.

This PR also **removes the heavyweight Java MCP server** from the Studio server
module (`McpServerImpl` / `McpServerRegistry`) and rewires `AiService` directly to
`ToolGatewayService` / `ToolCatalog`, so the `/api/ai` REST contract the TS clients
depend on is preserved while the Java server-side MCP indirection is gone.

## What's in this PR

- `rocketmq-dashboard-cli/` — the `rmqctl` CLI (for humans, on the command line).
- `rocketmq-dashboard-mcp/` — the `rmq-mcp` MCP server (for AI assistants).

Both share the same `/api/ai` contract and the same `StudioClient` shape (Bearer
auth, auto-login with a single 401 retry). The MCP server speaks the MCP protocol
(JSON-RPC 2.0 over stdio) to AI clients and forwards to the same `/api/ai` endpoints;
the CLI is a direct HTTP client. Neither implements an MCP server or any protocol
translation of its own — all of that lives server-side.

### `rmqctl` CLI (`rocketmq-dashboard-cli/`)

- `src/client.ts` — `StudioClient`: discovers tools (`GET /api/ai/tools`) and
  executes (`POST /api/ai/tools/{name}/execute`); Bearer auth, auto-login + 401 retry.
- `src/commands.ts` — `rmqctl <resource> <verb>` runner + `explain`; honors
  `--cluster`, `--output json|yaml|table`, `--dry-run`, `--yes`,
  `--enable-dangerous-ops`; only L1 tools are enabled server-side.
- `src/args.ts`, `src/naming.ts` (`rmq.<resource>.<verb>`), `src/format.ts`,
  `src/index.ts`.
- Unit tests: `naming`, `format`, `client`, `args`, `commands`, plus `index`
  (entry/help/version/run/explain/error paths) and `*extra` suites covering
  edge cases (empty bodies, 401-without-credentials, schema fallbacks, deprecated
  tools, missing params). **75 tests pass; `tsc --noEmit` clean.**

### `rmq-mcp` MCP server (`rocketmq-dashboard-mcp/`)

- Thin MCP adapter using the official `@modelcontextprotocol/sdk`; speaks MCP
  (JSON-RPC 2.0 over stdio) to AI clients.
- `tools/list` maps `AiToolVO` -> MCP tool, passing the server's `inputSchema`
  through verbatim. `tools/call` forwards args to `/api/ai/tools/{name}/execute`;
  failures return `isError:true` instead of aborting the protocol.
- `src/client.ts`, `src/mcp.ts` (`registerMcpHandlers` + `startMcpServer`),
  `src/args.ts`, `src/index.ts`, `src/types.ts`.
- Unit tests: `client`, `mcp`, `args`, plus `index` and `*extra` suites covering
  handler registration/invocation (incl. empty-args defaulting and error wrapping),
  `parseMcpArgs` flags, login error paths, and request error handling. **30 tests pass;
  `tsc --noEmit` clean.**

## Architecture

```
AI client  ──MCP (stdio)──►  rmq-mcp (TS)  ──plain JSON──►  Studio /api/ai  ──►  RocketMQ
human     ──rmqctl (TS)──►  (same /api/ai contract)
```

## Server-side Java change (Studio module)

The previous Java `McpServerImpl` / `McpServerRegistry` in
`server/src/main/java/org/apache/rocketmq/studio/ops/ai/` have been **deleted**.
`AiService` no longer delegates to an `McpServerRegistry`; it calls
`ToolGatewayService.discover(cluster)` / `.execute(name, input)` and `ToolCatalog`
directly for the catalog version/digest/minimum-client-version headers. The
`/api/ai/tools` and `/api/ai/tools/{name}/execute` REST endpoints — the contract
the TS CLI and TS MCP server rely on — are unchanged. `AiServiceTest` (11 tests)
passes.

## CI

`.github/workflows/ci.yml`: `cli-build` and `mcp-build` jobs (Node 22 + tsx)
replace the old Java/Maven `cli-build` job. Both run `typecheck` + `test`
(`npm ci` for reproducible installs).

## Verification

- `cd rocketmq-dashboard-cli && npm ci && npm run typecheck && npm test`
- `cd rocketmq-dashboard-mcp && npm ci && npm run typecheck && npm test`
- Coverage: `npm run coverage` in either module (v8) reports the new suites.
- E2E (per previous iteration): both modules exercised against a mock Studio
  server — CLI table/yaml output and the full MCP `initialize -> tools/list ->
  tools/call` round-trip over stdio.

## Notes / follow-ups

- Resource tools (cluster, topic, group, message, acl, metrics, …) are
  intentionally not in this PR; each lands as its own RIP-3 PR building on this
  skeleton.
- `rocketmq-dashboard-llm` (in-console LLM) is a later RIP-3 PR.
