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
import { LIVE_EVENT_TYPES, TIMELINE_EVENT_TYPES } from './aiEvents';
import type {
  ChatSseEvent,
  NoticeLevel,
  RunStatus,
  StopReason,
  TimelineEvent,
  TimelineItem,
  ToolResultFields,
} from './aiEvents';
import {
  coalesceLiveToTimeline,
  foldTimelineBlocks,
  groupIntoBubbles,
  liveToTimeline,
} from '../pages/ai/render/foldTimeline';
import { reduceLiveBlocks } from '../pages/ai/render/reduceLive';
import type { RenderBlock } from '../pages/ai/render/blocks';

/**
 * The ABSENT-optional-field half of the cross-language event contract.
 *
 * `aiEvents.contract.test.ts` drives the shared fixture
 * `server/src/test/resources/ai/ai-event-contract.json`, which carries exactly one example per event
 * type — and every one of those examples populates its optional fields (`hint`, `error`,
 * `enhancedPrompt`, `reason`). That proves the fields exist; it never proves they may be MISSING,
 * which is the shape the backend sends most of the time: `LiveEvent` and `TimelineEvent` are
 * `@JsonInclude(NON_NULL)`, so Jackson drops every null on serialisation. A successful `tool_result`
 * arrives without `error`, a normally completed run arrives without `reason`, and a provider error
 * usually arrives without `hint`.
 *
 * The fixture is deliberately left untouched: the Java `AiEventContractTest` reads the same file, and
 * a second example per type would have to be agreed by both languages in one go. So these cases build
 * their events inline in TypeScript — one MINIMAL variant per member of both unions, kept in the two
 * `Record` tables below. Those tables are the compile-time half of the coverage: adding an event type
 * to the contract without adding its minimal variant here is a type error, not a silently untested
 * frame.
 *
 * "Absent" is asserted strictly. `toStrictEqual` distinguishes `{ hint: undefined }` from `{}`, and
 * {@link expectJsonRoundTrips} adds the invariant the Java annotation actually promises: a field the
 * provider did not send must not exist on the block at all, because a renderer that checks
 * `'hint' in block` and one that checks `block.hint !== undefined` disagree otherwise.
 */

// ─── Minimal wire shapes: required fields only ──────────────────

/**
 * Every live frame with each optional field absent. `tool_start.input` stays `{}` rather than being
 * dropped: the contract types it `Record<string, unknown>` precisely because the backend emits an
 * empty object for a no-arg tool instead of null.
 */
const MINIMAL_LIVE: Record<ChatSseEvent['type'], ChatSseEvent> = {
  run_started: {
    type: 'run_started',
    runId: 41,
    conversationId: 7,
    title: '查看集群状态',
    turn: 3,
  },
  // One delta standing in for the whole stream: the persisted `text` is the coalesced form of the
  // deltas, so the two minimal variants carry the same string and stay comparable across paths.
  text_delta: { type: 'text_delta', content: '集群当前有 2 个 broker。' },
  thinking: { type: 'thinking', content: '用户想确认堆积，先查 topic 路由', source: 'model' },
  tool_start: { type: 'tool_start', tcId: 'toolu_01A', tool: 'rmq.topic.list', input: {} },
  tool_done: {
    type: 'tool_done',
    tcId: 'toolu_01A',
    tool: 'rmq.topic.list',
    output: '{"items":[]}',
    outputBytes: 13,
    truncated: false,
    durationMs: 212,
    success: true,
  },
  notice: { type: 'notice', level: 'info', message: '本次运行已恢复上游会话' },
  error: { type: 'error', code: 'llm.provider.overloaded', message: 'provider is overloaded' },
  run_finished: { type: 'run_finished', runId: 41, status: 'COMPLETED', durationMs: 8123 },
};

/**
 * Every persisted event with each optional field absent: `user` without `enhancedPrompt`,
 * `tool_result` without `error`, `error` without `hint`, `run_status` without `reason`.
 */
const MINIMAL_TIMELINE: Record<TimelineEvent['type'], TimelineEvent> = {
  user: { type: 'user', text: '查看集群状态' },
  thinking: { type: 'thinking', text: '用户想确认堆积，先查 topic 路由', source: 'model' },
  text: { type: 'text', text: '集群当前有 2 个 broker。' },
  tool_use: { type: 'tool_use', tcId: 'toolu_01A', tool: 'rmq.topic.list', input: {} },
  tool_result: {
    type: 'tool_result',
    tcId: 'toolu_01A',
    tool: 'rmq.topic.list',
    output: '{"items":[]}',
    outputBytes: 13,
    truncated: false,
    durationMs: 212,
    success: true,
  },
  notice: { type: 'notice', level: 'info', message: '本次运行已恢复上游会话' },
  error: { type: 'error', code: 'llm.provider.overloaded', message: 'provider is overloaded' },
  run_status: { type: 'run_status', status: 'COMPLETED' },
};

