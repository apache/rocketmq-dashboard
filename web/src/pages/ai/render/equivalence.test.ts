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
import type {
  ChatSseEvent,
  RunStatus,
  ThinkingSource,
  TimelineEvent,
  TimelineItem,
} from '../../../api/aiEvents';
import type { RenderBlock } from './blocks';
import { toolDisplayName } from './blocks';
import { reduceLiveBlocks } from './reduceLive';
import {
  coalesceLiveToTimeline,
  foldTimelineBlock,
  foldTimelineBlocks,
  groupIntoBubbles,
  liveToTimeline,
} from './foldTimeline';

/**
 * The property that justifies splitting live from persisted: the UI never knows which one it is
 * watching, because both reduce into the same `RenderBlock[]`.
 *
 * Two primitives are exercised here, on purpose:
 *
 * 1. {@link coalesceLiveToTimeline} models the server's buffered write over a whole sequence and
 *    is the primitive the equivalence property is stated with. Asserting
 *    `fold(coalesce(live)) === reduce(live)` says: whatever the stream produced, reloading the
 *    conversation from `rmq_ai_event` renders exactly the same thing.
 *
 * 2. {@link liveToTimeline} maps a single frame and is lossy by design — a lone `text_delta` has
 *    no persisted counterpart (the server coalesces deltas before writing) and a lone `tool_start`
 *    cannot know whether its input is complete. The tests below pin precisely what it loses, so
 *    nobody mistakes the per-event map for the equivalence primitive.
 */

// ─── Live frame builders ────────────────────────────────────────

const INSTANCE = 'open-source-local';

function runStarted(turn = 3, runId = 41): ChatSseEvent {
  return { type: 'run_started', runId, conversationId: 7, title: '查看集群状态', turn };
}

function text(...deltas: string[]): ChatSseEvent[] {
  return deltas.map((content) => ({ type: 'text_delta', content }));
}

function thinking(content: string, source: ThinkingSource): ChatSseEvent {
  return { type: 'thinking', content, source };
}

function toolStart(tcId: string, tool: string, input: Record<string, unknown>): ChatSseEvent {
  return { type: 'tool_start', tcId, tool, input };
}

interface ToolDoneOverrides {
  outputBytes?: number;
  truncated?: boolean;
  durationMs?: number;
  success?: boolean;
  error?: string;
}

function toolDone(
  tcId: string,
  tool: string,
  output: string,
  overrides: ToolDoneOverrides = {},
): ChatSseEvent {
  return {
    type: 'tool_done',
    tcId,
    tool,
    output,
    outputBytes: output.length,
    truncated: false,
    durationMs: 120,
    success: true,
    ...overrides,
  };
}

function notice(level: 'info' | 'warn', message: string): ChatSseEvent {
  return { type: 'notice', level, message };
}

function errorEvent(code: string, message: string, hint?: string): ChatSseEvent {
  return hint === undefined
    ? { type: 'error', code, message }
    : { type: 'error', code, message, hint };
}

function runFinished(status: RunStatus, durationMs = 1000): ChatSseEvent {
  return { type: 'run_finished', runId: 41, status, durationMs };
}

function reduceAll(events: ChatSseEvent[]): RenderBlock[] {
  return events.reduce<RenderBlock[]>((blocks, event) => reduceLiveBlocks(blocks, event), []);
}

function nonNull(event: TimelineEvent | null): event is TimelineEvent {
  return event !== null;
}

// ─── Scenarios ──────────────────────────────────────────────────

interface Scenario {
  name: string;
  events: ChatSseEvent[];
  /** stop_reason the server would persist alongside the terminal run_status. */
  stopReason?: 'USER_STOP';
}

