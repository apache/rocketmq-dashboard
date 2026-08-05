import { describe, it, expect, vi } from "vitest";
import { mapToolsToMcp, callToolAsMcp } from "../mcp";
import type { StudioClient } from "../client";
import type { AiToolVO } from "../types";

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

const clusterList: AiToolVO = {
  name: "rmq.cluster.list",
  description: "List clusters",
};

describe("mapToolsToMcp", () => {
  it("maps name/description and passes inputSchema through verbatim", () => {
    const mapped = mapToolsToMcp([topicList, clusterList]);
    expect(mapped).toHaveLength(2);
    expect(mapped[0]).toEqual({
      name: "rmq.topic.list",
      description: "List topics",
      inputSchema: topicList.parameters,
    });
  });

  it("falls back to an empty object schema when none is provided", () => {
    const mapped = mapToolsToMcp([clusterList]);
    expect(mapped[0].inputSchema).toEqual({ type: "object", properties: {} });
  });
});

describe("callToolAsMcp", () => {
  it("forwards args and wraps the result as text content", async () => {
    const executeTool = vi.fn(async () => ({ rows: [{ name: "t1" }] }));
    const client = { executeTool } as unknown as StudioClient;
    const res = await callToolAsMcp(client, "rmq.topic.list", { cluster: "local" });
    expect(executeTool).toHaveBeenCalledWith("rmq.topic.list", { cluster: "local" });
    expect(res.isError).toBe(false);
    expect(res.content[0].type).toBe("text");
    expect(res.content[0].text).toContain("t1");
  });

  it("returns isError=true on failure instead of throwing", async () => {
    const executeTool = vi.fn(async () => {
      throw new Error("boom");
    });
    const client = { executeTool } as unknown as StudioClient;
    const res = await callToolAsMcp(client, "rmq.topic.list", {});
    expect(res.isError).toBe(true);
    expect(res.content[0].text).toBe("boom");
  });
});
