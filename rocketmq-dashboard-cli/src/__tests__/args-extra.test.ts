import { describe, expect, it } from "vitest";
import { parseArgs } from "../args";

describe("parseArgs default handling", () => {
  const DEFAULT_SERVER = process.env.RMQ_STUDIO_URL || "http://localhost:8080";

  it("uses the table default when --output has no value", () => {
    const r = parseArgs(["topic", "list", "--output"]);
    expect(r.options.output).toBe("table");
  });

  it("falls back to the localhost server when --server has no value", () => {
    const r = parseArgs(["topic", "list", "--server"]);
    expect(r.options.server).toBe(DEFAULT_SERVER);
  });

  it("treats a flag with no value (next token is another flag) as undefined", () => {
    const r = parseArgs(["--user", "--password", "topic", "list"]);
    // --user has no value (next token is another flag) -> undefined
    expect(r.options.user).toBeUndefined();
    // --password consumes 'topic' as its value (standard flag semantics)
    expect(r.options.password).toBe("topic");
    expect(r.positionals).toEqual(["list"]);
  });

  it("treats --token at end of input as undefined", () => {
    const r = parseArgs(["topic", "list", "--token"]);
    expect(r.options.token).toBeUndefined();
    expect(r.positionals).toEqual(["topic", "list"]);
  });

  it("ignores an unknown flag and does not set a matching option", () => {
    const r = parseArgs(["topic", "list", "--unknown-flag", "value"]);
    expect(r.positionals).toEqual(["topic", "list"]);
    expect((r.options as unknown as Record<string, unknown>)["unknownFlag"]).toBeUndefined();
  });

  it("keeps the trailing value attached to --cluster even when followed by another flag", () => {
    const r = parseArgs(["topic", "list", "--cluster", "local", "--dry-run"]);
    expect(r.options.cluster).toBe("local");
    expect(r.options.dryRun).toBe(true);
  });
});
