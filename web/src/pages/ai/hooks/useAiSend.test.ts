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

import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createConversation } from '../../../api/aiConversations';
import { listInstances } from '../../../services/instanceService';
import { useAiSend } from './useAiSend';

const { navigate } = vi.hoisted(() => ({ navigate: vi.fn() }));
vi.mock('react-router-dom', () => ({ useNavigate: () => navigate }));
vi.mock('../../../api/aiConversations', () => ({ createConversation: vi.fn() }));
vi.mock('../../../services/instanceService', () => ({ listInstances: vi.fn() }));

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

const created = { id: 42 } as Awaited<ReturnType<typeof createConversation>>;
const start = { createBody: { instanceId: 'primary' }, request: { message: 'hello' } };

function render() {
  const send = vi.fn().mockResolvedValue(undefined);
  const onError = vi.fn();
  return {
    ...renderHook(({ id }: { id: number | null }) =>
      useAiSend({ conversationId: id, ready: true, send, onError }),
    { initialProps: { id: null as number | null } }),
    send, onError,
  };
}

describe('useAiSend creation lifecycle', () => {
  beforeEach(() => vi.resetAllMocks());

  it('does not navigate or send after unmount while creation is pending', async () => {
    const pending = deferred<typeof created>();
    vi.mocked(createConversation).mockReturnValue(pending.promise);
    const { result, unmount, send } = render();
    let operation!: Promise<number | null>;
    await act(async () => { operation = result.current(start); });
    unmount();
    await act(async () => { pending.resolve(created); await operation; });
    expect(navigate).not.toHaveBeenCalled();
    expect(send).not.toHaveBeenCalled();
    expect(await operation).toBeNull();
  });

  it('does not replace a conversation selected while creation was pending', async () => {
    const pending = deferred<typeof created>();
    vi.mocked(createConversation).mockReturnValue(pending.promise);
    const { result, rerender, send } = render();
    let operation!: Promise<number | null>;
    await act(async () => { operation = result.current(start); });
    rerender({ id: 7 });
    await act(async () => { pending.resolve(created); await operation; });
    expect(navigate).not.toHaveBeenCalled();
    expect(send).not.toHaveBeenCalled();
  });

  it('does not start creation after an abandoned default-instance lookup', async () => {
    const pending = deferred<Awaited<ReturnType<typeof listInstances>>>();
    vi.mocked(listInstances).mockReturnValue(pending.promise);
    const { result, unmount } = render();
    let operation!: Promise<number | null>;
    await act(async () => { operation = result.current({ ...start, createBody: {} }); });
    unmount();
    await act(async () => { pending.resolve([]); await operation; });
    expect(createConversation).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('does not report a creation failure after leaving its route', async () => {
    const pending = deferred<typeof created>();
    vi.mocked(createConversation).mockReturnValue(pending.promise);
    const { result, rerender, onError } = render();
    let operation!: Promise<number | null>;
    await act(async () => { operation = result.current(start); });
    rerender({ id: 7 });
    await act(async () => { pending.reject(new Error('offline')); await operation; });
    expect(onError).not.toHaveBeenCalled();
  });

  it('navigates and sends once when the created route arrives', async () => {
    vi.mocked(createConversation).mockResolvedValue(created);
    const { result, rerender, send } = render();
    await act(async () => { expect(await result.current(start)).toBe(42); });
    expect(navigate).toHaveBeenCalledWith('/ai/c/42', { replace: true, state: null });
    expect(send).not.toHaveBeenCalled();
    rerender({ id: 42 });
    expect(send).toHaveBeenCalledExactlyOnceWith(42, start.request);
    rerender({ id: 42 });
    expect(send).toHaveBeenCalledTimes(1);
  });

  it('reports a current creation failure', async () => {
    const error = new Error('offline');
    vi.mocked(createConversation).mockRejectedValue(error);
    const { result, onError } = render();
    await act(async () => { expect(await result.current(start)).toBeNull(); });
    expect(onError).toHaveBeenCalledWith(error);
  });
});
