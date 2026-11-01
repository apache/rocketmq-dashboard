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

import { beforeAll, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { LangProvider } from '../../../../i18n/LangContext';
import type { TimelineEvent } from '../../../../api/aiEvents';
import { foldTimelineBlocks } from '../../render/foldTimeline';
import { reduceLiveBlocks } from '../../render/reduceLive';
import type { ChatSseEvent } from '../../../../api/aiEvents';
import { runningToolBlock, withToolResult } from '../../render/blocks';
import type { ToolBlock as ToolBlockData } from '../../render/blocks';
import ToolBlock from '../blocks/ToolBlock';

// antd Table subscribes to the responsive observer on mount; jsdom has no matchMedia. Same stub
// the other Table-rendering suites use.
beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
});

/**
 * `ToolBlock` against the block shapes the event contract can actually produce, built by running the
 * reducers rather than hand-written, so the card is tested against the same objects the page renders.
 *
 * The case that matters most is the one `ToolBlock.input` is typed `unknown` for: a `tool_result`
 * whose `tool_use` is not in the loaded window (a paging window that opens mid-run, or a reconnect
 * that joined after the call started) folds into a DONE card with `input: null`. The card must still
 * show the output and must say the arguments are unknown — never render a literal `null`, and never
 * fake `{}`, because "this tool takes no arguments" and "we cannot see the arguments" are different
 * statements to an operator.
 *
 * `AssistantBubble.test.tsx` covers the same text through the whole bubble; these cases pin the
 * component itself, including the two optional fields whose absence has to stay invisible: a
 * result without `error` renders no error line, and a running card without `output` renders the
 * pending hint instead of an empty `<pre>`.
 */

/** A no-argument tool: the backend emits `{}` rather than null, so the card shows an empty object. */
const NO_ARG_INPUT = {};

const ORPHANED_RESULT: TimelineEvent = {
  type: 'tool_result',
  tcId: 'toolu_09Z',
  tool: 'mcp__rocketmq-studio__rmq.topic.list',
  output: '{"topics":["TopicA"]}',
  outputBytes: 22,
  truncated: false,
  durationMs: 212,
  success: true,
};

function renderBlock(block: ToolBlockData) {
  return render(
    <LangProvider>
      <ToolBlock block={block} />
    </LangProvider>,
  );
}

function foldOne(event: TimelineEvent): ToolBlockData {
  const blocks = foldTimelineBlocks([event]) as ToolBlockData[];
  expect(blocks).toHaveLength(1);
  expect(blocks[0].kind).toBe('tool');
  return blocks[0];
}

