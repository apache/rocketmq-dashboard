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

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { getConversationTimeline, type AiActiveRunRef } from '../../../api/aiConversations';
import type { TimelineItem } from '../../../api/aiEvents';
import type { Bubble } from '../render/blocks';
import { groupIntoBubbles } from '../render/foldTimeline';
import { describeThrownMessage } from '../../../utils/apiError';

/**
 * The persisted transcript of one conversation.
 *
 * The timeline endpoint is a forward cursor (`seq > after`), while a chat view needs the whole
 * transcript, so the initial load pages forward until the cursor is exhausted. `maxPages` bounds
 * that walk: a pathological conversation stops growing the request loop and leaves `hasMore` set,
 * which the caller can turn into a "加载更早内容" affordance via {@link UseConversationTimelineResult.loadMore}.
 *
 * `refetch` is the function `useAgentRun` awaits in its finally block. Refetching BEFORE the live
 * blocks are cleared is what makes the persisted transcript replace the streaming bubble in one
 * paint instead of flashing an empty gap.
 */

export const TIMELINE_PAGE_LIMIT = 200;
/** 25 pages x 200 events: far past any real conversation, small enough to stay a bounded walk. */
export const TIMELINE_MAX_PAGES = 25;

/** Fold the envelope's per-run stats into the speed map; null entries never overwrite. */
function collectRunSpeeds(
  target: Map<number, number>,
  runs: { id: number; tokensPerSecond: number | null }[] | undefined,
): void {
  for (const run of runs ?? []) {
    if (run.tokensPerSecond !== null) target.set(run.id, run.tokensPerSecond);
  }
}

export interface UseConversationTimelineOptions {
  /** Rows per cursor page. The backend caps this at 500. */
  limit?: number;
  /** Bound on the forward paging the initial load does to reach the tail. */
  maxPages?: number;
}

export interface UseConversationTimelineResult {
  /** Persisted rows in `seq` order, exactly as the server returned them. */
  items: TimelineItem[];
  /** The same rows folded into transcript bubbles — what the thread renders. */
  bubbles: Bubble[];
  /** The run still streaming, so a reload can re-attach instead of showing a dead transcript. */
  activeRun: AiActiveRunRef | null;
  /** Highest `seq` held; pass it to `attachRunStream` so a re-attach does not replay anything twice. */
  lastSeq: number;
  loading: boolean;
  /** Server-supplied message, or `''`; the caller pairs it with an i18n fallback. */
  error: string;
  /** True when the bounded forward walk stopped before the tail; `loadMore` continues it. */
  hasMore: boolean;
  /** Reload the whole transcript from `seq > 0`. Awaitable: `useAgentRun` depends on that. */
  refetch: () => Promise<void>;
  loadMore: () => Promise<void>;
}

export function useConversationTimeline(
  conversationId: number | null,
  options: UseConversationTimelineOptions = {},
): UseConversationTimelineResult {
  const limit = options.limit ?? TIMELINE_PAGE_LIMIT;
  const maxPages = options.maxPages ?? TIMELINE_MAX_PAGES;

  const [items, setItems] = useState<TimelineItem[]>([]);
  const [activeRun, setActiveRun] = useState<AiActiveRunRef | null>(null);
  const [nextAfter, setNextAfter] = useState<number | null>(null);
  const [runSpeeds, setRunSpeeds] = useState<Map<number, number>>(new Map());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const requestId = useRef(0);
  /**
   * Request id of the in-flight `loadMore`, or null. Guards `loadMore` only — `refetch` must stay
   * callable while another load is in flight, because `useAgentRun` awaits it in a finally block.
   * Holding the id rather than a boolean keeps a superseded request from clearing a newer one's
   * guard.
   */
  const loadingMoreRef = useRef<number | null>(null);

  const refetch = useCallback(async (): Promise<void> => {
    if (conversationId === null) {
      setItems([]);
      setActiveRun(null);
      setNextAfter(null);
      setError('');
      setLoading(false);
      return;
    }

    const id = ++requestId.current;
    setLoading(true);
    setError('');
    try {
      let collected: TimelineItem[] = [];
      let after = 0;
      let cursor: number | null = null;
      let run: AiActiveRunRef | null = null;
      const speeds = new Map<number, number>();

      for (let page = 0; page < maxPages; page += 1) {
        const result = await getConversationTimeline(conversationId, { after, limit });
        if (id !== requestId.current) return;
        collected = collected.concat(result.items);
        run = result.activeRun;
        cursor = result.nextAfter;
        collectRunSpeeds(speeds, result.runs);
        if (cursor === null) break;
        after = cursor;
      }

      setItems(collected);
      setActiveRun(run);
      setNextAfter(cursor);
      setRunSpeeds(speeds);
    } catch (loadError) {
      if (id !== requestId.current) return;
      setError(describeThrownMessage(loadError));
    } finally {
      if (id === requestId.current) setLoading(false);
    }
  }, [conversationId, limit, maxPages]);

  const loadMore = useCallback(async (): Promise<void> => {
    if (conversationId === null || nextAfter === null || loadingMoreRef.current !== null) return;

    const after = nextAfter;
    const id = ++requestId.current;
    loadingMoreRef.current = id;
    setLoading(true);
    setError('');
    try {
      const result = await getConversationTimeline(conversationId, { after, limit });
      if (id !== requestId.current) return;
      setItems((previous) => previous.concat(result.items));
      setActiveRun(result.activeRun);
      setNextAfter(result.nextAfter);
      setRunSpeeds((previous) => {
        const merged = new Map(previous);
        collectRunSpeeds(merged, result.runs);
        return merged;
      });
    } catch (loadError) {
      if (id !== requestId.current) return;
      setError(describeThrownMessage(loadError));
    } finally {
      if (loadingMoreRef.current === id) loadingMoreRef.current = null;
      if (id === requestId.current) setLoading(false);
    }
  }, [conversationId, limit, nextAfter]);

  useEffect(() => {
    // Loading is asynchronous; state updates happen after the timeline API resolves.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void refetch();
    return () => {
      requestId.current += 1;
    };
  }, [refetch]);

  const bubbles = useMemo(() => groupIntoBubbles(items, runSpeeds), [items, runSpeeds]);
  const lastSeq = items.length ? items[items.length - 1].seq : 0;

  return {
    items,
    bubbles,
    activeRun,
    lastSeq,
    loading,
    error,
    hasMore: nextAfter !== null,
    refetch,
    loadMore,
  };
}
