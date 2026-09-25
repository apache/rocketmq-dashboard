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

import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LangProvider } from '../../../../i18n/LangContext';
import type { McpTool } from '../../../../api/ai';
import type { Bubble, RenderBlock, ToolBlock } from '../../render/blocks';
import {
  appendText,
  appendThinking,
  errorBlock,
  noticeBlock,
  runningToolBlock,
  toolDisplayName,
  withToolResult,
} from '../../render/blocks';
import AssistantBubble from '../AssistantBubble';

/**
 * Replaces `AiMessage.test.tsx`, whose subject was the old `Message` shape: a hand-rolled union of
 * `toolCall`, `tableData`, `stats`, `descriptions` and `actions` that nothing but mock data ever
 * filled in. The markdown cases that file covered are carried over below, because repairing the
 * Markdown models actually emit is still load-bearing; the rest is new, because the rest did not
 * exist — a real chain of thought, a real tool call, a real stop.
 *
 * The block fixtures are built through the SAME primitives both reducers use (`appendText`,
 * `appendThinking`, `runningToolBlock`, `withToolResult`), so this suite also pins the coalescing
 * rules a renderer depends on: adjacent text merges, thinking merges only within one `source`.
 */

const CATALOG: McpTool[] = [
  {
    name: 'rmq.topic.list',
    description: '列出实例下的 Topic。',
    parameters: { type: 'object', properties: {} },
    riskLevel: 'L1',
    permission: 'topic:read',
  },
  {
    name: 'rmq.topic.delete',
    description: '删除 Topic。',
    parameters: { type: 'object', properties: {} },
    riskLevel: 'L3',
  },
];

const TOOL_RESULT = {
  tcId: 'toolu_1',
  tool: 'mcp__rocketmq-studio__rmq.topic.list',
  output: '{"topics":["TopicA","TopicB"]}',
  outputBytes: 28,
  truncated: false,
  durationMs: 1240,
  success: true,
};

function bubble(blocks: RenderBlock[], extra: Partial<Bubble> = {}): Bubble {
  return { role: 'assistant', blocks, ...extra };
}

function renderBubble(props: {
  blocks: RenderBlock[];
  streaming?: boolean;
  toolCatalog?: readonly McpTool[];
  bubbleExtra?: Partial<Bubble>;
}) {
  return render(
    <LangProvider>
      <AssistantBubble
        bubble={bubble(props.blocks, props.bubbleExtra)}
        streaming={props.streaming}
        toolCatalog={props.toolCatalog}
      />
    </LangProvider>,
  );
}