describe('ToolBlock', () => {
  it.each([
    ['persisted', null],
    ['persisted', undefined],
    ['live', null],
    ['live', undefined],
  ] as const)('rendersAResultWithAnUnavailableToolNameTest: %s %s', (source, tool) => {
    // The backend projector permits a missing name when no tool start was observed.
    // JSON serialization also omits undefined names, as NON_NULL serialization can do.
    const wire = JSON.parse(
      JSON.stringify({
        ...ORPHANED_RESULT,
        type: source === 'live' ? 'tool_done' : 'tool_result',
        tool,
      }),
    );
    const block = (
      source === 'live'
        ? reduceLiveBlocks([], wire as ChatSseEvent)[0]
        : foldOne(wire as TimelineEvent)
    ) as ToolBlockData;
    render(
      <LangProvider>
        <ToolBlock
          block={block}
          toolCatalog={[
            {
              name: 'rmq.topic.list',
              description: 'List topics',
              parameters: {},
              riskLevel: 'LOW',
            },
          ]}
        />
      </LangProvider>,
    );
    expect(screen.getByTestId('ai-tool-name')).toHaveTextContent('未知工具');
    expect(screen.getByTestId('tool-result')).toHaveTextContent('TopicA');
    expect(screen.getByTestId('ai-tool-status')).toHaveTextContent('成功');
    expect(screen.queryByTestId('ai-tool-risk')).not.toBeInTheDocument();
  });

  it('rendersADoneCardWithNullInputForAnOrphanedToolResultTest', () => {
    const block = foldOne(ORPHANED_RESULT);

    // The contract-level half of the case: no `tool_use` was folded first, so the input is unknown.
    expect(block.input).toBeNull();
    expect(block.status).toBe('done');

    renderBlock(block);

    const card = screen.getByTestId('ai-tool-block');
    expect(card).toHaveAttribute('data-status', 'done');
    expect(screen.getByTestId('ai-tool-name')).toHaveTextContent('topic.list');
    expect(screen.getByTestId('ai-tool-status')).toHaveTextContent('成功');
    // The output survives even though the call started outside the window, pretty-printed with
    // the raw transport string one click away.
    expect(screen.getByTestId('ai-tool-output-json')).toHaveTextContent('TopicA');
    fireEvent.click(screen.getByTestId('ai-tool-output-raw-toggle'));
    expect(screen.getByTestId('ai-tool-output-raw')).toHaveTextContent('"topics":["TopicA"]');
    expect(screen.getByTestId('ai-tool-input')).toHaveTextContent('输入参数不在已加载的事件范围内');
    // No `<pre>` for an input we do not have, and no bare `null` anywhere in the card.
    expect(screen.queryByTestId('tool-input')).not.toBeInTheDocument();
    expect(card).not.toHaveTextContent('null');
  });

  it('rendersAnEmptyInputObjectAsArgumentsNotAsUnavailableTest', () => {
    // `{}` means the tool takes no arguments — a different claim than "the input is outside the
    // loaded event window", so the two must not render alike.
    renderBlock(runningToolBlock('toolu_2', 'rmq.topic.list', NO_ARG_INPUT));

    expect(screen.getByTestId('tool-input')).toHaveTextContent('{}');
    expect(screen.getByTestId('ai-tool-input')).not.toHaveTextContent(
      '输入参数不在已加载的事件范围内',
    );
  });

  it('rendersARunningCardWithoutAnOutputYetTest', () => {
    renderBlock(runningToolBlock('toolu_1', 'rmq.topic.list', { instanceId: 'local' }));

    expect(screen.getByTestId('ai-tool-block')).toHaveAttribute('data-status', 'running');
    expect(screen.getByTestId('ai-tool-status')).toHaveTextContent('执行中');
    expect(screen.getByTestId('tool-input')).toHaveTextContent('"instanceId": "local"');
    expect(screen.getByTestId('ai-tool-output')).toHaveTextContent('工具执行中，暂无结果');
    expect(screen.queryByTestId('tool-result')).not.toBeInTheDocument();
  });

  it('rendersNoErrorLineWhenTheResultCarriedNoErrorTest', () => {
    // `error` is optional on the wire and `@JsonInclude(NON_NULL)` drops it on a successful call; the
    // card must not grow an empty error line for a field that was never sent.
    const block = foldOne(ORPHANED_RESULT);
    expect(block.error).toBeUndefined();

    renderBlock(block);

    expect(screen.queryByTestId('ai-tool-error')).not.toBeInTheDocument();
    expect(screen.getByTestId('ai-tool-status')).toHaveTextContent('成功');
  });

  it('rendersTheErrorOfAFailedResultTest', () => {
    const block = foldOne({
      type: 'tool_result',
      tcId: 'toolu_09Z',
      tool: 'rmq.topic.list',
      output: '',
      outputBytes: 0,
      truncated: false,
      durationMs: 88,
      success: false,
      error: 'instance not found: local',
    });

    renderBlock(block);

    expect(screen.getByTestId('ai-tool-status')).toHaveTextContent('失败');
    expect(screen.getByTestId('ai-tool-error')).toHaveTextContent('instance not found: local');
  });
});

