/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import { readLocalStorage, writeLocalStorage } from './browserStorage';

export const SAVED_MESSAGE_QUERIES_STORAGE_KEY = 'rocketmq-studio-saved-message-queries';
export const SAVED_MESSAGE_QUERY_LIMIT = 50;
export const SAVED_MESSAGE_QUERY_NAME_LIMIT = 80;

const STORAGE_VERSION = 1;
const VALUE_LIMIT = 1024;

export type SavedMessageQueryMode = 'topic' | 'key' | 'msgid';

export interface SavedMessageQueryDraft {
  instanceId: string;
  mode: SavedMessageQueryMode;
  topic: string;
  key?: string;
  msgId?: string;
  startTime?: number;
  endTime?: number;
}

export interface SavedMessageQuery extends SavedMessageQueryDraft {
  id: string;
  name: string;
  createdAt: number;
  updatedAt: number;
}

interface StoredSavedMessageQueries {
  version: number;
  queries: SavedMessageQuery[];
}

export type SavedMessageQueryMutation =
  | { ok: true; queries: SavedMessageQuery[]; query?: SavedMessageQuery }
  | { ok: false; reason: 'invalid' | 'duplicate' | 'storage' | 'not-found' };

const cleanText = (value: unknown, limit = VALUE_LIMIT): string | undefined => {
  if (typeof value !== 'string') return undefined;
  const normalized = value.trim();
  return normalized ? normalized.slice(0, limit) : undefined;
};

const finiteTimestamp = (value: unknown): number | undefined =>
  typeof value === 'number' && Number.isFinite(value) && value >= 0 ? value : undefined;

const validMode = (value: unknown): value is SavedMessageQueryMode =>
  value === 'topic' || value === 'key' || value === 'msgid';

const normalizeDraft = (value: unknown): SavedMessageQueryDraft | null => {
  if (!value || typeof value !== 'object') return null;
  const candidate = value as Partial<SavedMessageQueryDraft>;
  const instanceId = cleanText(candidate.instanceId);
  const topic = cleanText(candidate.topic);
  if (!instanceId || !topic || !validMode(candidate.mode)) return null;

  if (candidate.mode === 'key') {
    const key = cleanText(candidate.key);
    return key ? { instanceId, mode: 'key', topic, key } : null;
  }
  if (candidate.mode === 'msgid') {
    const msgId = cleanText(candidate.msgId);
    return msgId ? { instanceId, mode: 'msgid', topic, msgId } : null;
  }

  const startTime = finiteTimestamp(candidate.startTime);
  const endTime = finiteTimestamp(candidate.endTime);
  if (startTime === undefined || endTime === undefined || startTime > endTime) return null;
  return { instanceId, mode: 'topic', topic, startTime, endTime };
};

const normalizeQuery = (value: unknown): SavedMessageQuery | null => {
  if (!value || typeof value !== 'object') return null;
  const candidate = value as Partial<SavedMessageQuery>;
  const draft = normalizeDraft(candidate);
  const id = cleanText(candidate.id, 160);
  const name = cleanText(candidate.name, SAVED_MESSAGE_QUERY_NAME_LIMIT);
  const createdAt = finiteTimestamp(candidate.createdAt);
  const updatedAt = finiteTimestamp(candidate.updatedAt);
  if (!draft || !id || !name || createdAt === undefined || updatedAt === undefined) return null;
  return { ...draft, id, name, createdAt, updatedAt };
};

const newestFirst = (left: SavedMessageQuery, right: SavedMessageQuery) =>
  right.updatedAt - left.updatedAt || left.name.localeCompare(right.name);

const normalizeList = (values: unknown[]): SavedMessageQuery[] => {
  const ids = new Set<string>();
  return values
    .map(normalizeQuery)
    .filter((query): query is SavedMessageQuery => {
      if (!query || ids.has(query.id)) return false;
      ids.add(query.id);
      return true;
    })
    .sort(newestFirst)
    .slice(0, SAVED_MESSAGE_QUERY_LIMIT);
};

export const parseSavedMessageQueries = (raw: string | null): SavedMessageQuery[] => {
  if (!raw) return [];
  try {
    const parsed: unknown = JSON.parse(raw);
    if (Array.isArray(parsed)) return normalizeList(parsed);
    if (!parsed || typeof parsed !== 'object') return [];
    const envelope = parsed as Partial<StoredSavedMessageQueries>;
    if (envelope.version !== STORAGE_VERSION || !Array.isArray(envelope.queries)) return [];
    return normalizeList(envelope.queries);
  } catch {
    return [];
  }
};

