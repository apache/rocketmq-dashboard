/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import type {
  ErrorFields,
  NoticeFields,
  NoticeLevel,
  RunStatus,
  ThinkingSource,
  ToolResultFields,
} from '../../../api/aiEvents';

/**
 * The single render target both the live stream and the replayed timeline reduce into.
 *
 * Studio-scoped on purpose: no artifacts, subagents, plans or checkpoints. Anything a provider
 * emits that has no block here is either folded into a `notice` or dropped by the reducer.
 */

export interface TextBlock {
  kind: 'text';
  text: string;
}

export interface ThinkingBlock {
  kind: 'thinking';
  text: string;
  /**
   * Kept on the block so the renderer can label model reasoning and the prompt-enhancement
   * rewrite differently. Two adjacent thinking blocks with different sources never merge.
   */
  source: ThinkingSource;
}

export type ToolBlockStatus = 'running' | 'done';

export interface ToolBlock {
  kind: 'tool';
  tcId: string;
  /** Full provider tool name; use {@link toolDisplayName} for the visible label. */
  tool: string;
  /**
   * Assembled tool arguments. Structured on purpose: they come from the model as a JSON object and
   * the backend guarantees a no-arg tool emits `{}` rather than null, so the event contract can
   * type them `Record<string, unknown>`. On a block, `null` means only "the matching `tool_use` was
   * outside the paging window" — see {@link withToolResult}.
   */
  input: unknown;
  /**
   * Tool output as an opaque TRANSPORT STRING; `null` while the call is still running.
   *
   * The asymmetry with {@link ToolBlock.input} is deliberate. Input is structured because it comes
   * from the model. Output comes from whatever the tool returned: the backend has already stripped
   * inline base64 and capped it at 32 KiB (`outputBytes` keeps the real size, `truncated` says
   * whether the cap bit), and hands it over as text that merely *usually* holds serialised JSON.
   * Rendering it raw in a `<pre>` is therefore always correct, while parsing it is a per-tool
   * decision the caller makes — something the transport contract cannot promise.
   */
  output: string | null;
  outputBytes: number | null;
  truncated: boolean;
  durationMs: number | null;
  success: boolean | null;
  /** Present only when the call failed. */
  error?: string;
  status: ToolBlockStatus;
}

export interface NoticeBlock {
  kind: 'notice';
  level: NoticeLevel;
  message: string;
}

export interface ErrorBlock {
  kind: 'error';
  code: string;
  message: string;
  hint?: string;
}

export type RenderBlock = TextBlock | ThinkingBlock | ToolBlock | NoticeBlock | ErrorBlock;

export type BubbleRole = 'user' | 'assistant';

/** One message in the transcript: a user prompt or everything one run rendered. */
export interface Bubble {
  role: BubbleRole;
  blocks: RenderBlock[];
  turn?: number;
  /** Terminal status carried by the persisted `run_status` event, if the run reached one. */
  runStatus?: RunStatus;
  createdAt?: string;
  /** Persisted generation speed (token/s) the client reported for this bubble's run. */
  tokensPerSecond?: number | null;
}

const MCP_TOOL_PREFIX = 'mcp__rocketmq-studio__';
const RMQ_NAMESPACE_PREFIX = 'rmq.';

/**
 * Tool name as it should read in the UI: the MCP transport prefix and the `rmq.` namespace are
 * Studio-internal noise. Callers keep the raw name for a `title` attribute so the full identifier
 * stays discoverable.
 *
 * `mcp__rocketmq-studio__rmq.topic.list` -> `topic.list`, `rmq.topic.list` -> `topic.list`,
 * `Bash` -> `Bash`.
 */
export function toolDisplayName(tool: string): string {
  const withoutMcp = tool.startsWith(MCP_TOOL_PREFIX) ? tool.slice(MCP_TOOL_PREFIX.length) : tool;
  return withoutMcp.startsWith(RMQ_NAMESPACE_PREFIX)
    ? withoutMcp.slice(RMQ_NAMESPACE_PREFIX.length)
    : withoutMcp;
}

