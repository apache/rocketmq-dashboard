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

import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Button, Flex, theme } from 'antd';
import { ArrowDown } from '@phosphor-icons/react';
import { useLang } from '../../../i18n/LangContext';
import type { McpTool } from '../../../api/ai';
import type { Bubble, RenderBlock } from '../render/blocks';
import BubbleList from './BubbleList';

/**
 * The scrolling transcript.
 *
 * ─── Why this component exists ─────────────────────────────────
 * The page it replaces scrolled to the bottom on EVERY `messages` change, unconditionally. That was
 * tolerable while a stop actually stopped: the transcript only grew when the user was watching it.
 * Now a run keeps generating while the operator scrolls up to re-read an earlier tool result, and an
 * unconditional scroll yanks the viewport back on every token. So the follow behaviour is
 * conditional on `atBottom`, measured as `scrollHeight - scrollTop - clientHeight < 40`, and when the
 * reader has scrolled away a floating 回到最新 pill appears with a badge counting the messages that
 * arrived while they were away.
 *
 * ─── Ref plus state for the same flag ───────────────────────────
 * `atBottomRef` is what the effects read, `atBottom` is what renders the pill. Keeping the ref is not
 * a micro-optimisation: the auto-scroll effect must not list `atBottom` as a dependency, or scrolling
 * away would itself re-run the effect that decides whether to scroll back.
 *
 * `resetKey` clears both when the conversation changes — an unread badge belonging to the previous
 * conversation would be a lie about this one.
 */

/** Distance from the bottom that still counts as "reading the latest". */
const AT_BOTTOM_THRESHOLD_PX = 40;

export interface ChatThreadProps {
  bubbles: Bubble[];
  /** Blocks of the run in flight; rendered as a trailing streaming bubble. */
  liveBlocks?: RenderBlock[];
  /** True while that run is streaming. */
  streaming?: boolean;
  /** Catalog from `listTools()`, so tool blocks can show a risk level. */
  toolCatalog?: readonly McpTool[];
  /** Estimated speed of the run in flight, shown live on the streaming bubble. */
  liveTokensPerSecond?: number | null;
  /** Final speed of the last finished run, shown on the newest persisted assistant bubble. */
  lastRunTokensPerSecond?: number | null;
  /** Model currently selected in the composer; drives the assistant bubbles' brand logo avatar. */
  model?: string;
  /** Rendered when the transcript is empty. */
  empty?: ReactNode;
  /**
   * Rendered at the very bottom of the SAME scroll surface, after the transcript: the page reads
   * as one document the composer belongs to the end of, not a bar pinned under the viewport.
   */
  footer?: ReactNode;
  /** Earlier events exist outside the loaded window (`useConversationTimeline().hasMore`). */
  hasMore?: boolean;
  onLoadMore?: () => void;
  /** Change this to reset the scroll position and the unread badge, e.g. the conversation id. */
  resetKey?: string | number | null;
}

