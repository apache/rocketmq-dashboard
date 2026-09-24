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
  LiveEventTypesComplete,
  NoticeLevel,
  TimelineEvent,
  TimelineEventTypesComplete,
} from './aiEvents';
import { foldTimelineBlocks } from '../pages/ai/render/foldTimeline';
import { reduceLiveBlocks } from '../pages/ai/render/reduceLive';
import type { RenderBlock } from '../pages/ai/render/blocks';

/**
 * The TypeScript half of the cross-language drift alarm. The Java `AiEventContractTest` reads the
 * very same fixture; if either side renames, drops or adds an event type or field, one of the two
 * suites fails.
 *
 * Every fixture entry carries TWO examples and one declaration, and this file asserts all three:
 * - `example` — the richest legal wire form.
 * - `absent` — the optional fields, the ones the backend's `@JsonInclude(NON_NULL)` drops when it has
 *   nothing to say. A single example cannot show an absence, which is why this list exists: it is the
 *   only place the contract says "the client must tolerate this key being missing".
 * - `exampleMinimal` — the leanest legal wire form, carrying exactly the required fields. For a type
 *   with no optional field it doubles as a second example widening value coverage, which is how the
 *   fixture reaches `notice.level = "error"`, a completed run with no `reason`, and a no-argument tool
 *   call whose `input` is `{}` rather than null.
 *
 * The two closure rules the Java test asserts by reflection are asserted here against the `*_FIELDS`
 * tables: `fields(type) == keys(example) U absent` and `keys(exampleMinimal) == fields(type) - absent`.
 * Those tables are `Record<keyof Event, true>` literals, so the compiler — not this file — keeps them
 * complete: a field added to an interface in `aiEvents.ts` without being declared in the fixture is a
 * `tsc -b` failure in CI, and a field the fixture declares that the interface dropped is one too.
 *
 * The `*_EXAMPLES` / `*_MINIMALS` tables are the value-level bridge. Each literal is type-checked
 * against the mirror (so an illegal `level`, `status` or `source` spelling cannot compile) and is then
 * asserted deep-equal to the fixture at runtime (so a fixture value the mirror cannot express fails
 * here). Neither half alone catches both directions.
 */

// @types/node is deliberately not a dependency of the web package, so node:fs is reached through a
// dynamic import with an opaque specifier plus a local structural type, and `declare const` types
// the __dirname that vitest injects without emitting any code.
declare const __dirname: string;

interface NodeFs {
  readFileSync(path: string, encoding: string): string;
}

/** web/src/api -> repo root is three levels up; the fixture is shared with the Java suite. */
const FIXTURE_PATH = `${__dirname}/../../../server/src/test/resources/ai/ai-event-contract.json`;

interface ContractEntry {
  type: string;
  example: Record<string, unknown>;
  exampleMinimal: Record<string, unknown>;
  absent: string[];
}

interface AiEventContract {
  version: number;
  comment?: string;
  entryShape?: string;
  live: ContractEntry[];
  timeline: ContractEntry[];
}

async function readContract(): Promise<AiEventContract> {
  const fs = (await import('node:fs' as string)) as unknown as NodeFs;
  return JSON.parse(fs.readFileSync(FIXTURE_PATH, 'utf8')) as AiEventContract;
}

function typesOf(entries: ContractEntry[]): string[] {
  return entries.map((entry) => entry.type);
}

function reduceAll(events: ChatSseEvent[]): RenderBlock[] {
  return events.reduce<RenderBlock[]>((blocks, event) => reduceLiveBlocks(blocks, event), []);
}

/** The field names of one example, minus the discriminator that is not a field of the payload. */
function payloadFields(example: Record<string, unknown>): string[] {
  return Object.keys(example).filter((key) => key !== 'type');
}

/**
 * Assert the value carries no `undefined`-valued key anywhere. JSON drops those, so a lossless
 * round-trip is exactly the backend's `@JsonInclude(NON_NULL)` promise restated on this side of the
 * wire — and `toStrictEqual` is what tells `{ hint: undefined }` from `{}`.
 */
function expectJsonRoundTrips(value: unknown): void {
  expect(JSON.parse(JSON.stringify(value))).toStrictEqual(value);
}