describe('AssistantBubble', () => {
  beforeEach(() => {
    // Labels are asserted in Chinese, which is the default locale once localStorage is cleared.
    localStorage.clear();
  });

  it('rendersEveryBlockKindTest', () => {
    const blocks: RenderBlock[] = [
      ...appendThinking([], '先确认实例能力，再列 Topic。', 'model'),
      ...appendText([], '该实例有 2 个 Topic。'),
      runningToolBlock('toolu_1', 'rmq.topic.list', { instanceId: 'local' }),
      noticeBlock({ level: 'warn', message: 'rmqctl 不可用，本次会话已禁用 RocketMQ 工具' }),
      errorBlock({ code: 'llm.provider.error_max_turns', message: '达到最大轮次' }),
    ];

    renderBubble({ blocks, toolCatalog: CATALOG });

    expect(screen.getByTestId('ai-thinking-block')).toBeInTheDocument();
    expect(screen.getByTestId('ai-text-block')).toBeInTheDocument();
    expect(screen.getByTestId('ai-tool-block')).toBeInTheDocument();
    expect(screen.getByTestId('ai-notice-block')).toBeInTheDocument();
    expect(screen.getByTestId('ai-error-block')).toBeInTheDocument();
  });

  it('rendersMarkdownAndRepairsTheMarkersModelsEmitTest', () => {
    // Carried over from AiMessage.test.tsx: `##结论` without a space is not a heading in
    // CommonMark, and a fence whose info string runs into the code swallows the block.
    const text = [
      '# 扩缩容评估',
      '',
      '- **QPS/TPS**: 每秒查询或事务数',
      '',
      '| 指标 | 值 |',
      '| --- | --- |',
      '| CPU | 70% |',
      '',
      '```bash',
      'mqadmin clusterList',
      '```',
      '',
      '##结论',
      '-第一项',
      '```bashmqadmin topicList',
    ].join('\n');

    renderBubble({ blocks: appendText([], text) });

    expect(screen.getByRole('heading', { name: '扩缩容评估', level: 1 })).toBeInTheDocument();
    expect(screen.getByText('QPS/TPS')).toBeInTheDocument();
    expect(screen.getByRole('table')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '结论', level: 2 })).toBeInTheDocument();
    const listItems = screen.getAllByRole('listitem');
    expect(listItems[listItems.length - 1]).toHaveTextContent('第一项');
    expect(screen.getAllByText('mqadmin clusterList')[0].closest('pre')).toBeInTheDocument();
    expect(screen.getByText('mqadmin topicList').closest('pre')).toBeInTheDocument();
  });

  it('rendersWellFormedMarkdownWithoutRewritingItTest', () => {
    // The repair must be a no-op on Markdown that is already well formed. Before the fix the
    // heading rule backtracked to a shorter marker run, so `## 概述` rendered as an h1 whose text
    // started with a literal '#', `**加粗**` and *斜体* were read as list bullets, the `---`
    // became a list item instead of a rule, and the diff markers inside the fence were rewritten.
    const text = [
      '## 概述',
      '',
      '**加粗** 与 *斜体*',
      '',
      '---',
      '',
      '```diff',
      '-old line',
      '+new line',
      '```',
    ].join('\n');

    renderBubble({ blocks: appendText([], text) });

    expect(screen.getByRole('heading', { name: '概述', level: 2 })).toBeInTheDocument();
    expect(screen.getByText('加粗').tagName).toBe('STRONG');
    expect(screen.getByText('斜体').tagName).toBe('EM');
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
    expect(screen.getAllByText(/-old line/)[0]).toHaveTextContent('+new line');
    expect(screen.getAllByText(/-old line/)[0].closest('pre')).toBeInTheDocument();
  });

  it('collapsesThinkingByDefaultAndCountsItsCharactersTest', () => {
    const text = '先确认实例能力。';
    renderBubble({ blocks: appendThinking([], text, 'model') });

    const details = screen.getByTestId('ai-thinking-block');
    expect(details).not.toHaveAttribute('open');
    expect(screen.getByTestId('ai-thinking-summary')).toHaveTextContent(`（${text.length} 字）`);
  });

  it('labelsModelReasoningAndThePromptRewriteDifferentlyTest', () => {
    // The block this replaces labelled BOTH as `思维链：Prompt 增强改写`, i.e. it called the
    // model's reasoning a rewritten prompt. `source` is what tells them apart now.
    let blocks = appendThinking([], '模型推理', 'model');
    blocks = appendThinking(blocks, '改写后的 Prompt', 'enhance');

    renderBubble({ blocks });

    const rendered = screen.getAllByTestId('ai-thinking-block');
    expect(rendered).toHaveLength(2);
    expect(rendered[0]).toHaveAttribute('data-source', 'model');
    expect(rendered[1]).toHaveAttribute('data-source', 'enhance');
    expect(within(rendered[0]).getByText('思考过程')).toBeInTheDocument();
    expect(within(rendered[1]).getByText('Prompt 增强改写')).toBeInTheDocument();
    expect(screen.queryByText(/思维链/)).not.toBeInTheDocument();
  });

  it('doesNotMergeAdjacentThinkingFromDifferentSourcesTest', () => {
    // Guard on the primitive the label distinction depends on: merging these two would reproduce the
    // mislabelling bug with a single block whose source is whichever came first.
    let blocks = appendThinking([], 'A', 'model');
    blocks = appendThinking(blocks, 'B', 'enhance');
    blocks = appendThinking(blocks, 'C', 'enhance');

    expect(blocks).toHaveLength(2);
    expect(blocks[0]).toMatchObject({ kind: 'thinking', text: 'A', source: 'model' });
    expect(blocks[1]).toMatchObject({ kind: 'thinking', text: 'BC', source: 'enhance' });
  });

  it('opensTheBlockAndCollapsesItAgainTest', async () => {
    const user = userEvent.setup();
    renderBubble({ blocks: appendThinking([], '推理', 'model') });

    const details = screen.getByTestId('ai-thinking-block');
    expect(details).not.toHaveAttribute('open');

    await user.click(screen.getByTestId('ai-thinking-summary'));
    expect(details).toHaveAttribute('open');
    expect(screen.getByTestId('ai-thinking-body')).toHaveTextContent('推理');

    await user.click(screen.getByTestId('ai-thinking-summary'));
    expect(details).not.toHaveAttribute('open');
  });

  it('autoExpandsOnlyTheLastThinkingBlockWhileStreamingTest', () => {
    let blocks = appendThinking([], '第一段推理', 'model');
    blocks = appendText(blocks, '中间回答');
    blocks = appendThinking(blocks, '第二段推理', 'model');

    const { rerender } = render(
      <LangProvider>
        <AssistantBubble bubble={bubble(blocks)} streaming />
      </LangProvider>,
    );

    const rendered = screen.getAllByTestId('ai-thinking-block');
    expect(rendered[0]).not.toHaveAttribute('open');
    expect(rendered[1]).toHaveAttribute('open');

    // Done: the transcript collapses so the answer is what the reader scrolls to.
    rerender(
      <LangProvider>
        <AssistantBubble bubble={bubble(blocks)} streaming={false} />
      </LangProvider>,
    );
    const afterDone = screen.getAllByTestId('ai-thinking-block');
    expect(afterDone[0]).not.toHaveAttribute('open');
    expect(afterDone[1]).not.toHaveAttribute('open');
  });

  it('keepsAManualCollapseWhileStreamingTest', () => {
    let blocks = appendThinking([], '推理', 'model');
    const { rerender } = render(
      <LangProvider>
        <AssistantBubble bubble={bubble(blocks)} streaming />
      </LangProvider>,
    );
    const details = screen.getByTestId('ai-thinking-block');
    expect(details).toHaveAttribute('open');

    fireEvent.click(screen.getByTestId('ai-thinking-summary'));
    expect(details).not.toHaveAttribute('open');

    // More of the same block arrives: still collapsed, because the reader just said so.
    blocks = appendThinking(blocks, '继续推理', 'model');
    rerender(
      <LangProvider>
        <AssistantBubble bubble={bubble(blocks)} streaming />
      </LangProvider>,
    );
    expect(screen.getByTestId('ai-thinking-block')).not.toHaveAttribute('open');
  });

  it('showsAToolCallRunningThenDoneTest', () => {
    const running = runningToolBlock(TOOL_RESULT.tcId, TOOL_RESULT.tool, { instanceId: 'local' });
    const { rerender } = renderBubble({ blocks: [running], toolCatalog: CATALOG });

    const card = screen.getByTestId('ai-tool-block');
    expect(card).toHaveAttribute('data-status', 'running');
    expect(screen.getByTestId('ai-tool-status')).toHaveTextContent('执行中');
    // The display name drops the transport prefix and the `rmq.` namespace; the full name stays
    // discoverable on a title attribute.
    expect(screen.getByTestId('ai-tool-name')).toHaveTextContent('topic.list');
    expect(screen.getByTestId('ai-tool-name')).toHaveAttribute('title', TOOL_RESULT.tool);
    expect(toolDisplayName(TOOL_RESULT.tool)).toBe('topic.list');
    // Resolved from the catalog the page already loaded, not fetched per block.
    expect(screen.getByTestId('ai-tool-risk')).toHaveTextContent('L1');
    expect(screen.queryByTestId('ai-tool-truncated')).not.toBeInTheDocument();

    const done = withToolResult([running], TOOL_RESULT);
    rerender(
      <LangProvider>
        <AssistantBubble bubble={bubble(done)} toolCatalog={CATALOG} />
      </LangProvider>,
    );

    const finished = screen.getByTestId('ai-tool-block');
    expect(finished).toHaveAttribute('data-status', 'done');
    expect(screen.getByTestId('ai-tool-status')).toHaveTextContent('成功');
    expect(finished).toHaveTextContent('1.2 s');
    expect(screen.getByTestId('tool-result')).toHaveTextContent('"topics":["TopicA","TopicB"]');
  });

  it('rendersAColourlessRiskTagForAToolMissingFromTheCatalogTest', () => {
    renderBubble({
      blocks: [runningToolBlock('toolu_9', 'rmq.topic.delete', {})],
      toolCatalog: [CATALOG[0]],
    });

    expect(screen.getByTestId('ai-tool-name')).toHaveTextContent('topic.delete');
    // No catalog entry means no risk claim: an agent answer must not depend on the catalog loading.
    expect(screen.queryByTestId('ai-tool-risk')).not.toBeInTheDocument();
  });

  it('showsTheTruncatedBadgeWithTheRealOutputSizeTest', () => {
    const done = withToolResult([], {
      ...TOOL_RESULT,
      output: 'x'.repeat(64),
      outputBytes: 49_152,
      truncated: true,
    });

    renderBubble({ blocks: done, toolCatalog: CATALOG });

    expect(screen.getByTestId('ai-tool-truncated')).toHaveTextContent('已截断（48.0 KB）');
  });

  it('showsAFailedToolCallWithItsErrorTest', () => {
    const done = withToolResult([], {
      ...TOOL_RESULT,
      output: '',
      success: false,
      error: 'instance not found: local',
    });

    renderBubble({ blocks: done, toolCatalog: CATALOG });

    expect(screen.getByTestId('ai-tool-status')).toHaveTextContent('失败');
    expect(screen.getByTestId('ai-tool-error')).toHaveTextContent('instance not found: local');
  });

  it('explainsAToolResultWhoseCallFellOutsideThePagingWindowTest', () => {
    // `withToolResult` appends a completed block when no running block owns the tcId; its input is
    // unknown and must be reported as such rather than rendered as `null`.
    const done = withToolResult([], TOOL_RESULT) as ToolBlock[];

    renderBubble({ blocks: done, toolCatalog: CATALOG });

    expect(screen.getByTestId('ai-tool-input')).toHaveTextContent('输入参数不在已加载的事件范围内');
    expect(screen.getByTestId('tool-result')).toHaveTextContent('"TopicA"');
  });

  it('rendersANoticeAsANeutralBannerNotAColouredAlertTest', () => {
    const { container } = renderBubble({
      blocks: [noticeBlock({ level: 'warn', message: 'rmqctl 不可用' })],
    });

    // Project rule: a persistent notice uses the neutral InfoBanner treatment; coloured antd Alerts
    // are reserved for semantic states.
    expect(screen.getByTestId('ai-notice-block')).toHaveTextContent('rmqctl 不可用');
    expect(container.querySelector('.ant-alert')).toBeNull();
    expect(screen.getByTestId('ai-notice-block')).toHaveTextContent('注意');
  });

  it('rendersAnErrorBlockAsASemanticAlertWithItsCodeAndHintTest', () => {
    const { container } = renderBubble({
      blocks: [
        errorBlock({
          code: 'llm.stream.unexpected_content_type',
          message: 'AI stream rejected',
          hint: 'A reverse proxy is buffering the response.',
        }),
      ],
    });

    expect(screen.getByTestId('ai-error-block')).toHaveTextContent('AI stream rejected');
    expect(screen.getByTestId('ai-error-block')).toHaveTextContent(
      'llm.stream.unexpected_content_type',
    );
    expect(screen.getByTestId('ai-error-block')).toHaveTextContent(
      'A reverse proxy is buffering the response.',
    );
    // Here a coloured Alert IS correct, because a failure is a semantic state.
    expect(container.querySelector('.ant-alert-error')).not.toBeNull();
  });

  it('showsThePendingIndicatorBeforeTheFirstBlockLandsTest', () => {
    renderBubble({ blocks: [], streaming: true });
    expect(screen.getByTestId('ai-bubble-pending')).toHaveTextContent('正在思考…');
  });

  it('marksARunThatWasStoppedTest', () => {
    // A stop leaves a terminal `run_status` in the timeline, so a reloaded conversation still says
    // the answer was cut short instead of looking merely incomplete.
    renderBubble({ blocks: appendText([], '部分回答'), bubbleExtra: { runStatus: 'STOPPED' } });

    expect(screen.getByTestId('ai-bubble-run-status')).toHaveTextContent('已停止');
  });

  it('marksNothingForACompletedRunTest', () => {
    renderBubble({
      blocks: appendText([], '完整回答'),
      bubbleExtra: { runStatus: 'COMPLETED' },
    });

    expect(screen.queryByTestId('ai-bubble-run-status')).not.toBeInTheDocument();
  });

  it('formatsThePersistedTimestampAsUtcTest', () => {
    // The backend writes Clock.systemUTC() LocalDateTime with no offset; parsing it as local time
    // would shift every history entry by the viewer's offset.
    renderBubble({
      blocks: appendText([], '回答'),
      bubbleExtra: { createdAt: '2026-09-20T02:12:31' },
    });

    expect(screen.getByText(/2026-09-\d{2} \d{2}:\d{2}:\d{2}/)).toBeInTheDocument();
    expect(screen.queryByText('2026-09-20T02:12:31')).not.toBeInTheDocument();
  });
});
