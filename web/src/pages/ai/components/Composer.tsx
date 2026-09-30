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

import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  type KeyboardEvent as ReactKeyboardEvent,
  type ReactNode,
  type RefObject,
} from 'react';
import { Button, Flex, Progress, Select, Tag, Tooltip, theme } from 'antd';
import {
  BookOpen,
  CaretDown,
  ClockCounterClockwise,
  SlidersHorizontal,
  Sparkle,
} from '@phosphor-icons/react';
import { useNavigate } from 'react-router-dom';
import { useLang } from '../../../i18n/LangContext';
import type { LlmConfig } from '../../../api/llm';
import type { AgentEngine } from '../../../stores/engineStore';
import InfoBanner from '../../../components/InfoBanner';
import type { ChatMode } from '../chatDraft';
import ModelBadge from './ModelBadge';
import SendStopButton, { type SendStopState } from './SendStopButton';

/**
 * The input area: quick prompts, the runtime notices, the model/engine/mode selectors, the textarea
 * and the toolbar with the single send/stop slot.
 *
 * ─── Controlled, on purpose ────────────────────────────────────
 * The draft text lives in the PAGE (`hooks/useComposerDraft`: one `useState` plus one
 * `sessionStorage` key) because two other components need it — the prompt-template modal saves it as
 * a custom template and appends to it, and a template that is applied writes into it. A composer
 * that owned its own text would have to expose an imperative handle for that; a controlled one just
 * takes `value`/`onChange`.
 *
 * ─── One send/stop slot ────────────────────────────────────────
 * `SendStopButton` is always mounted, so the toolbar no longer shifts when a run starts. The state is
 * derived here with the hub's formula and nowhere else:
 *
 * ```ts
 * const generating = isStreaming && !stopRequested;
 * state = stopRequested ? 'stopping' : generating ? 'stop' : 'send';
 * canSend = value.trim() && !disabled && !readOnlyRole && llmReady && !generating;
 * ```
 *
 * `stopRequested` is what separates "the stop is on its way" from "the run is still going": the stop
 * is a server round-trip and the stream stays open to deliver the terminal frames, so the button sits
 * in `stopping` until they arrive rather than flipping straight back to `send`.
 *
 * ─── Keyboard ──────────────────────────────────────────────────
 * Enter sends, Shift+Enter inserts a newline, and Escape stops while generating. The `isComposing`
 * guard is load-bearing: confirming a CJK candidate with Enter must not send half a sentence.
 */

/** Prompt shortcuts, inserted into the draft rather than sent, so they stay editable. */
const DEFAULT_QUICK_ACTIONS = [
  '查看集群状态',
  'Topic 堆积 Top10',
  '诊断消费延迟',
  '创建 Topic',
  '消息轨迹查询',
  '扩缩容评估',
];

const ENGINE_OPTIONS: { value: AgentEngine; label: string }[] = [
  { value: 'claude-code', label: 'Claude Code' },
  { value: 'qoder', label: 'Qoder' },
  { value: 'http', label: 'HTTP' },
];

/** Max height the textarea grows to before it scrolls; matches `.chat-input { max-height }`. */
const TEXTAREA_MAX_HEIGHT = 180;

/** Context-window budget the progress bar is drawn against; provider models vary, 128k is the
 *  common denominator for the configured gateways. */
const CONTEXT_WINDOW_TOKENS = 128000;

export interface ModelOption {
  value: string;
  label: string;
  /** Rendered as a small chip in the dropdown, e.g. the home page's recommended model. */
  recommended?: boolean;
}

export interface ComposerProps {
  /** Draft text. */
  value: string;
  onChange: (value: string) => void;
  /**
   * Called with the trimmed draft once a send was accepted. The composer clears the draft itself, so
   * a caller that has to reject the send (no conversation yet, 409 from the server) should put the
   * text back with `onChange`.
   */
  onSend: (text: string) => void;
  onStop: () => void;

  /** A run is streaming: either generating or waiting for the terminal frames after a stop. */
  isStreaming: boolean;
  /** Stop was requested and the terminal frames have not arrived yet. */
  stopRequested: boolean;
  /** Provider configured, enabled and a model selected: a send would be accepted. */
  llmReady: boolean;
  /**
   * Nothing can be sent at all — mock mode, where the AI page does not participate and the page
   * renders the `ai.mockProviderDisabled` Alert instead of a composer that pretends to work.
   */
  disabled?: boolean;

