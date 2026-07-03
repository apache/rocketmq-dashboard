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
import { RobotOutlined } from '@ant-design/icons';
import { Select, ConfigProvider, theme } from 'antd';
import {
  Stethoscope,
  ChatCircleDots,
  ClockCounterClockwise,
  SlidersHorizontal,
  Sparkle,
  Microphone,
  ArrowUp,
  MagnifyingGlass,
  CaretDown,
  MegaphoneSimple,
  Database,
} from '@phosphor-icons/react';
import { useLang } from '../../i18n/LangContext';

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
  { key: 'query', labelKey: 'home.mode.query', icon: MagnifyingGlass },
  { key: 'diagnose', labelKey: 'home.mode.diagnose', icon: Stethoscope },
  { key: 'manage', labelKey: 'home.mode.manage', icon: Database },
  { key: 'chat', labelKey: 'home.mode.chat', icon: ChatCircleDots },
];

/* ─── Model option values ─── */
const modelOptions = [
  { value: 'qwen3.7-max', recommended: true },
  { value: 'qwen3.7-plus', recommended: false },
  { value: 'claude-opus-4.7', recommended: false },
  { value: 'gpt-5.4', recommended: false },
];

/* ═══════════════════════════════════════════════════════
   HomePage Component
   ═══════════════════════════════════════════════════════ */
const HomePage = () => {
  const [activeMode, setActiveMode] = useState('query');
  const [selectedModel, setSelectedModel] = useState('qwen3.7-max');
  const [indicatorStyle, setIndicatorStyle] = useState({ width: 83, left: 6 });
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const modeBarRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const { t, lang } = useLang();

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

  /* ─── Auto-resize textarea ─── */
  useEffect(() => {
    const ta = textareaRef.current;
    if (!ta) return;
    const handler = () => {
      ta.style.height = 'auto';
      ta.style.height = `${Math.min(ta.scrollHeight, 300)}px`;
    };
    ta.addEventListener('input', handler);
    return () => ta.removeEventListener('input', handler);
  }, []);

  /* ─── Keyboard shortcut: Enter to send ─── */
  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      navigate('/ai');
    }
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
                  <span>RocketMQ Studio — 跨集群 · 跨架构 · 跨云的统一管控平台</span>
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

              {/* Main Input Box */}
              <div className="relative overflow-visible border-[1.5px] backdrop-blur-xl border-white mx-auto rounded-2xl bg-white/80 shadow-[0_20px_60px_-20px_rgba(80,90,180,0.18)]">
                {/* Model Selector & History */}
                <div className="flex items-center justify-between gap-3 px-3.5 pt-4">
                  <div className="flex flex-1 min-w-0 items-center gap-2">
                    <Select
                      size="small"
                      value={selectedModel}
                      onChange={(val) => setSelectedModel(val)}
                      options={modelOptions.map((m) => ({
                        value: m.value,
                        label: m.recommended ? (
                          <span className="inline-flex items-center gap-1.5">
                            {m.value}
                            <span className="px-1 py-0.5 rounded text-[0.625rem] leading-none bg-purple-50 text-purple-600 font-medium">
                              {lang === 'zh' ? '推荐' : 'Rec.'}
                            </span>
                          </span>
                        ) : (
                          m.value
                        ),
                      }))}
                      variant="borderless"
                      popupMatchSelectWidth={false}
                      suffixIcon={<CaretDown size={10} color="#9CA3AF" />}
                      className="model-selector"
                      style={{ fontSize: '0.893rem' }}
                    />
                  </div>
                  <div className="flex shrink-0 items-center gap-1">
                    <button className="p-1 rounded-md text-gray-400 hover:text-gray-600 hover:bg-gray-50 transition-colors">
                      <ClockCounterClockwise size={20} />
                    </button>
                  </div>
                </div>

                {/* Textarea */}
                <div className="relative flex flex-col">
                  <textarea
                    ref={textareaRef}
                    className="chat-input"
                    placeholder="向 RocketMQ Bot 提问，全程加密、安全、可信"
                    onKeyDown={handleKeyDown}
                  />
                  <RobotOutlined
                    className="text-gray-400"
                    style={{
                      position: 'absolute',
                      top: 18,
                      left: 26,
                      fontSize: 17,
                    }}
                  />
                </div>

                {/* Bottom Toolbar */}
                <div className="flex justify-between text-sm items-center px-3.5 py-3 border-t border-gray-100/80">
                  <div className="flex flex-1 gap-1 items-center min-w-0">
                    <div className="flex items-center gap-2 w-full">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 overflow-x-auto scrollbar-hide max-w-full py-2">
                          <button className="tool-btn">
                            <SlidersHorizontal size={17} />
                            <span>工具</span>
                          </button>
                          <button className="tool-btn">
                            <Sparkle size={17} />
                            <span>Prompt 增强</span>
                          </button>
                          <button
                            className="tool-btn"
                            style={{ minHeight: 30, minWidth: 32, padding: 6 }}
                          >
                            <Microphone size={17} />
                          </button>
                        </div>
                      </div>
                      <div className="shrink-0 flex items-center gap-1">
                        <button
                          className="flex items-center justify-center w-9 h-9 rounded-full bg-gradient-to-r from-purple-500 to-violet-600 text-white shadow-lg hover:shadow-xl transition-all hover:scale-105"
                          onClick={() => navigate('/ai')}
                        >
                          <ArrowUp size={19} weight="bold" />
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
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
              href="#"
              className="transition-colors hover:text-purple-500"
              style={{ textDecoration: 'none' }}
            >
              文档中心
            </a>
            <span style={{ margin: '0 4px' }}>｜</span>
            <a
              href="#"
              className="transition-colors hover:text-purple-500"
              style={{ textDecoration: 'none' }}
            >
              RocketMQ 社区
            </a>
            <span style={{ margin: '0 4px' }}>｜</span>
            <span>RocketMQ Studio 出品</span>
          </span>
        </footer>
      </div>
    </ConfigProvider>
  );
};

export default HomePage;
