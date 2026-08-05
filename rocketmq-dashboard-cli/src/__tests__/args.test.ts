import { describe, it, expect } from "vitest";
import { parseArgs, splitToolArgs } from "../args";

const DEFAULT_SERVER = process.env.RMQ_STUDIO_URL || "http://localhost:8080";

describe("parseArgs", () => {
  it("parses positionals and long flags", () => {
    const r = parseArgs(["topic", "list", "--cluster", "local", "--output", "yaml"]);
    expect(r.subcommand).toBeUndefined();
    expect(r.positionals).toEqual(["topic", "list"]);
    expect(r.options.cluster).toBe("local");
    expect(r.options.output).toBe("yaml");
  });

  it("defaults server to RMQ_STUDIO_URL or localhost", () => {
    const r = parseArgs(["cluster", "list"]);
    expect(r.options.server).toBe(DEFAULT_SERVER);
  });

  it("honors --server / -s overrides", () => {
    expect(parseArgs(["-s", "http://example:8080", "cluster", "list"]).options.server).toBe(
      "http://example:8080",
    );
    expect(parseArgs(["--server", "http://other:9090", "cluster", "list"]).options.server).toBe(
      "http://other:9090",
    );
  });

  it("resolves short aliases -o/-u/-p", () => {
    const r = parseArgs(["-o", "json", "-u", "admin", "-p", "secret", "topic", "list"]);
    expect(r.options.output).toBe("json");
    expect(r.options.user).toBe("admin");
    expect(r.options.password).toBe("secret");
  });

  it("treats boolean flags as true", () => {
    const r = parseArgs(["--dry-run", "--yes", "--enable-dangerous-ops", "topic", "list"]);
    expect(r.options.dryRun).toBe(true);
    expect(r.options.yes).toBe(true);
    expect(r.options.enableDangerousOps).toBe(true);
  });

  it("parses --token", () => {
    expect(parseArgs(["--token", "abc", "topic", "list"]).options.token).toBe("abc");
  });

  it("detects the explain subcommand", () => {
    const r = parseArgs(["explain", "topic"]);
    expect(r.subcommand).toBe("explain");
    expect(r.positionals).toEqual(["topic"]);
  });

  it("recognizes -h / -V as help/version", () => {
    expect(parseArgs(["-h", "topic", "list"]).options.help).toBe(true);
    expect(parseArgs(["--version", "topic", "list"]).options.version).toBe(true);
  });
});

describe("splitToolArgs", () => {
  it("splits resource/verb words from key=value pairs", () => {
    expect(splitToolArgs(["topic", "list", "a=1", "b=2"])).toEqual({
      resource: "topic",
      verb: "list",
      kv: { a: "1", b: "2" },
    });
  });

  it("handles a single resource word", () => {
    expect(splitToolArgs(["cluster"])).toEqual({ resource: "cluster", verb: undefined, kv: {} });
  });

  it("returns empty resource for no positionals", () => {
    expect(splitToolArgs([])).toEqual({ resource: "", verb: undefined, kv: {} });
  });

  it("keeps an empty value when '=' is present", () => {
    expect(splitToolArgs(["x="])).toEqual({ resource: "", verb: undefined, kv: { x: "" } });
  });
});
