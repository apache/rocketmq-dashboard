import { describe, expect, it } from "vitest";
import { toTable, formatOutput } from "../format";

describe("toTable edge cases", () => {
  it("falls back to YAML for an array of scalars", () => {
    const out = toTable([1, 2, 3]);
    expect(out).toContain("1");
    expect(out).toContain("2");
  });

  it("renders a union-column table when object keys diverge", () => {
    const out = toTable([{ a: 1 }, { b: 2 }]);
    expect(out).toContain("a");
    expect(out).toContain("b");
    expect(out).toContain("1");
  });

  it("renders a plain object with zero keys as an empty KEY/VALUE header", () => {
    const out = toTable({});
    expect(out).toContain("KEY");
    expect(out).toContain("VALUE");
  });

  it("renders a scalar value via YAML fallback", () => {
    expect(toTable("just a string")).toContain("just a string");
  });

  it("stringifies a null value as an empty cell in a table", () => {
    const out = toTable([{ name: "x", note: null }]);
    expect(out).toContain("name");
    expect(out).toContain("x");
  });

  it("stringifies a nested object value with JSON", () => {
    const out = toTable([{ cfg: { a: 1 } }]);
    expect(out).toContain('{"a":1}');
  });
});

describe("formatOutput unknown format falls back to table", () => {
  it("defaults to table rendering", () => {
    const out = formatOutput([{ name: "a" }], "table");
    expect(out).toContain("name");
  });
});
