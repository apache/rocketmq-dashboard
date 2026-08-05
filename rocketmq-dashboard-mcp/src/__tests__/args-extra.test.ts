import { describe, it, expect } from "vitest";
import { parseMcpArgs } from "../args";

const DEFAULT_SERVER = process.env.RMQ_STUDIO_URL || "http://localhost:8080";

describe("parseMcpArgs", () => {
  it("parses every long flag with a value", () => {
    const r = parseMcpArgs([
      "--server", "http://s:8080",
      "--user", "admin",
      "--password", "secret",
      "--token", "TKN",
      "--cluster", "prod",
    ]);
    expect(r).toMatchObject({
      server: "http://s:8080",
      user: "admin",
      password: "secret",
      token: "TKN",
      cluster: "prod",
      help: false,
    });
  });

  it("parses every short flag with a value", () => {
    const r = parseMcpArgs(["-s", "http://s:8080", "-u", "a", "-p", "b", "-t", "T", "-c", "prod"]);
    expect(r).toMatchObject({
      server: "http://s:8080",
      user: "a",
      password: "b",
      token: "T",
      cluster: "prod",
    });
  });

  it("sets help for -h and --help", () => {
    expect(parseMcpArgs(["-h"]).help).toBe(true);
    expect(parseMcpArgs(["--help"]).help).toBe(true);
  });

  it("leaves a flag value unset when the next token is another flag", () => {
    const r = parseMcpArgs(["--server", "--user", "admin"]);
    expect(r.server).toBe(DEFAULT_SERVER);
    expect(r.user).toBe("admin");
  });

  it("ignores unknown tokens", () => {
    const r = parseMcpArgs(["--server", "http://s:8080", "bogus", "--also-bogus"]);
    expect(r.server).toBe("http://s:8080");
    expect((r as unknown as Record<string, unknown>)["bogus"]).toBeUndefined();
  });

  it("falls back to environment defaults", () => {
    const r = parseMcpArgs([]);
    expect(r.server).toBe(DEFAULT_SERVER);
    expect(r.user).toBe(process.env.RMQ_USER);
    expect(r.password).toBe(process.env.RMQ_PASSWORD);
    expect(r.token).toBe(process.env.RMQ_TOKEN);
  });
});
