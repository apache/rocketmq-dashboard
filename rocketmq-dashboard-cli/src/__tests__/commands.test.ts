import { describe, it, expect, vi } from "vitest";
import { runToolCommand, explainCommand } from "../commands";
import type { StudioClient } from "../client";
import type { AiToolVO } from "../types";

const EXEC_RESULT = { rows: [{ name: "t1", tps: 10 }] };

function makeClient(tools: AiToolVO[], executeResult: unknown = EXEC_RESULT) {
  const listTools = vi.fn(async () => tools);
  const executeTool = vi.fn(async (_name: string, _input: Record<string, unknown>) => executeResult);
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

describe("runToolCommand", () => {
  it("discovers, validates and executes an L1 tool, forwarding --cluster", async () => {
    const { client, listTools, executeTool } = makeClient([topicListTool]);
    const out = await runToolCommand(client, ["topic", "list"], baseOptions({ cluster: "local" }));
    expect(listTools).toHaveBeenCalledWith("local");
    expect(executeTool).toHaveBeenCalledWith("rmq.topic.list", { cluster: "local" });
    expect(out).toContain("t1");
  });

  it("throws on unknown tool", async () => {
    const { client } = makeClient([topicListTool]);
    await expect(
      runToolCommand(client, ["foo", "bar"], baseOptions({ cluster: "local" })),
    ).rejects.toThrow(/Unknown tool: rmq.foo.bar/);
  });

  it("throws on missing required parameter", async () => {
    const { client } = makeClient([topicListTool]);
    await expect(
      runToolCommand(client, ["topic", "list"], baseOptions({ cluster: undefined })),
    ).rejects.toThrow(/Missing required parameter\(s\): cluster/);
  });

  it("blocks non-L1 tools unless --enable-dangerous-ops", async () => {
    const l3: AiToolVO = {
      ...topicListTool,
      name: "rmq.topic.delete",
      riskLevel: "L3",
    };
    const { client } = makeClient([l3]);
    await expect(
      runToolCommand(client, ["topic", "delete", "cluster=local"], baseOptions({ cluster: "local" })),
    ).rejects.toThrow(/risk level L3/);

    const { client: c2, executeTool } = makeClient([l3]);
    await runToolCommand(
      c2,
      ["topic", "delete", "cluster=local"],
      baseOptions({ cluster: "local", enableDangerousOps: true }),
    );
    expect(executeTool).toHaveBeenCalled();
  });

  it("rejects deprecated tools", async () => {
    const dep: AiToolVO = {
      ...topicListTool,
      name: "rmq.old.thing",
      deprecated: true,
      replacement: "rmq.new.thing",
    };
    const { client } = makeClient([dep]);
    await expect(
      runToolCommand(client, ["old", "thing"], baseOptions({ cluster: "local" })),
    ).rejects.toThrow(/deprecated/);
  });
});

describe("explainCommand", () => {
  it("lists all tools when no filter", async () => {
    const { client } = makeClient([
      topicListTool,
      { ...topicListTool, name: "rmq.cluster.list" },
    ]);
    const out = await explainCommand(client, [], baseOptions({ cluster: "local", output: "table" }));
    expect(out).toContain("rmq.topic.list");
    expect(out).toContain("rmq.cluster.list");
  });

  it("filters by resource", async () => {
    const { client } = makeClient([
      topicListTool,
      { ...topicListTool, name: "rmq.cluster.list" },
    ]);
    const out = await explainCommand(client, ["topic"], baseOptions({ cluster: "local", output: "table" }));
    expect(out).toContain("rmq.topic.list");
    expect(out).not.toContain("rmq.cluster.list");
  });

  it("reports when no tools match the filter", async () => {
    const { client } = makeClient([topicListTool]);
    const out = await explainCommand(client, ["nope"], baseOptions({ cluster: "local", output: "table" }));
    expect(out).toContain("No tools found for resource 'nope'");
  });
});
