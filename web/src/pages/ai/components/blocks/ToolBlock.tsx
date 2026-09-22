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

import { memo, type CSSProperties } from 'react';
import { Progress, Tag, theme } from 'antd';
import { CaretRight, CheckCircle, CircleNotch, XCircle } from '@phosphor-icons/react';
import { useLang } from '../../../../i18n/LangContext';
import type { McpTool } from '../../../../api/ai';
import type { ToolBlock as ToolBlockData } from '../../render/blocks';
import { toolDisplayName } from '../../render/blocks';
import { formatBytes } from '../../../../utils/format';

/**
 * One tool call: what the agent invoked, with which arguments, and what came back.
 *
 * This is the half of the transcript that proves the loop closed — `rmqctl` calling back into
 * `/api/mcp` is the only thing in the system that can produce a `tcId`-paired start/result, so the
 * card is deliberately literal: the real tool name, the real arguments, the real output.
 *
 * ─── Names ──────────────────────────────────────────────────────
 * The visible label is {@link toolDisplayName} (the `mcp__rocketmq-studio__` transport prefix and the
 * `rmq.` namespace are Studio-internal noise), and the FULL name stays on a `title` attribute so the
 * identifier an operator has to grep for is never lost. The canonical dotted name arrives from the
 * backend, which reads it out of the CLI's `tool_use_meta.display_name`; the frontend must not try to
 * rebuild dots from underscores, because that mapping is ambiguous (`topic_list` could be
 * `topic.list` or a tool actually named `topic_list`).
 *
 * ─── Risk ───────────────────────────────────────────────────────
 * The risk tag is looked up in the tool catalog the page already loaded for the playground, never
 * fetched per block. A tool missing from the catalog simply renders without a tag: an agent answer
 * must not depend on a catalog request succeeding.
 *
 * ─── Output ─────────────────────────────────────────────────────
 * Rendered raw in a `<pre>`, because the event contract types output as an opaque TRANSPORT string
 * that merely usually holds serialised JSON. The backend already stripped inline base64 and capped
 * the text at 32 KiB; `outputBytes` keeps the real size and `truncated` says whether that cap bit,
 * which is what the 已截断（N KB） badge reports.
 *
 * Memoised on the block object: a run with dozens of tool calls re-renders the page at display
 * rate while the NEXT call streams, and `withToolResult` only replaces the block it fills — every
 * finished card keeps its identity and must not reconcile its (up to 32 KiB) `<pre>` again.
 */

/** Risk colour of the tool catalog: L1 reads, L2 previewable changes, L3 destructive changes. */
const RISK_TAG_COLOR: Record<string, string> = { L1: 'green', L2: 'orange', L3: 'red' };

/** Copied verbatim from the tool playground's result `<pre>` so both surfaces read the same. */
const usePreStyle = (): CSSProperties => {
  const { token } = theme.useToken();
  return {
    maxHeight: 280,
    margin: 0,
    padding: 12,
    overflow: 'auto',
    color: token.colorText,
    border: `1px solid ${token.colorBorderSecondary}`,
    borderRadius: 6,
    background: token.colorFillQuaternary,
    whiteSpace: 'pre-wrap',
    wordBreak: 'break-word',
    fontSize: 14,
  };
};

export interface ToolBlockProps {
  block: ToolBlockData;
  /** Catalog from `listTools()`, used for the risk tag. Optional: no catalog, no tag. */
  toolCatalog?: readonly McpTool[];
}

/** Duration as the operator reads it: milliseconds below a second, seconds above. */
function formatDuration(durationMs: number): string {
  if (!Number.isFinite(durationMs) || durationMs < 0) return '-';
  return durationMs < 1000 ? `${Math.round(durationMs)} ms` : `${(durationMs / 1000).toFixed(1)} s`;
}

/**
 * Find the catalog entry of a tool name as it arrives on the wire. Both the bare canonical name
 * (`rmq.topic.list`) and the MCP-qualified one (`mcp__rocketmq-studio__rmq.topic.list`) have to
 * resolve, so the comparison is done on the display name of each side.
 */
function resolveCatalogTool(
  catalog: readonly McpTool[] | undefined,
  tool: string,
): McpTool | undefined {
  if (!catalog || catalog.length === 0) return undefined;
  const exact = catalog.find((entry) => entry.name === tool);
  if (exact) return exact;
  const display = toolDisplayName(tool);
  return catalog.find((entry) => toolDisplayName(entry.name) === display);
}

