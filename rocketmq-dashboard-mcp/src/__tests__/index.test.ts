import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { main, printHelp } from "../index";
import type { Server } from "@modelcontextprotocol/sdk/server/index.js";
import type { StudioClient } from "../client";

function capture() {
  const logs: string[] = [];
  const logSpy = vi.spyOn(console, "log").mockImplementation((m) => logs.push(String(m)));
  return { logs, logSpy };
}

describe("printHelp", () => {
  it("prints the MCP server usage banner", () => {
    const { logs, logSpy } = capture();
    printHelp();
    expect(logSpy).toHaveBeenCalled();
    const out = logs.join("\n");
    expect(out).toContain("rmq-mcp");
    expect(out).toContain("stdio");
    expect(out).toContain("Claude Desktop");
  });
});

describe("main", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("prints help on --help without starting a server", async () => {
    const { logs, logSpy } = capture();
    const factory = vi.fn(async (_c: StudioClient, _o: { cluster?: string }) => ({}) as unknown as Server);
    const result = await main(["--help"], factory);
    expect(factory).not.toHaveBeenCalled();
    expect(result).toBeUndefined();
    expect(logSpy).toHaveBeenCalled();
    expect(logs.join("\n")).toContain("rmq-mcp");
  });

  it("starts the server via the factory with parsed options", async () => {
    const fakeServer = { name: "fake", close: vi.fn() } as unknown as Server;
    const factory = vi.fn(async () => fakeServer);
    const result = await main(
      ["-s", "http://studio:8080", "-c", "prod", "-u", "admin", "-p", "secret"],
      factory,
    );
    expect(factory).toHaveBeenCalledWith(
      expect.objectContaining({
        baseUrl: "http://studio:8080",
        user: "admin",
        password: "secret",
        token: undefined,
      }),
      { cluster: "prod" },
    );
    expect(result).toBe(fakeServer);
  });

  it("defaults to localhost and no cluster when flags are omitted", async () => {
    const fakeServer = { close: vi.fn() } as unknown as Server;
    const factory = vi.fn(async () => fakeServer);
    await main([], factory);
    expect(factory).toHaveBeenCalledWith(
      expect.objectContaining({ baseUrl: expect.any(String) }),
      { cluster: undefined },
    );
  });
});
