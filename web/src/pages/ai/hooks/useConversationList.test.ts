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
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AiConversationListItemVO } from '../../../api/aiEvents';
import { useConversationList } from './useConversationList';

vi.mock('../../../api/aiConversations', () => ({
  listConversations: vi.fn(),
}));

import { listConversations, type PageResult } from '../../../api/aiConversations';

/**
 * Server-paged history: the two behaviours a rewrite most easily loses are the requestId guard (a
 * slow response for an older query overwriting a newer one) and the "any filter change jumps back to
 * page 1" rule, because page 7 of a different search does not exist.
 */

const listMock = vi.mocked(listConversations);

function listItem(id: number, title: string): AiConversationListItemVO {
  return {
    id,
    title,
    engine: 'claude-code',
    model: 'claude-sonnet-4-5',
    mode: 'chat',
    instanceId: 'open-source-local',
    lastRunId: null,
    lastRunStatus: null,
    updatedAt: '2026-09-20T02:12:31',
    createdAt: '2026-09-20T02:11:00',
  };
}

function resultPage(items: AiConversationListItemVO[], total: number, page: number) {
  return { items, total, page, size: 15 } satisfies PageResult<AiConversationListItemVO>;
}

describe('useConversationList', () => {
  beforeEach(() => {
    listMock.mockReset();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('loadsTheFirstPageOnMountTest', async () => {
    listMock.mockResolvedValue(resultPage([listItem(7, '查看集群状态')], 1, 1));

    const { result } = renderHook(() => useConversationList());

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(listMock).toHaveBeenCalledWith({
      page: 1,
      size: 15,
      search: undefined,
      archived: false,
    });
    expect(result.current.items.map((row) => row.id)).toEqual([7]);
    expect(result.current.total).toBe(1);
    expect(result.current.pageSize).toBe(15);
  });

  it('forwardsPagingSearchAndTheArchivedFilterTest', async () => {
    listMock.mockResolvedValue(resultPage([], 0, 1));

    const { result } = renderHook(() => useConversationList(10));
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      result.current.setSearch('集群');
      result.current.setArchived(true);
      result.current.setPage(3);
    });

    await waitFor(() =>
      expect(listMock).toHaveBeenLastCalledWith({
        page: 3,
        size: 10,
        search: '集群',
        archived: true,
      }),
    );
  });

  it('jumpsBackToTheFirstPageWhenAFilterChangesTest', async () => {
    listMock.mockResolvedValue(resultPage([], 0, 1));

    const { result } = renderHook(() => useConversationList());
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      result.current.setPage(4);
    });
    await waitFor(() => expect(result.current.page).toBe(4));

    await act(async () => {
      result.current.setSearch('堆积');
    });
    expect(result.current.page).toBe(1);

    await act(async () => {
      result.current.setPage(5);
    });
    await act(async () => {
      result.current.setArchived(true);
    });
    expect(result.current.page).toBe(1);
  });

  it('clampsBackToTheLastValidPageWhenTheCurrentPageBecomesEmptyTest', async () => {
    // Page 2 held the only conversation of the last page; archiving or deleting it must pull the
    // list back to a populated page instead of resting on a blank one.
    listMock.mockImplementation(async (params) =>
      params.page === 2
        ? resultPage([], 15, 2)
        : resultPage(
            Array.from({ length: 15 }, (_, index) => listItem(index + 1, `会话 ${index + 1}`)),
            15,
            1,
          ),
    );

    const { result } = renderHook(() => useConversationList());
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      result.current.setPage(2);
    });
    await waitFor(() =>
      expect(listMock).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })),
    );
    await waitFor(() => expect(result.current.items).toHaveLength(15));
    expect(result.current.page).toBe(1);
  });

  it('discardsAResponseSupersededByANewerQueryTest', async () => {
    let releaseStale: (value: PageResult<AiConversationListItemVO>) => void = () => undefined;
    const stale = new Promise<PageResult<AiConversationListItemVO>>((resolve) => {
      releaseStale = resolve;
    });
    listMock.mockReturnValueOnce(stale);
    listMock.mockResolvedValue(resultPage([listItem(9, 'newer query')], 1, 1));

    const { result } = renderHook(() => useConversationList());
    // The second query overtakes the first one while it is still in flight.
    await act(async () => {
      result.current.setSearch('newer');
    });
    await waitFor(() => expect(listMock).toHaveBeenCalledTimes(2));

    releaseStale(resultPage([listItem(1, 'stale query')], 1, 1));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.items.map((row) => row.title)).toEqual(['newer query']);
  });

  it('reloadsTheCurrentQueryOnDemandTest', async () => {
    listMock.mockResolvedValue(resultPage([listItem(7, '查看集群状态')], 1, 1));

    const { result } = renderHook(() => useConversationList());
    await waitFor(() => expect(listMock).toHaveBeenCalledTimes(1));

    await act(async () => {
      result.current.reload();
    });

    await waitFor(() => expect(listMock).toHaveBeenCalledTimes(2));
    expect(listMock).toHaveBeenLastCalledWith({
      page: 1,
      size: 15,
      search: undefined,
      archived: false,
    });
  });

  it('reportsTheServerMessageAndEmptiesTheListOnFailureTest', async () => {
    listMock
      .mockResolvedValueOnce(resultPage([listItem(7, '查看集群状态')], 1, 1))
      .mockRejectedValueOnce(new Error('会话服务不可用'));

    const { result } = renderHook(() => useConversationList());
    await waitFor(() => expect(result.current.items).toHaveLength(1));

    await act(async () => {
      result.current.reload();
    });

    await waitFor(() => expect(result.current.error).toBe('会话服务不可用'));
    expect(result.current.items).toEqual([]);
    expect(result.current.total).toBe(0);
    expect(result.current.loading).toBe(false);
  });
});