// ─── Field tables: the compile-time closure the fixture is checked against ──

type LiveOf<T extends ChatSseEvent['type']> = Extract<ChatSseEvent, { type: T }>;
type TimelineOf<T extends TimelineEvent['type']> = Extract<TimelineEvent, { type: T }>;

/**
 * Every field of every live frame. Written as `Record<keyof ..., true>` so the compiler rejects both a
 * missing and an unknown key; `Object.keys` then yields the runtime list the closure rules use.
 */
const LIVE_FIELDS: { [T in ChatSseEvent['type']]: Record<keyof LiveOf<T>, true> } = {
  run_started: { type: true, runId: true, conversationId: true, title: true, turn: true },
  text_delta: { type: true, content: true },
  thinking: { type: true, content: true, source: true },
  tool_start: { type: true, tcId: true, tool: true, input: true },
  tool_done: {
    type: true,
    tcId: true,
    tool: true,
    output: true,
    outputBytes: true,
    truncated: true,
    durationMs: true,
    success: true,
    error: true,
  },
  notice: { type: true, level: true, message: true },
  error: { type: true, code: true, message: true, hint: true },
  run_finished: { type: true, runId: true, status: true, durationMs: true },
};

/** Every field of every persisted event; the same compile-time closure as {@link LIVE_FIELDS}. */
const TIMELINE_FIELDS: { [T in TimelineEvent['type']]: Record<keyof TimelineOf<T>, true> } = {
  user: { type: true, text: true, enhancedPrompt: true },
  thinking: { type: true, text: true, source: true },
  text: { type: true, text: true },
  tool_use: { type: true, tcId: true, tool: true, input: true },
  tool_result: {
    type: true,
    tcId: true,
    tool: true,
    output: true,
    outputBytes: true,
    truncated: true,
    success: true,
    durationMs: true,
    error: true,
  },
  notice: { type: true, level: true, message: true },
  error: { type: true, code: true, message: true, hint: true },
  run_status: { type: true, status: true, reason: true },
};

/** The three notice levels; `Record<NoticeLevel, true>` keeps the table complete by construction. */
const NOTICE_LEVELS: Record<NoticeLevel, true> = { info: true, warn: true, error: true };

// ─── Typed mirrors of the fixture examples ──────────────────────

const LIVE_EXAMPLES: Record<ChatSseEvent['type'], ChatSseEvent> = {
  run_started: {
    type: 'run_started',
    runId: 41,
    conversationId: 7,
    title: '查看集群状态',
    turn: 3,
  },
  text_delta: { type: 'text_delta', content: '集群当前有 ' },
  thinking: { type: 'thinking', content: '用户想确认堆积，先查 topic 路由', source: 'model' },
  tool_start: {
    type: 'tool_start',
    tcId: 'toolu_01A',
    tool: 'rmq.topic.list',
    input: { instanceId: 'open-source-local' },
  },
  tool_done: {
    type: 'tool_done',
    tcId: 'toolu_01A',
    tool: 'rmq.topic.list',
    output: '{"items":[{"name":"StudioTest"}]}',
    outputBytes: 33,
    truncated: false,
    durationMs: 212,
    success: true,
  },
  notice: { type: 'notice', level: 'warn', message: 'rmqctl 不可用，本次会话已禁用 RocketMQ 工具' },
  error: {
    type: 'error',
    code: 'llm.provider.timeout',
    message: 'claude CLI stream timed out after 300s',
    hint: 'Retry with a shorter prompt.',
  },
  run_finished: { type: 'run_finished', runId: 41, status: 'COMPLETED', durationMs: 8123 },
};

/**
 * The leanest legal form of each live frame. That `tool_done` keeps `output`, `outputBytes` and
 * `durationMs` is a statement about the contract, not an oversight: the client types them required, so
 * the only field it must tolerate missing is `error`.
 */
