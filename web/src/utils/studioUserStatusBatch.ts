/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import type { StudioUser } from '../api/studioUsers';

export interface UserStatusBatchSuccess {
  user: StudioUser;
  updated: StudioUser;
}
export interface UserStatusBatchFailure {
  user: StudioUser;
  error: string;
}
export interface UserStatusBatchResult {
  requested: number;
  changed: number;
  skipped: StudioUser[];
  successes: UserStatusBatchSuccess[];
  failures: UserStatusBatchFailure[];
}
const errorText = (error: unknown) =>
  error instanceof Error && error.message.trim() ? error.message : '更新用户状态失败';

/** 以固定并发执行状态更新，并保留每个账号的独立结果用于审计和重试。 */
export const executeUserStatusBatch = async (
  users: StudioUser[],
  enabled: boolean,
  mutate: (userId: number, enabled: boolean) => Promise<StudioUser>,
  concurrency = 4,
): Promise<UserStatusBatchResult> => {
  const unique = [...new Map(users.map((user) => [user.id, user])).values()];
  const skipped = unique.filter((user) => user.enabled === enabled);
  const pending = unique.filter((user) => user.enabled !== enabled);
  const successes: UserStatusBatchSuccess[] = [];
  const failures: UserStatusBatchFailure[] = [];
  let cursor = 0;
  const worker = async () => {
    while (cursor < pending.length) {
      const user = pending[cursor];
      cursor += 1;
      try {
        successes.push({ user, updated: await mutate(user.id, enabled) });
      } catch (error) {
        failures.push({ user, error: errorText(error) });
      }
    }
  };
  const workerCount = Math.min(Math.max(1, Math.floor(concurrency)), pending.length);
  await Promise.all(Array.from({ length: workerCount }, worker));
  successes.sort((a, b) => a.user.username.localeCompare(b.user.username));
  failures.sort((a, b) => a.user.username.localeCompare(b.user.username));
  return { requested: unique.length, changed: pending.length, skipped, successes, failures };
};

export const eligibleUserStatusTargets = (
  users: StudioUser[],
  currentUserId: number | null | undefined,
  enabled: boolean,
) => users.filter((user) => user.id !== currentUserId && user.enabled !== enabled);