// ─── Block construction primitives ──────────────────────────────
//
// `reduceLive.ts` (live SSE) and `foldTimeline.ts` (persisted replay) must build blocks through
// these primitives. That is what makes the two paths converge on structurally identical output
// instead of merely similar output: the coalescing and the field set live here exactly once.

/**
 * Append assistant text, merging into a trailing text block when there is one. Pure: returns a
 * new array.
 */
export function appendText(blocks: RenderBlock[], text: string): RenderBlock[] {
  const last = blocks[blocks.length - 1];
  if (last && last.kind === 'text') {
    return [...blocks.slice(0, -1), { kind: 'text', text: last.text + text }];
  }
  return [...blocks, { kind: 'text', text: text }];
}

/**
 * Append reasoning, merging into a trailing thinking block ONLY when `source` matches. A differing
 * source starts a new block: model reasoning and the prompt-enhancement rewrite are different
 * things and must never be coalesced into one 思维链 block. Pure: returns a new array.
 */
export function appendThinking(
  blocks: RenderBlock[],
  text: string,
  source: ThinkingSource,
): RenderBlock[] {
  const last = blocks[blocks.length - 1];
  if (last && last.kind === 'thinking' && last.source === source) {
    return [...blocks.slice(0, -1), { kind: 'thinking', text: last.text + text, source }];
  }
  return [...blocks, { kind: 'thinking', text, source }];
}

/** Advisory block. The live and persisted notice payloads are identical. */
export function noticeBlock(fields: NoticeFields): NoticeBlock {
  return { kind: 'notice', level: fields.level, message: fields.message };
}

/**
 * Failure block. `hint` is only set when the provider sent advice, so a hint-less error does not
 * grow an `undefined` key and stays deep-equal across both paths.
 */
export function errorBlock(fields: ErrorFields): ErrorBlock {
  const block: ErrorBlock = { kind: 'error', code: fields.code, message: fields.message };
  if (fields.hint !== undefined) block.hint = fields.hint;
  return block;
}

/**
 * The tool block both reducers push when a call starts. Sharing the factory is what makes a
 * live-reduced card and a replayed card structurally identical while the call is running.
 */
export function runningToolBlock(tcId: string, tool: string, input: unknown): ToolBlock {
  return {
    kind: 'tool',
    tcId,
    tool,
    input,
    output: null,
    outputBytes: null,
    truncated: false,
    durationMs: null,
    success: null,
    status: 'running',
  };
}

/**
 * Fill the tool block that owns `tcId` and mark it done. Pure: returns a new array.
 *
 * When no block owns the id — a reconnect that joined after the start, or a persisted
 * `tool_result` whose `tool_use` fell outside the paging window — a completed block is appended
 * anyway so the output is never silently dropped. Its `input` is unknown and stays `null`.
 */
export function withToolResult(blocks: RenderBlock[], result: ToolResultFields): RenderBlock[] {
  const index = findToolIndex(blocks, result.tcId);
  const filled: ToolBlock = {
    ...(index >= 0
      ? (blocks[index] as ToolBlock)
      : runningToolBlock(result.tcId, result.tool, null)),
    tcId: result.tcId,
    tool: result.tool,
    output: result.output,
    outputBytes: result.outputBytes,
    truncated: result.truncated,
    durationMs: result.durationMs,
    success: result.success,
    status: 'done',
  };
  // A retry of the same tcId must not keep the previous failure on the block.
  if (result.error === undefined) {
    delete filled.error;
  } else {
    filled.error = result.error;
  }

  if (index < 0) return [...blocks, filled];
  return [...blocks.slice(0, index), filled, ...blocks.slice(index + 1)];
}

/** Last tool block owning the id; the match is searched backwards because it is usually recent. */
function findToolIndex(blocks: RenderBlock[], tcId: string): number {
  for (let i = blocks.length - 1; i >= 0; i -= 1) {
    const block = blocks[i];
    if (block.kind === 'tool' && block.tcId === tcId) return i;
  }
  return -1;
}
