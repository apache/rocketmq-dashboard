import { describe, it, expect, vi } from "vitest";
import { StudioClient, StudioClientError } from "../client";
import type { AiToolVO } from "../types";

function mockResponse(body: string, status = 200, headers: Record<string, string> = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (k: string) => headers[k.toLowerCase()] ?? null },
    json: async () => JSON.parse(body),
    text: async () => body,
  } as unknown as Response;
}

describe("StudioClient", () => {
  it("executeTool POSTs to /api/ai/tools/{name}/execute with Bearer auth", async () => {
    const fetchImpl = vi.fn(async () =>
      mockResponse(JSON.stringify({ code: 200, message: "ok", data: { ok: true } }), 200),
    );
    const client = new StudioClient({
      baseUrl: "http://x:8080",
      token: "T",
      fetchImpl: fetchImpl as unknown as typeof fetch,
    });
    const data = await client.executeTool("rmq.topic.list", { cluster: "local" });
    expect(fetchImpl).toHaveBeenCalled();
    const [url, init] = fetchImpl.mock.calls[0] as unknown as [string, { method: string; headers: Record<string, string> }];
    expect(url).toBe("http://x:8080/api/ai/tools/rmq.topic.list/execute");
    expect(init.method).toBe("POST");
    expect(init.headers["Authorization"]).toBe("Bearer T");
    expect(data).toEqual({ ok: true });
  });

  it("auto-logs in and retries once on 401", async () => {
    let call = 0;
    const fetchImpl = vi.fn(async (url: string) => {
      call++;
      if (call === 1) return mockResponse("denied", 401);
      if (url.endsWith("/api/auth/login")) {
        return mockResponse(JSON.stringify({ code: 200, message: "ok", data: { token: "NEW" } }), 200);
      }
      return mockResponse(JSON.stringify({ code: 200, message: "ok", data: { ok: true } }), 200);
    });
    const client = new StudioClient({
      baseUrl: "http://x:8080",
      user: "u",
      password: "p",
      fetchImpl: fetchImpl as unknown as typeof fetch,
    });
    const data = await client.executeTool("rmq.topic.list", { cluster: "local" });
    expect(call).toBe(3); // 401 -> login -> retry
    expect(data).toEqual({ ok: true });
  });

  it("throws StudioClientError carrying status/code on server error", async () => {
    const fetchImpl = vi.fn(async () =>
      mockResponse(JSON.stringify({ code: 400, message: "bad", data: null }), 400),
    );
    const client = new StudioClient({
      baseUrl: "http://x:8080",
      token: "T",
      fetchImpl: fetchImpl as unknown as typeof fetch,
    });
    await expect(client.executeTool("x", {})).rejects.toMatchObject({ status: 400, code: 400 });
  });

  it("listTools appends ?cluster= when provided", async () => {
    const fetchImpl = vi.fn(async (url: string) =>
      mockResponse(
        JSON.stringify({ code: 200, message: "ok", data: [{ name: "rmq.topic.list" } as AiToolVO] }),
        200,
      ),
    );
    const client = new StudioClient({
      baseUrl: "http://x:8080",
      token: "T",
      fetchImpl: fetchImpl as unknown as typeof fetch,
    });
    const tools = await client.listTools("local");
    expect((fetchImpl.mock.calls[0] as unknown as [string])[0]).toContain("/api/ai/tools?cluster=local");
    expect(tools[0].name).toBe("rmq.topic.list");
  });
});
