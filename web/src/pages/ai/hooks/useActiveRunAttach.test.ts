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
import { describe, expect, it, vi } from 'vitest';
import type { AiTimelineVO } from '../../../api/aiEvents';
import { useActiveRunAttach } from './useActiveRunAttach';
import { useConversationTimeline } from './useConversationTimeline';

vi.mock('../../../api/aiConversations', () => ({
  getConversationTimeline: vi.fn(),
}));

import { getConversationTimeline } from '../../../api/aiConversations';

/**
 * `useActiveRunAttach` decides what to re-attach; `useConversationTimeline` is what it trusts to say
 * which run belongs to the conversation on screen. They are exercised together because the property
 * is the handover between them: an attach is addressed by run id alone, so one made while the user
 * switches conversation streams the previous conversation's frames into the new transcript.
 */

const timelineMock = vi.mocked(getConversationTimeline);

function page(activeRun: AiTimelineVO['activeRun']): AiTimelineVO {
  return { items: [], nextAfter: null, activeRun };
}

describe('useActiveRunAttach', () => {
  it('attachesOnlyTheRunOfTheConversationOnScreenTest', async () => {
    let resolveSecond: (value: AiTimelineVO) => void = () => {};
    timelineMock.mockImplementation((id) =>
      id === 7
        ? Promise.resolve(page({ id: 41, status: 'RUNNING' }))
        : new Promise<AiTimelineVO>((resolve) => {
            resolveSecond = resolve;
          }),
    );
    const attach = vi.fn().mockResolvedValue(undefined);

    const { rerender } = renderHook(
      ({ id }: { id: number | null }) =>
        useActiveRunAttach(id, useConversationTimeline(id), { isStreaming: false, attach }),
      { initialProps: { id: 7 } },
    );

    await waitFor(() => expect(attach).toHaveBeenCalledWith(7, 41, 0));

    // The user switches conversation and the new timeline has not answered yet, so the last thing the
    // server said is still conversation 7's run.
    rerender({ id: 9 });

    expect(attach).toHaveBeenCalledTimes(1);

    // Once conversation 9's own timeline lands, its run is the one that gets attached.
    await act(async () => {
      resolveSecond(page({ id: 77, status: 'RUNNING' }));
    });

    await waitFor(() => expect(attach).toHaveBeenCalledWith(9, 77, 0));
    expect(attach).toHaveBeenCalledTimes(2);
  });
});
