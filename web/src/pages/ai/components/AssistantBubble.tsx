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

import { memo, useState } from 'react';
import { Card, Flex, Tag, Tooltip, Typography, theme } from 'antd';
import { Check, Copy } from '@phosphor-icons/react';
import { useLang } from '../../../i18n/LangContext';
import type { McpTool } from '../../../api/ai';
import type { Bubble, RenderBlock, TextBlock as TextBlockData } from '../render/blocks';
import { formatUtcDateTime } from '../../../utils/format';
import TextBlock from './blocks/TextBlock';
import ThinkingBlock from './blocks/ThinkingBlock';
import ToolBlock from './blocks/ToolBlock';
import NoticeBlock from './blocks/NoticeBlock';
import ErrorBlock from './blocks/ErrorBlock';
import { modelBrandLogo } from './ModelBadge';

/**
 * Everything one run rendered: text, reasoning, tool calls, notices and errors, in arrival order.
 *
 * Replaces the old `AiMessage`, which rendered a hand-rolled `Message` shape carrying `toolCall`,
 * `tableData`, `tableColumns`, `stats`, `descriptions` and `actions` — fields nothing ever populated
 * except mock data, i.e. the "frontend hallucination" this rewrite removes. What is left is a
 * `RenderBlock[]`, the same target both reducers produce, so a live bubble and the reloaded bubble
 * for the same run are the same component with the same props.
 *
 * `streaming` marks the bubble the run in flight is filling. It drives exactly two things: the
 * pending dots before the first block lands, and the auto-expand of the LAST thinking block (see
 * `ThinkingBlock`). `toolCatalog` is passed through to `ToolBlock` for its risk tag.
 *
 * Memoised because a streaming run re-renders the page at display rate, and a persisted bubble's
 * props (the folded `Bubble` object, the catalog array, the model string) keep their identity
 * across those renders: without the bailout every tick re-ran ReactMarkdown over EVERY persisted
 * answer, i.e. one full transcript re-parse per frame once a conversation collected a few runs.
 */

export interface AssistantBubbleProps {
  bubble: Bubble;
  /** True while the run that owns this bubble is still streaming. */
  streaming?: boolean;
  /** Catalog from `listTools()`, so tool blocks can show a risk level. */
  toolCatalog?: readonly McpTool[];
  /** Estimated generation speed of this run, shown next to the timestamp when measurable. */
  tokensPerSecond?: number | null;
  /** Model that serves this run; drives the brand logo avatar; unknown models keep the rocket. */
  model?: string;
}

/** Index of the last thinking block, or -1. Only that one auto-expands while streaming. */
function lastThinkingIndex(blocks: readonly RenderBlock[]): number {
  for (let index = blocks.length - 1; index >= 0; index -= 1) {
    if (blocks[index].kind === 'thinking') return index;
  }
  return -1;
}

/** The answer text an operator copies: the text blocks, in arrival order. */
function copyableText(blocks: readonly RenderBlock[]): string {
  return blocks
    .filter((block): block is TextBlockData => block.kind === 'text')
    .map((block) => block.text)
    .join('\n\n');
}

