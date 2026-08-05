import { describe, it, expect, vi } from "vitest";
import { runToolCommand, explainCommand } from "../commands";
import type { StudioClient } from "../client";
import type { AiToolVO } from "../types";

function makeClient(tools: AiToolVO[], executeResult: unknown = { ok: true }) {
  const listTools = vi.fn(async () => tools);
  const executeTool = vi.fn(async () => executeResult);
  const client = { listTools, executeTool } as unknown as StudioClient;
  return { client, listTools, executeTool };
}

function baseOptions(overrides: Partial<{
  cluster?: string;
  output: "json" | "yaml" | "table";
  enableDangerousOps: boolean;
}> = {}) {
  return {
    cluster: overrides.cluster,
    output: overrides.output ?? "json",
    server: "http://localhost:8080",
    user: undefined,
    password: undefined,
    token: undefined,
    dryRun: false,
    yes: false,
    enableDangerousOps: overrides.enableDangerousOps ?? false,
    help: false,
    version: false,
  };
}

const topicListTool: AiToolVO = {
  name: "rmq.topic.list",
  description: "List topics",
  riskLevel: "L1",
  permission: "read",
  viewHint: "table",
  parameters: {
    type: "object",
    properties: { cluster: { type: "string", description: "cluster name" } },
    required: ["cluster"],
    additionalProperties: false,
  },
};

describe("runToolCommand edge cases", () => {
  it("throws a usage error when no resource is given", async () => {
    const { client } = makeClient([topicListTool]);
    await expect(runToolCommand(client, [], baseOptions())).rejects.toThrow(/Usage: rmqctl/);
  });

  it("allows a tool with no riskLevel (L1 gate skipped)", async () => {
    const tool: AiToolVO = { ...topicListTool, riskLevel: undefined };
    const { client, executeTool } = makeClient([tool]);
    await runToolCommand(client, ["topic", "list"], baseOptions({ cluster: "local" }));
    expect(executeTool).toHaveBeenCalled();
  });

  it("rejects a deprecated tool that has no replacement text", async () => {
    const dep: AiToolVO = { ...topicListTool, name: "rmq.old.thing", deprecated: true };
    const { client } = makeClient([dep]);
    await expect(
      runToolCommand(client, ["old", "thing"], baseOptions({ cluster: "local" })),
    ).rejects.toThrow(/deprecated/);
  });

  it("formats output without a viewHint", async () => {
    const tool: AiToolVO = { ...topicListTool, viewHint: undefined };
    const { client } = makeClient([tool], { rows: [{ name: "t1" }] });
    const out = await runToolCommand(client, ["topic", "list"], baseOptions({ cluster: "local", output: "json" }));
    expect(out).toContain("t1");
  });

  it("executes when the tool has no input schema properties", async () => {
    const tool: AiToolVO = { ...topicListTool, parameters: { type: "object" } as AiToolVO["parameters"] };
    const { client, executeTool } = makeClient([tool]);
    await runToolCommand(client, ["topic", "list"], baseOptions({ cluster: "local" }));
    // cluster is not in the tool's schema, so it is not forwarded
    expect(executeTool).toHaveBeenCalledWith("rmq.topic.list", {});
  });

  it("does not forward --cluster when the tool does not accept it", async () => {
    const tool: AiToolVO = {
      ...topicListTool,
      name: "rmq.topic.describe",
      parameters: {
        type: "object",
        properties: { topic: { type: "string" } },
        required: ["topic"],
        additionalProperties: false,
      },
    };
    const { client, executeTool } = makeClient([tool]);
    await runToolCommand(client, ["topic", "describe", "topic=my-topic"], baseOptions({ cluster: "local" }));
    expect(executeTool).toHaveBeenCalledWith("rmq.topic.describe", { topic: "my-topic" });
  });

  it("reports a missing required param supplied as an empty string", async () => {
    const { client } = makeClient([topicListTool]);
    await expect(
      runToolCommand(client, ["topic", "list", "cluster="], baseOptions()),
    ).rejects.toThrow(/Missing required parameter\(s\): cluster/);
  });
});

describe("explainCommand edge cases", () => {
  it("reports when no tools are available at all", async () => {
    const { client } = makeClient([]);
    const out = await explainCommand(client, [], baseOptions({ cluster: "local" }));
    expect(out).toContain("No tools available");
  });

  it("renders a param without a type or description", async () => {
    const tool: AiToolVO = {
      name: "rmq.topic.list",
      description: "List topics",
      parameters: {
        type: "object",
        properties: { cluster: {} },
        required: ["cluster"],
      },
    };
    const { client } = makeClient([tool]);
    const out = await explainCommand(client, ["topic"], baseOptions({ output: "table" }));
    expect(out).toContain("cluster (required)");
  });
});
