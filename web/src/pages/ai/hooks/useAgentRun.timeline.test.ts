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

import { act, renderHook, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { useAgentRun } from './useAgentRun';
import { useConversationTimeline } from './useConversationTimeline';
import { openRunStream, type RunStreamHandlers } from '../../../api/ai';
import { getConversationTimeline } from '../../../api/aiConversations';

vi.mock('../../../api/ai', () => ({ openRunStream: vi.fn(), attachRunStream: vi.fn() }));
vi.mock('../../../api/aiConversations', () => ({
  getConversationTimeline: vi.fn(),
  stopRun: vi.fn(),
  reportRunSpeed: vi.fn(),
}));

it('keepsTheLiveAnswerWhenTheRealTimelineRefreshFailsTest', async () => {
  vi.mocked(getConversationTimeline)
    .mockResolvedValueOnce({ items: [], nextAfter: null, activeRun: null })
    .mockRejectedValueOnce(new Error('timeline unavailable'));
  let handlers!: RunStreamHandlers;
  let finish!: () => void;
  vi.mocked(openRunStream).mockImplementation((_id, _body, callbacks) => {
    handlers = callbacks;
    return new Promise<void>((resolve) => {
      finish = resolve;
    });
  });
  const { result } = renderHook(() => {
    const timeline = useConversationTimeline(7);
    const run = useAgentRun(7, { refetchTimeline: timeline.refetch });
    return { timeline, run };
  });
  await waitFor(() => expect(result.current.timeline.loading).toBe(false));
  let sent!: Promise<void>;
  await act(async () => {
    sent = result.current.run.send(7, { message: 'hello' });
  });
  await act(async () => {
    handlers.onEvent({ type: 'text_delta', content: 'answer not yet in history' });
  });
  const liveAnswer = result.current.run.blocksRef.current;
  expect(liveAnswer.length).toBeGreaterThan(0);
  await act(async () => {
    finish();
    await sent;
  });
  expect(result.current.timeline.error).toBe('timeline unavailable');
  expect(result.current.run.blocksRef.current).toEqual(liveAnswer);
  expect(result.current.run.error).toBe('timeline unavailable');
});
