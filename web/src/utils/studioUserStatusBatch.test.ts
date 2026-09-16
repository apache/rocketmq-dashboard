/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import { describe, expect, it, vi } from 'vitest';
import type { StudioUser } from '../api/studioUsers';
import { eligibleUserStatusTargets, executeUserStatusBatch } from './studioUserStatusBatch';

const user = (id: number, enabled: boolean, username = `user-${id}`): StudioUser => ({
  id,
  username,
  enabled,
  admin: false,
  passwordChangedAt: '',
  gmtCreate: '',
  gmtModified: '',
});

describe('Studio user status batch', () => {
  it('updates only users whose status differs from the target', async () => {
    const mutate = vi.fn(async (id: number, enabled: boolean) => user(id, enabled));
    const result = await executeUserStatusBatch([user(1, false), user(2, true)], true, mutate);
    expect(mutate).toHaveBeenCalledTimes(1);
    expect(mutate).toHaveBeenCalledWith(1, true);
    expect(result).toMatchObject({ requested: 2, changed: 1 });
    expect(result.skipped.map((item) => item.id)).toEqual([2]);
  });

  it('deduplicates the same user ID before executing', async () => {
    const mutate = vi.fn(async (id: number, enabled: boolean) => user(id, enabled));
    const result = await executeUserStatusBatch([user(1, false), user(1, false)], true, mutate);
    expect(mutate).toHaveBeenCalledTimes(1);
    expect(result.requested).toBe(1);
  });

  it('captures independent failures without rolling back successes', async () => {
    const mutate = vi.fn(async (id: number, enabled: boolean) => {
      if (id === 2) throw new Error('account is locked');
      return user(id, enabled);
    });
    const result = await executeUserStatusBatch(
      [user(1, true), user(2, true), user(3, true)],
      false,
      mutate,
    );
    expect(result.successes.map((item) => item.user.id)).toEqual([1, 3]);
    expect(result.failures).toEqual([
      expect.objectContaining({
        user: expect.objectContaining({ id: 2 }),
        error: 'account is locked',
      }),
    ]);
  });

  it('uses a stable fallback for non-Error rejections', async () => {
    const result = await executeUserStatusBatch([user(1, false)], true, async () => {
      throw { code: 500 };
    });
    expect(result.failures[0].error).toBe('更新用户状态失败');
  });

  it('respects the requested concurrency limit', async () => {
    let active = 0;
    let maximum = 0;
    const releases: Array<() => void> = [];
    const mutate = vi.fn(async (id: number) => {
      active += 1;
      maximum = Math.max(maximum, active);
      await new Promise<void>((resolve) => releases.push(resolve));
      active -= 1;
      return user(id, true);
    });
    const promise = executeUserStatusBatch(
      [user(1, false), user(2, false), user(3, false), user(4, false)],
      true,
      mutate,
      2,
    );
    await vi.waitFor(() => expect(mutate).toHaveBeenCalledTimes(2));
    releases.splice(0).forEach((release) => release());
    await vi.waitFor(() => expect(mutate).toHaveBeenCalledTimes(4));
    releases.splice(0).forEach((release) => release());
    await promise;
    expect(maximum).toBe(2);
  });

  it('normalizes invalid concurrency to one worker', async () => {
    const order: number[] = [];
    const result = await executeUserStatusBatch(
      [user(2, false), user(1, false)],
      true,
      async (id) => {
        order.push(id);
        return user(id, true);
      },
      0,
    );
    expect(order).toEqual([2, 1]);
    expect(result.successes.map((item) => item.user.username)).toEqual(['user-1', 'user-2']);
  });

  it('returns an empty successful result for no users', async () => {
    const mutate = vi.fn();
    await expect(executeUserStatusBatch([], true, mutate)).resolves.toEqual({
      requested: 0,
      changed: 0,
      skipped: [],
      successes: [],
      failures: [],
    });
    expect(mutate).not.toHaveBeenCalled();
  });

  it('excludes the current user and already-matching statuses from eligible targets', () => {
    const users = [user(1, true), user(2, true), user(3, false)];
    expect(eligibleUserStatusTargets(users, 1, false).map((item) => item.id)).toEqual([2]);
    expect(eligibleUserStatusTargets(users, 1, true).map((item) => item.id)).toEqual([3]);
  });

  it('allows all non-matching users when current identity is unavailable', () => {
    expect(
      eligibleUserStatusTargets([user(1, true), user(2, false)], undefined, false).map(
        (item) => item.id,
      ),
    ).toEqual([1]);
  });
});