/**
 * The done card a lone `tool_done` / `tool_result` renders: with no `tool_start` in sight the
 * arguments are unknown, so `input` is null rather than a faked `{}`.
 */
const ORPHAN_TOOL_CARD: RenderBlock = {
  kind: 'tool',
  tcId: 'toolu_01A',
  tool: 'rmq.topic.list',
  input: null,
  output: '{"items":[]}',
  outputBytes: 13,
  truncated: false,
  durationMs: 212,
  success: true,
  status: 'done',
};

/** The same card once its `tool_start` (or persisted `tool_use`) supplied the arguments. */
const PAIRED_TOOL_CARD: RenderBlock = { ...ORPHAN_TOOL_CARD, input: {} };

/**
 * The exact blocks each minimal live frame renders IN ISOLATION; `[]` for the run-level frames that
 * render none.
 */
const EXPECTED_LIVE_BLOCKS: Record<ChatSseEvent['type'], RenderBlock[]> = {
  run_started: [],
  text_delta: [{ kind: 'text', text: '集群当前有 2 个 broker。' }],
  thinking: [{ kind: 'thinking', text: '用户想确认堆积，先查 topic 路由', source: 'model' }],
  tool_start: [
    {
      kind: 'tool',
      tcId: 'toolu_01A',
      tool: 'rmq.topic.list',
      input: {},
      output: null,
      outputBytes: null,
      truncated: false,
      durationMs: null,
      success: null,
      status: 'running',
    },
  ],
  tool_done: [ORPHAN_TOOL_CARD],
  notice: [{ kind: 'notice', level: 'info', message: '本次运行已恢复上游会话' }],
  error: [{ kind: 'error', code: 'llm.provider.overloaded', message: 'provider is overloaded' }],
  run_finished: [],
};

/**
 * The exact blocks each minimal persisted event folds to IN ISOLATION. Every entry that has a live
 * twin points at the same object: the two vocabularies must not merely look similar, they must
 * produce structurally identical blocks, absent optional fields included.
 */
const EXPECTED_TIMELINE_BLOCKS: Record<TimelineEvent['type'], RenderBlock[]> = {
  // `user` renders nothing here: the caller builds user bubbles separately.
  user: [],
  thinking: EXPECTED_LIVE_BLOCKS.thinking,
  text: EXPECTED_LIVE_BLOCKS.text_delta,
  tool_use: EXPECTED_LIVE_BLOCKS.tool_start,
  tool_result: [ORPHAN_TOOL_CARD],
  notice: EXPECTED_LIVE_BLOCKS.notice,
  error: EXPECTED_LIVE_BLOCKS.error,
  // `run_status` renders nothing: it is run metadata carried by groupIntoBubbles.
  run_status: [],
};

// ─── Enum coverage tables ───────────────────────────────────────
//
// Written as `Record<T, true>` literals so the compiler enforces completeness: a new RunStatus on the
// Java side that nobody adds here is a type error, and `Object.keys` then yields the runtime list, so
// the table cannot disagree with the loop it drives.

const RUN_STATUS_COVERAGE: Record<RunStatus, true> = {
  QUEUED: true,
  RUNNING: true,
  COMPLETED: true,
  STOPPED: true,
  FAILED: true,
};

const STOP_REASON_COVERAGE: Record<StopReason, true> = {
  USER_STOP: true,
  SHUTDOWN: true,
  TIMEOUT: true,
  OUTPUT_LIMIT: true,
  PROVIDER_ERROR: true,
  SERVER_RESTART: true,
  OVERLOADED: true,
  ORPHANED: true,
};

const NOTICE_LEVEL_COVERAGE: Record<NoticeLevel, true> = {
  info: true,
  warn: true,
  // Emitted by ClaudeCodeStreamParser for a `result` frame carrying `errors`, and for a non-null
  // `api_error_status`. The run survives it, which is why it is a notice and not an `error` event.
  error: true,
};