const SCENARIOS: Scenario[] = [
  {
    name: 'plainChat',
    events: [runStarted(), ...text('集群当前有 ', '2 个 broker。'), runFinished('COMPLETED', 8123)],
  },
  {
    name: 'chatWithModelThinking',
    events: [
      runStarted(),
      thinking('用户想确认堆积，', 'model'),
      thinking('先查 topic 路由', 'model'),
      ...text('堆积 1200 条。'),
      runFinished('COMPLETED'),
    ],
  },
  {
    name: 'chatWithTwoToolCalls',
    events: [
      runStarted(),
      ...text('我来查一下。'),
      toolStart('toolu_01A', 'rmq.topic.list', { instanceId: INSTANCE }),
      toolDone('toolu_01A', 'rmq.topic.list', '{"items":[{"name":"StudioTest"}]}'),
      toolStart('toolu_01B', 'mcp__rocketmq-studio__rmq.group.progress', {
        instanceId: INSTANCE,
        groupName: 'GID_demo',
      }),
      toolDone('toolu_01B', 'mcp__rocketmq-studio__rmq.group.progress', '{"lag":1200}', {
        outputBytes: 4096,
        truncated: true,
        durationMs: 87,
      }),
      ...text('共 1200 条堆积。'),
      runFinished('COMPLETED', 4310),
    ],
  },
  {
    name: 'stoppedMidTool',
    stopReason: 'USER_STOP',
    events: [
      runStarted(),
      thinking('需要先看 broker 列表', 'model'),
      toolStart('toolu_02A', 'rmq.broker.list', { instanceId: INSTANCE }),
      // No tool_done: the user hit stop while the call was in flight.
      runFinished('STOPPED', 4200),
    ],
  },
  {
    name: 'providerError',
    events: [
      runStarted(),
      ...text('正在'),
      errorEvent(
        'llm.provider.timeout',
        'claude CLI stream timed out after 300s',
        'Retry with a shorter prompt.',
      ),
      runFinished('FAILED', 300000),
    ],
  },
  {
    name: 'enhanceInterleavedWithModelThinking',
    events: [
      runStarted(),
      thinking('把「堆积」明确为 ', 'enhance'),
      thinking('consumer lag', 'enhance'),
      thinking('用户想确认堆积，先查 topic 路由', 'model'),
      thinking('（改写后补充实例范围）', 'enhance'),
      ...text('已按优化后的提示词处理。'),
      runFinished('COMPLETED'),
    ],
  },
];

/**
 * Exactly what the per-event {@link liveToTimeline} map is documented to lose: streamed text (no
 * single delta becomes a row) and tool input (only `tool_use` carries it, and the per-event map
 * cannot emit one). A tool card whose result never arrived disappears as well.
 */
function documentedPerEventLoss(live: RenderBlock[]): RenderBlock[] {
  return live
    .filter((block) => block.kind !== 'text')
    .filter((block) => block.kind !== 'tool' || block.status === 'done')
    .map((block) => (block.kind === 'tool' ? { ...block, input: null } : block));
}

describe('live and replay converge', () => {
  for (const scenario of SCENARIOS) {
    it(`${scenario.name}RendersIdenticallyLiveAndReplayedTest`, () => {
      const live = reduceAll(scenario.events);
      expect(live.length, `scenario ${scenario.name} renders something`).toBeGreaterThan(0);

      const replayed = foldTimelineBlocks(
        coalesceLiveToTimeline(scenario.events, { stopReason: scenario.stopReason }),
      );
      expect(replayed).toStrictEqual(live);
    });

    it(`${scenario.name}PerEventTranslationLosesOnlyTextAndToolInputTest`, () => {
      const live = reduceAll(scenario.events);
      const perEvent = scenario.events.map(liveToTimeline).filter(nonNull);
      const replayed = foldTimelineBlocks(perEvent);

      expect(replayed).toStrictEqual(documentedPerEventLoss(live));
    });
  }

  it('textDeltaStreamBecomesOneBlockOnBothPathsTest', () => {
    // Invariant under test: the server coalesces deltas before persisting, and both reducers
    // coalesce adjacent text again, so a 200-delta stream is ONE text block live and ONE text
    // block replayed — regardless of how the server chose to chunk it.
    const deltas = Array.from({ length: 200 }, (_, i) => `片段 ${i} `);
    const events: ChatSseEvent[] = [runStarted(), ...text(...deltas), runFinished('COMPLETED')];
    const joined = deltas.join('');

    const live = reduceAll(events);
    expect(live).toStrictEqual([{ kind: 'text', text: joined }]);

    // One persisted event when the buffer never overflows...
    expect(
      foldTimelineBlocks(coalesceLiveToTimeline(events, { maxChars: Number.MAX_SAFE_INTEGER })),
    ).toStrictEqual(live);
    // ...and still one block when it is flushed into many.
    const chunked = coalesceLiveToTimeline(events, { maxChars: 64 });
    expect(chunked.filter((event) => event.type === 'text').length).toBeGreaterThan(1);
    expect(foldTimelineBlocks(chunked)).toStrictEqual(live);
  });
});

