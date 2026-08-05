#!/usr/bin/env tsx
import { parseMcpArgs } from "./args";
import { StudioClient } from "./client";
import { startMcpServer } from "./mcp";

const VERSION = "1.0.0";

function printHelp(): void {
  // NOTE: rmq-mcp speaks MCP over stdio. Help text goes to stdout only when the
  // server is not attached to an MCP client, so it never corrupts the protocol.
  console.log(`rmq-mcp v${VERSION} — RocketMQ Studio MCP server (RIP-3)

A lightweight Model Context Protocol server that exposes RocketMQ Studio tools
to AI clients. It forwards tool calls to a Studio server over plain JSON.

Usage:
  rmq-mcp [options]

Options:
  -s, --server <url>   Studio base URL (default $RMQ_STUDIO_URL or http://localhost:8080)
  -u, --user <user>    Studio username (default $RMQ_USER)
  -p, --password <pw>  Studio password (default $RMQ_PASSWORD)
  -t, --token <token>  Bearer token (default $RMQ_TOKEN); if omitted, auto-login is attempted
  -c, --cluster <name> Default cluster to scope tools to
  -h, --help           Show this help

Wire it to an MCP-aware AI client, e.g. Claude Desktop or Cursor, via stdio.`);
}

async function main(): Promise<void> {
  const opts = parseMcpArgs(process.argv.slice(2));
  if (opts.help) {
    printHelp();
    return;
  }

  const client = new StudioClient({
    baseUrl: opts.server,
    user: opts.user,
    password: opts.password,
    token: opts.token,
  });

  await startMcpServer(client, { cluster: opts.cluster });
  // The stdio transport keeps the process alive and drives all I/O.
}

main().catch((err) => {
  // Errors must go to stderr so they never interleave with MCP JSON on stdout.
  console.error(err instanceof Error ? err.stack ?? err.message : String(err));
  process.exit(1);
});