const AssistantBubble = ({
  bubble,
  streaming = false,
  toolCatalog,
  tokensPerSecond = null,
  model,
}: AssistantBubbleProps) => {
  const { t } = useLang();
  const { token } = theme.useToken();
  const blocks = bubble.blocks;
  // The avatar is the model's brand logo when the id maps to a known brand — the same prefix
  // matching the composer's selector uses — so the transcript reads as a dialogue with a specific
  // model. Unknown or missing models fall back to the Studio rocket mark.
  const brandLogo = model ? modelBrandLogo(model) : null;
  const lastThinking = streaming ? lastThinkingIndex(blocks) : -1;
  const pending = streaming && blocks.length === 0;
  const terminalStatus =
    bubble.runStatus === 'STOPPED' || bubble.runStatus === 'FAILED' ? bubble.runStatus : null;
  // While streaming the label is always there from the first token — reading 0.0 token/s until
  // the sample window is long enough — so the eye tracks one stable slot instead of a label that
  // pops in mid-answer. Persisted bubbles only show a speed their run actually reported.
  const speedLabel = streaming
    ? `${(tokensPerSecond ?? 0).toFixed(1)} token/s`
    : tokensPerSecond === null
      ? null
      : `${tokensPerSecond.toFixed(1)} token/s`;
  const [copied, setCopied] = useState(false);
  const answerText = copyableText(blocks);

  // The standard per-message action: copy the answer as markdown. Clipboard API first, the
  // hidden-textarea fallback second (non-secure contexts), silent no-op third.
  const handleCopy = () => {
    const done = () => {
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    };
    if (navigator.clipboard?.writeText) {
      navigator.clipboard
        .writeText(answerText)
        .then(done)
        .catch(() => undefined);
      return;
    }
    const textarea = document.createElement('textarea');
    textarea.value = answerText;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    try {
      if (document.execCommand('copy')) done();
    } finally {
      document.body.removeChild(textarea);
    }
  };

  return (
    <Flex gap={12} align="flex-start" style={{ marginBottom: 16 }}>
      {brandLogo ? (
        <div
          data-testid="ai-bubble-model-avatar"
          title={model}
          style={{
            width: 36,
            height: 36,
            borderRadius: '50%',
            background: '#ffffff',
            border: `1px solid ${token.colorBorderSecondary}`,
            flexShrink: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: `0 2px 8px ${token.colorTextQuaternary}`,
          }}
        >
          <img
            src={brandLogo}
            alt=""
            aria-hidden
            width={24}
            height={24}
            style={{ objectFit: 'contain', userSelect: 'none' }}
          />
        </div>
      ) : (
        <div
          data-testid="ai-bubble-default-avatar"
          style={{
            width: 36,
            height: 36,
            borderRadius: '50%',
            background: `linear-gradient(135deg, ${token.colorPrimary} 0%, ${token.colorPrimaryHover} 100%)`,
            flexShrink: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 2px 8px rgba(22, 119, 255, 0.3)',
          }}
        >
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="white"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91-.09z" />
            <path d="m12 15-3-3a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.35 22.35 0 0 1-4 2z" />
            <path d="M9 12H4s.55-3.03 2-4c1.62-1.08 5 0 5 0" />
            <path d="M12 15v5s3.03-.55 4-2c1.08-1.62 0-5 0-5" />
          </svg>
        </div>
      )}
      <Card
        size="small"
        style={{
          maxWidth: '75%',
          background: token.colorBgElevated,
          borderColor: token.colorBorderSecondary,
          boxShadow: `0 1px 4px ${token.colorTextQuaternary}`,
          borderRadius: 12,
          borderTopLeftRadius: 4,
        }}
        styles={{ body: { padding: '12px 16px' } }}
      >
        {/* Blocks sit in a column with a uniform gap so every adjacent pair (text→tool,
            thinking→text, tool→tool…) breathes the same. Blocks must NOT set their own vertical
            margin, or flex would stack it on top of this gap. */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {blocks.map((block, index) => {
            switch (block.kind) {
              case 'text':
                return <TextBlock key={`text-${index}`} block={block} />;
              case 'thinking':
                return (
                  <ThinkingBlock
                    key={`thinking-${index}`}
                    block={block}
                    streaming={streaming}
                    latest={index === lastThinking}
                  />
                );
              case 'tool':
                return (
                  <ToolBlock
                    // Position is part of the key: a retried call can reuse the same tcId, and two
                    // children sharing a key would make React drop one of the cards.
                    key={`tool-${index}-${block.tcId}`}
                    block={block}
                    toolCatalog={toolCatalog}
                  />
                );
              case 'notice':
                return <NoticeBlock key={`notice-${index}`} block={block} />;
              case 'error':
                return <ErrorBlock key={`error-${index}`} block={block} />;
              default: {
                // Exhaustiveness guard: a new RenderBlock kind that nobody renders is a compile error,
                // not a silently dropped block in the transcript.
                const unhandled: never = block;
                throw new Error(`Unhandled render block kind: ${JSON.stringify(unhandled)}`);
              }
            }
          })}
        </div>

        {pending && (
          <Flex gap={4} align="center" style={{ padding: '2px 0' }} data-testid="ai-bubble-pending">
            <span className="ai-thinking-dot" />
            <span className="ai-thinking-dot" />
            <span className="ai-thinking-dot" />
            <Typography.Text type="secondary" style={{ fontSize: 14, marginLeft: 8 }}>
              {t('ai.thinking.pending')}
            </Typography.Text>
          </Flex>
        )}

        {terminalStatus && (
          <Tag
            data-testid="ai-bubble-run-status"
            color={terminalStatus === 'STOPPED' ? 'default' : 'error'}
            style={{ marginTop: 12, marginInlineEnd: 0, fontSize: 14 }}
          >
            {t(`ai.runStatus.${terminalStatus}`)}
          </Tag>
        )}

        {(bubble.createdAt || speedLabel || (!streaming && answerText)) && (
          <Flex
            gap={8}
            align="center"
            style={{ marginTop: 8, color: token.colorTextTertiary, fontSize: 14 }}
          >
            {bubble.createdAt && <span>{formatUtcDateTime(bubble.createdAt)}</span>}
            {speedLabel && (
              <span data-testid="ai-bubble-speed" title={t('ai.speedHint')}>
                {speedLabel}
              </span>
            )}
            {!streaming && answerText && (
              <Tooltip title={copied ? t('ai.bubble.copied') : t('ai.bubble.copy')}>
                <button
                  type="button"
                  data-testid="ai-bubble-copy"
                  aria-label={t('ai.bubble.copy')}
                  onClick={handleCopy}
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    cursor: 'pointer',
                    border: 0,
                    padding: 2,
                    borderRadius: 4,
                    background: 'transparent',
                    color: copied ? token.colorSuccess : token.colorTextTertiary,
                  }}
                >
                  {copied ? <Check size={14} weight="bold" /> : <Copy size={14} />}
                </button>
              </Tooltip>
            )}
          </Flex>
        )}
      </Card>
    </Flex>
  );
};

export default memo(AssistantBubble);