describe('thinking sources never coalesce', () => {
  it('adjacentEnhanceAndModelThinkingStaySeparateTest', () => {
    // The bug this pins: the prompt-enhancement rewrite used to be rendered under a 思维链 heading,
    // because model reasoning and the rewrite were folded into one block.
    const events: ChatSseEvent[] = [
      thinking('改写：把「堆积」明确为 consumer lag', 'enhance'),
      thinking('用户想确认堆积，先查 topic 路由', 'model'),
      thinking('（改写补充：限定实例范围）', 'enhance'),
    ];

    const live = reduceAll(events);
    expect(live).toStrictEqual([
      { kind: 'thinking', text: '改写：把「堆积」明确为 consumer lag', source: 'enhance' },
      { kind: 'thinking', text: '用户想确认堆积，先查 topic 路由', source: 'model' },
      { kind: 'thinking', text: '（改写补充：限定实例范围）', source: 'enhance' },
    ]);

    const persisted = coalesceLiveToTimeline(events);
    expect(persisted.map((event) => event.type)).toStrictEqual([
      'thinking',
      'thinking',
      'thinking',
    ]);
    expect(foldTimelineBlocks(persisted)).toStrictEqual(live);
  });

  it('sameSourceThinkingCoalescesOnBothPathsTest', () => {
    const events: ChatSseEvent[] = [thinking('先看路由', 'model'), thinking('，再查堆积', 'model')];
    const live = reduceAll(events);

    expect(live).toStrictEqual([{ kind: 'thinking', text: '先看路由，再查堆积', source: 'model' }]);
    expect(coalesceLiveToTimeline(events)).toStrictEqual([
      { type: 'thinking', text: '先看路由，再查堆积', source: 'model' },
    ]);
    expect(foldTimelineBlocks(coalesceLiveToTimeline(events))).toStrictEqual(live);
  });
});

