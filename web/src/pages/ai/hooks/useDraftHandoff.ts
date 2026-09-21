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
import { useLocation, useNavigate } from 'react-router-dom';
import type { AiMessageRequest } from '../../../api/aiConversations';
import { getChatDraft, shouldOpenChatHistory, shouldOpenTools, type ChatDraft } from '../chatDraft';
import type { StartRunOptions } from './useAiSend';

/**
 * The home-page handoff: consume the router-state intents (`prompt` draft, `historyIntent`,
 * `toolsIntent`) exactly once and turn a draft into a server-side conversation.
 *
 * The dance, kept from the pre-persistence page:
 * 1. the FIRST effect run with a draft claims it (`consumedDraftRef`) — React StrictMode re-runs
 *    mounted effects, and a second claim would create a second conversation for one submit;
 * 2. the draft is applied to the composer state and handed to `startRun`, which POSTs
 *    `/api/ai/conversations` and replace-navigates to `/ai/c/{id}` carrying `{...draft}` as the
 *    history entry's state, so the entry that a reload would see is self-describing;
 * 3. the effect run triggered by THAT entry recognises its own state object by identity
 *    (`handoffStateRef`) and strips it with `navigate(pathname, {replace:true, state:null})` —
 *    without the strip, a reload of `/ai/c/{id}` would replay the draft and re-send the prompt.
 *
 * A draft that arrives while a conversation is already on screen (only possible by hand-editing
 * the location state — the home page always navigates to the bare `/ai`) is sent on that
 * conversation directly and the state is stripped without the identity dance.
 */

export interface UseDraftHandoffOptions {
  /** Write the draft into the composer state (text, model, engine, mode, enhance). */
  applyDraft: (draft: ChatDraft) => void;
  /** The message request the draft auto-sends once its conversation is resolved. */
  buildDraftRequest: (draft: ChatDraft) => AiMessageRequest;
  /** `useAiSend().startRun`: create-if-needed, arm the auto-send and navigate. */
  startRun: (start: StartRunOptions) => Promise<number | null>;
  openHistory: () => void;
  openTools: () => void;
}

export function useDraftHandoff(
  conversationId: number | null,
  options: UseDraftHandoffOptions,
): void {
  const location = useLocation();
  const navigate = useNavigate();
  const consumedDraftRef = useRef(false);
  const handoffStateRef = useRef<unknown>(null);
  const consumedToolsIntentRef = useRef(false);

  // Callbacks arrive as fresh closures on every render; the ref keeps the effects below dependent
  // only on the location, not on the identity of every inline arrow the page passes.
  const optionsRef = useRef(options);
  useEffect(() => {
    optionsRef.current = options;
  });

  /* 首页「工具」按钮带 toolsIntent 跳转过来时，自动打开工具弹窗（只消费一次）。 */
  useEffect(() => {
    if (!shouldOpenTools(location.state) || consumedToolsIntentRef.current) return;
    consumedToolsIntentRef.current = true;
    void Promise.resolve().then(() => {
      optionsRef.current.openTools();
      navigate(location.pathname, { replace: true, state: null });
    });
  }, [location.pathname, location.state, navigate]);

  useEffect(() => {
    const state = location.state;
    // Step 3 of the dance: the entry this hook itself installed one navigation ago.
    if (handoffStateRef.current !== null && state === handoffStateRef.current) {
      handoffStateRef.current = null;
      navigate(location.pathname, { replace: true, state: null });
      return;
    }
    const draft = getChatDraft(state);
    const wantsHistory = shouldOpenChatHistory(state);
    if ((!draft && !wantsHistory) || consumedDraftRef.current) return;
    consumedDraftRef.current = true;

    void (async () => {
      const { applyDraft, buildDraftRequest, openHistory, startRun } = optionsRef.current;
      if (wantsHistory) openHistory();
      if (!draft) {
        navigate(location.pathname, { replace: true, state: null });
        return;
      }
      applyDraft(draft);
      const start: StartRunOptions = {
        createBody: { instanceId: draft.instanceId ?? null, mode: draft.mode ?? 'chat' },
        request: buildDraftRequest(draft),
      };
      if (conversationId === null) {
        const carried = { ...draft };
        handoffStateRef.current = carried;
        const target = await startRun({ ...start, carryState: carried });
        if (target === null) {
          // Creation failed (already toasted): strip the state instead of letting a reload retry,
          // and keep the draft visible so the operator can submit it again manually.
          handoffStateRef.current = null;
          navigate(location.pathname, { replace: true, state: null });
          return;
        }
        // The request is on its way: clear the composer so the applied draft does not linger in
        // the textarea and look like it was never sent (which reads as "press Enter again").
        applyDraft({ ...draft, prompt: '' });
        return;
      }
      const target = await startRun(start);
      // `startRun` settles on admission — the server accepted the send — not on the end of the
      // answer, so both clearings below happen right away, not when the run finishes.
      if (target === null) {
        // A refused send (the server already has a run in flight, the provider rejected it) keeps
        // the draft on screen on purpose: send the state away so a reload cannot replay it, but
        // leave the text where the operator can submit it again.
        navigate(location.pathname, { replace: true, state: null });
        return;
      }
      // Admitted: clear the composer immediately (the prompt is streaming) and strip the history
      // entry's state now — on trunk this only happened after the whole answer, leaving a window
      // where a reload re-entered the handoff with a fresh `consumedDraftRef` and sent twice.
      applyDraft({ ...draft, prompt: '' });
      navigate(location.pathname, { replace: true, state: null });
    })();
  }, [conversationId, location.pathname, location.state, navigate]);
}
