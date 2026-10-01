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

/**
 * The run the server reported, paired with the conversation it was reported for.
 *
 * The pairing is the point. `refetch` is asynchronous, so after the user switches conversation the
 * state below still holds the previous one's run for at least a commit — and a run id is the only
 * thing the attach endpoint needs, so acting on it would stream the previous conversation's frames
 * into the transcript on screen. See {@link UseConversationTimelineResult.activeRun}.
 */
interface LoadedActiveRun {
  conversationId: number;
  run: AiActiveRunRef | null;
}

export interface UseConversationTimelineResult {
  /** Persisted rows in `seq` order, exactly as the server returned them. */
  items: TimelineItem[];
  /** The same rows folded into transcript bubbles — what the thread renders. */
  bubbles: Bubble[];
  /**
   * The run still streaming in this conversation, so a reload can re-attach instead of showing a
   * dead transcript. Null while that conversation's timeline has not loaded yet, even if the
   * previous conversation's run is still the last thing the server reported.
   */
  activeRun: AiActiveRunRef | null;
  /** Highest `seq` held; pass it to `attachRunStream` so a re-attach does not replay anything twice. */
  lastSeq: number;
  loading: boolean;
  /** Server-supplied message, or `''`; the caller pairs it with an i18n fallback. */
  error: string;
  /** True when the bounded forward walk stopped before the tail; `loadMore` continues it. */
  hasMore: boolean;
  /** Reload the whole transcript from `seq > 0`; reject on failure so live blocks are retained. */
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
  const [loadedActiveRun, setLoadedActiveRun] = useState<LoadedActiveRun | null>(null);
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
      setLoadedActiveRun(null);
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
      setLoadedActiveRun({ conversationId, run });
      setNextAfter(cursor);
      setRunSpeeds(speeds);
    } catch (loadError) {
      if (id !== requestId.current) return;
      setError(describeThrownMessage(loadError));
      throw loadError;
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
      setLoadedActiveRun({ conversationId, run: result.activeRun });
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
    void refetch().catch(() => undefined);
    return () => {
      requestId.current += 1;
    };
  }, [refetch]);

  const bubbles = useMemo(() => groupIntoBubbles(items, runSpeeds), [items, runSpeeds]);
  const lastSeq = items.length ? items[items.length - 1].seq : 0;
  // Only the run of the conversation on screen: a run loaded for another one is not this
  // conversation's to attach, and it is not this conversation's to render either.
  const activeRun =
    loadedActiveRun !== null && loadedActiveRun.conversationId === conversationId
      ? loadedActiveRun.run
      : null;

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