  /**
   * The signed-in account may not start an agent run, so sending is refused up front instead of
   * answering a 403 to a message somebody already typed.
   *
   * Distinct from {@link ComposerProps.disabled}: that one says "this page cannot work at all"
   * (mock mode), this one says "it works, just not for you". The notices differ, so the two reasons
   * must not be collapsed into one flag.
   *
   * Why starting a run is admin-only while reading a transcript is not: an agent's tool calls are
   * signed with the instance credential the server resolves, not with the caller's identity, and
   * `/api/mcp/**` bypasses the session interceptor entirely. A run therefore crosses a privilege
   * boundary; reading your own transcript drives no tool and stays open to every operator. The
   * authority is `AuthInterceptor.requiresAdmin`, which `AuthInterceptorTest` pins.
   */
  readOnlyRole?: boolean;

  /** LLM config as loaded, or null when the runtime may not be inspected. Drives the notices. */
  llmConfig?: LlmConfig | null;
  model: string;
  modelOptions: ModelOption[];
  modelsLoading?: boolean;
  onModelChange: (model: string) => void;
  /** Accounts that may not inspect the LLM runtime get a disabled model selector. */
  canSelectModel?: boolean;
  engine: AgentEngine;
  onEngineChange: (engine: AgentEngine) => void;
  mode: ChatMode;
  onModeChange: (mode: ChatMode) => void;
  enhance: boolean;
  onEnhanceChange: (enhance: boolean) => void;

  onOpenTools: () => void;
  /** Only invoked when `showTemplates` is on; the home page hides the entry. */
  onOpenPromptTemplates?: () => void;
  onOpenHistory: () => void;
  /** Passed down so the page can focus the textarea after applying a prompt template. */
  textareaRef?: RefObject<HTMLTextAreaElement>;
  quickActions?: string[];
  /** Estimated tokens of the current conversation, drawn as the context-usage progress bar. */
  contextTokens?: number;
  /** Textarea placeholder; defaults to the AI page's copy. */
  placeholder?: string;
  /** Quick-prompt chip row above the selectors; the home page hides it. */
  showQuickActions?: boolean;
  /** Context-usage progress bar; the home page has no conversation yet and hides it. */
  showContextBar?: boolean;
  /** Mode selector in the header; the home page picks the mode with its own mode bar. */
  showModeSelect?: boolean;
  /** Prompt-template toolbar entry; the home page hides it. */
  showTemplates?: boolean;
  /** Extra toolbar buttons rendered after the enhance toggle (e.g. the home page's mic). */
  toolbarExtra?: ReactNode;
  /**
   * The home page's original look has no visible panel border (a white border on the white
   * page); the AI page keeps the grey one. Defaults to the bordered AI-page variant.
   */
  panelBorderless?: boolean;
}

