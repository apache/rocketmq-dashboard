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

import type { ReactNode } from 'react';
import { theme } from 'antd';
import { Database, GraduationCap, Heartbeat, MagnifyingGlass } from '@phosphor-icons/react';
import { useLang } from '../../../i18n/LangContext';
import type { ChatMode } from '../chatDraft';

/**
 * Empty-state starter cards, the industry-standard welcome screen: a blank transcript is not a
 * dead end but a guided entry point. Each card binds a common RocketMQ operations scenario to
 * a chat mode; picking one prefills the composer (it does NOT auto-send, so the operator stays
 * in control of what actually runs).
 */

export interface Starter {
  icon: ReactNode;
  titleKey: string;
  descKey: string;
  promptKey: string;
  mode: ChatMode;
}

const STARTERS: Starter[] = [
  {
    icon: <Heartbeat size={20} weight="duotone" />,
    titleKey: 'ai.welcome.starter.health.title',
    descKey: 'ai.welcome.starter.health.desc',
    promptKey: 'ai.welcome.starter.health.prompt',
    mode: 'diagnose',
  },
  {
    icon: <Database size={20} weight="duotone" />,
    titleKey: 'ai.welcome.starter.inventory.title',
    descKey: 'ai.welcome.starter.inventory.desc',
    promptKey: 'ai.welcome.starter.inventory.prompt',
    mode: 'query',
  },
  {
    icon: <MagnifyingGlass size={20} weight="duotone" />,
    titleKey: 'ai.welcome.starter.lag.title',
    descKey: 'ai.welcome.starter.lag.desc',
    promptKey: 'ai.welcome.starter.lag.prompt',
    mode: 'diagnose',
  },
  {
    icon: <GraduationCap size={20} weight="duotone" />,
    titleKey: 'ai.welcome.starter.learn.title',
    descKey: 'ai.welcome.starter.learn.desc',
    promptKey: 'ai.welcome.starter.learn.prompt',
    mode: 'chat',
  },
];

export interface WelcomeStartersProps {
  /** Called with the starter's prompt and mode; the caller prefills the composer. */
  onPick: (prompt: string, mode: ChatMode) => void;
}

const WelcomeStarters = ({ onPick }: WelcomeStartersProps) => {
  const { t } = useLang();
  const { token } = theme.useToken();

  return (
    <div data-testid="ai-welcome-starters" style={{ padding: '48px 0 24px' }}>
      <div
        style={{
          textAlign: 'center',
          fontSize: 22,
          fontWeight: 600,
          color: token.colorText,
          marginBottom: 8,
        }}
      >
        {t('ai.welcome.title')}
      </div>
      <div
        style={{
          textAlign: 'center',
          fontSize: 14,
          color: token.colorTextTertiary,
          marginBottom: 28,
        }}
      >
        {t('ai.welcome.subtitle')}
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(2, 1fr)',
          gap: 12,
          maxWidth: 640,
          margin: '0 auto',
        }}
      >
        {STARTERS.map((starter) => (
          <button
            key={starter.titleKey}
            type="button"
            data-testid={`ai-welcome-starter-${starter.mode}-${starter.titleKey}`}
            onClick={() => onPick(t(starter.promptKey), starter.mode)}
            style={{
              display: 'flex',
              alignItems: 'flex-start',
              gap: 12,
              padding: '14px 16px',
              textAlign: 'left',
              cursor: 'pointer',
              font: 'inherit',
              color: 'inherit',
              background: token.colorBgElevated,
              border: `1px solid ${token.colorBorderSecondary}`,
              borderRadius: 12,
              transition: 'border-color 0.2s, box-shadow 0.2s',
            }}
            onMouseEnter={(event) => {
              event.currentTarget.style.borderColor = token.colorPrimary;
              event.currentTarget.style.boxShadow = `0 2px 8px ${token.colorFillSecondary}`;
            }}
            onMouseLeave={(event) => {
              event.currentTarget.style.borderColor = token.colorBorderSecondary;
              event.currentTarget.style.boxShadow = 'none';
            }}
          >
            <span style={{ color: token.colorPrimary, display: 'flex', flexShrink: 0 }}>
              {starter.icon}
            </span>
            <span style={{ minWidth: 0, flex: 1, overflow: 'hidden' }}>
              <span style={{ display: 'block', fontSize: 14, fontWeight: 600 }}>
                {t(starter.titleKey)}
              </span>
              {/* One line, ellipsised: a wrapping description made the two-column grid ragged. */}
              <span
                title={t(starter.descKey)}
                style={{
                  display: 'block',
                  fontSize: 14,
                  marginTop: 2,
                  color: token.colorTextTertiary,
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                }}
              >
                {t(starter.descKey)}
              </span>
            </span>
          </button>
        ))}
      </div>
    </div>
  );
};

export default WelcomeStarters;
