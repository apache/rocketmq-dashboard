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

import { memo, useEffect, useState, type CSSProperties } from 'react';
import { Tag, theme } from 'antd';
import { CaretRight, CheckCircle, CircleNotch, MinusCircle, XCircle } from '@phosphor-icons/react';
import { useLang } from '../../../../i18n/LangContext';
import type { McpTool } from '../../../../api/ai';
import type { RunStatus } from '../../../../api/aiEvents';
import type { ToolBlock as ToolBlockData } from '../../render/blocks';
import { toolDisplayName } from '../../render/blocks';
import { formatBytes } from '../../../../utils/format';
import ToolOutput from './ToolOutput';

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
  runStatus?: RunStatus;
  /** Catalog from `listTools()`, used for the risk tag. Optional: no catalog, no tag. */
  toolCatalog?: readonly McpTool[];
}

/** Duration as the operator reads it: milliseconds below a second, seconds above. */
function formatDuration(durationMs: number): string {
  if (!Number.isFinite(durationMs) || durationMs < 0) return '-';
  return durationMs < 1000 ? `${Math.round(durationMs)} ms` : `${(durationMs / 1000).toFixed(1)} s`;
}

/** A call this long is suspicious; this long is probably stuck. Thresholds match mq-hub's tool card. */
const WARN_ELAPSED_MS = 5 * 60 * 1000;
const STUCK_ELAPSED_MS = 10 * 60 * 1000;

