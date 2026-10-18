/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { act, renderHook } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { createConversation } from '../../../api/aiConversations';
import type { Instance } from '../../../api/instance';
import { listInstances } from '../../../services/instanceService';
import { useAiSend } from './useAiSend';

vi.mock('react-router-dom', () => ({ useNavigate: () => vi.fn() }));
vi.mock('../../../api/aiConversations', () => ({ createConversation: vi.fn() }));
vi.mock('../../../services/instanceService', () => ({ listInstances: vi.fn() }));

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(createConversation).mockResolvedValue({
    id: 7,
    owner: 'operator',
    title: '',
    engine: 'http',
    model: '',
    mode: 'chat',
    lastSeq: 0,
    archived: false,
    createdAt: '',
    updatedAt: '',
  });
  vi.mocked(listInstances).mockResolvedValue(
    ['prod', 'staging'].map((name, id): Instance => ({
      id,
      name,
      endpoint: `${name}:9876`,
      type: 'DIRECT',
      adminCredentialRef: 'credential',
      remark: null,
      topicCount: 0,
      consumerGroupCount: 0,
      gmtCreate: '',
      gmtModified: '',
    })),
  );
});

it('preservesExplicitPlainChatWithoutChoosingACredentialledInstanceTest', async () => {
  const { result } = renderHook(() =>
    useAiSend({ conversationId: null, ready: true, send: vi.fn(), onError: vi.fn() }),
  );
  await act(async () => {
    await result.current({
      createBody: { instanceId: null, mode: 'chat' },
      request: { message: 'hello', engine: 'http' },
    });
  });
  expect(createConversation).toHaveBeenCalledWith({ instanceId: null, mode: 'chat' });
  expect(listInstances).not.toHaveBeenCalled();
});

it('rejectsARemovedTargetInsteadOfFallingBackToAnotherInstanceTest', async () => {
  const onError = vi.fn();
  const { result } = renderHook(() =>
    useAiSend({ conversationId: null, ready: true, send: vi.fn(), onError }),
  );
  let target: number | null = 0;
  await act(async () => {
    target = await result.current({
      createBody: { instanceId: 'removed' },
      request: { message: 'hello', engine: 'http' },
    });
  });
  expect(target).toBeNull();
  expect(createConversation).not.toHaveBeenCalled();
  expect(onError).toHaveBeenCalledTimes(1);
});

it('abortsBoundCreationWhenDirectoryValidationFailsTest', async () => {
  vi.mocked(listInstances).mockRejectedValue(new Error('directory unavailable'));
  const onError = vi.fn();
  const { result } = renderHook(() =>
    useAiSend({ conversationId: null, ready: true, send: vi.fn(), onError }),
  );
  await act(async () => {
    await result.current({
      createBody: { instanceId: 'prod' },
      request: { message: 'hello', engine: 'http' },
    });
  });
  expect(createConversation).not.toHaveBeenCalled();
  expect(onError).toHaveBeenCalledWith(
    expect.objectContaining({ message: 'directory unavailable' }),
  );
});
