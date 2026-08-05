import { describe, it, expect, vi } from "vitest";
import { StudioClient, StudioClientError } from "../client";

function mockResponse(body: string, status = 200, headers: Record<string, string> = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (k: string) => headers[k.toLowerCase()] ?? null },
    json: async () => JSON.parse(body),
    text: async () => body,
  } as unknown as Response;
}

describe("StudioClient.login error paths", () => {
  it("throws when RMQ_USER/RMQ_PASSWORD are absent", async () => {
    const fetchImpl = vi.fn(async () => mockResponse("{}")) as unknown as typeof fetch;
    const client = new StudioClient({ baseUrl: "http://x", fetchImpl });
    await expect(client.login()).rejects.toThrow(/Login requires/);
  });

  it("throws when the login endpoint fails", async () => {
    const fetchImpl = vi.fn(async () => mockResponse('{"message":"no"}', 401)) as unknown as typeof fetch;
    const client = new StudioClient({ baseUrl: "http://x", user: "u", password: "p", fetchImpl });
    await expect(client.login()).rejects.toMatchObject({ status: 401 });
  });

  it("throws when the login response omits a token", async () => {
    const fetchImpl = vi.fn(async () => mockResponse('{"code":200,"message":"ok","data":{}}')) as unknown as typeof fetch;
    const client = new StudioClient({ baseUrl: "http://x", user: "u", password: "p", fetchImpl });
    await expect(client.login()).rejects.toThrow(/token/i);
  });
});

describe("StudioClient.request error handling", () => {
  it("does not retry and throws on 401 without credentials", async () => {
    const fetchImpl = vi.fn(async () => mockResponse("denied", 401)) as unknown as typeof fetch;
    const client = new StudioClient({ baseUrl: "http://x", fetchImpl });
    await expect(client.executeTool("x", {})).rejects.toBeInstanceOf(StudioClientError);
    expect(fetchImpl).toHaveBeenCalledTimes(1);
  });

  it("uses the raw body text when the error response is not JSON", async () => {
    const fetchImpl = vi.fn(async () => mockResponse("boom", 500)) as unknown as typeof fetch;
    const client = new StudioClient({ baseUrl: "http://x", token: "T", fetchImpl });
    await expect(client.executeTool("x", {})).rejects.toThrow(/boom/);
  });

  it("falls back to a generic message on an empty error body", async () => {
    const fetchImpl = vi.fn(async () => mockResponse("", 502)) as unknown as typeof fetch;
    const client = new StudioClient({ baseUrl: "http://x", token: "T", fetchImpl });
    await expect(client.executeTool("x", {})).rejects.toThrow(/502/);
  });

  it("strips trailing slashes from the base URL", async () => {
    const fetchImpl = vi.fn(async () => mockResponse('{"code":200,"message":"ok","data":{"ok":true}}')) as unknown as typeof fetch;
    const client = new StudioClient({ baseUrl: "http://x/", token: "T", fetchImpl });
    await client.executeTool("rmq.topic.list", { cluster: "local" });
    const calls = (fetchImpl as unknown as { mock: { calls: [string, RequestInit?][] } }).mock.calls;
    expect(calls[0][0]).toBe("http://x/api/ai/tools/rmq.topic.list/execute");
  });

  it("sends Accept but no Content-Type on GET (listTools)", async () => {
    let captured: RequestInit | undefined;
    const fetchImpl = vi.fn(async (_url: string, init?: RequestInit) => {
      captured = init;
      return mockResponse('{"code":200,"message":"ok","data":[]}');
    }) as unknown as typeof fetch;
    const client = new StudioClient({ baseUrl: "http://x", token: "T", fetchImpl });
    await client.listTools();
    expect((captured?.headers as Record<string, string> | undefined)?.["Accept"]).toBe("application/json");
    expect((captured?.headers as Record<string, string> | undefined)?.["Content-Type"]).toBeUndefined();
  });

  it("throws on an empty success body", async () => {
    const fetchImpl = vi.fn(async () => mockResponse("")) as unknown as typeof fetch;
    const client = new StudioClient({ baseUrl: "http://x", token: "T", fetchImpl });
    await expect(client.listTools()).rejects.toThrow(/Empty response/);
  });
});
