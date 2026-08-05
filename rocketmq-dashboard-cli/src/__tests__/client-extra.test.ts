import { describe, it, expect, vi } from "vitest";
import { StudioClient, StudioClientError } from "../client";

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

describe("StudioClient.login error paths", () => {
  it("throws when credentials are missing", async () => {
    const fetchMock = vi.fn(async () => mockResponse({})) as unknown as typeof fetch;
    const client = makeClient(fetchMock);
    await expect(client.login()).rejects.toBeInstanceOf(StudioClientError);
  });

  it("throws when the login endpoint returns non-OK", async () => {
    const fetchMock = vi.fn(async () => mockResponse({ code: 401, message: "bad" }, 401)) as unknown as typeof fetch;
    const client = makeClient(fetchMock, { user: "u", password: "p" });
    await expect(client.login()).rejects.toMatchObject({ status: 401 });
  });

  it("throws when the login response has no token", async () => {
    const fetchMock = vi.fn(async () => mockResponse({ code: 200, message: "ok", data: {} })) as unknown as typeof fetch;
    const client = makeClient(fetchMock, { user: "u", password: "p" });
    await expect(client.login()).rejects.toThrow(/token/i);
  });
});

describe("StudioClient.request error handling", () => {
  it("attaches Authorization when a token is preset", async () => {
    let authHeader: string | undefined;
    const fetchMock = vi.fn(async (_url: string, init?: RequestInit) => {
      authHeader = (init?.headers as Record<string, string> | undefined)?.["Authorization"];
      return mockResponse({ code: 200, message: "ok", data: [1] });
    }) as unknown as typeof fetch;
    const client = makeClient(fetchMock, { token: "TKN" });
    await client.listTools();
    expect(authHeader).toBe("Bearer TKN");
  });

  it("does not retry and throws on 401 without credentials", async () => {
    const fetchMock = vi.fn(async () => mockResponse({ code: 401, message: "Unauthorized" }, 401)) as unknown as typeof fetch;
    const client = makeClient(fetchMock);
    await expect(client.executeTool("x", {})).rejects.toBeInstanceOf(StudioClientError);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("throws with the raw body text when the error response is not JSON", async () => {
    const fetchMock = vi.fn(async () => mockResponse("internal explosion", 500)) as unknown as typeof fetch;
    const client = makeClient(fetchMock, { token: "T" });
    await expect(client.executeTool("x", {})).rejects.toThrow(/internal explosion/);
  });

  it("falls back to a generic message on an empty error body", async () => {
    const fetchMock = vi.fn(async () => mockResponse("", 502)) as unknown as typeof fetch;
    const client = makeClient(fetchMock, { token: "T" });
    await expect(client.executeTool("x", {})).rejects.toThrow(/502/);
  });

  it("throws on an empty success body", async () => {
    const fetchMock = vi.fn(async () => mockResponse("")) as unknown as typeof fetch;
    const client = makeClient(fetchMock, { token: "T" });
    await expect(client.listTools()).rejects.toThrow(/Empty response/);
  });
});