describe('reduceLiveBlocks', () => {
  it('isPureAndKeepsTheReferenceWhenNothingRendersTest', () => {
    const blocks: RenderBlock[] = [{ kind: 'text', text: '已有内容' }];
    const snapshot = structuredClone(blocks);

    const afterText = reduceLiveBlocks(blocks, { type: 'text_delta', content: '追加' });
    expect(afterText).not.toBe(blocks);
    expect(blocks).toStrictEqual(snapshot);
    expect(afterText).toStrictEqual([{ kind: 'text', text: '已有内容追加' }]);

    // Run-level frames render nothing and hand the very same array back, so callers can skip work.
    expect(reduceLiveBlocks(blocks, runStarted())).toBe(blocks);
    expect(reduceLiveBlocks(blocks, runFinished('COMPLETED'))).toBe(blocks);
    expect(blocks).toStrictEqual(snapshot);
  });

  it('startsNewTextBlockWhenTheTailIsNotTextTest', () => {
    const blocks = reduceAll([
      ...text('第一段'),
      notice('info', '本次运行已恢复上游会话'),
      ...text('第二段'),
    ]);

    expect(blocks).toStrictEqual([
      { kind: 'text', text: '第一段' },
      { kind: 'notice', level: 'info', message: '本次运行已恢复上游会话' },
      { kind: 'text', text: '第二段' },
    ]);
  });

  it('fillsTheToolCardOpenedByToolStartTest', () => {
    const blocks = reduceAll([toolStart('toolu_01A', 'rmq.topic.list', { instanceId: INSTANCE })]);
    expect(blocks).toStrictEqual([
      {
        kind: 'tool',
        tcId: 'toolu_01A',
        tool: 'rmq.topic.list',
        input: { instanceId: INSTANCE },
        output: null,
        outputBytes: null,
        truncated: false,
        durationMs: null,
        success: null,
        status: 'running',
      },
    ]);

    const filled = reduceLiveBlocks(blocks, {
      type: 'tool_done',
      tcId: 'toolu_01A',
      tool: 'rmq.topic.list',
      output: '{"items":[]}',
      outputBytes: 13,
      truncated: false,
      durationMs: 212,
      success: true,
    });
    expect(filled).toStrictEqual([
      {
        kind: 'tool',
        tcId: 'toolu_01A',
        tool: 'rmq.topic.list',
        input: { instanceId: INSTANCE },
        output: '{"items":[]}',
        outputBytes: 13,
        truncated: false,
        durationMs: 212,
        success: true,
        status: 'done',
      },
    ]);
    // The running card was not mutated in place.
    expect(blocks[0]).toMatchObject({ status: 'running', output: null });
  });

  it('carriesToolOutputVerbatimAsATransportStringTest', () => {
    // Tool INPUT comes from the model and is structured; tool OUTPUT comes from whatever the tool
    // returned (already base64-stripped and capped at 32 KiB server-side) and is opaque text that
    // only usually holds JSON. Neither reducer may parse, reformat or reject it.
    const raw = 'rmqctl: connect tcp 127.0.0.1:8888: dial timeout\n\texit status 1';
    const live = reduceAll([toolDone('toolu_01C', 'rmq.topic.list', raw)]);
    const replayed = foldTimelineBlocks([
      {
        type: 'tool_result',
        tcId: 'toolu_01C',
        tool: 'rmq.topic.list',
        output: raw,
        outputBytes: raw.length,
        truncated: false,
        durationMs: 120,
        success: true,
      },
    ]);

    expect(live).toStrictEqual(replayed);
    expect(live[0]).toMatchObject({ kind: 'tool', output: raw, status: 'done' });

    // A JSON-looking payload stays a string too: whether to JSON.parse it is the renderer's call.
    const structured = reduceAll([toolDone('toolu_01D', 'rmq.topic.list', '{"items":[]}')]);
    expect(structured[0]).toMatchObject({ output: '{"items":[]}' });
    expect(typeof (structured[0] as { output: unknown }).output).toBe('string');
  });

  it('appendsADoneToolCardWhenTheStartWasMissedTest', () => {
    // A reconnect can join after tool_start; the output must still be shown.
    const blocks = reduceAll([
      toolDone('toolu_09Z', 'rmq.topic.list', '{"items":[]}', { success: false, error: 'boom' }),
    ]);

    expect(blocks).toStrictEqual([
      {
        kind: 'tool',
        tcId: 'toolu_09Z',
        tool: 'rmq.topic.list',
        input: null,
        output: '{"items":[]}',
        outputBytes: 12,
        truncated: false,
        durationMs: 120,
        success: false,
        error: 'boom',
        status: 'done',
      },
    ]);
  });

  it('omitsTheHintKeyWhenTheProviderSentNoneTest', () => {
    const blocks = reduceAll([errorEvent('llm.provider.overloaded', 'provider is overloaded')]);

    expect(blocks).toStrictEqual([
      { kind: 'error', code: 'llm.provider.overloaded', message: 'provider is overloaded' },
    ]);
    expect(Object.keys(blocks[0])).not.toContain('hint');
  });
});