describe('running stopwatch', () => {
  // The colours are compared against each other rather than against theme hex values: what the
  // operator needs is three visibly distinct stages, and pinning #faad14 here would couple the
  // suite to the antd default theme.
  it('ticksElapsedAndEscalatesTheColourAtFiveThenTenMinutesTest', () => {
    vi.useFakeTimers();
    try {
      renderBlock(runningToolBlock('toolu_sw', 'rmq.topic.list', NO_ARG_INPUT));

      expect(screen.getByTestId('ai-tool-elapsed')).toHaveTextContent('0s');
      expect(screen.queryByTestId('ai-tool-may-timeout')).not.toBeInTheDocument();
      const fresh = screen.getByTestId('ai-tool-elapsed').style.color;

      act(() => {
        vi.advanceTimersByTime(5 * 60 * 1000 + 1000);
      });
      expect(screen.getByTestId('ai-tool-elapsed')).toHaveTextContent('5m');
      expect(screen.queryByTestId('ai-tool-may-timeout')).not.toBeInTheDocument();
      const warned = screen.getByTestId('ai-tool-elapsed').style.color;

      act(() => {
        vi.advanceTimersByTime(5 * 60 * 1000);
      });
      expect(screen.getByTestId('ai-tool-elapsed')).toHaveTextContent('10m');
      expect(screen.getByTestId('ai-tool-may-timeout')).toHaveTextContent('可能超时');
      const stuck = screen.getByTestId('ai-tool-elapsed').style.color;

      expect(new Set([fresh, warned, stuck]).size).toBe(3);
    } finally {
      vi.useRealTimers();
    }
  });

  it('reportsTheAuthoritativeDurationInsteadOfAStopwatchOnceDoneTest', () => {
    const block = withToolResult([], {
      tcId: 'toolu_sw',
      tool: 'rmq.topic.list',
      output: '{}',
      outputBytes: 2,
      truncated: false,
      durationMs: 1240,
      success: true,
    })[0] as ToolBlockData;

    renderBlock(block);

    expect(screen.queryByTestId('ai-tool-elapsed')).not.toBeInTheDocument();
    expect(screen.getByTestId('ai-tool-block')).toHaveTextContent('1.2 s');
  });
});

describe('structured output', () => {
  const doneWith = (output: string): ToolBlockData =>
    (
      withToolResult([], {
        tcId: 'toolu_s',
        tool: 'rmq.topic.list',
        output,
        outputBytes: output.length,
        truncated: false,
        durationMs: 10,
        success: true,
      }) as ToolBlockData[]
    )[0];

  it('tablesAJsonArrayOfUniformObjectsTest', () => {
    renderBlock(doneWith('[{"topic":"T1","perm":6},{"topic":"T2","perm":4}]'));

    const table = screen.getByTestId('ai-tool-output-table');
    expect(table).toHaveTextContent('T1');
    expect(table).toHaveTextContent('T2');
    // antd renders the header twice when `scroll` is set (a hidden measure row), so the column
    // title is asserted as "present", not "unique".
    expect(within(table).getAllByText('topic').length).toBeGreaterThan(0);
  });

  it('tablesCsvShapedTextTest', () => {
    renderBlock(doneWith('topic,perm\nT1,6\nT2,4'));

    expect(screen.getByTestId('ai-tool-output-table')).toHaveTextContent('T2');
  });

  it('tablesTheSingleRowListInsideAnEnvelopeObjectTest', () => {
    // The shape most Studio tools actually return: {"items": [...]} plus scalar envelope fields.
    renderBlock(doneWith('{"total":2,"items":[{"name":"T1","perm":6},{"name":"T2","perm":4}]}'));

    const table = screen.getByTestId('ai-tool-output-table');
    expect(table).toHaveTextContent('T1');
    expect(table).toHaveTextContent('T2');
  });

  it('keepsAnEnvelopeWithTwoRowListsAsJsonTest', () => {
    renderBlock(doneWith('{"a":[{"x":1}],"b":[{"y":2}]}'));

    expect(screen.queryByTestId('ai-tool-output-table')).not.toBeInTheDocument();
    expect(screen.getByTestId('ai-tool-output-json')).toBeInTheDocument();
  });

  it('keepsProseVerbatimWithoutAToggleTest', () => {
    renderBlock(doneWith('OK: 3 topics, see broker log'));

    expect(screen.getByTestId('ai-tool-output-raw')).toHaveTextContent('OK: 3 topics');
    expect(screen.queryByTestId('ai-tool-output-raw-toggle')).not.toBeInTheDocument();
  });
});
