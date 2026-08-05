import type { AiToolVO } from "./types";

/**
 * The CLI is data-driven: the first two positionals are `<resource> <verb>`
 * and map to a server tool named `rmq.<resource>.<verb>`. A single positional
 * (e.g. `capabilities`) maps to `rmq.<resource>` — the server's meta tools.
 */

export function buildToolName(resource: string, verb?: string): string {
  const base = `rmq.${resource}`;
  return verb ? `${base}.${verb}` : base;
}

/** Split a server tool name back into its resource/verb parts. */
export function parseToolName(name: string): { resource: string; verb: string } {
  const rest = name.startsWith("rmq.") ? name.slice("rmq.".length) : name;
  const parts = rest.split(".");
  return { resource: parts[0] ?? "", verb: parts.slice(1).join(".") };
}

/** Filter a tool list by resource, used by `rmqctl explain <resource>`. */
export function toolMatchesResource(tool: AiToolVO, resource?: string): boolean {
  if (!resource) return true;
  return parseToolName(tool.name).resource === resource;
}
