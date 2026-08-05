/**
 * Shared types for the rmq-mcp server. The shapes mirror the RocketMQ Studio
 * server's `/api/ai` contract so this MCP server stays a thin, dumb adapter.
 */

/** A tool as described by the Studio server's catalog (`GET /api/ai/tools`). */
export interface AiToolVO {
  name: string;
  description: string;
  parameters?: ToolInputSchema;
  riskLevel?: string;
  permission?: string;
  requiredCapabilities?: string[];
  outputSchema?: unknown;
  viewHint?: string;
  deprecated?: boolean;
  replacement?: string;
}

export interface ToolInputProperty {
  type?: string;
  description?: string;
}

export interface ToolInputSchema {
  type?: string;
  properties?: Record<string, ToolInputProperty>;
  required?: string[];
  additionalProperties?: boolean;
}

/** The server wraps every response in a `Result` envelope. */
export interface Result<T> {
  code: number;
  message: string;
  data: T;
}
