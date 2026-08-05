import { describe, expect, it } from "vitest";
import { formatOutput, toTable } from "../format";

describe("formatOutput", () => {
  const data = [
    { name: "a", tps: 10 },
    { name: "b", tps: 20 },
  ];

  it("renders json", () => {
    expect(formatOutput(data, "json")).toBe(JSON.stringify(data, null, 2));
  });

  it("renders yaml", () => {
    const yaml = formatOutput(data, "yaml");
    expect(yaml).toContain("name: a");
    expect(yaml).toContain("tps: 10");
  });

  it("renders an array of objects as a table", () => {
    const table = formatOutput(data, "table");
    expect(table).toContain("name");
    expect(table).toContain("tps");
    expect(table).toContain("a");
    expect(table).toContain("20");
  });

  it("renders a single object as a key/value table", () => {
    const table = formatOutput({ cluster: "local", brokers: 3 }, "table");
    expect(table).toContain("cluster");
    expect(table).toContain("local");
    expect(table).toContain("brokers");
  });
});

describe("toTable", () => {
  it("returns empty string for no rows", () => {
    expect(toTable([], "table")).toBe("");
  });
});
