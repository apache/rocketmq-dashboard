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

import type { ReactNode } from 'react';
import type { McpTool } from '../../../api/ai';
import type { Bubble, RenderBlock, TextBlock } from '../render/blocks';
import UserBubble from './UserBubble';
import AssistantBubble from './AssistantBubble';

/**
 * Transcript: persisted bubbles, then the bubble of the run in flight.
 *
 * Kept deliberately dumb — no fetching, no reducing, no scroll handling (`ChatThread` owns that).
 * It receives the bubbles `useConversationTimeline` folded plus the live blocks `useAgentRun` is
 * filling, and renders them in that order.
 *
 * Appending the live bubble AFTER the persisted ones is what makes the handover invisible: the run's
 * finally block refetches the timeline first and only then clears the live blocks, so for one commit
 * the answer exists in both lists. Because the live blocks are cleared in the same tick that the
 * persisted rows arrive, the duplicate never paints — and if the refetch failed, the live bubble is
 * still here as the only copy of the answer.
 */

export interface BubbleListProps {
  bubbles: Bubble[];
  /** Blocks of the run in flight, rendered as a trailing streaming assistant bubble. */
  liveBlocks?: RenderBlock[];
  /** True while that run is streaming; drives the pending dots and the thinking auto-expand. */
  streaming?: boolean;
  /** Catalog from `listTools()`, so tool blocks can show a risk level. */
  toolCatalog?: readonly McpTool[];
  /** Estimated speed of the run in flight, shown live on the streaming bubble. */
  liveTokensPerSecond?: number | null;
  /** Final speed of the last finished run, shown on the newest persisted assistant bubble. */
  lastRunTokensPerSecond?: number | null;
  /** Model currently selected in the composer; drives the assistant bubbles' brand logo avatar. */
  model?: string;
  /** Rendered when the transcript is completely empty. */
  empty?: ReactNode;
}

/** A user bubble is the persisted `user` event; its text blocks are the prompt that was sent. */
function bubbleText(blocks: readonly RenderBlock[]): string {
  return blocks
    .filter((block): block is TextBlock => block.kind === 'text')
    .map((block) => block.text)
    .join('\n');
}

const BubbleList = ({
  bubbles,
  liveBlocks,
  streaming = false,
  toolCatalog,
  liveTokensPerSecond = null,
  lastRunTokensPerSecond = null,
  model,
  empty,
}: BubbleListProps) => {
  const hasLive = liveBlocks !== undefined && (streaming || liveBlocks.length > 0);

  if (bubbles.length === 0 && !hasLive) return <>{empty ?? null}</>;

  // The speed of the last finished run belongs to the newest persisted assistant bubble — but
  // only while no newer run is streaming (the hook resets it when one starts).
  let lastAssistantIndex = -1;
  if (!streaming && lastRunTokensPerSecond !== null) {
    for (let index = bubbles.length - 1; index >= 0; index -= 1) {
      if (bubbles[index].role === 'assistant') {
        lastAssistantIndex = index;
        break;
      }
    }
  }

  return (
    <>
      {bubbles.map((bubble, index) =>
        bubble.role === 'user' ? (
          <UserBubble
            key={`user-${bubble.turn ?? index}-${index}`}
            text={bubbleText(bubble.blocks)}
            createdAt={bubble.createdAt}
          />
        ) : (
          <AssistantBubble
            key={`assistant-${bubble.turn ?? index}-${index}`}
            bubble={bubble}
            toolCatalog={toolCatalog}
            model={model}
            tokensPerSecond={
              bubble.tokensPerSecond ??
              (index === lastAssistantIndex ? lastRunTokensPerSecond : null)
            }
          />
        ),
      )}
      {hasLive && (
        <AssistantBubble
          key="assistant-live"
          bubble={{ role: 'assistant', blocks: liveBlocks ?? [] }}
          streaming={streaming}
          toolCatalog={toolCatalog}
          model={model}
          tokensPerSecond={liveTokensPerSecond}
        />
      )}
    </>
  );
};

export default BubbleList;
