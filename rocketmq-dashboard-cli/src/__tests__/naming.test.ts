import { describe, expect, it } from "vitest";
import { buildToolName, parseToolName, toolMatchesResource } from "../naming";
import { splitToolArgs } from "../args";
import type { AiToolVO } from "../types";

describe("tool naming", () => {
  it("builds rmq.<resource>.<verb>", () => {
    expect(buildToolName("topic", "list")).toBe("rmq.topic.list");
  });

  it("builds rmq.<resource> for meta tools", () => {
    expect(buildToolName("capabilities")).toBe("rmq.capabilities");
  });

  it("round-trips name -> resource/verb", () => {
    expect(parseToolName("rmq.topic.list")).toEqual({ resource: "topic", verb: "list" });
    expect(parseToolName("rmq.capabilities")).toEqual({ resource: "capabilities", verb: "" });
  });
});

describe("splitToolArgs", () => {
  it("separates words and key=value pairs", () => {
    const r = splitToolArgs(["topic", "list", "cluster=local", "limit=10"]);
    expect(r).toEqual({ resource: "topic", verb: "list", kv: { cluster: "local", limit: "10" } });
  });

  it("treats the first bare word as resource", () => {
    const r = splitToolArgs(["capabilities"]);
    expect(r).toEqual({ resource: "capabilities", verb: undefined, kv: {} });
  });
});

describe("toolMatchesResource", () => {
  const tool = (name: string): AiToolVO => ({ name, description: "" });
  it("matches by resource", () => {
    expect(toolMatchesResource(tool("rmq.topic.list"), "topic")).toBe(true);
    expect(toolMatchesResource(tool("rmq.topic.list"), "cluster")).toBe(false);
  });
  it("matches everything when no filter", () => {
    expect(toolMatchesResource(tool("rmq.topic.list"))).toBe(true);
  });
});
