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

/* ─── Mode definitions ─── */
const modes = [
  { key: 'query', labelKey: 'home.mode.query', icon: MagnifyingGlass },
  { key: 'diagnose', labelKey: 'home.mode.diagnose', icon: Stethoscope },
  { key: 'manage', labelKey: 'home.mode.manage', icon: Database },
  { key: 'chat', labelKey: 'home.mode.chat', icon: ChatCircleDots },
];

/* ─── Model options ─── */
const modelOptions = [
  { value: 'qwen3.7-max', recommended: true },
  { value: 'qwen3.7-plus', recommended: false },
  { value: 'claude-opus-4.7', recommended: false },
  { value: 'gpt-5.4', recommended: false },
];

/* ─── Quick action capsules ── */
const quickActions = [
  {
    key: '/instance/message',
    icon: MagnifyingGlass,
    labelKey: 'home.mode.query',
    color: '#1677ff',
  },
  { key: '/cluster', icon: Stethoscope, labelKey: 'home.mode.diagnose', color: '#722ed1' },
  { key: '/instance', icon: Database, labelKey: 'home.mode.manage', color: '#52c41a' },
  { key: '/ai', icon: ChatCircleDots, labelKey: 'home.mode.chat', color: '#fa8c16' },
];

/* ═══════════════════════════════════════════════════════════════
   HomePage Component
   ═══════════════════════════════════════════════════════════════ */
const HomePage = () => {
  const [activeMode, setActiveMode] = useState('query');
  const [selectedModel, setSelectedModel] = useState('qwen3.7-max');
  const [indicatorStyle, setIndicatorStyle] = useState({ width: 83, left: 6 });
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const modeBarRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const { t, lang } = useLang();

  const handleModeSwitch = useCallback((key: string, btn: HTMLButtonElement) => {
    setActiveMode(key);
    const parent = modeBarRef.current;
    if (parent) {
      const rect = btn.getBoundingClientRect();
      const parentRect = parent.getBoundingClientRect();
      setIndicatorStyle({ width: rect.width, left: rect.left - parentRect.left - 6 });
    }
  }, []);

  useEffect(() => {
    const parent = modeBarRef.current;
    if (!parent) return;
    const activeBtn = parent.querySelector('.mode-btn.active') as HTMLButtonElement | null;
    if (activeBtn) {
      const rect = activeBtn.getBoundingClientRect();
      const parentRect = parent.getBoundingClientRect();
      setIndicatorStyle({ width: rect.width, left: rect.left - parentRect.left - 6 });
    }
  }, [lang, activeMode]);

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
          minHeight: '100%',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          background: '#fff',
        }}
      >
        {/* ── Main Content ── */}
        <div
          className="relative z-[1] mx-auto flex w-full max-w-[960px] flex-col items-center px-6"
          style={{ paddingTop: 48, flex: 1 }}
        >
          {/* ── Greeting Section ── */}
          <div
            style={{ marginBottom: 28, textAlign: 'center', animation: 'float-in 0.6s ease-out' }}
          >
            <div
              style={{
                fontSize: '2.5rem',
                lineHeight: 1.2,
                fontWeight: 700,
                letterSpacing: '-0.025em',
                color: '#1b1b1a',
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
              <span className="gradient-text" style={{ marginLeft: '0.1em' }}>
                RocketMQ Studio
              </span>
            </div>
            <div style={{ marginTop: 10, fontSize: '1rem', lineHeight: 1.6, color: '#7c7670' }}>
              {t('home.tagline')}
            </div>
          </div>

          {/* ── Quick Action Capsules ── */}
          <div
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              gap: 10,
              justifyContent: 'center',
              marginBottom: 32,
              animation: 'float-in 0.6s ease-out 0.1s both',
            }}
          >
            {quickActions.map((action) => {
              const Icon = action.icon;
              return (
                <button
                  key={action.key}
                  onClick={() => navigate(action.key)}
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 6,
                    padding: '6px 14px',
                    borderRadius: 9999,
                    fontSize: 13,
                    fontWeight: 500,
                    color: action.color,
                    background: `${action.color}0a`,
                    border: `1px solid ${action.color}20`,
                    cursor: 'pointer',
                    transition: 'all 0.2s',
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.background = `${action.color}15`;
                    e.currentTarget.style.borderColor = `${action.color}40`;
                    e.currentTarget.style.transform = 'translateY(-1px)';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.background = `${action.color}0a`;
                    e.currentTarget.style.borderColor = `${action.color}20`;
                    e.currentTarget.style.transform = 'none';
                  }}
                >
                  <Icon size={15} weight="duotone" />
                  <span>{t(action.labelKey)}</span>
                </button>
              );
            })}
          </div>

          {/* ── AI Chat Card ── */}
          <div
            className="w-full"
            style={{ animation: 'float-in 0.6s ease-out 0.15s both', maxWidth: 800 }}
          >
            <div className="w-full mx-auto relative z-[300]">
              {/* Mode Toggle Bar */}
              <div className="flex justify-center mb-5">
                <div
                  ref={modeBarRef}
                  className="relative rounded-full bg-white/70 shadow-[0_8px_24px_rgba(120,120,180,0.08)] backdrop-blur-md border border-white"
                  style={{ display: 'inline-flex', alignItems: 'center', gap: 4, padding: 6 }}
                >
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

                <div className="relative flex flex-col">
                  <textarea
                    ref={textareaRef}
                    className="chat-input"
                    placeholder="向 RocketMQ Bot 提问，全程加密、安全、可信"
                    onKeyDown={handleKeyDown}
                  />
                  <RobotOutlined
                    className="text-gray-400"
                    style={{ position: 'absolute', top: 18, left: 26, fontSize: 17 }}
                  />
                </div>

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
            paddingTop: 48,
            textAlign: 'center',
            fontSize: '0.857rem',
            lineHeight: 1.5,
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
