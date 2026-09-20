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

import { useEffect, useRef } from 'react';
import type { UseAgentRunResult } from './useAgentRun';
import type { UseConversationTimelineResult } from './useConversationTimeline';

/**
 * Re-attach to the run that is still generating, if any.
 *
 * Generation belongs to the run, not to the HTTP connection, so navigating away (or reloading)
 * leaves it streaming server-side. The timeline endpoint reports it as `activeRun`; this hook turns
 * that row into exactly one `attach(conversationId, runId, lastSeq)` per run id per visit — `lastSeq`
 * being the replay cursor that keeps the attach from rendering persisted rows twice.
 *
 * ─── One attempt per run id per visit ───────────────────────────
 * `requestedRunRef` is what stands between a dead attach (idle timeout, server restart mid-run) and
 * a hot re-attach loop: the failure surfaces through the run's error state and a reload retries.
 * The ref is cleared when the conversation changes, so navigating away mid-run and BACK re-attaches
 * to the same run id — which is the whole point of the exercise.
 *
 * The reset effect is declared before the attach effect on purpose: within one commit effects run
 * in declaration order, and arriving at a conversation must clear the previous visit's marker
 * before the attach decision is made.
 */
export function useActiveRunAttach(
  conversationId: number | null,
  timeline: Pick<UseConversationTimelineResult, 'activeRun' | 'lastSeq'>,
  run: Pick<UseAgentRunResult, 'isStreaming' | 'attach'>,
): void {
  const requestedRunRef = useRef<number | null>(null);
  const { activeRun, lastSeq } = timeline;
  const { isStreaming, attach } = run;

  useEffect(() => {
    requestedRunRef.current = null;
  }, [conversationId]);

  useEffect(() => {
    if (conversationId === null || activeRun === null || isStreaming) return;
    if (activeRun.status !== 'QUEUED' && activeRun.status !== 'RUNNING') return;
    if (requestedRunRef.current === activeRun.id) return;
    requestedRunRef.current = activeRun.id;
    void attach(conversationId, activeRun.id, lastSeq);
  }, [activeRun, attach, conversationId, isStreaming, lastSeq]);
}
