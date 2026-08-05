import { describe, it, expect, vi } from "vitest";
import { registerMcpHandlers } from "../mcp";
import type { Server } from "@modelcontextprotocol/sdk/server/index.js";
import type { StudioClient } from "../client";
import type { AiToolVO } from "../types";

/** A minimal fake Server that records registered handlers for direct invocation. */
function makeFakeServer() {
  const handlers: Array<(req: unknown) => Promise<unknown>> = [];
  const server = {
    setRequestHandler: (_schema: unknown, handler: (req: unknown) => Promise<unknown>) => {
      handlers.push(handler);
    },
  } as unknown as Server;
  return { server, handlers };
}

const topicList: AiToolVO = {
  name: "rmq.topic.list",
  description: "List topics",
  parameters: {
    type: "object",
    properties: { cluster: { type: "string" } },
    required: ["cluster"],
  },
  riskLevel: "L1",
};

describe("registerMcpHandlers", () => {
  it("registers tools/list and tools/call and wires them to the client", async () => {
    const listTools = vi.fn(async () => [topicList]);
    const executeTool = vi.fn(async () => ({ rows: [{ name: "t1", tps: 10 }] }));
    const client = { listTools, executeTool } as unknown as StudioClient;
    const { server, handlers } = makeFakeServer();

    registerMcpHandlers(server, client, { cluster: "local" });

    expect(handlers).toHaveLength(2);

    // tools/list
    const listed = (await handlers[0]({})) as { tools: Array<{ name: string; inputSchema: unknown }> };
    expect(listTools).toHaveBeenCalledWith("local");
    expect(listed.tools[0].name).toBe("rmq.topic.list");
    expect(listed.tools[0].inputSchema).toEqual(topicList.parameters);

    // tools/call
    const called = (await handlers[1]({
      params: { name: "rmq.topic.list", arguments: { cluster: "local" } },
    })) as { content: Array<{ type: "text"; text: string }>; isError: boolean };
    expect(executeTool).toHaveBeenCalledWith("rmq.topic.list", { cluster: "local" });
    expect(called.isError).toBe(false);
    expect(called.content[0].text).toContain("t1");
  });

  it("defaults arguments to {} when the client omits them", async () => {
    const executeTool = vi.fn(async () => ({}));
    const client = { listTools: vi.fn(async () => []), executeTool } as unknown as StudioClient;
    const { server, handlers } = makeFakeServer();

    registerMcpHandlers(server, client, {});
    await handlers[1]({ params: { name: "rmq.topic.list" } });

    expect(executeTool).toHaveBeenCalledWith("rmq.topic.list", {});
  });

  it("passes tool failures through as isError:true content", async () => {
    const executeTool = vi.fn(async () => {
      throw new Error("tool exploded");
    });
    const client = { listTools: vi.fn(async () => []), executeTool } as unknown as StudioClient;
    const { server, handlers } = makeFakeServer();

    registerMcpHandlers(server, client, {});
    const result = (await handlers[1]({
      params: { name: "rmq.topic.list", arguments: {} },
    })) as { content: Array<{ type: "text"; text: string }>; isError: boolean };

    expect(result.isError).toBe(true);
    expect(result.content[0].text).toBe("tool exploded");
  });
});
