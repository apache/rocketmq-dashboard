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

import { useCallback, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Flex, message, theme } from 'antd';
import { useLang } from '../../i18n/LangContext';
import type { McpTool } from '../../api/ai';
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
  const [inputValue, setInputValue] = useComposerDraft();
  const [selectedMode, setSelectedMode] = useState<ChatMode>('chat');
  const [enhance, setEnhance] = useState(false);
  const [overlay, setOverlay] = useState<Overlay>(null);
  const [tools, setTools] = useState<McpTool[]>([]);
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
      if (!llm.llmReady) {
        setInputValue(text); // a rejected send gives the cleared draft back
        message.warning(t('ai.providerRequired'));
        return;
      }
      void startRun({
        createBody: { mode: selectedMode },
        request: buildMessageRequest(text, llm.selectedModel, engine, selectedMode, enhance),
      }).then((target) => {
        if (target === null) setInputValue(text);
      });
    },
    [engine, enhance, llm.llmReady, llm.selectedModel, selectedMode, setInputValue, startRun, t],
  );

  // Estimated context footprint of this conversation — persisted text plus whatever the run in
  // flight has produced so far — drawn as the composer's context-usage bar. The persisted half is
  // memoised on the timeline rows: a streaming tick re-renders this page at display rate, and
  // re-scanning EVERY persisted answer with the token estimator each frame is pure waste; only
  // the live blocks of the run in flight are recomputed per render.
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
  let contextTokens = persistedTokens;
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
        liveTokensPerSecond={run.liveTokensPerSecond}
        lastRunTokensPerSecond={run.lastRunTokensPerSecond}
        model={llm.selectedModel}
        empty={
          // Suppressed while the transcript is loading: a welcome panel that flashes for one
          // instant before the persisted bubbles arrive is the other half of the refresh jitter.
          timeline.loading ? null : (
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