const ChatThread = ({
  bubbles,
  liveBlocks,
  streaming = false,
  toolCatalog,
  liveTokensPerSecond = null,
  lastRunTokensPerSecond = null,
  model,
  empty,
  footer,
  hasMore = false,
  onLoadMore,
  resetKey = null,
}: ChatThreadProps) => {
  const { t } = useLang();
  const { token } = theme.useToken();
  const containerRef = useRef<HTMLDivElement>(null);
  const sentinelRef = useRef<HTMLDivElement>(null);
  const atBottomRef = useRef(true);
  // False until the transcript has been positioned once after mount / conversation switch.
  // That first jump must be instant: a smooth animation from the top of a freshly loaded
  // transcript is exactly the page jitter a refresh shows.
  const positionedRef = useRef(false);
  const [atBottom, setAtBottom] = useState(true);
  const [unread, setUnread] = useState(0);
  const prevBubbleCountRef = useRef(bubbles.length);

  const bubbleCount = bubbles.length + (liveBlocks && liveBlocks.length > 0 ? 1 : 0);

  const followToBottom = useCallback((behavior: ScrollBehavior = 'smooth') => {
    sentinelRef.current?.scrollIntoView({ behavior });
  }, []);

  const jumpToLatest = useCallback(() => {
    atBottomRef.current = true;
    setAtBottom(true);
    setUnread(0);
    followToBottom('smooth');
  }, [followToBottom]);

  const handleScroll = useCallback(() => {
    const container = containerRef.current;
    if (!container) return;
    const distance = container.scrollHeight - container.scrollTop - container.clientHeight;
    const bottom = distance < AT_BOTTOM_THRESHOLD_PX;
    atBottomRef.current = bottom;
    setAtBottom(bottom);
    if (bottom) setUnread(0);
  }, []);

  // Switching conversation: the new transcript starts at its tail, and the previous conversation's
  // unread count must not survive the switch. Instant on purpose — see positionedRef.
  useEffect(() => {
    atBottomRef.current = true;
    positionedRef.current = false;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setAtBottom(true);
    setUnread(0);
    followToBottom('auto');
  }, [followToBottom, resetKey]);

  // Follow the stream only while the reader is already at the bottom. The first arrival after a
  // reset positions instantly (the transcript just loaded, there is nothing to animate over);
  // later arrivals follow smoothly.
  useEffect(() => {
    if (!atBottomRef.current) return;
    if (!positionedRef.current) {
      positionedRef.current = true;
      followToBottom('auto');
      return;
    }
    followToBottom('smooth');
  }, [bubbles, followToBottom, liveBlocks]);

  // Count what arrives while the reader is away: one per message, not one per streamed token.
  // Only a genuine increase in bubble count is a new message — a streaming failure that removes
  // the live bubble must not inflate the badge.
  useEffect(() => {
    const previous = prevBubbleCountRef.current;
    prevBubbleCountRef.current = bubbleCount;
    if (atBottomRef.current || bubbleCount <= previous) return;
    setUnread((count) => count + 1);
  }, [bubbleCount]);

  return (
    <div style={{ position: 'relative', flex: 1, minHeight: 0 }}>
      <div
        ref={containerRef}
        onScroll={handleScroll}
        data-testid="ai-thread-scroll"
        className="w-full scrollbar-hide"
        style={{ height: '100%', overflowY: 'auto', padding: '16px 24px' }}
      >
        {/* The stream fills the full content width; bubbles and cards size themselves.
            minHeight 100% + the flex spacer pin the composer to the bottom of the visible
            area while the transcript is short; once the transcript overflows, the spacer
            collapses to zero and the composer scrolls with the content like any document. */}
        <div style={{ width: '100%', minHeight: '100%', display: 'flex', flexDirection: 'column' }}>
          {hasMore && onLoadMore && (
            <Flex justify="center" style={{ marginBottom: 12 }}>
              <Button size="small" onClick={() => onLoadMore()} style={{ fontSize: 14 }}>
                {t('ai.thread.loadEarlier')}
              </Button>
            </Flex>
          )}
          <BubbleList
            bubbles={bubbles}
            liveBlocks={liveBlocks}
            streaming={streaming}
            toolCatalog={toolCatalog}
            liveTokensPerSecond={liveTokensPerSecond}
            lastRunTokensPerSecond={lastRunTokensPerSecond}
            model={model}
            empty={empty}
          />
          <div ref={sentinelRef} />
          <div style={{ flex: 1 }} />
          {footer}
        </div>
      </div>

      {!atBottom && (
        <button
          type="button"
          data-testid="ai-thread-jump-to-latest"
          onClick={jumpToLatest}
          title={t('ai.thread.jumpToLatest')}
          style={{
            position: 'absolute',
            bottom: 16,
            left: '50%',
            transform: 'translateX(-50%)',
            display: 'inline-flex',
            alignItems: 'center',
            gap: 6,
            padding: '6px 14px',
            fontSize: 14,
            color: token.colorText,
            background: token.colorBgElevated,
            border: `1px solid ${token.colorBorderSecondary}`,
            borderRadius: 999,
            boxShadow: `0 4px 16px ${token.colorTextQuaternary}`,
            cursor: 'pointer',
          }}
        >
          <ArrowDown size={15} />
          <span>{t('ai.thread.jumpToLatest')}</span>
          {unread > 0 && (
            <span
              data-testid="ai-thread-unread"
              aria-label={t('ai.thread.unread', { count: unread })}
              style={{
                minWidth: 18,
                padding: '0 5px',
                fontSize: 14,
                lineHeight: '18px',
                textAlign: 'center',
                color: token.colorTextLightSolid,
                background: token.colorPrimary,
                borderRadius: 9,
              }}
            >
              {unread > 99 ? '99+' : unread}
            </span>
          )}
        </button>
      )}
    </div>
  );
};

export default ChatThread;
