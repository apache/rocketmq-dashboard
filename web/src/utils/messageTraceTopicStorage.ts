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

const STORAGE_KEY_PREFIX = 'rocketmq-studio-message-trace-topic:';

export interface MessageTraceTopicOwner {
  userId: number | null;
  username: string | null;
}

export const messageTraceTopicOwnerKey = ({ userId, username }: MessageTraceTopicOwner): string => {
  if (userId !== null && Number.isSafeInteger(userId)) return `user-id:${userId}`;
  const normalizedUsername = username?.trim();
  return normalizedUsername ? `username:${normalizedUsername}` : 'anonymous';
};

const normalizeInstanceId = (instanceId: string | number | undefined): string | undefined => {
  if (instanceId === undefined || instanceId === null) return undefined;
  const normalized = String(instanceId).trim();
  return normalized || undefined;
};

const storageKey = (
  instanceId: string | number | undefined,
  owner: MessageTraceTopicOwner,
): string | undefined => {
  const normalized = normalizeInstanceId(instanceId);
  return normalized
    ? `${STORAGE_KEY_PREFIX}${encodeURIComponent(messageTraceTopicOwnerKey(owner))}:${encodeURIComponent(normalized)}`
    : undefined;
};

const getStorage = (): Storage | null => {
  if (typeof window === 'undefined') return null;
  try {
    return window.localStorage;
  } catch {
    // Browsers can deny storage access in private mode or under a restrictive policy.
    return null;
  }
};

/**
 * Reads the last custom trace topic used for one account and instance.
 * A missing, blank, or unreadable value intentionally means provider default.
 * The old instance-only key is not migrated because its account owner is unknown.
 */
export const readMessageTraceTopic = (
  instanceId: string | number | undefined,
  owner: MessageTraceTopicOwner,
): string => {
  const key = storageKey(instanceId, owner);
  const storage = getStorage();
  if (!key || !storage) return '';
  try {
    return storage.getItem(key)?.trim() || '';
  } catch {
    return '';
  }
};

/**
 * Persists a normalized custom trace topic without making browser storage a prerequisite for
 * querying traces. Blank values remove the per-instance preference and restore default behavior.
 */
export const writeMessageTraceTopic = (
  instanceId: string | number | undefined,
  traceTopic: string | undefined,
  owner: MessageTraceTopicOwner,
): void => {
  const key = storageKey(instanceId, owner);
  const storage = getStorage();
  if (!key || !storage) return;
  const normalized = traceTopic?.trim() || '';
  try {
    if (normalized) {
      storage.setItem(key, normalized);
    } else {
      storage.removeItem(key);
    }
  } catch {
    // Storage failure must not block an otherwise valid trace request.
  }
};
