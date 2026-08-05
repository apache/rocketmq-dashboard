import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { CallToolRequestSchema, ListToolsRequestSchema } from "@modelcontextprotocol/sdk/types.js";
import type { StudioClient } from "./client";
import type { AiToolVO } from "./types";

export interface McpServerOptions {
  cluster?: string;
}

/**
 * Map a Studio tool catalog entry to an MCP tool definition. The server's
 * `inputSchema` (a JSON schema) is passed through verbatim so the AI client
 * sees the exact contract the Studio server enforces.
 */
export function mapToolsToMcp(tools: AiToolVO[]): Array<{
  name: string;
  description: string;
  inputSchema: unknown;
}> {
  return tools.map((t) => ({
    name: t.name,
    description: t.description ?? "",
    inputSchema: (t.parameters as unknown) ?? { type: "object", properties: {} },
  }));
}

/**
 * Execute a tool via the Studio server and wrap the result in an MCP
 * `CallToolResult`. Errors are returned as `isError: true` content rather than
 * thrown, so the AI client receives a structured failure instead of a protocol
 * error.
 */
export async function callToolAsMcp(
  client: StudioClient,
  name: string,
  args: Record<string, unknown>,
): Promise<{ content: Array<{ type: "text"; text: string }>; isError: boolean }> {
  try {
    const data = await client.executeTool(name, args);
    const text = typeof data === "string" ? data : JSON.stringify(data, null, 2);
    return { content: [{ type: "text", text }], isError: false };
  } catch (err) {
    const text = err instanceof Error ? err.message : String(err);
    return { content: [{ type: "text", text }], isError: true };
  }
}

/**
 * Register the MCP request handlers on an existing `Server`. Kept separate from
 * `startMcpServer` so the handler logic (tool mapping, argument extraction,
 * error wrapping) is unit-testable without binding to a stdio transport.
 */
export function registerMcpHandlers(server: Server, client: StudioClient, options: McpServerOptions): void {
  server.setRequestHandler(ListToolsRequestSchema, async () => {
    const tools = await client.listTools(options.cluster);
    return { tools: mapToolsToMcp(tools) } as never;
  });

  server.setRequestHandler(CallToolRequestSchema, async (request) => {
    const name = request.params.name;
    const args = (request.params.arguments ?? {}) as Record<string, unknown>;
    return callToolAsMcp(client, name, args) as never;
  });
}

/**
 * Start the MCP server over stdio. It is a pure adapter: tool discovery and
 * execution are delegated to the Studio server via the `/api/ai` HTTP API.
 */
export async function startMcpServer(client: StudioClient, options: McpServerOptions): Promise<Server> {
  const server = new Server(
    { name: "rocketmq-studio-mcp", version: "1.0.0" },
    { capabilities: { tools: {} } },
  );

  registerMcpHandlers(server, client, options);

  const transport = new StdioServerTransport();
  await server.connect(transport);
  return server;
}