const RUN_STATUSES = Object.keys(RUN_STATUS_COVERAGE) as RunStatus[];
const STOP_REASONS = Object.keys(STOP_REASON_COVERAGE) as StopReason[];
const NOTICE_LEVELS = Object.keys(NOTICE_LEVEL_COVERAGE) as NoticeLevel[];

// ─── Helpers ────────────────────────────────────────────────────

function reduceAll(events: ChatSseEvent[]): RenderBlock[] {
  return events.reduce<RenderBlock[]>((blocks, event) => reduceLiveBlocks(blocks, event), []);
}

/**
 * Assert the value carries no `undefined`-valued key anywhere: JSON drops those, so a lossless
 * round-trip is exactly the `@JsonInclude(NON_NULL)` promise restated on this side of the wire.
 */
function expectJsonRoundTrips(value: unknown): void {
  expect(JSON.parse(JSON.stringify(value))).toStrictEqual(value);
}

/** A persisted row envelope around one event; only `event` varies in these cases. */
function item(event: TimelineEvent, seq = 1): TimelineItem {
  return { id: seq, turn: 3, seq, createdAt: '2026-09-15T10:21:49', event, runId: 41 };
}

/** Generated test names read `noticeAtLevelInfoSurvivesBothPathsTest`, not `...Levelinfo...`. */
function capitalise(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1);
}

/** A call that failed, with the optional `error` present. */
const FAILED_RESULT: ToolResultFields = {
  tcId: 'toolu_01F',
  tool: 'rmq.topic.list',
  output: '',
  outputBytes: 0,
  truncated: false,
  durationMs: 88,
  success: false,
  error: 'instance not found: local',
};

/** The same call retried and succeeding, with `error` absent. */
const RETRIED_RESULT: ToolResultFields = {
  tcId: 'toolu_01F',
  tool: 'rmq.topic.list',
  output: '{"items":[]}',
  outputBytes: 13,
  truncated: false,
  durationMs: 140,
  success: true,
};