describe('foldTimelineBlocks', () => {
  it('ignoresUserAndRunStatusEventsTest', () => {
    const blocks: RenderBlock[] = [{ kind: 'text', text: '已有内容' }];

    expect(foldTimelineBlock(blocks, { type: 'user', text: '查看集群状态' })).toBe(blocks);
    expect(foldTimelineBlock(blocks, { type: 'run_status', status: 'STOPPED' })).toBe(blocks);
    expect(
      foldTimelineBlocks([
        { type: 'user', text: '查看集群状态', enhancedPrompt: '请列出 broker 与 topic 概况' },
        { type: 'run_status', status: 'COMPLETED' },
      ]),
    ).toStrictEqual([]);
  });

  it('coalescesAdjacentTextAndSplitsOnInterruptionTest', () => {
    const blocks = foldTimelineBlocks([
      { type: 'text', text: '第一段' },
      { type: 'text', text: '（续）' },
      { type: 'notice', level: 'warn', message: 'rmqctl 不可用' },
      { type: 'text', text: '第二段' },
    ]);

    expect(blocks).toStrictEqual([
      { kind: 'text', text: '第一段（续）' },
      { kind: 'notice', level: 'warn', message: 'rmqctl 不可用' },
      { kind: 'text', text: '第二段' },
    ]);
  });

  it('appendsADoneToolCardWhenToolUseFellOutsideThePageTest', () => {
    const blocks = foldTimelineBlocks([
      {
        type: 'tool_result',
        tcId: 'toolu_09Z',
        tool: 'rmq.topic.list',
        output: '{"items":[]}',
        outputBytes: 13,
        truncated: false,
        durationMs: 212,
        success: true,
      },
    ]);

    expect(blocks).toStrictEqual([
      {
        kind: 'tool',
        tcId: 'toolu_09Z',
        tool: 'rmq.topic.list',
        input: null,
        output: '{"items":[]}',
        outputBytes: 13,
        truncated: false,
        durationMs: 212,
        success: true,
        status: 'done',
      },
    ]);
  });
});

describe('liveToTimeline', () => {
  it('mapsPersistableFramesAndDocumentsTheNullsTest', () => {
    // Run-level metadata: the rmq_ai_run row is the truth, nothing is persisted per event.
    expect(liveToTimeline(runStarted())).toBeNull();
    // Deltas: the server coalesces them into one persisted `text`.
    expect(liveToTimeline({ type: 'text_delta', content: '集群当前有 ' })).toBeNull();
    // The persisted tool_use is written at input-complete, which one frame cannot know.
    expect(
      liveToTimeline(toolStart('toolu_01A', 'rmq.topic.list', { instanceId: INSTANCE })),
    ).toBeNull();

    expect(liveToTimeline(thinking('先查路由', 'enhance'))).toStrictEqual({
      type: 'thinking',
      text: '先查路由',
      source: 'enhance',
    });
    expect(liveToTimeline(notice('warn', 'rmqctl 不可用'))).toStrictEqual({
      type: 'notice',
      level: 'warn',
      message: 'rmqctl 不可用',
    });
    expect(liveToTimeline(errorEvent('llm.provider.timeout', 'timed out', 'Retry.'))).toStrictEqual(
      {
        type: 'error',
        code: 'llm.provider.timeout',
        message: 'timed out',
        hint: 'Retry.',
      },
    );
    expect(liveToTimeline(errorEvent('llm.provider.timeout', 'timed out'))).toStrictEqual({
      type: 'error',
      code: 'llm.provider.timeout',
      message: 'timed out',
    });
    // Lossy: the live frame carries no stop reason, so the persisted twin has none either.
    expect(liveToTimeline(runFinished('STOPPED'))).toStrictEqual({
      type: 'run_status',
      status: 'STOPPED',
    });

    const result = liveToTimeline(
      toolDone('toolu_01A', 'rmq.topic.list', '{"items":[]}', { success: false, error: 'boom' }),
    );
    expect(result).toStrictEqual({
      type: 'tool_result',
      tcId: 'toolu_01A',
      tool: 'rmq.topic.list',
      output: '{"items":[]}',
      outputBytes: 12,
      truncated: false,
      durationMs: 120,
      success: false,
      error: 'boom',
    });
  });

  it('coalesceDropsRunStartedAndCarriesTheStopReasonTest', () => {
    const persisted = coalesceLiveToTimeline(
      [runStarted(), ...text('集群当前有 2 个 broker。'), runFinished('STOPPED')],
      { stopReason: 'USER_STOP' },
    );

    expect(persisted).toStrictEqual([
      { type: 'text', text: '集群当前有 2 个 broker。' },
      { type: 'run_status', status: 'STOPPED', reason: 'USER_STOP' },
    ]);
  });

  it('coalesceKeepsToolPairsJoinedByTcIdTest', () => {
    const persisted = coalesceLiveToTimeline([
      toolStart('toolu_01A', 'rmq.topic.list', { instanceId: INSTANCE }),
      toolDone('toolu_01A', 'rmq.topic.list', '{"items":[]}'),
    ]);

    expect(persisted).toStrictEqual([
      {
        type: 'tool_use',
        tcId: 'toolu_01A',
        tool: 'rmq.topic.list',
        input: { instanceId: INSTANCE },
      },
      {
        type: 'tool_result',
        tcId: 'toolu_01A',
        tool: 'rmq.topic.list',
        output: '{"items":[]}',
        outputBytes: 12,
        truncated: false,
        durationMs: 120,
        success: true,
      },
    ]);
  });
});

