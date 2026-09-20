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

import { useState } from 'react';
import { theme } from 'antd';
import { Brain, Sparkle } from '@phosphor-icons/react';
import { useLang } from '../../../../i18n/LangContext';
import type { ThinkingBlock as ThinkingBlockData } from '../../render/blocks';

/**
 * Reasoning, as a disclosure that is collapsed by default.
 *
 * ─── The label depends on `source` ──────────────────────────────
 * `source: 'model'` is real chain-of-thought (`thinking_delta` from the CLI); `source: 'enhance'` is
 * the prompt-enhancement rewrite the gateway produces before handing the prompt to the agent. The
 * block this replaces labelled BOTH as `思维链：Prompt 增强改写`, i.e. it called model reasoning a
 * rewritten prompt. The two reducers refuse to merge adjacent thinking blocks with different
 * sources, and this component is the other half of that fix: it never renders one label for both.
 *
 * ─── Open while streaming, closed when done ─────────────────────
 * Watching reasoning arrive is the point of showing it at all, so the LAST thinking block of the
 * bubble that is currently streaming opens itself, and everything collapses once the run finishes —
 * a transcript nobody has to scroll through to reach the answer. Earlier thinking blocks of the same
 * run stay closed.
 *
 * The open state is derived, not effect-synced: a manual toggle is remembered only for as long as
 * `autoOpen` keeps its value, so the transition into or out of streaming always wins over a stale
 * click. That is what makes "collapse on done" unconditional without an effect fighting the user.
 */

export interface ThinkingBlockProps {
  block: ThinkingBlockData;
  /** True while the run that owns this block is still streaming. */
  streaming?: boolean;
  /** True when this is the LAST thinking block of the streaming bubble. */
  latest?: boolean;
}

interface Toggle {
  /** Value of `autoOpen` the user's click applied to. */
  autoOpen: boolean;
  open: boolean;
}

const ThinkingBlock = ({ block, streaming = false, latest = false }: ThinkingBlockProps) => {
  const { t } = useLang();
  const { token } = theme.useToken();
  const autoOpen = streaming && latest;
  const [toggle, setToggle] = useState<Toggle | null>(null);
  const open = toggle !== null && toggle.autoOpen === autoOpen ? toggle.open : autoOpen;

  const enhance = block.source === 'enhance';
  const label = enhance ? t('ai.thinking.enhance') : t('ai.thinking.model');

  return (
    <details data-testid="ai-thinking-block" data-source={block.source} open={open}>
      <summary
        data-testid="ai-thinking-summary"
        // Fully controlled disclosure: the native toggle is suppressed so the streaming
        // auto-expand and the collapse-on-done rule are the only other writers of `open`.
        onClick={(event) => {
          event.preventDefault();
          setToggle({ autoOpen, open: !open });
        }}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 6,
          cursor: 'pointer',
          color: token.colorPrimary,
          fontSize: 14,
          fontWeight: 500,
          userSelect: 'none',
        }}
      >
        {enhance ? <Sparkle size={15} /> : <Brain size={15} />}
        <span>{label}</span>
        <span style={{ color: token.colorTextTertiary, fontWeight: 400 }}>
          {t('ai.thinking.chars', { count: block.text.length })}
        </span>
      </summary>
      <div
        data-testid="ai-thinking-body"
        style={{
          marginTop: 8,
          padding: '8px 12px',
          background: token.colorFillSecondary,
          border: `1px solid ${token.colorBorderSecondary}`,
          borderRadius: 8,
          fontSize: 14,
          lineHeight: 1.7,
          color: token.colorTextSecondary,
          whiteSpace: 'pre-wrap',
        }}
      >
        {block.text}
      </div>
    </details>
  );
};

export default ThinkingBlock;