describe('aiEvents optional fields absent', () => {
  it('everyMinimalLiveFrameReducesToItsExactBlockTest', () => {
    for (const type of LIVE_EVENT_TYPES) {
      const event = MINIMAL_LIVE[type];
      const blocks = reduceLiveBlocks([], event);

      expect(() => reduceLiveBlocks([], event), `reduce ${type}`).not.toThrow();
      expect(blocks, `blocks for ${type}`).toStrictEqual(EXPECTED_LIVE_BLOCKS[type]);
      expectJsonRoundTrips(blocks);
    }
  });

  it('everyMinimalTimelineEventFoldsToItsExactBlockTest', () => {
    for (const type of TIMELINE_EVENT_TYPES) {
      const event = MINIMAL_TIMELINE[type];
      const blocks = foldTimelineBlocks([event]);

      expect(() => foldTimelineBlocks([event]), `fold ${type}`).not.toThrow();
      expect(blocks, `blocks for ${type}`).toStrictEqual(EXPECTED_TIMELINE_BLOCKS[type]);
      expectJsonRoundTrips(blocks);
    }
  });

  it('theWholeMinimalLiveSequenceInventsNoUndefinedKeysTest', () => {
    const blocks = reduceAll(LIVE_EVENT_TYPES.map((type) => MINIMAL_LIVE[type]));

    // tool_start and tool_done share tcId toolu_01A, so the two frames are ONE card — and because the
    // start supplied the arguments, that card carries `input: {}`, not the orphan's null. Everything
    // else renders its own block and the two run-level frames render nothing at all.
    expect(blocks).toStrictEqual([
      EXPECTED_LIVE_BLOCKS.text_delta[0],
      EXPECTED_LIVE_BLOCKS.thinking[0],
      PAIRED_TOOL_CARD,
      EXPECTED_LIVE_BLOCKS.notice[0],
      EXPECTED_LIVE_BLOCKS.error[0],
    ]);
    expectJsonRoundTrips(blocks);

    // Spelled out so a regression names the field instead of showing a diff of two look-alike
    // objects: a hint-less error keeps no `hint` key, a successful call keeps no `error` key.
    expect(Object.keys(blocks[4])).not.toContain('hint');
    expect(Object.keys(blocks[2])).not.toContain('error');
  });

  it('theWholeMinimalTimelineFoldsToTheSameBlocksAsTheLiveSequenceTest', () => {
    const replayed = foldTimelineBlocks(TIMELINE_EVENT_TYPES.map((type) => MINIMAL_TIMELINE[type]));
    // Ordered thinking-then-text so the live array and the persisted array are element-wise
    // comparable; `LIVE_EVENT_TYPES` order would put the text block first.
    const live = reduceAll([
      MINIMAL_LIVE.run_started,
      MINIMAL_LIVE.thinking,
      MINIMAL_LIVE.text_delta,
      MINIMAL_LIVE.tool_start,
      MINIMAL_LIVE.tool_done,
      MINIMAL_LIVE.notice,
      MINIMAL_LIVE.error,
      MINIMAL_LIVE.run_finished,
    ]);

    expect(replayed).toStrictEqual([
      EXPECTED_TIMELINE_BLOCKS.thinking[0],
      EXPECTED_TIMELINE_BLOCKS.text[0],
      PAIRED_TOOL_CARD,
      EXPECTED_TIMELINE_BLOCKS.notice[0],
      EXPECTED_TIMELINE_BLOCKS.error[0],
    ]);
    // The property the fixture cannot state: with every optional field absent on both sides, the two
    // vocabularies land on structurally IDENTICAL blocks — no `hint: undefined` on one path and a
    // missing key on the other, which is what would make replay and live render differently.
    expect(replayed).toStrictEqual(live);
    expectJsonRoundTrips(replayed);
  });

  it('theLiveToPersistedTranslationNeverInventsAnAbsentOptionalFieldTest', () => {
    // The two minimal tables are each other's twin: a frame with no optional field translates into a
    // persisted event with no optional field, and deep equality fails the moment one side grows a
    // `hint: undefined` / `error: undefined` / `reason: undefined` key.
    expect(liveToTimeline(MINIMAL_LIVE.thinking)).toStrictEqual(MINIMAL_TIMELINE.thinking);
    expect(liveToTimeline(MINIMAL_LIVE.tool_done)).toStrictEqual(MINIMAL_TIMELINE.tool_result);
    expect(liveToTimeline(MINIMAL_LIVE.notice)).toStrictEqual(MINIMAL_TIMELINE.notice);
    expect(liveToTimeline(MINIMAL_LIVE.error)).toStrictEqual(MINIMAL_TIMELINE.error);
    expect(liveToTimeline(MINIMAL_LIVE.run_finished)).toStrictEqual(MINIMAL_TIMELINE.run_status);

    // Frames with no per-event persisted counterpart stay null, and `run_finished` loses nothing but
    // the stop reason, which only the server knows.
    expect(liveToTimeline(MINIMAL_LIVE.run_started)).toBeNull();
    expect(liveToTimeline(MINIMAL_LIVE.text_delta)).toBeNull();
    expect(liveToTimeline(MINIMAL_LIVE.tool_start)).toBeNull();
    expectJsonRoundTrips(liveToTimeline(MINIMAL_LIVE.tool_done));
    expectJsonRoundTrips(liveToTimeline(MINIMAL_LIVE.run_finished));
  });
});

describe('notice levels', () => {
  for (const level of NOTICE_LEVELS) {
    it(`noticeAtLevel${capitalise(level)}SurvivesBothPathsTest`, () => {
      const message =
        level === 'error'
          ? 'provider reported: No conversation found with session ID: 6f1c'
          : 'rmqctl 不可用，本次会话已禁用 RocketMQ 工具';

      const live = reduceLiveBlocks([], { type: 'notice', level, message });
      const replayed = foldTimelineBlocks([{ type: 'notice', level, message }]);

      expect(live).toStrictEqual([{ kind: 'notice', level, message }]);
      expect(replayed).toStrictEqual(live);
      expect(liveToTimeline({ type: 'notice', level, message })).toStrictEqual({
        type: 'notice',
        level,
        message,
      });
      expect(coalesceLiveToTimeline([{ type: 'notice', level, message }])).toStrictEqual([
        { type: 'notice', level, message },
      ]);
    });
  }

  it('coversEveryNoticeLevelInTheVocabularyTest', () => {
    expect([...NOTICE_LEVELS].sort()).toEqual(['error', 'info', 'warn']);
  });
});

