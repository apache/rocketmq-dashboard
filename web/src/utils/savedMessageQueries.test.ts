/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  SAVED_MESSAGE_QUERIES_STORAGE_KEY,
  SAVED_MESSAGE_QUERY_LIMIT,
  addSavedMessageQuery,
  describeSavedMessageQuery,
  listSavedMessageQueries,
  loadSavedMessageQueries,
  parseSavedMessageQueries,
  persistSavedMessageQueries,
  removeSavedMessageQuery,
  renameSavedMessageQuery,
  type SavedMessageQuery,
  type SavedMessageQueryDraft,
} from './savedMessageQueries';

const topicDraft: SavedMessageQueryDraft = {
  instanceId: 'instance-a',
  mode: 'topic',
  topic: 'orders',
  startTime: 1_700_000_000_000,
  endTime: 1_700_003_600_000,
};

const saved = (overrides: Partial<SavedMessageQuery> = {}): SavedMessageQuery => ({
  ...topicDraft,
  id: 'query-1',
  name: 'Order investigation',
  createdAt: 100,
  updatedAt: 100,
  ...overrides,
});

describe('savedMessageQueries', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('adds and reloads a normalized topic query', () => {
    const result = addSavedMessageQuery([], '  Order investigation  ', topicDraft, 100, () => 0.5);

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.query).toMatchObject({
      name: 'Order investigation',
      instanceId: 'instance-a',
      mode: 'topic',
      topic: 'orders',
      createdAt: 100,
      updatedAt: 100,
    });
    expect(loadSavedMessageQueries()).toEqual(result.queries);
  });

  it('normalizes identifiers and mode-specific values before saving', () => {
    const keyResult = addSavedMessageQuery(
      [],
      ' Key lookup ',
      { instanceId: ' instance-a ', mode: 'key', topic: ' orders ', key: ' ORDER-1 ' },
      200,
      () => 0.25,
    );
    expect(keyResult.ok).toBe(true);
    if (!keyResult.ok) return;
    expect(keyResult.query).toMatchObject({
      instanceId: 'instance-a',
      topic: 'orders',
      key: 'ORDER-1',
    });

    const messageIdResult = addSavedMessageQuery(
      keyResult.queries,
      'Message lookup',
      { instanceId: 'instance-a', mode: 'msgid', topic: 'orders', msgId: ' MID-1 ' },
      300,
      () => 0.75,
    );
    expect(messageIdResult.ok).toBe(true);
    if (!messageIdResult.ok) return;
    expect(messageIdResult.query?.msgId).toBe('MID-1');
    expect(messageIdResult.queries.map((query) => query.name)).toEqual([
      'Message lookup',
      'Key lookup',
    ]);
  });

  it.each([
    ['', topicDraft],
    ['Missing instance', { ...topicDraft, instanceId: ' ' }],
    ['Missing topic', { ...topicDraft, topic: '' }],
    ['Missing key', { instanceId: 'instance-a', mode: 'key', topic: 'orders' }],
    ['Missing id', { instanceId: 'instance-a', mode: 'msgid', topic: 'orders' }],
    [
      'Reversed range',
      { instanceId: 'instance-a', mode: 'topic', topic: 'orders', startTime: 20, endTime: 10 },
    ],
  ])('rejects invalid drafts: %s', (name, draft) => {
    expect(addSavedMessageQuery([], name, draft as SavedMessageQueryDraft)).toEqual({
      ok: false,
      reason: 'invalid',
    });
  });

  it('treats names as duplicates only inside the same instance', () => {
    const existing = [saved()];
    expect(addSavedMessageQuery(existing, 'order investigation', topicDraft)).toEqual({
      ok: false,
      reason: 'duplicate',
    });

    const otherInstance = addSavedMessageQuery(
      existing,
      'order investigation',
      { ...topicDraft, instanceId: 'instance-b' },
      500,
      () => 0.2,
    );
    expect(otherInstance.ok).toBe(true);
  });

  it('renames an existing query and moves it to the top', () => {
    const queries = [saved(), saved({ id: 'query-2', name: 'Older', updatedAt: 50 })];
    const result = renameSavedMessageQuery(queries, 'query-2', ' Renamed ', 300);

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.queries[0]).toMatchObject({ id: 'query-2', name: 'Renamed', updatedAt: 300 });
    expect(loadSavedMessageQueries()).toEqual(result.queries);
  });

  it('rejects duplicate and empty rename targets', () => {
    const queries = [saved(), saved({ id: 'query-2', name: 'Other' })];
    expect(renameSavedMessageQuery(queries, 'query-2', 'ORDER INVESTIGATION')).toEqual({
      ok: false,
      reason: 'duplicate',
    });
    expect(renameSavedMessageQuery(queries, 'query-2', ' ')).toEqual({
      ok: false,
      reason: 'invalid',
    });
    expect(renameSavedMessageQuery(queries, 'missing', 'Valid')).toEqual({
      ok: false,
      reason: 'not-found',
    });
  });

  it('removes only the selected query', () => {
    const queries = [saved(), saved({ id: 'query-2', name: 'Other' })];
    const result = removeSavedMessageQuery(queries, 'query-1');

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.queries).toEqual([expect.objectContaining({ id: 'query-2' })]);
    expect(loadSavedMessageQueries()).toEqual(result.queries);
    expect(removeSavedMessageQuery(result.queries, 'query-1')).toEqual({
      ok: false,
      reason: 'not-found',
    });
  });

  it('reports storage failures without returning a misleading successful mutation', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });

    expect(addSavedMessageQuery([], 'Orders', topicDraft)).toEqual({
      ok: false,
      reason: 'storage',
    });
    expect(renameSavedMessageQuery([saved()], 'query-1', 'Renamed')).toEqual({
      ok: false,
      reason: 'storage',
    });
    expect(removeSavedMessageQuery([saved()], 'query-1')).toEqual({
      ok: false,
      reason: 'storage',
    });
  });

  it('returns an empty list for unavailable, malformed, or unsupported storage', () => {
    expect(parseSavedMessageQueries(null)).toEqual([]);
    expect(parseSavedMessageQueries('{')).toEqual([]);
    expect(parseSavedMessageQueries('null')).toEqual([]);
    expect(parseSavedMessageQueries(JSON.stringify({ version: 2, queries: [saved()] }))).toEqual(
      [],
    );
    expect(parseSavedMessageQueries(JSON.stringify({ version: 1, queries: 'invalid' }))).toEqual(
      [],
    );
  });

  it('filters malformed entries, duplicate ids, and invalid ranges while loading', () => {
    const valid = saved();
    const raw = JSON.stringify({
      version: 1,
      queries: [
        valid,
        { ...valid, name: 'Duplicate id' },
        { ...valid, id: 'bad-mode', mode: 'queue' },
        { ...valid, id: 'bad-range', startTime: 200, endTime: 100 },
        { ...valid, id: 'missing-name', name: '' },
      ],
    });

    expect(parseSavedMessageQueries(raw)).toEqual([valid]);
  });

  it('reads the legacy array format and writes the current version envelope', () => {
    localStorage.setItem(SAVED_MESSAGE_QUERIES_STORAGE_KEY, JSON.stringify([saved()]));
    expect(loadSavedMessageQueries()).toEqual([saved()]);

    expect(persistSavedMessageQueries([saved()])).toBe(true);
    expect(JSON.parse(localStorage.getItem(SAVED_MESSAGE_QUERIES_STORAGE_KEY)!)).toEqual({
      version: 1,
      queries: [saved()],
    });
  });

  it('enforces the capacity with the most recently updated queries retained', () => {
    const queries = Array.from({ length: SAVED_MESSAGE_QUERY_LIMIT + 5 }, (_, index) =>
      saved({
        id: `query-${index}`,
        name: `Query ${index}`,
        createdAt: index,
        updatedAt: index,
      }),
    );

    expect(persistSavedMessageQueries(queries)).toBe(true);
    const loaded = loadSavedMessageQueries();
    expect(loaded).toHaveLength(SAVED_MESSAGE_QUERY_LIMIT);
    expect(loaded[0].id).toBe(`query-${SAVED_MESSAGE_QUERY_LIMIT + 4}`);
    expect(loaded[loaded.length - 1]?.id).toBe('query-5');
  });

  it('limits persisted names and query values without splitting the data model', () => {
    const result = addSavedMessageQuery(
      [],
      'n'.repeat(200),
      { ...topicDraft, topic: ` ${'t'.repeat(1500)} ` },
      100,
      () => 0.1,
    );
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.query?.name).toHaveLength(80);
    expect(result.query?.topic).toHaveLength(1024);
  });

  it('lists only the active instance and searches all visible identifiers', () => {
    const queries = [
      saved({ name: 'Orders by topic' }),
      saved({ id: 'query-2', name: 'Payment key', mode: 'key', key: 'PAY-100' }),
      saved({ id: 'query-3', instanceId: 'instance-b', topic: 'orders-archive' }),
    ];

    expect(listSavedMessageQueries(queries, undefined)).toEqual([]);
    expect(listSavedMessageQueries(queries, 'instance-a')).toHaveLength(2);
    expect(listSavedMessageQueries(queries, 'instance-a', 'pay-100')).toEqual([
      expect.objectContaining({ id: 'query-2' }),
    ]);
    expect(listSavedMessageQueries(queries, 'instance-a', 'ORDERS BY')).toEqual([
      expect.objectContaining({ id: 'query-1' }),
    ]);
  });

  it('describes topic, key, and message-id criteria for the table', () => {
    expect(describeSavedMessageQuery(saved({ mode: 'key', key: 'ORDER-1' }))).toBe(
      'orders · Key: ORDER-1',
    );
    expect(describeSavedMessageQuery(saved({ mode: 'msgid', msgId: 'MID-1' }))).toBe(
      'orders · Message ID: MID-1',
    );
    expect(describeSavedMessageQuery(saved())).toContain('orders · ');
  });
});
