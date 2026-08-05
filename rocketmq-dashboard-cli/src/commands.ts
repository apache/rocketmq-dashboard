import type { StudioClient } from "./client";
import type { CliOptions } from "./args";
import { splitToolArgs } from "./args";
import { buildToolName, parseToolName, toolMatchesResource } from "./naming";
import { formatOutput } from "./format";
import type { AiToolVO, ToolInputSchema } from "./types";

interface ParamInfo {
  name: string;
  required: boolean;
  type?: string;
  description?: string;
}

function toolParameters(tool: AiToolVO): ParamInfo[] {
  const schema = tool.parameters as ToolInputSchema | undefined;
  if (!schema || !schema.properties) return [];
  const required = new Set<string>(schema.required ?? []);
  return Object.entries(schema.properties).map(([name, def]) => ({
    name,
    required: required.has(name),
    type: def.type,
    description: def.description,
  }));
}

/** Run a tool invocation: `rmqctl <resource> [verb] [key=value ...]`. */
export async function runToolCommand(
  client: StudioClient,
  positionals: string[],
  options: CliOptions,
): Promise<string> {
  const { resource, verb, kv } = splitToolArgs(positionals);
  if (!resource) {
    throw new Error("Usage: rmqctl <resource> [verb] [key=value ...] --cluster <name>");
  }

  const name = buildToolName(resource, verb);
  const tools = await client.listTools(options.cluster);
  const tool = tools.find((t) => t.name === name);
  if (!tool) {
    const available = tools.map((t) => t.name).join(", ");
    throw new Error(`Unknown tool: ${name}.${available ? ` Available: ${available}` : " Run 'rmqctl explain' to discover tools."}`);
  }
  if (tool.deprecated) {
    throw new Error(`Tool ${name} is deprecated${tool.replacement ? `; use ${tool.replacement}` : ""}.`);
  }

  // Server currently enables only L1 (read-only) tools; surface a clear hint.
  if (tool.riskLevel && tool.riskLevel !== "L1" && !options.enableDangerousOps) {
    throw new Error(
      `Tool ${name} is risk level ${tool.riskLevel}. The Studio server currently enables only L1 tools. ` +
        `Re-run with --enable-dangerous-ops to acknowledge (the server still gates execution).`,
    );
  }

  const params = toolParameters(tool);
  const paramNames = new Set(params.map((p) => p.name));

  const input: Record<string, unknown> = { ...kv };
  // Only forward --cluster when the tool actually accepts it, otherwise the
  // server's input schema (additionalProperties: false) would reject it.
  if (options.cluster && paramNames.has("cluster")) {
    input.cluster = options.cluster;
  }

  const missing = params.filter((p) => p.required && (input[p.name] === undefined || input[p.name] === ""));
  if (missing.length > 0) {
    throw new Error(`Missing required parameter(s): ${missing.map((p) => p.name).join(", ")} for ${name}.`);
  }

  const result = await client.executeTool(name, input);
  return formatOutput(result, options.output, tool.viewHint);
}

/** Describe available tools: `rmqctl explain [resource]`. */
export async function explainCommand(
  client: StudioClient,
  positionals: string[],
  options: CliOptions,
): Promise<string> {
  const resourceFilter = positionals[0];
  const tools = await client.listTools(options.cluster);
  const filtered = tools.filter((t) => toolMatchesResource(t, resourceFilter));

  if (filtered.length === 0) {
    return resourceFilter
      ? `No tools found for resource '${resourceFilter}'. Run 'rmqctl explain' to list everything.`
      : `No tools available${options.cluster ? ` for cluster '${options.cluster}'` : ""}. ` +
          `Run 'rmqctl explain --cluster <name>' to see cluster-scoped tools.`;
  }

  const lines: string[] = [];
  for (const tool of filtered) {
    const { resource, verb } = parseToolName(tool.name);
    lines.push(`● ${tool.name}  (risk ${tool.riskLevel ?? "?"}, ${tool.permission ?? "n/a"})`);
    lines.push(`  ${tool.description}`);
    const params = toolParameters(tool);
    if (params.length > 0) {
      lines.push("  params:");
      for (const p of params) {
        const tail = [p.type ? `${p.type}` : "any", p.description ? `— ${p.description}` : ""]
          .filter(Boolean)
          .join(" ");
        lines.push(`    - ${p.name}${p.required ? " (required)" : ""} ${tail}`.trimEnd());
      }
    }
    if (tool.viewHint) lines.push(`  view: ${tool.viewHint}`);
    lines.push("");
  }
  return lines.join("\n").trimEnd();
}