describe('tool result error field', () => {
  it('keepsTheErrorOfAFailedCallOnBothPathsTest', () => {
    const start: ChatSseEvent = {
      type: 'tool_start',
      tcId: FAILED_RESULT.tcId,
      tool: FAILED_RESULT.tool,
      input: { instanceId: 'nope' },
    };

    const live = reduceAll([start, { type: 'tool_done', ...FAILED_RESULT }]);
    const replayed = foldTimelineBlocks([
      {
        type: 'tool_use',
        tcId: FAILED_RESULT.tcId,
        tool: FAILED_RESULT.tool,
        input: { instanceId: 'nope' },
      },
      { type: 'tool_result', ...FAILED_RESULT },
    ]);

    expect(live).toStrictEqual([
      {
        kind: 'tool',
        tcId: 'toolu_01F',
        tool: 'rmq.topic.list',
        input: { instanceId: 'nope' },
        output: '',
        outputBytes: 0,
        truncated: false,
        durationMs: 88,
        success: false,
        error: 'instance not found: local',
        status: 'done',
      },
    ]);
    expect(replayed).toStrictEqual(live);
    expectJsonRoundTrips(live);
  });

  it('omitsTheErrorKeyOfASuccessfulCallOnBothPathsTest', () => {
    const start: ChatSseEvent = {
      type: 'tool_start',
      tcId: RETRIED_RESULT.tcId,
      tool: RETRIED_RESULT.tool,
      input: {},
    };

    const live = reduceAll([start, { type: 'tool_done', ...RETRIED_RESULT }]);
    const replayed = foldTimelineBlocks([
      { type: 'tool_use', tcId: RETRIED_RESULT.tcId, tool: RETRIED_RESULT.tool, input: {} },
      { type: 'tool_result', ...RETRIED_RESULT },
    ]);

    expect(live).toStrictEqual([
      {
        kind: 'tool',
        tcId: 'toolu_01F',
        tool: 'rmq.topic.list',
        input: {},
        output: '{"items":[]}',
        outputBytes: 13,
        truncated: false,
        durationMs: 140,
        success: true,
        status: 'done',
      },
    ]);
    expect(Object.keys(live[0])).not.toContain('error');
    expect(replayed).toStrictEqual(live);
    expectJsonRoundTrips(replayed);
  });

  it('dropsTheStaleErrorWhenTheSameCallIsRetriedAndSucceedsTest', () => {
    // A retry reuses the tcId, so `withToolResult` fills the card that already carries the failure.
    // The absent `error` on the second frame must REMOVE the key, not leave the previous one behind:
    // a green card that still prints a red error line is worse than no card.
    const blocks = reduceAll([
      { type: 'tool_start', tcId: FAILED_RESULT.tcId, tool: FAILED_RESULT.tool, input: {} },
      { type: 'tool_done', ...FAILED_RESULT },
      { type: 'tool_done', ...RETRIED_RESULT },
    ]);

    expect(blocks).toHaveLength(1);
    expect(Object.keys(blocks[0])).not.toContain('error');
    expect(blocks[0]).toStrictEqual({
      kind: 'tool',
      tcId: 'toolu_01F',
      tool: 'rmq.topic.list',
      input: {},
      output: '{"items":[]}',
      outputBytes: 13,
      truncated: false,
      durationMs: 140,
      success: true,
      status: 'done',
    });
    expectJsonRoundTrips(blocks);
  });
});