// ─── groupIntoBubbles ───────────────────────────────────────────

function timelineItem(
  seq: number,
  turn: number,
  event: TimelineEvent,
  createdAt = `2026-09-17 10:00:${String(seq).padStart(2, '0')}`,
): TimelineItem {
  return { id: seq, turn, seq, createdAt, event, runId: 41 };
}

describe('TimelineItem envelope', () => {
  it('requiresTheRunIdOfEveryPersistedRowTest', () => {
    // `rmq_ai_event.run_id` is NOT NULL, so the REST envelope must not be weaker than the storage.
    // The directive below fails the build if `runId` is ever made optional again.
    // @ts-expect-error runId is required: every persisted event belongs to exactly one run
    const partial: TimelineItem = {
      id: 1,
      turn: 1,
      seq: 1,
      createdAt: '2026-09-17 10:00:01',
      event: { type: 'text', text: 'hi' },
    };

    expect(partial.runId).toBeUndefined();
    expect(timelineItem(1, 1, { type: 'text', text: 'hi' }).runId).toBe(41);
  });
});

const DONE_TOOL_CARD: RenderBlock = {
  kind: 'tool',
  tcId: 'toolu_01A',
  tool: 'rmq.topic.list',
  input: { instanceId: INSTANCE },
  output: '{"items":[]}',
  outputBytes: 13,
  truncated: false,
  durationMs: 212,
  success: true,
  status: 'done',
};