/** Elapsed as a stopwatch reads: seconds, then minutes and seconds, then hours. */
function formatElapsed(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  if (total < 60) return `${total}s`;
  const minutes = Math.floor(total / 60);
  if (minutes < 60) return `${minutes}m ${total % 60}s`;
  return `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
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
  if (typeof tool !== 'string' || !tool.trim() || !catalog || catalog.length === 0)
    return undefined;
  const exact = catalog.find((entry) => entry.name === tool);
  if (exact) return exact;
  const display = toolDisplayName(tool);
  return catalog.find((entry) => toolDisplayName(entry.name) === display);
}

const ToolBlock = ({ block, toolCatalog, runStatus }: ToolBlockProps) => {
  const { t } = useLang();
  const { token } = theme.useToken();
  const preStyle = usePreStyle();

  const catalogTool = resolveCatalogTool(toolCatalog, block.tool);
  const ended = runStatus === 'COMPLETED' || runStatus === 'STOPPED' || runStatus === 'FAILED';
  const missingResult = block.status === 'running' && ended;
  const running = block.status === 'running' && !ended;
  const failed = block.status === 'done' && block.success === false;
  const displayName = toolDisplayName(block.tool) || t('ai.tool.unknownName');

  // A client-side stopwatch: neither wire vocabulary carries a tool_start timestamp, and a finished
  // card already reports the authoritative durationMs — so only the running card needs a clock, and
  // rejoining a call in flight counts from the reconnect (the elapsed hint says so out loud).
  const [expanded, setExpanded] = useState(false);
  const [showInput, setShowInput] = useState(false);
  const [showOutput, setShowOutput] = useState(false);
  const [now, setNow] = useState(0);
  // The start stamp is state, not a ref: reading a ref during render is a react-hooks violation,
  // and the stopwatch only needs it while `running` holds.
  const [startedAt, setStartedAt] = useState<number | null>(null);
  useEffect(() => {
    if (!running) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setStartedAt(null);
      return undefined;
    }
    const started = Date.now();

    setStartedAt((previous) => previous ?? started);

    setNow(started);
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [running]);
  const elapsedMs = running && startedAt !== null ? Math.max(0, now - startedAt) : 0;
  const elapsedColor =
    elapsedMs >= STUCK_ELAPSED_MS
      ? token.colorError
      : elapsedMs >= WARN_ELAPSED_MS
        ? token.colorWarning
        : token.colorTextTertiary;

  const inputText =
    block.input === null || block.input === undefined
      ? null
      : typeof block.input === 'string'
        ? block.input
        : JSON.stringify(block.input, null, 2);

  const panelToggleStyle: CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 4,
    border: 0,
    padding: 0,
    background: 'transparent',
    color: token.colorTextTertiary,
    fontSize: 14,
    cursor: 'pointer',
    flexShrink: 0,
  };
  const panelCaret = (open: boolean): CSSProperties => ({
    transform: open ? 'rotate(90deg)' : undefined,
    transition: 'transform 0.2s',
  });

  return (
    <div
      data-testid="ai-tool-block"
      data-status={missingResult ? 'missing-result' : block.status}
      data-expanded={expanded || undefined}
      className="ai-tool-card"
      style={{
        padding: '8px 12px',
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: 8,
        background: token.colorFillQuaternary,
      }}
    >
      {/* Collapsed the card is exactly ONE line: identity and verdict on the left, the two panel
          switches on the right. The panels used to be <details> sections of their own, which spent
          two extra rows per call saying nothing a finished call needs; as right-hand switches the
          same words cost zero height. The panels stay mounted (hidden) so a reader can open either
          without the card re-laying-out, and so screen readers keep the sections addressable. */}
      <div
        data-testid="ai-tool-summary"
        onClick={() => setExpanded((value) => !value)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          flexWrap: 'nowrap',
          minWidth: 0,
          cursor: 'pointer',
          userSelect: 'none',
        }}
      >
        {running ? (
          <CircleNotch size={16} className="ai-icon-spin" style={{ color: token.colorPrimary }} />
        ) : missingResult ? (
          <MinusCircle size={16} style={{ color: token.colorTextSecondary }} />
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
          color={running ? 'processing' : missingResult ? 'default' : failed ? 'error' : 'success'}
          style={{ marginInlineEnd: 0, fontSize: 14, flexShrink: 0 }}
        >
          {running
            ? t('ai.tool.running')
            : missingResult
              ? t('ai.tool.missingResult')
              : failed
                ? t('ai.tool.failed')
                : t('ai.tool.success')}
        </Tag>
        {running ? (
          <span
            data-testid="ai-tool-elapsed"
            title={t('ai.tool.elapsedHint')}
            style={{
              fontSize: 14,
              color: elapsedColor,
              flexShrink: 0,
              fontVariantNumeric: 'tabular-nums',
            }}
          >
            {formatElapsed(elapsedMs)}
          </span>
        ) : (
          block.durationMs !== null && (
            <span style={{ fontSize: 14, color: token.colorTextTertiary, flexShrink: 0 }}>
              {formatDuration(block.durationMs)}
            </span>
          )
        )}
        {running && elapsedMs >= STUCK_ELAPSED_MS && (
          <Tag
            data-testid="ai-tool-may-timeout"
            color="error"
            style={{ marginInlineEnd: 0, fontSize: 14, flexShrink: 0 }}
          >
            {t('ai.tool.mayTimeout')}
          </Tag>
        )}
        {block.truncated && (
          <Tag data-testid="ai-tool-truncated" style={{ marginInlineEnd: 0, fontSize: 14 }}>
            {t('ai.tool.truncated', { size: formatBytes(block.outputBytes ?? 0) })}
          </Tag>
        )}
        <span
          style={{
            marginLeft: 'auto',
            display: 'inline-flex',
            alignItems: 'center',
            gap: 10,
            flexShrink: 0,
          }}
        >
          <button
            type="button"
            data-testid="ai-tool-toggle-input"
            aria-expanded={showInput}
            onClick={(event) => {
              event.stopPropagation();
              setShowInput((value) => !value);
            }}
            style={panelToggleStyle}
          >
            {t('ai.tool.input')}
            <CaretRight size={10} style={panelCaret(showInput)} />
          </button>
          <button
            type="button"
            data-testid="ai-tool-toggle-output"
            aria-expanded={showOutput}
            onClick={(event) => {
              event.stopPropagation();
              setShowOutput((value) => !value);
            }}
            style={panelToggleStyle}
          >
            {t('ai.tool.output')}
            <CaretRight size={10} style={panelCaret(showOutput)} />
          </button>
          <CaretRight
            size={12}
            className="ai-tool-chevron"
            style={{ color: token.colorTextTertiary }}
          />
        </span>
      </div>

      {/* A failure is never hidden behind a toggle: the operator scanning a transcript must see it
          without interacting with the card. */}
      {block.error && (
        <div
          data-testid="ai-tool-error"
          style={{ marginTop: 6, fontSize: 14, color: token.colorError, whiteSpace: 'pre-wrap' }}
        >
          {block.error}
        </div>
      )}

      {expanded && catalogTool?.description && (
        <div style={{ marginTop: 4, fontSize: 14, color: token.colorTextTertiary }}>
          {catalogTool.description}
        </div>
      )}

      <div data-testid="ai-tool-input" hidden={!showInput} style={{ marginTop: 8 }}>
        {inputText === null ? (
          <div style={{ fontSize: 14, color: token.colorTextTertiary }}>
            {t('ai.tool.inputUnavailable')}
          </div>
        ) : (
          <pre data-testid="tool-input" style={{ ...preStyle, marginTop: 6 }}>
            {inputText}
          </pre>
        )}
      </div>

      <div data-testid="ai-tool-output" hidden={!showOutput} style={{ marginTop: 8 }}>
        {block.output === null ? (
          <div style={{ fontSize: 14, color: token.colorTextTertiary }}>
            {t(missingResult ? 'ai.tool.outputMissing' : 'ai.tool.outputPending')}
          </div>
        ) : (
          <div style={{ marginTop: 6 }}>
            <ToolOutput text={block.output} preStyle={preStyle} />
          </div>
        )}
      </div>
    </div>
  );
};

export default memo(ToolBlock);