const ToolBlock = ({ block, toolCatalog }: ToolBlockProps) => {
  const { t } = useLang();
  const { token } = theme.useToken();
  const preStyle = usePreStyle();

  const catalogTool = resolveCatalogTool(toolCatalog, block.tool);
  const running = block.status === 'running';
  const failed = block.status === 'done' && block.success === false;
  const displayName = toolDisplayName(block.tool);

  const inputText =
    block.input === null || block.input === undefined
      ? null
      : typeof block.input === 'string'
        ? block.input
        : JSON.stringify(block.input, null, 2);

  const summaryStyle: CSSProperties = {
    cursor: 'pointer',
    color: token.colorTextSecondary,
    fontSize: 14,
    fontWeight: 500,
    userSelect: 'none',
  };

  return (
    <details
      data-testid="ai-tool-block"
      data-status={block.status}
      className="ai-tool-card"
      open={running || undefined}
      style={{
        padding: '8px 12px',
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: 8,
        background: token.colorFillQuaternary,
      }}
    >
      {/* Collapsed the card is exactly one line — icon, name, status, duration — so a finished
          call stays out of the reader's way; expanding reveals the arguments and the result.
          While running it is forced open so the progress line is visible. */}
      <summary
        data-testid="ai-tool-summary"
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          flexWrap: 'nowrap',
          minWidth: 0,
          cursor: 'pointer',
          listStyle: 'none',
          userSelect: 'none',
        }}
      >
        {running ? (
          <CircleNotch size={16} className="ai-icon-spin" style={{ color: token.colorPrimary }} />
        ) : failed ? (
          <XCircle size={16} weight="fill" style={{ color: token.colorError }} />
        ) : (
          <CheckCircle size={16} weight="fill" style={{ color: token.colorSuccess }} />
        )}
        <span
          title={block.tool}
          data-testid="ai-tool-name"
          style={{
            fontSize: 14,
            fontWeight: 600,
            color: token.colorText,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {displayName}
        </span>
        {catalogTool?.riskLevel && (
          <Tag
            data-testid="ai-tool-risk"
            color={RISK_TAG_COLOR[catalogTool.riskLevel] ?? 'default'}
            style={{ marginInlineEnd: 0, fontSize: 14, flexShrink: 0 }}
          >
            {catalogTool.riskLevel}
          </Tag>
        )}
        <Tag
          data-testid="ai-tool-status"
          color={running ? 'processing' : failed ? 'error' : 'success'}
          style={{ marginInlineEnd: 0, fontSize: 14, flexShrink: 0 }}
        >
          {running ? t('ai.tool.running') : failed ? t('ai.tool.failed') : t('ai.tool.success')}
        </Tag>
        {block.durationMs !== null && (
          <span style={{ fontSize: 14, color: token.colorTextTertiary, flexShrink: 0 }}>
            {formatDuration(block.durationMs)}
          </span>
        )}
        {block.truncated && (
          <Tag data-testid="ai-tool-truncated" style={{ marginInlineEnd: 0, fontSize: 14 }}>
            {t('ai.tool.truncated', { size: formatBytes(block.outputBytes ?? 0) })}
          </Tag>
        )}
        <CaretRight
          size={12}
          className="ai-tool-chevron"
          style={{ color: token.colorTextTertiary, flexShrink: 0, marginLeft: 'auto' }}
        />
      </summary>

      {catalogTool?.description && (
        <div style={{ marginTop: 4, fontSize: 14, color: token.colorTextTertiary }}>
          {catalogTool.description}
        </div>
      )}

      {running && (
        <div data-testid="ai-tool-progress" style={{ marginTop: 8 }}>
          <Progress percent={100} status="active" showInfo={false} strokeWidth={2} />
        </div>
      )}

      {block.error && (
        <div
          data-testid="ai-tool-error"
          style={{ marginTop: 6, fontSize: 14, color: token.colorError, whiteSpace: 'pre-wrap' }}
        >
          {block.error}
        </div>
      )}

      <details data-testid="ai-tool-input" style={{ marginTop: 8 }}>
        <summary style={summaryStyle}>{t('ai.tool.input')}</summary>
        {inputText === null ? (
          <div style={{ marginTop: 6, fontSize: 14, color: token.colorTextTertiary }}>
            {t('ai.tool.inputUnavailable')}
          </div>
        ) : (
          <pre data-testid="tool-input" style={{ ...preStyle, marginTop: 6 }}>
            {inputText}
          </pre>
        )}
      </details>

      <details data-testid="ai-tool-output" style={{ marginTop: 8 }}>
        <summary style={summaryStyle}>{t('ai.tool.output')}</summary>
        {block.output === null ? (
          <div style={{ marginTop: 6, fontSize: 14, color: token.colorTextTertiary }}>
            {t('ai.tool.outputPending')}
          </div>
        ) : (
          <pre data-testid="tool-result" style={{ ...preStyle, marginTop: 6 }}>
            {block.output}
          </pre>
        )}
      </details>
    </details>
  );
};

export default memo(ToolBlock);
