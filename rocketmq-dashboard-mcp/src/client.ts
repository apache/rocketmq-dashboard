import type { AiToolVO, Result } from "./types";

/** Thrown when the Studio server returns a non-OK response or cannot be reached. */
export class StudioClientError extends Error {
  status?: number;
  code?: number;
  constructor(message: string, status?: number, code?: number) {
    super(message);
    this.name = "StudioClientError";
    this.status = status;
    this.code = code;
  }
}

export interface StudioClientOptions {
  baseUrl: string;
  user?: string;
  password?: string;
  token?: string;
  /** Injectable fetch for tests; defaults to the global fetch. */
  fetchImpl?: typeof fetch;
}

/**
 * Thin HTTP client for the RocketMQ Studio `/api/ai` contract. It performs no
 * protocol translation of its own — it only discovers tools and forwards calls,
 * exactly like the `rmqctl` CLI. Auth uses a Bearer token obtained from
 * `/api/auth/login` (cached and retried once on 401).
 */
export class StudioClient {
  private readonly baseUrl: string;
  private readonly user?: string;
  private readonly password?: string;
  private token?: string;
  private readonly fetchImpl: typeof fetch;

  constructor(opts: StudioClientOptions) {
    this.baseUrl = opts.baseUrl.replace(/\/+$/, "");
    this.user = opts.user;
    this.password = opts.password;
    this.token = opts.token;
    this.fetchImpl = opts.fetchImpl ?? fetch;
  }

  async login(): Promise<void> {
    if (!this.user || !this.password) {
      throw new StudioClientError("Login requires RMQ_USER and RMQ_PASSWORD (or --user/--password).");
    }
    const res = await this.fetchImpl(`${this.baseUrl}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: this.user, password: this.password }),
    });
    if (!res.ok) {
      throw new StudioClientError(`Login failed: ${res.status}`, res.status);
    }
    const data = (await res.json()) as Result<{ token: string }>;
    this.token = data.data?.token;
    if (!this.token) {
      throw new StudioClientError("Login response did not contain a token.");
    }
  }

  async listTools(cluster?: string): Promise<AiToolVO[]> {
    const qs = cluster ? `?cluster=${encodeURIComponent(cluster)}` : "";
    const res = await this.request<Result<AiToolVO[]>>("GET", `/api/ai/tools${qs}`);
    return res.data ?? [];
  }

  async executeTool(name: string, input: Record<string, unknown>): Promise<unknown> {
    const res = await this.request<Result<unknown>>(
      "POST",
      `/api/ai/tools/${encodeURIComponent(name)}/execute`,
      input,
    );
    return res.data;
  }

  private async request<T>(method: string, path: string, body?: unknown, allowRetry = true): Promise<T> {
    const headers: Record<string, string> = { Accept: "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (this.token) headers["Authorization"] = `Bearer ${this.token}`;

    const res = await this.fetchImpl(`${this.baseUrl}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });

    if (res.status === 401 && allowRetry && this.user && this.password) {
      this.token = undefined;
      await this.login();
      return this.request<T>(method, path, body, false);
    }

    const text = await res.text();
    if (!res.ok) {
      const parsed = parseResult<{ message?: string; code?: number }>(text);
      const message = parsed?.message || text || `Request failed with ${res.status}`;
      throw new StudioClientError(message, res.status, parsed?.code);
    }
    return parseResult<T>(text) as T;
  }
}

function parseResult<T>(text: string): T | null {
  if (!text) return null;
  try {
    return JSON.parse(text) as T;
  } catch {
    return null;
  }
}