const Composer = ({
  value,
  onChange,
  onSend,
  onStop,
  isStreaming,
  stopRequested,
  llmReady,
  disabled = false,
  readOnlyRole = false,
  llmConfig = null,
  model,
  modelOptions,
  modelsLoading = false,
  onModelChange,
  canSelectModel = true,
  engine,
  onEngineChange,
  mode,
  onModeChange,
  enhance,
  onEnhanceChange,
  onOpenTools,
  onOpenPromptTemplates,
  onOpenHistory,
  textareaRef,
  quickActions = DEFAULT_QUICK_ACTIONS,
  contextTokens = 0,
  placeholder,
  showQuickActions = true,
  showContextBar = true,
  showModeSelect = true,
  showTemplates = true,
  toolbarExtra,
  panelBorderless = false,
}: ComposerProps) => {
  const { t } = useLang();
  const { token } = theme.useToken();
  const navigate = useNavigate();
  const innerRef = useRef<HTMLTextAreaElement>(null);
  const textarea = textareaRef ?? innerRef;

  const chatModeOptions = useMemo<Array<{ value: ChatMode; label: string }>>(
    () => [
      { value: 'chat', label: t('ai.mode.chat') },
      { value: 'diagnose', label: t('ai.mode.diagnose') },
      { value: 'manage', label: t('ai.mode.manage') },
      { value: 'query', label: t('ai.mode.query') },
    ],
    [t],
  );

  // Grow with the content, capped at the `.chat-input` max height. Driven by `value` rather than by
  // an `input` listener so a template applied programmatically resizes too.
  useEffect(() => {
    const element = textarea.current;
    if (!element) return;
    element.style.height = 'auto';
    element.style.height = `${Math.min(element.scrollHeight, TEXTAREA_MAX_HEIGHT)}px`;
  }, [textarea, value]);

  const generating = isStreaming && !stopRequested;
  const sendStopState: SendStopState = stopRequested ? 'stopping' : generating ? 'stop' : 'send';
  const canSend = !disabled && !readOnlyRole && llmReady && !generating && value.trim().length > 0;

  const contextPercent = Math.min(100, Math.round((contextTokens / CONTEXT_WINDOW_TOKENS) * 100));
  const contextColor =
    contextPercent >= 85 ? '#ff4d4f' : contextPercent >= 60 ? '#faad14' : '#52c41a';

  const handleSend = useCallback(() => {
    const text = value.trim();
    if (!text || !canSend) return;
    onSend(text);
    onChange('');
  }, [canSend, onChange, onSend, value]);

  const handleKeyDown = useCallback(
    (event: ReactKeyboardEvent<HTMLTextAreaElement>) => {
      // An IME candidate being confirmed must not send the half-typed prompt.
      if (event.nativeEvent.isComposing) return;
      if (event.key === 'Escape') {
        if (generating) {
          event.preventDefault();
          onStop();
        }
        return;
      }
      if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        handleSend();
      }
    },
    [generating, handleSend, onStop],
  );

  // Not wrapped in useCallback: it reads `textarea.current`, and a ref read is not a dependency the
  // compiler can reconcile with a manual dependency list. The compiler memoizes it on its own.
  const handleQuickAction = (action: string) => {
    onChange(action);
    textarea.current?.focus();
  };

  return (
    // Rendered inside the thread's scroll surface (ChatThread footer), which already provides the
    // horizontal padding: the composer is the document's last section, not a pinned bar.
    <div className="w-full" style={{ flexShrink: 0, padding: '16px 0 8px' }}>
      {/* The composer is the document's closing section and follows the transcript's full,
          adaptive width. */}
      <div>
        {/* Conditional, actionable notices only: a read-only role or an unconfigured provider.
            The always-on settings hint is gone — pointing at settings on every visit is noise
            once the provider works. The read-only role wins because a non-admin cannot read the
            LLM runtime at all (`llmConfig` is null) and the settings tab answers 403 for them. */}
        {readOnlyRole && (
          <InfoBanner
            title={t('ai.readOnlyRole')}
            description={t('ai.readOnlyRoleDescription')}
            style={{ marginBottom: 12 }}
            data-testid="ai-read-only-role-banner"
          />
        )}
        {!readOnlyRole && llmConfig && !llmReady && (
          <InfoBanner
            title={t('ai.providerNotReady')}
            description={t('ai.providerNotReadyDescription')}
            style={{ marginBottom: 12 }}
            data-testid="ai-not-ready-banner"
          >
            <Button
              type="link"
              size="small"
              style={{ paddingLeft: 0, marginTop: 4, fontSize: 14 }}
              onClick={() => navigate('/settings?tab=ai')}
            >
              {t('ai.goToSettings')}
            </Button>
          </InfoBanner>
        )}

        <div
          className={
            panelBorderless
              ? // The home page's original panel: a white border (invisible on the white page),
                // white/80 glass background and the soft indigo shadow — no grey outline.
                'ai-chat-panel relative overflow-visible border-[1.5px] border-white backdrop-blur-xl rounded-2xl bg-white/80 shadow-[0_20px_60px_-20px_rgba(80,90,180,0.18)]'
              : 'ai-chat-panel relative overflow-visible border-[1.5px] backdrop-blur-xl rounded-2xl'
          }
        >
          <div className="flex items-center justify-between gap-3 px-3 pt-2.5">
            {/* Wraps on narrow viewports: the selectors carry min-widths and the provider status
                never shrinks, so without wrap the row overflows the panel instead of reflowing. */}
            <div className="flex flex-1 min-w-0 items-center gap-2 flex-wrap">
              <span className="shrink-0 text-gray-500" style={{ fontSize: '0.893rem' }}>
                {t('ai.composer.modelLabel')}
              </span>
              <Select
                size="small"
                aria-label={t('llm.model')}
                value={model || undefined}
                onChange={(value: string) => onModelChange(value)}
                options={modelOptions}
                loading={modelsLoading}
                disabled={disabled || !canSelectModel}
                variant="borderless"
                placeholder={
                  modelsLoading ? t('ai.composer.modelsLoading') : t('ai.composer.modelPlaceholder')
                }
                popupMatchSelectWidth={false}
                suffixIcon={<CaretDown size={10} color="#9CA3AF" />}
                className="model-selector"
                style={{ fontSize: '0.893rem' }}
                labelRender={(props) => (
                  <span className="inline-flex items-center gap-1.5">
                    <ModelBadge model={String(props.value ?? '')} />
                    <span>{String(props.label ?? props.value ?? '')}</span>
                  </span>
                )}
                optionRender={(option) => {
                  const matched = modelOptions.find(
                    (entry) => entry.value === String(option.value ?? ''),
                  );
                  return (
                    <span className="inline-flex items-center gap-1.5">
                      <ModelBadge model={String(option.value ?? '')} />
                      <span>{String(option.value ?? '')}</span>
                      {matched?.recommended && (
                        <span
                          className="px-1 py-0.5 rounded text-[0.625rem] leading-none bg-purple-50 text-purple-600 font-medium"
                          style={{ marginInlineStart: 'auto' }}
                        >
                          {t('home.recommended')}
                        </span>
                      )}
                    </span>
                  );
                }}
              />
              <span className="shrink-0 text-gray-500" style={{ fontSize: '0.893rem' }}>
                {t('ai.composer.agentLabel')}
              </span>
              <Select
                size="small"
                value={engine}
                onChange={(value) => onEngineChange(value as AgentEngine)}
                options={ENGINE_OPTIONS}
                variant="borderless"
                popupMatchSelectWidth={false}
                suffixIcon={<CaretDown size={10} color="#9CA3AF" />}
                title={t('ai.composer.engine')}
                style={{ fontSize: '0.893rem', minWidth: 110 }}
              />
              {showModeSelect && (
                <Select
                  size="small"
                  value={mode}
                  onChange={(value: ChatMode) => onModeChange(value)}
                  options={chatModeOptions}
                  variant="borderless"
                  popupMatchSelectWidth={false}
                  suffixIcon={<CaretDown size={10} color="#9CA3AF" />}
                  title={t('ai.mode.title')}
                  style={{ fontSize: '0.893rem', minWidth: 90 }}
                />
              )}
              {llmConfig && llmReady && (
                /* Ready is the normal state: quiet grey text, no emphasis. The not-ready state
                   keeps a visible tag because it is the one the operator must act on. */
                <span
                  data-testid="ai-provider-status"
                  style={{ fontSize: 14, color: token.colorTextTertiary, flexShrink: 0 }}
                >
                  {llmConfig.provider || 'openai'} {t('ai.composer.providerReady')}
                </span>
              )}
              {llmConfig && !llmReady && (
                <Tag color="default" style={{ borderRadius: 6, fontSize: 14 }}>
                  {llmConfig.provider || 'openai'} {t('ai.composer.providerNotReady')}
                </Tag>
              )}
            </div>
            <button
              type="button"
              className="p-1 rounded-md text-gray-400 hover:text-gray-600 hover:bg-gray-50 transition-colors"
              aria-label={t('ai.history.title')}
              title={t('ai.history.title')}
              onClick={onOpenHistory}
            >
              <ClockCounterClockwise size={20} />
            </button>
          </div>

          {/* Quick prompts live INSIDE the panel, a subtle horizontally-scrollable chip row between
              the selectors and the textarea: they belong to the composer, not to the document flow
              above it. The home page hides the row (showQuickActions=false). */}
          {showQuickActions && (
            <div
              className="flex items-center gap-2 overflow-x-auto scrollbar-hide px-3 pt-2"
              aria-label={t('ai.commonCommands')}
            >
              {quickActions.map((action) => (
                <Tag
                  key={action}
                  style={{
                    cursor: 'pointer',
                    borderRadius: 999,
                    padding: '1px 10px',
                    fontSize: 14,
                    userSelect: 'none',
                    flexShrink: 0,
                    marginInlineEnd: 0,
                    color: token.colorTextSecondary,
                    borderColor: token.colorBorderSecondary,
                    background: token.colorFillQuaternary,
                    transition: 'all 0.2s',
                  }}
                  onClick={() => handleQuickAction(action)}
                >
                  {action}
                </Tag>
              ))}
            </div>
          )}

          <div className="relative flex flex-col">
            <textarea
              ref={textarea}
              className="chat-input"
              value={value}
              disabled={disabled}
              onChange={(event) => onChange(event.target.value)}
              onKeyDown={handleKeyDown}
              placeholder={placeholder ?? t('ai.composer.placeholder')}
            />
            <Sparkle
              className="text-gray-400"
              style={{ position: 'absolute', top: 18, left: 26, fontSize: 17 }}
            />
          </div>

          <div className="ai-chat-toolbar flex justify-between text-sm items-center px-3 py-2 border-t">
            <div className="flex flex-1 gap-1 items-center min-w-0">
              <div className="flex items-center gap-2 w-full">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 overflow-x-auto scrollbar-hide max-w-full py-2">
                    <button type="button" className="tool-btn" onClick={onOpenTools}>
                      <SlidersHorizontal size={17} />
                      <span>{t('ai.composer.tools')}</span>
                    </button>
                    {showTemplates && (
                      <button type="button" className="tool-btn" onClick={onOpenPromptTemplates}>
                        <BookOpen size={17} />
                        <span>{t('ai.promptTemplates.button')}</span>
                      </button>
                    )}
                    <button
                      type="button"
                      className="tool-btn"
                      onClick={() => onEnhanceChange(!enhance)}
                      aria-pressed={enhance}
                      style={
                        enhance
                          ? {
                              background: '#f9f0ff',
                              color: '#722ed1',
                              boxShadow: 'inset 0 0 0 1px #d3adf7',
                            }
                          : undefined
                      }
                      title={t('ai.promptEnhanceTitle')}
                    >
                      <Sparkle size={17} weight={enhance ? 'fill' : 'regular'} />
                      <span>{t('ai.promptEnhance')}</span>
                    </button>
                    {toolbarExtra}
                  </div>
                </div>
                <div className="shrink-0 flex items-center gap-2">
                  {/* Context-usage meter, the pattern the AI products converged on (ChatGPT's
                      "Context left" pill, Cursor's composer meter): a mini bar PLUS the percent
                      number — a bare bar is unreadable at a glance — hidden while the conversation
                      is still under 1% of the window so a fresh chat carries no noise. */}
                  {showContextBar && contextPercent >= 1 && (
                    <Tooltip
                      title={t('ai.composer.contextUsage', {
                        used: contextTokens.toLocaleString(),
                        total: `${Math.round(CONTEXT_WINDOW_TOKENS / 1000)}k`,
                        percent: contextPercent,
                      })}
                    >
                      <Flex
                        align="center"
                        gap={6}
                        data-testid="ai-context-bar"
                        style={{ cursor: 'default' }}
                      >
                        <Progress
                          percent={contextPercent}
                          showInfo={false}
                          strokeWidth={8}
                          strokeColor={contextColor}
                          trailColor={token.colorFillSecondary}
                          style={{ margin: 0, width: 56 }}
                        />
                        <span
                          style={{
                            fontSize: 14,
                            color: token.colorTextSecondary,
                            fontVariantNumeric: 'tabular-nums',
                          }}
                        >
                          {contextPercent}%
                        </span>
                      </Flex>
                    </Tooltip>
                  )}
                  <SendStopButton
                    state={sendStopState}
                    canSend={canSend}
                    onSend={handleSend}
                    onStop={onStop}
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Composer;
