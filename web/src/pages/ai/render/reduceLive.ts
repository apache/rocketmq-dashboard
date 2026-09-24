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

import type { ChatSseEvent } from '../../../api/aiEvents';
import type { RenderBlock } from './blocks';
import {
  appendText,
  appendThinking,
  errorBlock,
  noticeBlock,
  runningToolBlock,
  withToolResult,
} from './blocks';

/**
 * Fold one live SSE frame into the render blocks of the bubble that is currently streaming.
 *
 * Pure: the input array is never mutated. A frame that renders nothing (`run_started`,
 * `run_finished`) returns the very same reference so callers can skip a re-render cheaply.
 *
 * Coalescing rules — `foldTimeline.ts` applies the identical rules to the persisted events, which
 * is what lets replay and live converge on the same `RenderBlock[]`:
 * - `text_delta` appends to a trailing text block, otherwise starts one.
 * - `thinking` appends to a trailing thinking block ONLY when the source matches; a differing
 *   source starts a new block, so the prompt-enhancement rewrite is never merged into (and never
 *   labelled as) model reasoning.
 * - `tool_start` pushes a running tool card, `tool_done` fills it.
 * - `notice` / `error` push their own block.
 * - `run_started` / `run_finished` are run-level metadata the caller handles; they render nothing.
 */
export function reduceLiveBlocks(blocks: RenderBlock[], event: ChatSseEvent): RenderBlock[] {
  switch (event.type) {
    case 'run_started':
    case 'run_finished':
      return blocks;

    case 'text_delta':
      return appendText(blocks, event.content);

    case 'thinking':
      return appendThinking(blocks, event.content, event.source);

    case 'tool_start':
      return [...blocks, runningToolBlock(event.tcId, event.tool, event.input)];

    case 'tool_done':
      return withToolResult(blocks, event);

    case 'notice':
      return [...blocks, noticeBlock(event)];

    case 'error':
      return [...blocks, errorBlock(event)];

    default: {
      // Exhaustiveness guard: adding an event type to the contract without handling it here is a
      // compile error, not a silently dropped frame.
      const _never: never = event;
      throw new Error(`Unhandled live event type: ${JSON.stringify(_never)}`);
    }
  }
}
