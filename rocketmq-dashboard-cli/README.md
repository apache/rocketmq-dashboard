# rmqctl — RocketMQ operations CLI (RIP-3)

A **lightweight** command-line client for the RocketMQ Studio server, written
in TypeScript and run with [`tsx`](https://github.com/esbuild-kit/tsx). It is
the reworked form of the original `pr-15` Java implementation, which was
rejected in review as too heavyweight (see the PR discussion).

## Design decision

The reviewer's guidance:

> Implementing the `rmqctl` CLI and MCP server in Java is too heavyweight.
> Implement the CLI side in a lightweight language (Go / TypeScript (tsx) /
> Python, etc.), keeping only protocol translation on the server / Studio side.
> The CLI-to-server communication can use either the MCP protocol or plain JSON.

Concretely:

- **CLI in TypeScript (tsx).** No JVM, no Maven module — a single small package
  you run with `npx tsx src/index.ts` or install globally.
- **Server owns protocol translation.** The Studio server already exposes the
  tool catalog and execution under `/api/ai` (`listTools`, `executeTool`). The
  CLI is a *thin client*: it discovers tools from the server and forwards calls.
  All validation, risk gating (only L1 tools are enabled server-side today), and
  execution happen on the server.
- **Communication: plain JSON over HTTP.** The CLI calls the Studio REST API
  (`GET /api/ai/tools`, `POST /api/ai/tools/{name}/execute`). This satisfies the
  "plain JSON" option; the server remains the MCP/protocol-translation layer.

## Usage

```bash
# list clusters
rmqctl cluster list

# list topics on a cluster (most tools require --cluster)
rmqctl topic list --cluster local

# discover tools
rmqctl explain
rmqctl explain topic

# choose output format
rmqctl topic list --cluster local -o yaml
```

### Authentication

`/api/ai` requires a session token. The CLI logs in automatically when you
supply credentials and retries the request once on a `401`:

```bash
rmqctl topic list --cluster local -u admin -p admin
# or via environment variables
export RMQ_USER=admin RMQ_PASSWORD=admin
rmqctl topic list --cluster local
# or skip login with a pre-issued token
rmqctl topic list --cluster local --token <bearer-token>
```

## Development

```bash
npm install        # install dev dependencies (also creates package-lock.json)
npm run typecheck  # tsc --noEmit
npm test           # vitest
npm start -- --help
```

## CI

The `cli-build` job in `.github/workflows/ci.yml` runs `npm ci`, `npm run
typecheck`, `npm test`, and a `tsx` smoke test (`rmqctl --help`) on Node 22.
