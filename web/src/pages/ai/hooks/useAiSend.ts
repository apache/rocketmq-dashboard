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

import { useCallback, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  createConversation,
  type AiConversationCreateRequest,
  type AiMessageRequest,
} from '../../../api/aiConversations';
import { listInstances } from '../../../services/instanceService';

/**
 * Starting a run, including the "there is no conversation yet" case.
 *
 * A send on the bare `/ai` route must first POST `/api/ai/conversations` and then install the new
 * id in the URL (`navigate('/ai/c/{id}', {replace:true})`), because the conversation is
 * deep-linkable and browser-back has to work. The stream itself may only start AFTER that
 * navigation settled: `useAgentRun` drops frames whose conversation the route has moved past, so
 * firing the send before the param arrives would race its own guard.
 *
 * Hence the two-phase shape: `startRun` creates + arms `pendingSendRef` + navigates, and the
 * auto-send effect fires the armed request exactly once when the resolved id shows up as the route
 * param. The id comparison IS the double-fire guard — the replace-navigation re-runs the effect
 * while the pending entry is already cleared, and a route that moved on to a DIFFERENT
 * conversation disarms the stale entry instead of firing it somewhere it was never meant to land.
 */

export interface StartRunOptions {
  /** Body of the `POST /api/ai/conversations` used when no conversation is on screen yet. */
  createBody: AiConversationCreateRequest;
  /** The message to stream once the conversation id is resolved. */
  request: AiMessageRequest;
  /**
   * Router state to install on the new `/ai/c/{id}` entry. The home-page handoff carries the draft
   * here so the history entry is self-describing; the draft dance strips it again right after.
   */
  carryState?: unknown;
}

export interface UseAiSendOptions {
  /** The `/ai/c/:conversationId` route param, or null on the bare `/ai` route. */
  conversationId: number | null;
  /** Provider ready AND no stream in flight: an armed send may fire. */
  ready: boolean;
  /** `useAgentRun().send`; resolves to whether the server admitted the run. */
  send: (conversationId: number, request: AiMessageRequest) => Promise<boolean>;
  /** Conversation creation failure; the caller pairs it with an i18n fallback toast. */
  onError: (error: unknown) => void;
}

/**
 * @returns `startRun`, resolving to the conversation id the request was (or will be) sent on, or
 *   null when nothing was sent — creating the conversation failed (already reported through
 *   `onError`) or the send on an existing conversation was refused. The caller restores whatever
 *   input the send consumed on null.
 */
export function useAiSend(
  options: UseAiSendOptions,
): (start: StartRunOptions) => Promise<number | null> {
  const { conversationId, ready, send } = options;
  const navigate = useNavigate();
  const pendingSendRef = useRef<{ conversationId: number; request: AiMessageRequest } | null>(null);

  // Callbacks arrive as fresh closures on every render; the ref keeps `startRun` referentially
  // stable so callers can put it in dependency arrays without re-firing anything.
  const optionsRef = useRef(options);
  useEffect(() => {
    optionsRef.current = options;
  });

  const conversationIdRef = useRef(conversationId);
  useEffect(() => {
    conversationIdRef.current = conversationId;
  }, [conversationId]);

  useEffect(() => {
    const pending = pendingSendRef.current;
    if (!pending) return;
    if (pending.conversationId !== conversationId) {
      // Still on the bare route: the replace-navigation installing the created id commits in a
      // later render, and an intermediate dep flip (e.g. the runtime turning ready) must NOT
      // disarm the send that navigation is on its way to fire. Only a route that has settled on a
      // different conversation makes the armed entry stale.
      if (conversationId !== null) pendingSendRef.current = null;
      return;
    }
    if (!ready) return;
    pendingSendRef.current = null;
    void send(pending.conversationId, pending.request);
  }, [conversationId, ready, send]);

  return useCallback(
    async ({ createBody, request, carryState }: StartRunOptions): Promise<number | null> => {
      const current = conversationIdRef.current;
      if (current !== null) {
        // Null, not `current`, when the server refused the send: the caller pairs that with the
        // input the composer already cleared and puts it back (see ComposerProps.onSend).
        const admitted = await optionsRef.current.send(current, request);
        return admitted ? current : null;
      }
      let createdId: number;
      try {
        const created = await createConversation(await withDefaultInstance(createBody));
        createdId = created.id;
      } catch (error) {
        optionsRef.current.onError(error);
        return null;
      }
      pendingSendRef.current = { conversationId: createdId, request };
      navigate(`/ai/c/${createdId}`, { replace: true, state: carryState ?? null });
      return createdId;
    },
    [navigate],
  );
}

/**
 * A conversation without a bound instance runs with every RocketMQ tool disabled, so even
 * “list my instances” cannot be answered. When the caller did not pick one, bind the first
 * instance of the (backend-sorted) list as the default, preferring one that carries an admin
 * credential ref — binding a credential-less instance makes every turn fail admission with a
 * 422. Leave the body alone when there is none or the lookup fails: the conversation still
 * degrades to plain chat.
 */
async function withDefaultInstance(
  body: AiConversationCreateRequest,
): Promise<AiConversationCreateRequest> {
  if (body.instanceId) return body;
  try {
    const instances = await listInstances();
    const fallback =
      instances.find((instance) => instance.adminCredentialRef)?.name ?? instances[0]?.name;
    return fallback ? { ...body, instanceId: fallback } : body;
  } catch {
    return body;
  }
}
