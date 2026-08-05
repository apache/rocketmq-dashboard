import type { AiToolVO, Result } from "./types";

export interface StudioClientOptions {
  baseUrl: string;
  user?: string;
  password?: string;
  token?: string;
  /** Injectable fetch for testing. Defaults to the global `fetch`. */
  fetchImpl?: typeof fetch;
}

/** Error thrown for any non-2xx response from the Studio server. */
export class StudioClientError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code?: number,
  ) {
    super(message);
    this.name = "StudioClientError";
  }
}

/**
 * Thin HTTP client for the RocketMQ Studio server. It performs only protocol
 * translation: auth handling and forwarding tool calls. All business logic
 * (tool catalog, validation, execution) lives server-side.
 */
export class StudioClient {
  private token?: string;
  private readonly fetchImpl: typeof fetch;

  constructor(private readonly opts: StudioClientOptions) {
    this.token = opts.token;
    this.fetchImpl = opts.fetchImpl ?? fetch;
  }

  private headers(): Record<string, string> {
    const headers: Record<string, string> = { "Content-Type": "application/json" };
    if (this.token) headers["Authorization"] = `Bearer ${this.token}`;
    return headers;
  }

  private async request<T>(method: string, path: string, body?: unknown, allowRetry = true): Promise<Result<T>> {
    const response = await this.fetchImpl(`${this.opts.baseUrl}${path}`, {
      method,
      headers: this.headers(),
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });

    if (response.status === 401 && allowRetry && this.opts.user && this.opts.password && !this.token) {
      await this.login();
      return this.request<T>(method, path, body, false);
    }

    const text = await response.text();
    const parsed = parseResult<T>(text);
    if (!response.ok) {
      throw new StudioClientError(parsed?.message ?? response.statusText ?? `HTTP ${response.status}`, response.status, parsed?.code);
    }
    if (!parsed) throw new StudioClientError(`Empty response from ${path}`, response.status);
    return parsed;
  }

  /** Log in and cache the bearer token for subsequent requests. */
  async login(): Promise<void> {
    const response = await this.fetchImpl(`${this.opts.baseUrl}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: this.opts.user, password: this.opts.password }),
    });
    const text = await response.text();
    if (!response.ok) {
      throw new StudioClientError(parseResult<unknown>(text)?.message ?? "Login failed", response.status);
    }
    const data = parseResult<{ token: string }>(text);
    this.token = data?.data?.token;
    if (!this.token) throw new StudioClientError("Login response missing token", response.status);
  }

  /** Discover tools from the server catalog. */
  async listTools(cluster?: string): Promise<AiToolVO[]> {
    const qs = cluster ? `?cluster=${encodeURIComponent(cluster)}` : "";
    const result = await this.request<AiToolVO[]>("GET", `/api/ai/tools${qs}`);
    return result.data ?? [];
  }

  /** Execute a tool by name, returning the raw tool output. */
  async executeTool(name: string, input: Record<string, unknown>): Promise<unknown> {
    const result = await this.request<unknown>("POST", `/api/ai/tools/${encodeURIComponent(name)}/execute`, input);
    return result.data;
  }
}

function parseResult<T>(text: string): Result<T> | undefined {
  if (!text) return undefined;
  try {
    return JSON.parse(text) as Result<T>;
  } catch {
    return undefined;
  }
}
