#!/usr/bin/env tsx
import { pathToFileURL } from "node:url";
import { StudioClient, type StudioClientOptions } from "./client";
import { parseArgs } from "./args";
import { explainCommand, runToolCommand } from "./commands";

const VERSION = "1.0.0";

export function printHelp(): void {
  console.log(`rmqctl - RocketMQ operations CLI (RIP-3, TypeScript)

A lightweight client for the RocketMQ Studio server. It discovers tools from
the server's /api/ai catalog and forwards tool calls over plain JSON; the
server owns all protocol translation and execution.

Usage:
  rmqctl <resource> [verb] [key=value ...] [options]
  rmqctl explain [resource]
  rmqctl --help

Options:
  --cluster <name>            Target cluster (required by most tools)
  -o, --output <fmt>          json | yaml | table (default table)
  -s, --server <url>          Studio base URL (default $RMQ_STUDIO_URL or http://localhost:8080)
  -u, --user <user>           Studio username ($RMQ_USER)
  -p, --password <pwd>        Studio password ($RMQ_PASSWORD)
      --token <token>         Pre-issued Bearer token (skips login)
      --dry-run               Preview only (forward-compatible)
      --yes                   Confirm a mutating operation (forward-compatible)
      --enable-dangerous-ops  Acknowledge L2/L3 risk tools
  -h, --help                  Show this help
  -V, --version               Show version

Examples:
  rmqctl cluster list
  rmqctl topic list --cluster local
  rmqctl explain
  rmqctl explain topic
`);
}

export async function main(
  argv: string[] = process.argv.slice(2),
  clientFactory: (opts: StudioClientOptions) => StudioClient = (opts) => new StudioClient(opts),
): Promise<void> {
  const { subcommand, positionals, options } = parseArgs(argv);

  if (options.help) {
    printHelp();
    return;
  }
  if (options.version) {
    console.log(`rmqctl ${VERSION}`);
    return;
  }
  if (positionals.length === 0 && !subcommand) {
    printHelp();
    return;
  }

  const client = clientFactory({
    baseUrl: options.server,
    user: options.user,
    password: options.password,
    token: options.token,
  });

  try {
    const out = subcommand === "explain"
      ? await explainCommand(client, positionals, options)
      : await runToolCommand(client, positionals, options);
    console.log(out);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    console.error(`Error: ${message}`);
    process.exitCode = 1;
  }
}

const isMain = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;
if (isMain) {
  main();
}