describe('groupIntoBubbles', () => {
  it('splitsOnUserEventsAndCarriesTurnAndStatusTest', () => {
    const items: TimelineItem[] = [
      timelineItem(1, 1, {
        type: 'user',
        text: '查看集群状态',
        enhancedPrompt: '请列出当前集群的 broker 与 topic 概况',
      }),
      timelineItem(2, 1, { type: 'thinking', text: '先查 topic 路由', source: 'model' }),
      timelineItem(3, 1, {
        type: 'tool_use',
        tcId: 'toolu_01A',
        tool: 'rmq.topic.list',
        input: { instanceId: INSTANCE },
      }),
      timelineItem(4, 1, {
        type: 'tool_result',
        tcId: 'toolu_01A',
        tool: 'rmq.topic.list',
        output: '{"items":[]}',
        outputBytes: 13,
        truncated: false,
        durationMs: 212,
        success: true,
      }),
      timelineItem(5, 1, { type: 'text', text: '集群当前有 2 个 broker。' }),
      timelineItem(6, 1, { type: 'run_status', status: 'COMPLETED' }),
      timelineItem(7, 2, { type: 'user', text: '停一下' }),
      timelineItem(8, 2, {
        type: 'tool_use',
        tcId: 'toolu_02A',
        tool: 'rmq.broker.list',
        input: { instanceId: INSTANCE },
      }),
      timelineItem(9, 2, { type: 'run_status', status: 'STOPPED', reason: 'USER_STOP' }),
    ];

    expect(groupIntoBubbles(items)).toStrictEqual([
      {
        role: 'user',
        blocks: [{ kind: 'text', text: '查看集群状态' }],
        turn: 1,
        createdAt: '2026-09-17 10:00:01',
      },
      {
        role: 'assistant',
        blocks: [
          { kind: 'thinking', text: '先查 topic 路由', source: 'model' },
          DONE_TOOL_CARD,
          { kind: 'text', text: '集群当前有 2 个 broker。' },
        ],
        turn: 1,
        createdAt: '2026-09-17 10:00:02',
        runStatus: 'COMPLETED',
      },
      {
        role: 'user',
        blocks: [{ kind: 'text', text: '停一下' }],
        turn: 2,
        createdAt: '2026-09-17 10:00:07',
      },
      {
        role: 'assistant',
        blocks: [
          {
            kind: 'tool',
            tcId: 'toolu_02A',
            tool: 'rmq.broker.list',
            input: { instanceId: INSTANCE },
            output: null,
            outputBytes: null,
            truncated: false,
            durationMs: null,
            success: null,
            status: 'running',
          },
        ],
        turn: 2,
        createdAt: '2026-09-17 10:00:08',
        runStatus: 'STOPPED',
      },
    ]);
  });

  it('opensAnAssistantBubbleWhenHistoryStartsMidRunTest', () => {
    // A paging window can open after the `user` row of the run it continues.
    const bubbles = groupIntoBubbles([
      timelineItem(5, 1, { type: 'text', text: '集群当前有 2 个 broker。' }),
      timelineItem(6, 1, { type: 'run_status', status: 'COMPLETED' }),
    ]);

    expect(bubbles).toStrictEqual([
      {
        role: 'assistant',
        blocks: [{ kind: 'text', text: '集群当前有 2 个 broker。' }],
        turn: 1,
        createdAt: '2026-09-17 10:00:05',
        runStatus: 'COMPLETED',
      },
    ]);
  });

  it('keepsTheTerminalStatusOfARunThatRenderedNothingTest', () => {
    // Stopped before a single block: the status-only bubble is how a reloaded conversation still
    // shows 已停止 without joining rmq_ai_run. Callers may render zero blocks as a placeholder.
    const bubbles = groupIntoBubbles([
      timelineItem(1, 1, { type: 'user', text: '查看集群状态' }),
      timelineItem(2, 1, { type: 'run_status', status: 'STOPPED', reason: 'USER_STOP' }),
    ]);

    expect(bubbles).toStrictEqual([
      {
        role: 'user',
        blocks: [{ kind: 'text', text: '查看集群状态' }],
        turn: 1,
        createdAt: '2026-09-17 10:00:01',
      },
      {
        role: 'assistant',
        blocks: [],
        turn: 1,
        createdAt: '2026-09-17 10:00:02',
        runStatus: 'STOPPED',
      },
    ]);
  });

  it('returnsNothingForAnEmptyTimelineTest', () => {
    expect(groupIntoBubbles([])).toStrictEqual([]);
  });
});

describe('toolDisplayName', () => {
  it('stripsTransportAndNamespacePrefixesTest', () => {
    expect(toolDisplayName('mcp__rocketmq-studio__rmq.topic.list')).toBe('topic.list');
    expect(toolDisplayName('rmq.topic.list')).toBe('topic.list');
    expect(toolDisplayName('mcp__rocketmq-studio__Bash')).toBe('Bash');
    expect(toolDisplayName('Bash')).toBe('Bash');
    expect(toolDisplayName('rmqctl')).toBe('rmqctl');
    expect(toolDisplayName('')).toBe('');
  });
});