const LIVE_MINIMALS: Record<ChatSseEvent['type'], ChatSseEvent> = {
  run_started: {
    type: 'run_started',
    runId: 41,
    conversationId: 7,
    title: '查看集群状态',
    turn: 3,
  },
  text_delta: { type: 'text_delta', content: '集群当前有 ' },
  thinking: { type: 'thinking', content: '用户想确认堆积，先查 topic 路由', source: 'model' },
  // A no-argument call: `input` is `{}` because the backend guarantees an object rather than null.
  tool_start: {
    type: 'tool_start',
    tcId: 'toolu_01B',
    tool: 'rmq.instance.capabilities',
    input: {},
  },
  tool_done: {
    type: 'tool_done',
    tcId: 'toolu_01B',
    tool: 'rmq.instance.capabilities',
    output: '{"rmqctlAvailable":true}',
    outputBytes: 24,
    truncated: false,
    durationMs: 8,
    success: true,
  },
  // The error level, which the single `notice` example per section cannot also carry: a run survives
  // this, which is why it is a notice and not an `error` event.
  notice: {
    type: 'notice',
    level: 'error',
    message: 'provider reported: No conversation found with session ID: 6f1c',
  },
  error: {
    type: 'error',
    code: 'llm.provider.timeout',
    message: 'claude CLI stream timed out after 300s',
  },
  run_finished: { type: 'run_finished', runId: 41, status: 'COMPLETED', durationMs: 8123 },
};

const TIMELINE_EXAMPLES: Record<TimelineEvent['type'], TimelineEvent> = {
  user: {
    type: 'user',
    text: '查看集群状态',
    enhancedPrompt: '请列出当前集群的 broker 与 topic 概况',
  },
  thinking: { type: 'thinking', text: '用户想确认堆积，先查 topic 路由', source: 'model' },
  text: { type: 'text', text: '集群当前有 2 个 broker。' },
  tool_use: {
    type: 'tool_use',
    tcId: 'toolu_01A',
    tool: 'rmq.topic.list',
    input: { instanceId: 'open-source-local' },
  },
  tool_result: {
    type: 'tool_result',
    tcId: 'toolu_01A',
    tool: 'rmq.topic.list',
    output: '{"items":[{"name":"StudioTest"}]}',
    outputBytes: 33,
    truncated: false,
    success: true,
    durationMs: 212,
  },
  notice: { type: 'notice', level: 'info', message: '本次运行已恢复上游会话' },
  error: {
    type: 'error',
    code: 'llm.provider.error_max_turns',
    message: 'agent reached the maximum turn count',
    hint: 'Split the request.',
  },
  run_status: { type: 'run_status', status: 'STOPPED', reason: 'USER_STOP' },
};

const TIMELINE_MINIMALS: Record<TimelineEvent['type'], TimelineEvent> = {
  // The common case: prompt enhancement off, so nothing was rewritten and there is nothing to show.
  user: { type: 'user', text: '查看集群状态' },
  thinking: { type: 'thinking', text: '用户想确认堆积，先查 topic 路由', source: 'model' },
  text: { type: 'text', text: '集群当前有 2 个 broker。' },
  tool_use: { type: 'tool_use', tcId: 'toolu_01B', tool: 'rmq.instance.capabilities', input: {} },
  tool_result: {
    type: 'tool_result',
    tcId: 'toolu_01B',
    tool: 'rmq.instance.capabilities',
    output: '{"rmqctlAvailable":true}',
    outputBytes: 24,
    truncated: false,
    success: true,
    durationMs: 8,
  },
  notice: { type: 'notice', level: 'error', message: 'upstream API error status: 529' },
  error: {
    type: 'error',
    code: 'llm.provider.error_max_turns',
    message: 'agent reached the maximum turn count',
  },
  // A run that completed normally has no stop reason, so `reason` is simply not on the wire.
  run_status: { type: 'run_status', status: 'COMPLETED' },
};

/** The done card the no-argument call of the two minimal examples renders, on either path. */
const MINIMAL_TOOL_CARD: RenderBlock = {
  kind: 'tool',
  tcId: 'toolu_01B',
  tool: 'rmq.instance.capabilities',
  input: {},
  output: '{"rmqctlAvailable":true}',
  outputBytes: 24,
  truncated: false,
  durationMs: 8,
  success: true,
  status: 'done',
};

/**
 * One fixture entry's declaration, checked against the field table of its type. Shared by both
 * sections so the two cannot drift apart in how strictly they are read.
 */
