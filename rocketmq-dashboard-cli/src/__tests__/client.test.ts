import { describe, expect, it, vi } from "vitest";
import { StudioClient, StudioClientError } from "../client";

/** Minimal Response-like object (avoids depending on a global fetch impl). */
function mockResponse(body: unknown, status = 200): Response {
  const text = typeof body === "string" ? body : JSON.stringify(body);
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => text,
  } as unknown as Response;
}

function makeClient(fetchMock: typeof fetch, opts: Record<string, unknown> = {}) {
  return new StudioClient({ baseUrl: "http://studio", fetchImpl: fetchMock, ...opts });
}

describe("StudioClient", () => {
  it("executes a tool and returns its data", async () => {
    const fetchMock = vi.fn(async () => mockResponse({ code: 200, message: "success", data: { ok: 1 } })) as unknown as typeof fetch;
    const client = makeClient(fetchMock);

    const data = await client.executeTool("rmq.cluster.list", {});

    expect(data).toEqual({ ok: 1 });
    expect(fetchMock).toHaveBeenCalledWith(
      "http://studio/api/ai/tools/rmq.cluster.list/execute",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("auto-logs-in on 401 when credentials are present, then retries", async () => {
    let call = 0;
    const fetchMock = vi.fn(async (url: string | URL) => {
      const u = String(url);
      if (u.endsWith("/api/auth/login")) {
        return mockResponse({ code: 200, message: "success", data: { token: "T1" } });
      }
      call += 1;
      if (call === 1) return mockResponse({ code: 401, message: "Unauthorized" }, 401);
      return mockResponse({ code: 200, message: "success", data: [1, 2] });
    }) as unknown as typeof fetch;
    const client = makeClient(fetchMock, { user: "u", password: "p" });

    const data = await client.listTools();

    expect(data).toEqual([1, 2]);
    expect(fetchMock).toHaveBeenCalledWith(
      "http://studio/api/auth/login",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("does not attempt login without credentials on 401", async () => {
    const fetchMock = vi.fn(async () => mockResponse({ code: 401, message: "Unauthorized" }, 401)) as unknown as typeof fetch;
    const client = makeClient(fetchMock);

    await expect(client.listTools()).rejects.toBeInstanceOf(StudioClientError);
    expect(fetchMock).not.toHaveBeenCalledWith(
      "http://studio/api/auth/login",
      expect.anything(),
    );
  });

  it("throws StudioClientError carrying status and code on business errors", async () => {
    const fetchMock = vi.fn(async () =>
      mockResponse({ code: 400, message: "Tool input validation failed" }, 400),
    ) as unknown as typeof fetch;
    const client = makeClient(fetchMock);

    await expect(client.executeTool("rmq.x.y", {})).rejects.toMatchObject({
      status: 400,
      code: 400,
    });
  });

  it("lists tools and forwards the cluster query param", async () => {
    const fetchMock = vi.fn(async () =>
      mockResponse({ code: 200, message: "success", data: [{ name: "rmq.topic.list" }] }),
    ) as unknown as typeof fetch;
    const client = makeClient(fetchMock);

    const tools = await client.listTools("local");

    expect(tools).toEqual([{ name: "rmq.topic.list" }]);
    expect(fetchMock).toHaveBeenCalledWith(
      "http://studio/api/ai/tools?cluster=local",
      expect.objectContaining({ method: "GET" }),
    );
  });
});
