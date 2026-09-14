/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import client from '../api/client';

export const SAVED_MESSAGE_QUERY_NAME_LIMIT = 80;

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

const endpoint = '/query-history/named-messages';

export async function loadSavedMessageQueries(instanceId: string): Promise<SavedMessageQuery[]> {
  const response = await client.get<{ data: SavedMessageQuery[] }>(endpoint, {
    params: { instanceId },
  });
  return response.data.data;
}

export async function addSavedMessageQuery(name: string, draft: SavedMessageQueryDraft) {
  await client.post(endpoint, { ...draft, name: name.trim() });
}

export async function renameSavedMessageQuery(instanceId: string, id: string, name: string) {
  await client.put(
    `${endpoint}/${encodeURIComponent(id)}`,
    { name: name.trim() },
    { params: { instanceId } },
  );
}

export async function removeSavedMessageQuery(instanceId: string, id: string) {
  await client.delete(`${endpoint}/${encodeURIComponent(id)}`, { params: { instanceId } });
}

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
