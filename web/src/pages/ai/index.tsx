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
import { useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Flex, message, theme } from 'antd';
import { useLang } from '../../i18n/LangContext';
import type { McpTool } from '../../api/ai';
import { suggestFollowups } from '../../api/aiConversations';
import useAuthStore from '../../stores/authStore';
import { useDataModeStore } from '../../stores/dataModeStore';
import { useEngineStore, type AgentEngine } from '../../stores/engineStore';
import { describeThrownMessage } from '../../utils/apiError';
import { applyPromptTemplate } from './promptTemplates';
import { estimateTokens } from './render/streamSpeed';
import {
  buildMessageRequest,
  draftToMessageRequest,
  parseConversationId,
  type ChatMode,
} from './chatDraft';
import { useActiveRunAttach } from './hooks/useActiveRunAttach';
import { useAgentCapabilities } from './hooks/useAgentCapabilities';
import { useAgentRun } from './hooks/useAgentRun';
import { useAiSend } from './hooks/useAiSend';
import { useComposerDraft } from './hooks/useComposerDraft';
import { useConversationTimeline } from './hooks/useConversationTimeline';
import { useDraftHandoff } from './hooks/useDraftHandoff';
import { useLlmRuntime } from './hooks/useLlmRuntime';
import ChatThread from './components/ChatThread';
import Composer from './components/Composer';
import ConversationListModal from './components/ConversationListModal';
import PromptTemplateModal from './components/PromptTemplateModal';
import RuntimeNotices from './components/RuntimeNotices';
import ToolPlaygroundModal from './components/ToolPlaygroundModal';
import WelcomeStarters from './components/WelcomeStarters';
import { aiPageStyle } from './pageStyle';

type Overlay = 'history' | 'tools' | 'templates' | null;

/**
 * The AI page shell: read the optional `/ai/c/:conversationId` param, wire the hooks, render
 * `ChatThread` + `Composer`. Transcript rendering lives in `components/`, streaming/persistence in
 * `hooks/`. ONE component serves both routes so the composer state and the draft-handoff effect
 * survive the `/ai` → `/ai/c/{id}` replace-navigation, which a per-route page would remount.
 */