function assertEntryDeclaresItsFields(where: string, entry: ContractEntry, fields: string[]): void {
  expect(entry.example.type, `${where}: the example must carry its discriminator`).toBe(entry.type);
  expect(
    entry.exampleMinimal.type,
    `${where}: the minimal example must carry its discriminator`,
  ).toBe(entry.type);
  expect(Array.isArray(entry.absent), `${where}: absent must be an array`).toBe(true);
  expect(new Set(entry.absent).size, `${where}: absent must not declare the same field twice`).toBe(
    entry.absent.length,
  );

  const exampleFields = payloadFields(entry.example);
  const minimalFields = payloadFields(entry.exampleMinimal);
  for (const name of [...exampleFields, ...minimalFields, ...entry.absent]) {
    expect(fields, `${where}: ${name} must be a field of ${entry.type}`).toContain(name);
  }

  // Closure 1: between them the example and absent[] account for every field of the type, the
  // discriminator included. Without it a field nobody declared would simply never be exercised, and
  // the client would never learn about it.
  expect(
    [...new Set(['type', ...exampleFields, ...entry.absent])].sort(),
    `${where}: example plus absent must cover every field of ${entry.type}`,
  ).toEqual([...fields].sort());

  // Closure 2: the minimal example is exactly the required-field set, so a required field cannot be
  // dropped from it unnoticed.
  expect(
    [...minimalFields].sort(),
    `${where}: exampleMinimal must carry exactly the required fields of ${entry.type}`,
  ).toEqual(fields.filter((field) => field !== 'type' && !entry.absent.includes(field)).sort());
}