export const loadSavedMessageQueries = (): SavedMessageQuery[] =>
  parseSavedMessageQueries(readLocalStorage(SAVED_MESSAGE_QUERIES_STORAGE_KEY));

export const persistSavedMessageQueries = (queries: SavedMessageQuery[]): boolean =>
  writeLocalStorage(
    SAVED_MESSAGE_QUERIES_STORAGE_KEY,
    JSON.stringify({ version: STORAGE_VERSION, queries: normalizeList(queries) }),
  );

const sameName = (left: string, right: string): boolean =>
  left.localeCompare(right, undefined, { sensitivity: 'accent' }) === 0;

const uniqueId = (existing: SavedMessageQuery[], now: number, random: () => number): string => {
  const ids = new Set(existing.map((query) => query.id));
  let id = `${now.toString(36)}-${Math.floor(random() * Number.MAX_SAFE_INTEGER).toString(36)}`;
  while (ids.has(id)) {
    id = `${now.toString(36)}-${Math.floor(random() * Number.MAX_SAFE_INTEGER).toString(36)}`;
  }
  return id;
};

export const addSavedMessageQuery = (
  queries: SavedMessageQuery[],
  nameValue: string,
  draftValue: SavedMessageQueryDraft,
  now = Date.now(),
  random = Math.random,
): SavedMessageQueryMutation => {
  const name = cleanText(nameValue, SAVED_MESSAGE_QUERY_NAME_LIMIT);
  const draft = normalizeDraft(draftValue);
  if (!name || !draft) return { ok: false, reason: 'invalid' };
  if (
    queries.some((query) => query.instanceId === draft.instanceId && sameName(query.name, name))
  ) {
    return { ok: false, reason: 'duplicate' };
  }

  const query: SavedMessageQuery = {
    ...draft,
    id: uniqueId(queries, now, random),
    name,
    createdAt: now,
    updatedAt: now,
  };
  const next = [query, ...queries].sort(newestFirst).slice(0, SAVED_MESSAGE_QUERY_LIMIT);
  return persistSavedMessageQueries(next)
    ? { ok: true, queries: next, query }
    : { ok: false, reason: 'storage' };
};

export const renameSavedMessageQuery = (
  queries: SavedMessageQuery[],
  id: string,
  nameValue: string,
  now = Date.now(),
): SavedMessageQueryMutation => {
  const name = cleanText(nameValue, SAVED_MESSAGE_QUERY_NAME_LIMIT);
  const current = queries.find((query) => query.id === id);
  if (!name) return { ok: false, reason: 'invalid' };
  if (!current) return { ok: false, reason: 'not-found' };
  if (
    queries.some(
      (query) =>
        query.id !== id && query.instanceId === current.instanceId && sameName(query.name, name),
    )
  ) {
    return { ok: false, reason: 'duplicate' };
  }

  const renamed = { ...current, name, updatedAt: now };
  const next = queries.map((query) => (query.id === id ? renamed : query)).sort(newestFirst);
  return persistSavedMessageQueries(next)
    ? { ok: true, queries: next, query: renamed }
    : { ok: false, reason: 'storage' };
};

export const removeSavedMessageQuery = (
  queries: SavedMessageQuery[],
  id: string,
): SavedMessageQueryMutation => {
  if (!queries.some((query) => query.id === id)) return { ok: false, reason: 'not-found' };
  const next = queries.filter((query) => query.id !== id);
  return persistSavedMessageQueries(next)
    ? { ok: true, queries: next }
    : { ok: false, reason: 'storage' };
};

export const listSavedMessageQueries = (
  queries: SavedMessageQuery[],
  instanceId: string | undefined,
  search = '',
): SavedMessageQuery[] => {
  if (!instanceId) return [];
  const needle = search.trim().toLocaleLowerCase();
  return queries.filter((query) => {
    if (query.instanceId !== instanceId) return false;
    if (!needle) return true;
    return [query.name, query.topic, query.key, query.msgId].some((value) =>
      value?.toLocaleLowerCase().includes(needle),
    );
  });
};

export const describeSavedMessageQuery = (query: SavedMessageQuery): string => {
  if (query.mode === 'key') return `${query.topic} · Key: ${query.key}`;
  if (query.mode === 'msgid') return `${query.topic} · Message ID: ${query.msgId}`;
  return `${query.topic} · ${new Date(query.startTime!).toLocaleString()} – ${new Date(
    query.endTime!,
  ).toLocaleString()}`;
};
