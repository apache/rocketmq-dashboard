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

import { useState, useRef, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { ConfigProvider, theme, App } from 'antd';
import {
  Stethoscope,
  ChatCircleDots,
  Microphone,
  MagnifyingGlass,
  MegaphoneSimple,
  Database,
} from '@phosphor-icons/react';
import { getLlmConfig, type LlmConfig } from '../../api/llm';
import { useEngineStore } from '../../stores/engineStore';
import { useDataModeStore } from '../../stores/dataModeStore';
import { useLang } from '../../i18n/LangContext';
import Composer from '../ai/components/Composer';
import type { ChatMode } from '../ai/chatDraft';

/* ─── Time-aware greeting key ─── */
function getGreetingKey(): string {
  const h = new Date().getHours();
  if (h < 6) return 'home.greeting.night';
  if (h < 12) return 'home.greeting.morning';
  if (h < 14) return 'home.greeting.noon';
  if (h < 18) return 'home.greeting.afternoon';
  return 'home.greeting.evening';
}

/* ─── Mode definitions (keys only, labels resolved via t()) ─── */
const modes = [
  { key: 'chat', labelKey: 'home.mode.chat', icon: ChatCircleDots },
  { key: 'diagnose', labelKey: 'home.mode.diagnose', icon: Stethoscope },
  { key: 'manage', labelKey: 'home.mode.manage', icon: Database },
  { key: 'query', labelKey: 'home.mode.query', icon: MagnifyingGlass },
];

interface ModelOption {
  value: string;
  recommended: boolean;
}

const ROCKETMQ_DOCS_URL = 'https://rocketmq.apache.org/docs/';
const ROCKETMQ_COMMUNITY_URL = 'https://rocketmq.apache.org/';

// 首页只暴露这些模型（token-plan 网关实际可对话的模型集），qwen3.8-max 为推荐项。
const HOME_MODELS = [
  'qwen3.8-max',
  'qwen3.7-max',
  'qwen3.7-plus',
  'gpt-5',
  'gpt-5.1',
  'claude-fable-5',
  'claude-opus-5',
  'claude-sonnet-5',
  'deepseek-v4-pro',
  'deepseek-v4-flash',
  'MiniMax-M2.5',
  'glm-5.2',
];
const RECOMMENDED_MODEL = 'qwen3.8-max';

/* ═══════════════════════════════════════════════════════
   HomePage Component
   ═══════════════════════════════════════════════════════ */
const HomePage = () => {
  const [activeMode, setActiveMode] = useState('chat');
  const [modelOptions, setModelOptions] = useState<ModelOption[]>([]);
  const [selectedModel, setSelectedModel] = useState('');
  const engine = useEngineStore((s) => s.engine);
  const setEnginePreference = useEngineStore((s) => s.setEngine);
  const useMock = useDataModeStore((s) => s.useMock);
  const [promoteOn, setPromoteOn] = useState(false);
  const [inputValue, setInputValue] = useState('');
  const [llmConfig, setLlmConfig] = useState<LlmConfig | null>(null);
  const [indicatorStyle, setIndicatorStyle] = useState({ width: 83, left: 6 });
  const modeBarRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const { t, lang } = useLang();
  const { message } = App.useApp();

  // Mirrors useLlmRuntime's formula on the AI page: the provider answers and a model is chosen.
  // Mock mode counts as ready — the handoff only navigates, the AI page explains mock mode.
  const homeLlmReady =
    useMock || Boolean((llmConfig?.ready ?? llmConfig?.enabled) && selectedModel);

  useEffect(() => {
    let cancelled = false;

    const loadModels = async () => {
      const useMock = useDataModeStore.getState().useMock;
      if (useMock) {
        if (cancelled) return;
        setModelOptions(
          HOME_MODELS.map((value) => ({ value, recommended: value === RECOMMENDED_MODEL })),
        );
        setSelectedModel((current) =>
          current && HOME_MODELS.includes(current) ? current : RECOMMENDED_MODEL,
        );
        return;
      }
      const config = await getLlmConfig().catch(() => null);
      if (cancelled) return;
      setLlmConfig(config);

      const configuredModel = config?.model?.trim() ?? '';
      const values = Array.from(
        new Set([
          ...HOME_MODELS,
          ...(configuredModel && !HOME_MODELS.includes(configuredModel) ? [configuredModel] : []),
        ]),
      );

      setModelOptions(
        values.map((value) => ({
          value,
          recommended: value === RECOMMENDED_MODEL,
        })),
      );
      setSelectedModel((current) =>
        current && values.includes(current) ? current : RECOMMENDED_MODEL,
      );
    };

    void loadModels();

    return () => {
      cancelled = true;
    };
  }, []);

  /* ─── Mode switch handler ─── */
  const handleModeSwitch = useCallback((key: string, btn: HTMLButtonElement) => {
    setActiveMode(key);
    const parent = modeBarRef.current;
    if (parent) {
      const rect = btn.getBoundingClientRect();
      const parentRect = parent.getBoundingClientRect();
      setIndicatorStyle({
        width: rect.width,
        left: rect.left - parentRect.left - 6,
      });
    }
  }, []);

  /* ─── Fix indicator on mount and language/mode change ─── */
  useEffect(() => {
    const parent = modeBarRef.current;
    if (!parent) return;
    const activeBtn = parent.querySelector('.mode-btn.active') as HTMLButtonElement | null;
    if (activeBtn) {
      const rect = activeBtn.getBoundingClientRect();
      const parentRect = parent.getBoundingClientRect();
      setIndicatorStyle({
        width: rect.width,
        left: rect.left - parentRect.left - 6,
      });
    }
  }, [lang, activeMode]);

  const handleEngineChange = (value: string) => {
    setEnginePreference(value as 'claude-code' | 'qoder' | 'http');
  };

  const handlePromptSubmit = (text: string) => {
    const prompt = text.trim();
    navigate('/ai', {
      state: prompt
        ? {
            prompt,
            ...(selectedModel ? { model: selectedModel } : {}),
            engine,
            mode: activeMode,
            ...(promoteOn ? { enhance: true } : {}),
          }
        : null,
    });
  };

  const handleHistoryOpen = () => {
    navigate('/ai', { state: { historyIntent: 'open' } });
  };

  return (
    <ConfigProvider theme={{ algorithm: theme.defaultAlgorithm }}>
      <div
        className="relative w-full overflow-y-auto scrollbar-hide"
        style={{
          minHeight: 'calc(100vh - 48px)',
          display: 'flex',
          flexDirection: 'column',
          background: '#fff',
        }}
      >
        {/* ── Animated Orbs Background ── */}
        <div
          className="pointer-events-none absolute inset-0 overflow-hidden"
          style={{ animation: '8s ease-in-out infinite oneday-bg-drift' }}
        >
          {/* Top-left blue orb */}
          <div
            aria-hidden="true"
            className="absolute"
            style={{
              top: '-14%',
              left: '-7%',
              width: '42%',
              height: '42%',
              background:
                'radial-gradient(circle at 30% 30%, rgb(186, 230, 253) 0%, transparent 65%)',
              opacity: 0.45,
              filter: 'blur(80px)',
              animation: '8s ease-in-out infinite oneday-orb-drift-a',
              willChange: 'transform, opacity',
            }}
          />
          {/* Bottom-right violet orb */}
          <div
            aria-hidden="true"
            className="absolute"
            style={{
              bottom: '-18%',
              right: '-10%',
              width: '48%',
              height: '48%',
              background:
                'radial-gradient(circle at 70% 70%, rgb(221, 214, 254) 0%, rgb(233, 213, 255) 40%, transparent 68%)',
              opacity: 0.4,
              filter: 'blur(90px)',
              animation: '10s ease-in-out infinite oneday-orb-drift-b',
              willChange: 'transform, opacity',
            }}
          />
          {/* Top-right warm accent */}
          <div
            aria-hidden="true"
            className="absolute"
            style={{
              top: '-8%',
              right: '5%',
              width: '30%',
              height: '30%',
              background:
                'radial-gradient(circle at 60% 40%, rgb(254, 215, 170) 0%, transparent 60%)',
              opacity: 0.3,
              filter: 'blur(70px)',
              animation: '12s ease-in-out infinite oneday-orb-drift-c',
              willChange: 'transform, opacity',
            }}
          />
          {/* Center-bottom subtle blue-green */}
          <div
            aria-hidden="true"
            className="absolute"
            style={{
              bottom: '10%',
              left: '20%',
              width: '35%',
              height: '28%',
              background:
                'radial-gradient(ellipse at 50% 80%, rgb(153, 246, 228) 0%, transparent 60%)',
              opacity: 0.2,
              filter: 'blur(80px)',
              animation: '14s ease-in-out infinite oneday-orb-drift-a',
              willChange: 'transform, opacity',
            }}
          />
          {/* Noise texture overlay */}
          <div
            aria-hidden="true"
            className="absolute inset-0"
            style={{
              backgroundImage: `url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='160' height='160'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='2' stitchTiles='stitch'/></filter><rect width='100%' height='100%' filter='url(%23n)' opacity='1'/></svg>")`,
              opacity: 0.025,
              mixBlendMode: 'multiply',
            }}
          />
        </div>

        {/* ── Top Banner ── */}
        <div className="sticky top-0 z-10 w-full">
          <div
            className="flex justify-center items-center backdrop-blur-[8px]"
            style={{
              background: 'linear-gradient(to bottom, rgba(255,255,255,0.8) 0%, transparent 100%)',
            }}
          >
            <div className="max-w-[920px] w-full mx-auto">
              <div className="flex justify-center items-center px-4 py-2 min-h-[36px]">
                <span className="inline-flex items-center gap-2 text-sm text-amber-600 cursor-pointer hover:text-amber-700 transition-colors">
                  <MegaphoneSimple size={16} weight="fill" />
                  <span>{t('home.banner')}</span>
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* ── Main Content ── */}
        <div
          className="relative z-[1] mx-auto flex w-full max-w-[960px] flex-col items-center px-6"
          style={{ flex: 1, justifyContent: 'center' }}
        >
          {/* ── Greeting Section ── */}
          <div
            className="pt-4"
            style={{
              marginBottom: 32,
              textAlign: 'center',
              animation: 'float-in 0.6s ease-out',
            }}
          >
            <div
              style={{
                fontSize: '3rem',
                lineHeight: 1.15,
                fontWeight: 600,
                letterSpacing: '-0.025em',
                whiteSpace: 'nowrap',
                color: 'var(--bolt-elements-textPrimary)',
              }}
            >
              {t(getGreetingKey())}
              {lang === 'zh' ? '，欢迎' : ', welcome'}
              <span
                style={{
                  display: 'inline-block',
                  transformOrigin: '70% 70%',
                  animation: 'wave 1.6s ease-in-out infinite',
                  margin: '0 0.2em',
                }}
              >
                🚀
              </span>
              {lang === 'zh' ? t('home.welcomeTo') : 'to'}{' '}
              <span
                className="bg-clip-text text-transparent bg-gradient-to-r from-violet-600 via-fuchsia-500 to-orange-500"
                style={{ marginLeft: '0.15em' }}
              >
                RocketMQ Studio
              </span>
            </div>
            <div
              style={{
                marginTop: 12,
                fontSize: '1.071rem',
                lineHeight: 1.5,
                color: 'var(--bolt-elements-textSecondary)',
              }}
            >
              {t('home.tagline')}
            </div>
          </div>

          {/* ── Chat Input Area ── */}
          <div className="w-full" style={{ animation: 'float-in 0.6s ease-out 0.1s both' }}>
            <div className="w-full mx-auto relative z-[300] max-w-[60rem]">
              {/* Mode Toggle Bar */}
              <div className="flex justify-center mb-5">
                <div
                  ref={modeBarRef}
                  className="relative rounded-full bg-white/70 shadow-[0_8px_24px_rgba(120,120,180,0.08)] backdrop-blur-md border border-white"
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 4,
                    padding: 6,
                  }}
                >
                  {/* Active indicator */}
                  <div
                    className="pointer-events-none absolute top-1.5 bottom-1.5 rounded-full bg-purple-50 shadow-sm transition-all duration-300"
                    style={{
                      width: indicatorStyle.width,
                      transform: `translateX(${indicatorStyle.left}px)`,
                    }}
                  />

                  {modes.map((m) => {
                    const Icon = m.icon;
                    return (
                      <button
                        key={m.key}
                        className={`mode-btn relative ${activeMode === m.key ? 'active' : ''}`}
                        onClick={(e) => handleModeSwitch(m.key, e.currentTarget)}
                      >
                        <Icon size={16} weight={activeMode === m.key ? 'fill' : 'regular'} />
                        <span>{t(m.labelKey)}</span>
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Main Input Box — the SAME Composer component as the AI page, so the input
                  panel has one source of truth; home-specific bits are props. */}
              <Composer
                value={inputValue}
                onChange={setInputValue}
                onSend={handlePromptSubmit}
                onStop={() => undefined}
                isStreaming={false}
                stopRequested={false}
                llmReady={homeLlmReady}
                llmConfig={llmConfig}
                model={selectedModel}
                modelOptions={modelOptions.map((m) => ({
                  value: m.value,
                  label: m.value,
                  recommended: m.recommended,
                }))}
                modelsLoading={modelOptions.length === 0}
                onModelChange={setSelectedModel}
                engine={engine}
                onEngineChange={(value) => handleEngineChange(value)}
                mode={activeMode as ChatMode}
                onModeChange={(mode) => setActiveMode(mode)}
                enhance={promoteOn}
                onEnhanceChange={setPromoteOn}
                onOpenTools={() => navigate('/ai', { state: { toolsIntent: 'open' } })}
                onOpenHistory={handleHistoryOpen}
                showQuickActions={false}
                showContextBar={false}
                showModeSelect={false}
                showTemplates={false}
                panelBorderless
                placeholder={
                  lang === 'zh'
                    ? '向 RocketMQ Bot 提问，全程加密、安全、可信'
                    : 'Ask RocketMQ Bot — encrypted, secure, trusted'
                }
                toolbarExtra={
                  <button
                    type="button"
                    className="tool-btn"
                    style={{ minHeight: 30, minWidth: 32, padding: 6 }}
                    title={
                      lang === 'zh' ? '语音输入（暂未支持）' : 'Voice input (not supported yet)'
                    }
                    onClick={() => message.info(t('home.voiceNotSupported'))}
                  >
                    <Microphone size={17} />
                  </button>
                }
              />
            </div>
          </div>
        </div>

        {/* ── Footer ── */}
        <footer
          className="pointer-events-none text-gray-400"
          style={{
            position: 'relative',
            zIndex: 1,
            paddingBottom: 24,
            paddingTop: 64,
            textAlign: 'center',
            fontSize: '0.857rem',
            lineHeight: 1.5,
            background:
              'linear-gradient(to top, var(--bolt-elements-bg-depth-1) 0%, color-mix(in srgb, var(--bolt-elements-bg-depth-1) 92%, transparent) 55%, transparent 100%)',
          }}
        >
          <span className="pointer-events-auto">
            <a
              href={ROCKETMQ_DOCS_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="transition-colors hover:text-purple-500"
              style={{ textDecoration: 'none' }}
            >
              {t('home.docs')}
            </a>
            <span style={{ margin: '0 4px' }}>｜</span>
            <a
              href={ROCKETMQ_COMMUNITY_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="transition-colors hover:text-purple-500"
              style={{ textDecoration: 'none' }}
            >
              {t('home.community')}
            </a>
            <span style={{ margin: '0 4px' }}>｜</span>
            <span>{t('home.brand')}</span>
            <span style={{ margin: '0 4px' }}>｜</span>
            <span>
              当前版本 {__BUILD_TIME__} build({__BUILD_COMMIT__})
            </span>
          </span>
        </footer>
      </div>
    </ConfigProvider>
  );
};

export default HomePage;