const AiPage = () => {
  const { t } = useLang();
  const { token } = theme.useToken();
  const navigate = useNavigate();
  const conversationId = parseConversationId(useParams().conversationId);
  const useMock = useDataModeStore((state) => state.useMock);
  const userId = useAuthStore((state) => state.userId);
  const admin = useAuthStore((state) => state.admin);
  const engine = useEngineStore((state) => state.engine);
  const setEngine = useEngineStore((state) => state.setEngine);
  const [inputValue, setInputValue, captureDraftRestore] = useComposerDraft();
  const [selectedMode, setSelectedMode] = useState<ChatMode>('chat');
  const [enhance, setEnhance] = useState(false);
  const [overlay, setOverlay] = useState<Overlay>(null);
  const [tools, setTools] = useState<McpTool[]>([]);
  // Messages typed while a run is in flight. Component state on purpose: a queue belongs to the
  // conversation it was typed in, so switching conversations drops it rather than admitting a
  // message into a transcript the operator is no longer looking at.
  const [queue, setQueue] = useState<string[]>([]);
  const [followups, setFollowups] = useState<string[]>([]);
  const followupKeyRef = useRef<string | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const draftEngineRef = useRef<AgentEngine | null>(null);

  const llm = useLlmRuntime({
    enabled: !useMock && (!userId || admin === true),
    onEngine: (configured) => {
      if (draftEngineRef.current === null) setEngine(configured);
    },
    onError: (error) => message.error(describeThrownMessage(error) || t('ai.runtimeLoadFailed')),
  });
  const timeline = useConversationTimeline(conversationId);
  const run = useAgentRun(conversationId, { refetchTimeline: timeline.refetch });
  const rmqctlAvailable = useAgentCapabilities(!useMock);
  const startRun = useAiSend({
    conversationId,
    ready: llm.llmReady && !run.isStreaming,
    send: run.send,
    onError: (error) =>
      message.error(describeThrownMessage(error) || t('ai.conversationCreateFailed')),
  });

  useDraftHandoff(conversationId, {
    applyDraft: (draft) => {
      setInputValue(draft.prompt);
      if (draft.enhance !== undefined) setEnhance(draft.enhance);
      if (draft.mode) setSelectedMode(draft.mode);
      if (draft.engine) draftEngineRef.current = draft.engine;
      if (draft.engine) setEngine(draft.engine);
      if (draft.model) llm.selectModel(draft.model);
    },
    buildDraftRequest: (draft) => draftToMessageRequest(draft, engine),
    startRun,
    openHistory: () => setOverlay('history'),
    openTools: () => setOverlay('tools'),
  });
  useActiveRunAttach(conversationId, timeline, run);

  const handleSend = useCallback(
    (text: string) => {
      setFollowups([]);
      if (!llm.llmReady) {
        setInputValue(text); // a rejected send gives the cleared draft back
        message.warning(t('ai.providerRequired'));
        return;
      }
      const restoreDraft = captureDraftRestore(text);
      void startRun({
        createBody: { mode: selectedMode },
        request: buildMessageRequest(text, llm.selectedModel, engine, selectedMode, enhance),
      }).then((target) => {
        if (target === null) restoreDraft();
      });
    },
    [
      captureDraftRestore,
      engine,
      enhance,
      llm.llmReady,
      llm.selectedModel,
      selectedMode,
      setInputValue,
      startRun,
      t,
    ],
  );

  useEffect(() => {
    // A queue belongs to the conversation it was typed in; switching conversations drops it rather
    // than admitting a message into a transcript the operator is no longer looking at.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setQueue([]);
    setFollowups([]);
  }, [conversationId]);

  const queueMessage = useCallback((text: string) => {
    setQueue((previous) => [...previous, text]);
  }, []);

  const dropQueued = useCallback(
    (index: number) => {
      setQueue(queue.filter((_, position) => position !== index));
    },
    [queue],
  );

  const editQueued = useCallback(
    (index: number) => {
      const text = queue[index];
      if (text === undefined) return;
      setInputValue(text);
      setQueue(queue.filter((_, position) => position !== index));
      textareaRef.current?.focus();
    },
    [queue, setInputValue],
  );

  const promoteQueued = useCallback(
    (index: number) => {
      if (index <= 0 || index >= queue.length) return;
      const next = [...queue];
      const [item] = next.splice(index, 1);
      next.unshift(item);
      setQueue(next);
    },
    [queue],
  );

  // Drain one queued message per terminal transition, through the SAME path a typed message uses,
  // so admission, draft restore and the 409 guard all apply unchanged. One per transition rather
  // than a loop: each send flips isStreaming back on, and the next terminal state drains the next.
  useEffect(() => {
    if (run.isStreaming || !llm.llmReady || queue.length === 0) return;
    const [head, ...rest] = queue;
    // Draining is inherently an effect: the head leaves through the same send path a typed message
    // uses, which flips isStreaming back on and schedules the next drain.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setQueue(rest);
    handleSend(head);
  }, [handleSend, llm.llmReady, queue, run.isStreaming]);

  // Follow-up chips for the latest answer. Refetched whenever the transcript tail moves while
  // idle — which is exactly "a run just finished and the refetch landed" — and keyed on
  // (conversation, lastSeq) so a finished fetch is never repeated. A tail that ends on a user
  // message has no answer to follow up on, so the chips stay hidden.
  useEffect(() => {
    if (conversationId === null || run.isStreaming || timeline.loading) return;
    const last = timeline.bubbles[timeline.bubbles.length - 1];
    if (last === undefined || last.role !== 'assistant') {
      followupKeyRef.current = null;
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setFollowups([]);
      return;
    }
    const key = `${conversationId}:${timeline.lastSeq}`;
    if (followupKeyRef.current === key) return;
    followupKeyRef.current = key;
    let cancelled = false;
    suggestFollowups(conversationId)
      .then((items) => {
        if (!cancelled) setFollowups(items);
      })
      .catch(() => {
        if (!cancelled) setFollowups([]);
      });
    return () => {
      cancelled = true;
    };
  }, [conversationId, run.isStreaming, timeline.bubbles, timeline.lastSeq, timeline.loading]);

  // Context footprint of this conversation for the composer's meter. The provider's own usage of
  // the newest run wins — a resumed CLI session replays the whole transcript, so that number is
  // what the next call actually carries — and the transcript estimate stays the fallback for
  // engines that report no usage. Either way the live blocks of the run in flight are added on
  // top per render: they are not in any persisted row yet. The persisted half is memoised on the
  // timeline rows, because a streaming tick re-renders this page at display rate and re-scanning
  // EVERY persisted answer with the token estimator each frame is pure waste.
  const persistedTokens = useMemo(() => {
    let tokens = 0;
    for (const item of timeline.items) {
      const event = item.event;
      if (event.type === 'text' || event.type === 'user') {
        tokens += estimateTokens(event.text);
      }
    }
    return tokens;
  }, [timeline.items]);
  const contextFromUsage = timeline.contextUsage !== null;
  let contextTokens = contextFromUsage ? (timeline.contextUsage as number) : persistedTokens;
  for (const block of run.blocksRef.current) {
    if (block.kind === 'text') contextTokens += estimateTokens(block.text);
  }

  return (
    <Flex vertical className="ai-page" style={aiPageStyle(token)}>
      <ChatThread
        bubbles={timeline.bubbles}
        liveBlocks={run.blocksRef.current}
        streaming={run.isStreaming}
        pendingUserText={run.pendingUserMessage}
        toolCatalog={tools}
        followups={inputValue.trim().length === 0 ? followups : []}
        onFollowup={handleSend}
        liveTokensPerSecond={run.liveTokensPerSecond}
        lastRunTokensPerSecond={run.lastRunTokensPerSecond}
        model={llm.selectedModel}
        empty={
          // Suppressed while the transcript is loading: a welcome panel that flashes for one
          // instant before the persisted bubbles arrive is the other half of the refresh jitter.
          timeline.loading || timeline.error !== null ? null : (
            <WelcomeStarters
              onPick={(prompt, mode) => {
                setInputValue(prompt);
                setSelectedMode(mode);
                window.setTimeout(() => textareaRef.current?.focus(), 0);
              }}
            />
          )
        }
        hasMore={timeline.hasMore}
        onLoadMore={() => void timeline.loadMore()}
        resetKey={conversationId}
        footer={
          <>
            {timeline.error !== null && (
              <Alert
                type="error"
                showIcon
                message={t('ai.timeline.loadFailed')}
                description={timeline.error || undefined}
                action={
                  <Button
                    size="small"
                    loading={timeline.loading}
                    onClick={() => void timeline.refetch().catch(() => undefined)}
                  >
                    {t('common.retry')}
                  </Button>
                }
              />
            )}
            <RuntimeNotices mock={useMock} rmqctlAvailable={rmqctlAvailable} runError={run.error} />
            <Composer
              value={inputValue}
              onChange={setInputValue}
              onSend={handleSend}
              onStop={() => void run.stop()}
              isStreaming={run.isStreaming}
              stopRequested={run.stopRequested}
              llmReady={llm.llmReady}
              disabled={useMock}
              // Exact negation of canSelectModel below, so the two role checks cannot drift.
              // `admin === null` (unknown — a session persisted by an older build) counts as read-only.
              // That is not a lockout: the LLM runtime query above is already gated on `admin === true`,
              // so such an account has no llmConfig and could not send regardless. All this changes is
              // replacing a "go to settings" hint they cannot act on with an accurate explanation.
              readOnlyRole={Boolean(userId) && admin !== true}
              llmConfig={llm.config}
              model={llm.selectedModel}
              modelOptions={llm.modelOptions}
              modelsLoading={llm.modelsLoading}
              onModelChange={llm.setSelectedModel}
              canSelectModel={!userId || admin === true}
              engine={engine}
              onEngineChange={setEngine}
              mode={selectedMode}
              onModeChange={setSelectedMode}
              enhance={enhance}
              onEnhanceChange={setEnhance}
              onOpenTools={() => setOverlay('tools')}
              onOpenPromptTemplates={() => setOverlay('templates')}
              onOpenHistory={() => setOverlay('history')}
              textareaRef={textareaRef}
              contextTokens={contextTokens}
              contextSource={contextFromUsage ? 'usage' : 'estimate'}
              queue={queue}
              onQueue={queueMessage}
              onEditQueued={editQueued}
              onDropQueued={dropQueued}
              onPromoteQueued={promoteQueued}
            />
          </>
        }
      />
      <ConversationListModal
        open={overlay === 'history'}
        onClose={() => setOverlay(null)}
        activeConversationId={conversationId}
        onSelect={(id) => {
          setOverlay(null);
          navigate(`/ai/c/${id}`);
        }}
        // The transcript on screen was just deleted, so staying on `/ai/c/{id}` would leave the
        // timeline hooks polling an id that now answers 404.
        onActiveDeleted={() => {
          setOverlay(null);
          navigate('/ai');
        }}
      />
      <PromptTemplateModal
        open={overlay === 'templates'}
        onClose={() => setOverlay(null)}
        inputValue={inputValue}
        mode={selectedMode}
        enhance={enhance}
        onApply={(template, applyMode) => {
          setInputValue(applyPromptTemplate(template, inputValue, applyMode));
          setSelectedMode(template.mode);
          setEnhance(template.enhance);
          setOverlay(null);
          window.setTimeout(() => textareaRef.current?.focus(), 0);
        }}
      />
      <ToolPlaygroundModal
        open={overlay === 'tools'}
        onClose={() => setOverlay(null)}
        disabled={useMock}
        onToolsLoaded={setTools}
      />
    </Flex>
  );
};

export default AiPage;