describe('run terminal vocabulary', () => {
  it('runFinishedAtEveryStatusRendersNothingAndPersistsAReasonlessRunStatusTest', () => {
    const blocks: RenderBlock[] = [{ kind: 'text', text: '部分回答' }];

    for (const status of RUN_STATUSES) {
      const event: ChatSseEvent = { type: 'run_finished', runId: 41, status, durationMs: 8123 };

      // Same reference back: a run-level frame renders nothing, so the caller can skip a re-render.
      expect(reduceLiveBlocks(blocks, event), `run_finished ${status}`).toBe(blocks);
      expect(coalesceLiveToTimeline([event]), `persisted twin of ${status}`).toStrictEqual([
        { type: 'run_status', status },
      ]);
    }

    expect(RUN_STATUSES).toHaveLength(5);
  });

  it('everyStatusAndReasonCombinationStampsTheBubbleWithoutRenderingABlockTest', () => {
    // The contract does not restrict which reason may accompany which status — only the run row does
    // (QUEUED/RUNNING have none). Folding must not care, so every pair is exercised.
    let combinations = 0;

    for (const status of RUN_STATUSES) {
      for (const reason of STOP_REASONS) {
        const bubbles = groupIntoBubbles([item({ type: 'run_status', status, reason })]);

        expect(bubbles, `${status}/${reason}`).toStrictEqual([
          {
            role: 'assistant',
            blocks: [],
            turn: 3,
            createdAt: '2026-09-15T10:21:49',
            runStatus: status,
          },
        ]);
        // `reason` is run metadata: it never becomes a block and never grows a key on the bubble.
        expect(Object.keys(bubbles[0]), `${status}/${reason}`).not.toContain('reason');
        expectJsonRoundTrips(bubbles);
        combinations += 1;
      }
    }

    expect(combinations).toBe(RUN_STATUSES.length * STOP_REASONS.length);
    expect(combinations).toBe(40);
    expect(STOP_REASONS).toHaveLength(8);
  });

  it('runStatusWithoutAReasonStampsTheStatusAndClosesTheBubbleTest', () => {
    for (const status of RUN_STATUSES) {
      const bubbles = groupIntoBubbles([
        item({ type: 'user', text: '查看集群状态' }, 1),
        item({ type: 'text', text: '集群当前有 2 个 broker。' }, 2),
        item({ type: 'run_status', status }, 3),
        item({ type: 'user', text: '那堆积呢' }, 4),
      ]);

      expect(bubbles, `run_status ${status}`).toHaveLength(3);
      expect(bubbles[1].runStatus, `run_status ${status}`).toBe(status);
      expect(Object.keys(bubbles[1]), `run_status ${status}`).not.toContain('reason');
      // A terminal status closes the run, so the next user row opens a fresh bubble pair.
      expect(bubbles[2], `run_status ${status}`).toStrictEqual({
        role: 'user',
        blocks: [{ kind: 'text', text: '那堆积呢' }],
        turn: 3,
        createdAt: '2026-09-15T10:21:49',
      });
    }
  });

  it('aTerminalRunStatusWithNoContentStillYieldsAStoppedBubbleTest', () => {
    // The run was stopped before it emitted a single block: history must still be able to say 已停止
    // without joining rmq_ai_run, which is the whole reason run_status is persisted.
    const bubbles = groupIntoBubbles([
      item({ type: 'user', text: '查看集群状态' }, 1),
      item({ type: 'run_status', status: 'STOPPED', reason: 'USER_STOP' }, 2),
    ]);

    expect(bubbles).toStrictEqual([
      {
        role: 'user',
        blocks: [{ kind: 'text', text: '查看集群状态' }],
        turn: 3,
        createdAt: '2026-09-15T10:21:49',
      },
      {
        role: 'assistant',
        blocks: [],
        turn: 3,
        createdAt: '2026-09-15T10:21:49',
        runStatus: 'STOPPED',
      },
    ]);
  });
});

describe('a tool_result replayed without its tool_use', () => {
  it('yieldsADoneCardWhoseInputIsNullOnBothPathsTest', () => {
    // `ToolBlock.input` is `unknown` rather than `Record<string, unknown>` for exactly this case: a
    // paging window that opens mid-run, or a reconnect that joined after tool_start, delivers the
    // result with no start in sight. The output must still be shown, with input reported as unknown
    // (`null`) rather than faked as `{}` — an empty argument list and an unavailable one mean
    // different things to an operator.
    const orphaned: TimelineEvent = { type: 'tool_result', ...RETRIED_RESULT };
    const replayed = foldTimelineBlocks([orphaned]);
    const live = reduceAll([{ type: 'tool_done', ...RETRIED_RESULT }]);

    const expected: RenderBlock = {
      kind: 'tool',
      tcId: 'toolu_01F',
      tool: 'rmq.topic.list',
      input: null,
      output: '{"items":[]}',
      outputBytes: 13,
      truncated: false,
      durationMs: 140,
      success: true,
      status: 'done',
    };

    expect(replayed).toStrictEqual([expected]);
    expect(live).toStrictEqual(replayed);
    // "We cannot see the arguments" is not "this tool takes no arguments": the two cards differ only
    // in `input`, and a renderer that collapsed null into `{}` would claim the latter.
    expect(replayed[0]).not.toStrictEqual({ ...expected, input: {} });
    expectJsonRoundTrips(replayed);
  });

  it('keepsTheErrorOfAnOrphanedFailedResultTest', () => {
    const blocks = foldTimelineBlocks([{ type: 'tool_result', ...FAILED_RESULT }]);

    expect(blocks).toStrictEqual([
      {
        kind: 'tool',
        tcId: 'toolu_01F',
        tool: 'rmq.topic.list',
        input: null,
        output: '',
        outputBytes: 0,
        truncated: false,
        durationMs: 88,
        success: false,
        error: 'instance not found: local',
        status: 'done',
      },
    ]);
  });
});
