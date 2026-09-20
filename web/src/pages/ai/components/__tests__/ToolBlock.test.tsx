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

import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { LangProvider } from '../../../../i18n/LangContext';
import type { TimelineEvent } from '../../../../api/aiEvents';
import { foldTimelineBlocks } from '../../render/foldTimeline';
import { runningToolBlock } from '../../render/blocks';
import type { ToolBlock as ToolBlockData } from '../../render/blocks';
import ToolBlock from '../blocks/ToolBlock';

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
    // The output survives even though the call started outside the window.
    expect(screen.getByTestId('tool-result')).toHaveTextContent('"topics":["TopicA"]');
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
