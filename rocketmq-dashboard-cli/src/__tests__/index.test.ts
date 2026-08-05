import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { main, printHelp } from "../index";
import type { StudioClient, StudioClientOptions } from "../client";
import type { AiToolVO } from "../types";

function capture() {
  const logs: string[] = [];
  const errs: string[] = [];
  const logSpy = vi.spyOn(console, "log").mockImplementation((m) => logs.push(String(m)));
  const errSpy = vi.spyOn(console, "error").mockImplementation((m) => errs.push(String(m)));
  return { logs, errs, logSpy, errSpy };
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

function fakeClient(tools: AiToolVO[] = [], executeResult: unknown = { rows: [{ name: "t1", tps: 10 }] }) {
  const listTools = vi.fn(async () => tools);
  const executeTool = vi.fn(async () => executeResult);
  return { client: { listTools, executeTool } as unknown as StudioClient, listTools, executeTool };
}

describe("printHelp", () => {
  it("prints the usage banner", () => {
    const { logs, logSpy } = capture();
    printHelp();
    expect(logSpy).toHaveBeenCalled();
    const out = logs.join("\n");
    expect(out).toContain("rmqctl");
    expect(out).toContain("Usage:");
    expect(out).toContain("rmqctl topic list --cluster local");
  });
});

describe("main", () => {
  beforeEach(() => {
    process.exitCode = 0;
  });
  afterEach(() => {
    vi.restoreAllMocks();
    process.exitCode = 0;
  });

  it("prints help on --help without creating a client", async () => {
    const { logs, logSpy } = capture();
    const factory = vi.fn((_opts: StudioClientOptions) => ({}) as unknown as StudioClient);
    await main(["--help"], factory);
    expect(factory).not.toHaveBeenCalled();
    expect(logSpy).toHaveBeenCalled();
    expect(logs.join("\n")).toContain("Usage:");
  });

  it("prints version on --version", async () => {
    const { logs } = capture();
    const factory = vi.fn((_opts: StudioClientOptions) => ({}) as unknown as StudioClient);
    await main(["--version"], factory);
    expect(factory).not.toHaveBeenCalled();
    expect(logs.join("\n")).toContain("rmqctl 1.0.0");
  });

  it("prints help when invoked with no arguments", async () => {
    const { logs } = capture();
    const factory = vi.fn((_opts: StudioClientOptions) => ({}) as unknown as StudioClient);
    await main([], factory);
    expect(factory).not.toHaveBeenCalled();
    expect(logs.join("\n")).toContain("Usage:");
  });

  it("runs a tool through the injected client factory and prints the result", async () => {
    const { logs } = capture();
    const { client, executeTool } = fakeClient([topicListTool]);
    const factory = vi.fn((_opts: StudioClientOptions) => client);
    await main(["topic", "list", "--cluster", "local"], factory);
    expect(factory).toHaveBeenCalledWith(
      expect.objectContaining({ baseUrl: expect.any(String), user: undefined, password: undefined, token: undefined }),
    );
    expect(executeTool).toHaveBeenCalledWith("rmq.topic.list", { cluster: "local" });
    expect(logs.join("\n")).toContain("t1");
  });

  it("runs explain through the injected client factory", async () => {
    const { logs } = capture();
    const { client, listTools } = fakeClient([]);
    const factory = vi.fn(() => client);
    await main(["explain"], factory);
    expect(listTools).toHaveBeenCalled();
    expect(logs.length).toBeGreaterThan(0);
  });

  it("sets exitCode 1 and prints to stderr on a command error", async () => {
    const { errs } = capture();
    const listTools = vi.fn(async () => {
      throw new Error("boom");
    });
    const client = { listTools, executeTool: vi.fn() } as unknown as StudioClient;
    const factory = vi.fn(() => client);
    await main(["topic", "list"], factory);
    expect(process.exitCode).toBe(1);
    expect(errs.join("\n")).toContain("Error: boom");
  });

  it("passes auth options through the client factory", async () => {
    const { logs } = capture();
    const { client, executeTool } = fakeClient([topicListTool]);
    const factory = vi.fn(() => client);
    await main(
      ["topic", "list", "--cluster", "local", "-u", "admin", "-p", "secret", "--token", "TKN"],
      factory,
    );
    expect(factory).toHaveBeenCalledWith(
      expect.objectContaining({ user: "admin", password: "secret", token: "TKN" }),
    );
    expect(executeTool).toHaveBeenCalled();
    expect(logs.join("\n")).toContain("t1");
  });
});
