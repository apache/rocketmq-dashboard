import yaml from "js-yaml";
import type { OutputFormat } from "./types";

/** Render tool output in the requested format. */
export function formatOutput(data: unknown, format: OutputFormat, viewHint?: string): string {
  switch (format) {
    case "json":
      return JSON.stringify(data, null, 2);
    case "yaml":
      return yaml.dump(data, { noRefs: true });
    case "table":
    default:
      return toTable(data, viewHint);
  }
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function toTable(data: unknown, viewHint?: string): string {
  if (Array.isArray(data)) {
    if (data.length === 0) return "";
    if (data.every(isPlainObject)) {
      const rows = data as Record<string, unknown>[];
      const cols = collectColumns(rows);
      if (cols.length > 0) {
        const matrix = [cols, ...rows.map((r) => cols.map((c) => stringify(r[c])))];
        return renderTable(matrix);
      }
    }
    // Fallback for arrays of scalars / nested structures.
    return yaml.dump(data, { noRefs: true });
  }
  if (isPlainObject(data)) {
    const rows = Object.entries(data).map(([k, v]) => [k, stringify(v)]);
    return renderTable([["KEY", "VALUE"], ...rows]);
  }
  // Fallback for scalars.
  return yaml.dump(data, { noRefs: true });
}

function collectColumns(rows: Record<string, unknown>[]): string[] {
  const seen = new Set<string>();
  const cols: string[] = [];
  for (const row of rows) {
    for (const key of Object.keys(row)) {
      if (!seen.has(key)) {
        seen.add(key);
        cols.push(key);
      }
    }
  }
  return cols;
}

function renderTable(matrix: string[][]): string {
  if (matrix.length === 0) return "";
  const widths = matrix[0].map((_, col) => Math.max(...matrix.map((r) => (r[col] ?? "").length)));
  return matrix
    .map((r) => r.map((cell, col) => (cell ?? "").padEnd(widths[col])).join("  "))
    .join("\n");
}

function stringify(value: unknown): string {
  if (value === null || value === undefined) return "";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}