describe('aiEvents contract', () => {
  it('liveEventTypesMatchFixtureTest', async () => {
    const contract = await readContract();
    const fixtureTypes = typesOf(contract.live);

    expect(new Set(fixtureTypes).size).toBe(fixtureTypes.length);
    expect(new Set(LIVE_EVENT_TYPES).size).toBe(LIVE_EVENT_TYPES.length);
    expect([...fixtureTypes].sort()).toEqual([...LIVE_EVENT_TYPES].sort());
  });

  it('timelineEventTypesMatchFixtureTest', async () => {
    const contract = await readContract();
    const fixtureTypes = typesOf(contract.timeline);

    expect(new Set(fixtureTypes).size).toBe(fixtureTypes.length);
    expect(new Set(TIMELINE_EVENT_TYPES).size).toBe(TIMELINE_EVENT_TYPES.length);
    expect([...fixtureTypes].sort()).toEqual([...TIMELINE_EVENT_TYPES].sort());
  });

  it('eventTuplesCoverEveryUnionMemberTest', async () => {
    // Compile-time half of the alarm: these two assignments only typecheck while the `as const`
    // tuples list every member of their union. A missing member turns the type into `never`.
    const liveComplete: LiveEventTypesComplete = true;
    const timelineComplete: TimelineEventTypesComplete = true;

    expect(liveComplete && timelineComplete).toBe(true);
    // Version 2 added `exampleMinimal` and `absent` to every entry; version 1 carried one example.
    expect((await readContract()).version).toBe(2);
  });

  it('fixtureExamplesAreSelfDescribingTest', async () => {
    const contract = await readContract();
    const knownTypes: string[] = [...LIVE_EVENT_TYPES, ...TIMELINE_EVENT_TYPES];

    for (const entry of [...contract.live, ...contract.timeline]) {
      expect(entry.example.type, `example for ${entry.type}`).toBe(entry.type);
      expect(entry.exampleMinimal.type, `minimal example for ${entry.type}`).toBe(entry.type);
      expect(knownTypes, `known type ${entry.type}`).toContain(entry.type);
    }
  });

  it('fixtureEntriesDeclareTheOptionalFieldsOfEveryTypeTest', async () => {
    const contract = await readContract();

    for (const entry of contract.live) {
      assertEntryDeclaresItsFields(
        `live/${entry.type}`,
        entry,
        Object.keys(LIVE_FIELDS[entry.type as ChatSseEvent['type']]),
      );
    }
    for (const entry of contract.timeline) {
      assertEntryDeclaresItsFields(
        `timeline/${entry.type}`,
        entry,
        Object.keys(TIMELINE_FIELDS[entry.type as TimelineEvent['type']]),
      );
    }

    // Spelled out so a regression names the fields instead of showing a set diff: these four are the
    // whole optional surface of the contract, and every other field is required on both paths.
    expect(contract.live.map((entry) => [entry.type, entry.absent])).toEqual([
      ['run_started', []],
      ['text_delta', []],
      ['thinking', []],
      ['tool_start', []],
      ['tool_done', ['error']],
      ['notice', []],
      ['error', ['hint']],
      ['run_finished', []],
    ]);
    expect(contract.timeline.map((entry) => [entry.type, entry.absent])).toEqual([
      ['user', ['enhancedPrompt']],
      ['thinking', []],
      ['text', []],
      ['tool_use', []],
      ['tool_result', ['error']],
      ['notice', []],
      ['error', ['hint']],
      ['run_status', ['reason']],
    ]);
  });

  it('fixtureExamplesMatchTheirTypedMirrorsTest', async () => {
    const contract = await readContract();

    // Deep equality against a literal the compiler has already checked against aiEvents.ts: the
    // fixture cannot carry a value the mirror cannot express, and the mirror cannot drift from the
    // fixture without this failing.
    for (const entry of contract.live) {
      const type = entry.type as ChatSseEvent['type'];
      expect(entry.example, `live/${entry.type} example`).toStrictEqual(LIVE_EXAMPLES[type]);
      expect(entry.exampleMinimal, `live/${entry.type} exampleMinimal`).toStrictEqual(
        LIVE_MINIMALS[type],
      );
    }
    for (const entry of contract.timeline) {
      const type = entry.type as TimelineEvent['type'];
      expect(entry.example, `timeline/${entry.type} example`).toStrictEqual(
        TIMELINE_EXAMPLES[type],
      );
      expect(entry.exampleMinimal, `timeline/${entry.type} exampleMinimal`).toStrictEqual(
        TIMELINE_MINIMALS[type],
      );
    }

    // A mirror that "satisfies" an optional field with `hint: undefined` compiles but is not the
    // absence the backend sends; JSON has no undefined, so this is the check that tells them apart.
    for (const type of LIVE_EVENT_TYPES) {
      expectJsonRoundTrips(LIVE_EXAMPLES[type]);
      expectJsonRoundTrips(LIVE_MINIMALS[type]);
    }
    for (const type of TIMELINE_EVENT_TYPES) {
      expectJsonRoundTrips(TIMELINE_EXAMPLES[type]);
      expectJsonRoundTrips(TIMELINE_MINIMALS[type]);
    }
  });

  it('fixtureNoticeLevelsCoverTheWholeVocabularyTest', async () => {
    const contract = await readContract();
    const levels = new Set<string>();

    for (const entry of [...contract.live, ...contract.timeline]) {
      if (entry.type !== 'notice') continue;
      levels.add(entry.example.level as string);
      levels.add(entry.exampleMinimal.level as string);
    }

    // `error` is a notice level rather than an `error` event because the run survives it, and the
    // single example per section cannot show three levels — the minimal example carries the third.
    expect([...levels].sort(), 'the fixture must show every notice level').toEqual(
      Object.keys(NOTICE_LEVELS).sort(),
    );
    expect([...levels].sort()).toEqual(['error', 'info', 'warn']);
  });

  it('liveExamplesReduceWithoutThrowingTest', async () => {
    const contract = await readContract();

    for (const entry of contract.live) {
      const event = entry.example as unknown as ChatSseEvent;
      expect(() => reduceLiveBlocks([], event), `reduce ${entry.type}`).not.toThrow();
      expect(Array.isArray(reduceLiveBlocks([], event))).toBe(true);
    }

    // The whole live sequence in fixture order must fold too, not just each frame in isolation.
    const events = contract.live.map((entry) => entry.example as unknown as ChatSseEvent);
    expect(() => reduceAll(events)).not.toThrow();
  });

  it('timelineExamplesFoldWithoutThrowingTest', async () => {
    const contract = await readContract();

    for (const entry of contract.timeline) {
      const event = entry.example as unknown as TimelineEvent;
      expect(() => foldTimelineBlocks([event]), `fold ${entry.type}`).not.toThrow();
    }

    const events = contract.timeline.map((entry) => entry.example as unknown as TimelineEvent);
    expect(() => foldTimelineBlocks(events)).not.toThrow();
  });

  it('minimalLiveExamplesReduceWithoutThrowingTest', async () => {
    const contract = await readContract();

    // The absent-optional-field half of the contract: every leanest form must still be a frame the
    // reducer accepts, alone and in sequence. A client that reads `event.hint` unguarded, or that
    // assumes a tool result always carries `error`, throws here rather than in production.
    for (const entry of contract.live) {
      const event = entry.exampleMinimal as unknown as ChatSseEvent;
      expect(() => reduceLiveBlocks([], event), `reduce minimal ${entry.type}`).not.toThrow();
      expect(Array.isArray(reduceLiveBlocks([], event))).toBe(true);
    }

    const events = contract.live.map((entry) => entry.exampleMinimal as unknown as ChatSseEvent);
    expect(() => reduceAll(events)).not.toThrow();
  });

  it('minimalTimelineExamplesFoldWithoutThrowingTest', async () => {
    const contract = await readContract();

    for (const entry of contract.timeline) {
      const event = entry.exampleMinimal as unknown as TimelineEvent;
      expect(() => foldTimelineBlocks([event]), `fold minimal ${entry.type}`).not.toThrow();
    }

    const events = contract.timeline.map(
      (entry) => entry.exampleMinimal as unknown as TimelineEvent,
    );
    expect(() => foldTimelineBlocks(events)).not.toThrow();
  });

  it('liveFixtureReducesToFieldExactBlocksTest', async () => {
    const contract = await readContract();
    const events = contract.live.map((entry) => entry.example as unknown as ChatSseEvent);

    // Field names are asserted literally: a rename on the Java side (outputBytes -> bytes, say)
    // shows up here as a missing value rather than as a silent undefined in the UI.
    expect(reduceAll(events)).toStrictEqual([
      { kind: 'text', text: '集群当前有 ' },
      { kind: 'thinking', text: '用户想确认堆积，先查 topic 路由', source: 'model' },
      {
        kind: 'tool',
        tcId: 'toolu_01A',
        tool: 'rmq.topic.list',
        input: { instanceId: 'open-source-local' },
        output: '{"items":[{"name":"StudioTest"}]}',
        outputBytes: 33,
        truncated: false,
        durationMs: 212,
        success: true,
        status: 'done',
      },
      { kind: 'notice', level: 'warn', message: 'rmqctl 不可用，本次会话已禁用 RocketMQ 工具' },
      {
        kind: 'error',
        code: 'llm.provider.timeout',
        message: 'claude CLI stream timed out after 300s',
        hint: 'Retry with a shorter prompt.',
      },
    ] satisfies RenderBlock[]);
  });

  it('timelineFixtureFoldsToFieldExactBlocksTest', async () => {
    const contract = await readContract();
    const events = contract.timeline.map((entry) => entry.example as unknown as TimelineEvent);
    const blocks = foldTimelineBlocks(events);

    expect(blocks).toStrictEqual([
      // `user` renders nothing here: the caller builds user bubbles separately.
      { kind: 'thinking', text: '用户想确认堆积，先查 topic 路由', source: 'model' },
      { kind: 'text', text: '集群当前有 2 个 broker。' },
      {
        kind: 'tool',
        tcId: 'toolu_01A',
        tool: 'rmq.topic.list',
        input: { instanceId: 'open-source-local' },
        output: '{"items":[{"name":"StudioTest"}]}',
        outputBytes: 33,
        truncated: false,
        durationMs: 212,
        success: true,
        status: 'done',
      },
      { kind: 'notice', level: 'info', message: '本次运行已恢复上游会话' },
      {
        kind: 'error',
        code: 'llm.provider.error_max_turns',
        message: 'agent reached the maximum turn count',
        hint: 'Split the request.',
      },
      // `run_status` renders nothing: it is run metadata carried by groupIntoBubbles.
    ] satisfies RenderBlock[]);

    // The tool card is the one block both vocabularies express field-for-field, so it must come out
    // identical whether it was streamed live or replayed from rmq_ai_event.
    const liveEvents = contract.live.map((entry) => entry.example as unknown as ChatSseEvent);
    expect(blocks[2]).toStrictEqual(reduceAll(liveEvents)[2]);
  });

  it('minimalLiveFixtureReducesToFieldExactBlocksTest', async () => {
    const contract = await readContract();
    const events = contract.live.map((entry) => entry.exampleMinimal as unknown as ChatSseEvent);

    expect(reduceAll(events)).toStrictEqual([
      { kind: 'text', text: '集群当前有 ' },
      { kind: 'thinking', text: '用户想确认堆积，先查 topic 路由', source: 'model' },
      MINIMAL_TOOL_CARD,
      {
        kind: 'notice',
        level: 'error',
        message: 'provider reported: No conversation found with session ID: 6f1c',
      },
      // No `hint` key at all, rather than `hint: undefined`: the renderer must be able to ask
      // `'hint' in block` and get the same answer the backend meant when it omitted the field.
      {
        kind: 'error',
        code: 'llm.provider.timeout',
        message: 'claude CLI stream timed out after 300s',
      },
    ] satisfies RenderBlock[]);
  });

  it('minimalTimelineFixtureFoldsToFieldExactBlocksTest', async () => {
    const contract = await readContract();
    const events = contract.timeline.map(
      (entry) => entry.exampleMinimal as unknown as TimelineEvent,
    );
    const blocks = foldTimelineBlocks(events);

    expect(blocks).toStrictEqual([
      // `user` without `enhancedPrompt` still renders nothing here, exactly like the full one.
      { kind: 'thinking', text: '用户想确认堆积，先查 topic 路由', source: 'model' },
      { kind: 'text', text: '集群当前有 2 个 broker。' },
      MINIMAL_TOOL_CARD,
      { kind: 'notice', level: 'error', message: 'upstream API error status: 529' },
      {
        kind: 'error',
        code: 'llm.provider.error_max_turns',
        message: 'agent reached the maximum turn count',
      },
      // `run_status` with no reason renders nothing, exactly like one that carries a reason.
    ] satisfies RenderBlock[]);

    // With every optional field absent on both sides the two vocabularies must still converge on a
    // structurally identical card — the property the fixture cannot state on its own.
    const liveEvents = contract.live.map(
      (entry) => entry.exampleMinimal as unknown as ChatSseEvent,
    );
    expect(blocks[2]).toStrictEqual(reduceAll(liveEvents)[2]);
  });

  it('minimalExamplesNeverInventAnAbsentOptionalFieldTest', async () => {
    const contract = await readContract();
    const liveBlocks = reduceAll(
      contract.live.map((entry) => entry.exampleMinimal as unknown as ChatSseEvent),
    );
    const timelineBlocks = foldTimelineBlocks(
      contract.timeline.map((entry) => entry.exampleMinimal as unknown as TimelineEvent),
    );

    // A block that carries `hint: undefined` survives `JSON.stringify` but fails `toStrictEqual`,
    // which is the point: this is the client-side statement of @JsonInclude(NON_NULL).
    expectJsonRoundTrips(liveBlocks);
    expectJsonRoundTrips(timelineBlocks);

    expect(Object.keys(liveBlocks[4]), 'a hint-less error keeps no hint key').not.toContain('hint');
    expect(Object.keys(liveBlocks[2]), 'a successful call keeps no error key').not.toContain(
      'error',
    );
    expect(Object.keys(timelineBlocks[4]), 'a hint-less error keeps no hint key').not.toContain(
      'hint',
    );
    expect(Object.keys(timelineBlocks[2]), 'a successful call keeps no error key').not.toContain(
      'error',
    );

    // The leanest forms are also the ones an operator sees most often, so both vocabularies must
    // agree on them block for block. The fixture gives `thinking` the same text on both paths, so the
    // two blocks must come out identical; the text blocks are deliberately NOT compared here, because
    // a live `text_delta` is one fragment while a persisted `text` is the coalesced whole.
    expect(timelineBlocks[0]).toStrictEqual(liveBlocks[1]);
  });
});
