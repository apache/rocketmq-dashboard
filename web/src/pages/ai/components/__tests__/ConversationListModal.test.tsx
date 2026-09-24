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

import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { LangProvider } from '../../../../i18n/LangContext';
import type { AiConversationListItemVO } from '../../../../api/aiEvents';
import {
  deleteConversation,
  listConversations,
  updateConversation,
  type PageResult,
} from '../../../../api/aiConversations';
import ConversationListModal from '../ConversationListModal';

vi.mock('../../../../api/aiConversations', () => ({
  listConversations: vi.fn(),
  deleteConversation: vi.fn(),
  updateConversation: vi.fn(),
}));

/**
 * The server-paged history modal.
 *
 * Four behaviours a rewrite most easily loses, and the reason each is asserted here rather than left
 * to the hook's own suite:
 * - paging and searching are SERVER-side, so a stale page number is a request for a page that does
 *   not exist (an empty table under a pagination bar that claims otherwise);
 * - the requestId guard, because the modal is where an operator types a search while the previous
 *   query is still in flight — a slow older response overwriting a newer one looks exactly like a
 *   search that does not work;
 * - navigation from the title cell ONLY, the project's rule for every list: a whole-row onClick
 *   teleports the reader who meant to select a cell's text;
 * - the query must not fire while the modal is closed, or every visit to `/ai` costs a list request.
 *
 * Deletion is asserted here for two reasons of its own: a hard delete is the one irreversible action
 * this list offers, so which ids it sends and what it reports when only some of them went is the
 * contract; and the row that disappears may be the one on screen, which the caller has to navigate
 * away from because this component does not know the router.
 */

const listMock = vi.mocked(listConversations);
const deleteMock = vi.mocked(deleteConversation);
const updateMock = vi.mocked(updateConversation);

beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
});

function conversation(id: number, title: string): AiConversationListItemVO {
  return {
    id,
    title,
    engine: 'claude-code',
    model: 'claude-sonnet-4-5',
    mode: 'chat',
    instanceId: 'open-source-local',
    lastRunId: id * 10,
    lastRunStatus: 'COMPLETED',
    updatedAt: '2026-09-20T02:12:31',
    createdAt: '2026-09-20T02:11:00',
  };
}

function page(
  items: AiConversationListItemVO[],
  total: number,
  page: number,
): PageResult<AiConversationListItemVO> {
  return { items, total, page, size: 15 };
}

function deferredPage() {
  let resolve!: (value: PageResult<AiConversationListItemVO>) => void;
  const promise = new Promise<PageResult<AiConversationListItemVO>>((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

function renderModal(
  props: {
    open?: boolean;
    activeConversationId?: number | null;
    onSelect?: (conversationId: number) => void;
    onActiveDeleted?: () => void;
  } = {},
) {
  const onSelect = props.onSelect ?? vi.fn();
  const onActiveDeleted = props.onActiveDeleted ?? vi.fn();
  const view = render(
    <App>
      <LangProvider>
        <ConversationListModal
          open={props.open ?? true}
          onClose={vi.fn()}
          activeConversationId={props.activeConversationId ?? null}
          onSelect={onSelect}
          onActiveDeleted={onActiveDeleted}
        />
      </LangProvider>
    </App>,
  );
  return { ...view, onSelect, onActiveDeleted };
}

/**
 * Confirms the Popconfirm that is currently open.
 *
 * antd portals it onto `document.body` rather than into the modal, and its OK button carries no
 * accessible name that distinguishes it from the toolbar's two `删除` buttons — so the lookup goes
 * through the popover's own class, scoped to the one that is not hidden.
 */
async function confirmDelete(user: ReturnType<typeof userEvent.setup>) {
  const okButton = await waitFor(() => {
    const button = document.querySelector<HTMLElement>(
      '.ant-popconfirm:not(.ant-popover-hidden) .ant-btn-dangerous',
    );
    if (!button) throw new Error('the delete confirmation did not open');
    return button;
  });
  await user.click(okButton);
}

describe('ConversationListModal', () => {
  beforeEach(() => {
    // `mockReset`, not just `clearAllMocks`: a test that failed early can leave queued
    // `mockReturnValueOnce` implementations behind, which the next test would then consume instead
    // of its own stub.
    listMock.mockReset();
    deleteMock.mockReset();
    updateMock.mockReset();
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('archivesAConversationFromTheActiveScopeTest', async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    listMock.mockResolvedValue(page([conversation(7, '检查集群状态')], 1, 1));
    updateMock.mockResolvedValue({ id: 7 } as never);
    renderModal();
    await screen.findByText('检查集群状态');
    const listCallsBefore = listMock.mock.calls.length;

    await user.click(screen.getByTestId('ai-conversation-row-archive-7'));

    await waitFor(() => expect(updateConversation).toHaveBeenCalledWith(7, { archived: true }));
    // The scope reloads so the archived row leaves the active list.
    await waitFor(() => expect(listMock.mock.calls.length).toBeGreaterThan(listCallsBefore));
  });

  it('unarchivesAConversationFromTheArchivedScopeTest', async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    listMock.mockResolvedValue(page([conversation(7, '检查集群状态')], 1, 1));
    updateMock.mockResolvedValue({ id: 7 } as never);
    renderModal();
    await screen.findByText('检查集群状态');

    await user.click(screen.getByText('已归档'));
    await waitFor(() =>
      expect(listConversations).toHaveBeenLastCalledWith(
        expect.objectContaining({ archived: true }),
      ),
    );

    await user.click(screen.getByTestId('ai-conversation-row-archive-7'));
    await waitFor(() => expect(updateConversation).toHaveBeenCalledWith(7, { archived: false }));
  });

  it('doesNotQueryWhileClosedTest', async () => {
    renderModal({ open: false });

    // Give any stray effect a chance to fire; the panel is not mounted, so nothing may.
    await act(async () => {
      await Promise.resolve();
    });
    expect(listConversations).not.toHaveBeenCalled();
  });

  it('loadsTheFirstPageWhenOpenedTest', async () => {
    listMock.mockResolvedValue(page([conversation(7, '检查集群状态')], 1, 1));
    renderModal();

    expect(await screen.findByText('检查集群状态')).toBeInTheDocument();
    expect(listConversations).toHaveBeenCalledWith({
      page: 1,
      size: 15,
      search: undefined,
      archived: false,
    });
    // Run status and mode come back translated, timestamps as the viewer's local time.
    expect(screen.getByText('已完成')).toBeInTheDocument();
    expect(screen.getByText('对话')).toBeInTheDocument();
    expect(screen.queryByText('2026-09-20T02:12:31')).not.toBeInTheDocument();
  });

  it('pagesThroughTheServerSideListTest', async () => {
    listMock.mockResolvedValue(page([conversation(1, '会话一')], 45, 1));
    renderModal();
    await screen.findByText('会话一');

    listMock.mockResolvedValue(page([conversation(21, '会话二十一')], 45, 2));
    fireEvent.click(screen.getByTitle('2'));

    expect(await screen.findByText('会话二十一')).toBeInTheDocument();
    expect(listConversations).toHaveBeenLastCalledWith({
      page: 2,
      size: 15,
      search: undefined,
      archived: false,
    });
  });

  it('resetsToTheFirstPageWhenTheSearchChangesTest', async () => {
    const user = userEvent.setup();
    listMock.mockResolvedValue(page([conversation(1, '会话一')], 45, 1));
    renderModal();
    await screen.findByText('会话一');

    listMock.mockResolvedValue(page([conversation(21, '会话二十一')], 45, 2));
    fireEvent.click(screen.getByTitle('2'));
    await screen.findByText('会话二十一');

    listMock.mockResolvedValue(page([conversation(9, '磁盘水位巡检')], 1, 1));
    await user.type(screen.getByRole('searchbox', { name: '搜索会话标题' }), '磁盘{Enter}');

    expect(await screen.findByText('磁盘水位巡检')).toBeInTheDocument();
    // Page 2 of a different search does not exist: the filter change has to jump back to page 1.
    expect(listConversations).toHaveBeenLastCalledWith({
      page: 1,
      size: 15,
      search: '磁盘',
      archived: false,
    });
  });

  it('queriesTheArchivedScopeFromPageOneTest', async () => {
    const user = userEvent.setup();
    listMock.mockResolvedValue(page([conversation(1, '会话一')], 45, 1));
    renderModal();
    await screen.findByText('会话一');

    listMock.mockResolvedValue(page([conversation(21, '会话二十一')], 45, 2));
    fireEvent.click(screen.getByTitle('2'));
    await screen.findByText('会话二十一');

    listMock.mockResolvedValue(page([conversation(3, '已归档会话')], 1, 1));
    await user.click(screen.getByText('已归档'));

    expect(await screen.findByText('已归档会话')).toBeInTheDocument();
    expect(listConversations).toHaveBeenLastCalledWith({
      page: 1,
      size: 15,
      search: undefined,
      archived: true,
    });
  });

  it('ignoresASlowerResponseForAnOlderQueryTest', async () => {
    const first = deferredPage();
    const second = deferredPage();
    listMock.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    const user = userEvent.setup();
    renderModal();
    await waitFor(() => expect(listConversations).toHaveBeenCalledTimes(1));

    await user.type(screen.getByRole('searchbox', { name: '搜索会话标题' }), '磁盘{Enter}');
    await waitFor(() => expect(listConversations).toHaveBeenCalledTimes(2));

    // The NEWER query answers first, then the older one lands late.
    await act(async () => {
      second.resolve(page([conversation(9, '磁盘水位巡检')], 1, 1));
    });
    expect(await screen.findByText('磁盘水位巡检')).toBeInTheDocument();

    await act(async () => {
      first.resolve(page([conversation(1, '陈旧结果')], 45, 1));
    });
    await waitFor(() => expect(listConversations).toHaveBeenCalledTimes(2));
    expect(screen.queryByText('陈旧结果')).not.toBeInTheDocument();
    expect(screen.getByText('磁盘水位巡检')).toBeInTheDocument();
  });

  it('navigatesFromTheTitleCellOnlyTest', async () => {
    listMock.mockResolvedValue(page([conversation(7, '检查集群状态')], 1, 1));
    const { onSelect } = renderModal();
    const row = (await screen.findByText('检查集群状态')).closest('tr');
    expect(row).not.toBeNull();

    // Clicking any other cell of the row must not navigate — no whole-row onClick.
    fireEvent.click(within(row as HTMLElement).getByText('claude-sonnet-4-5'));
    fireEvent.click(within(row as HTMLElement).getByText('已完成'));
    expect(onSelect).not.toHaveBeenCalled();

    fireEvent.click(screen.getByTestId('ai-conversation-link-7'));
    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(onSelect).toHaveBeenCalledWith(7);
  });

  it('marksTheOpenConversationInsteadOfOfferingItAsATargetTest', async () => {
    listMock.mockResolvedValue(
      page([conversation(7, '检查集群状态'), conversation(8, '另一个会话')], 2, 1),
    );
    const { onSelect } = renderModal({ activeConversationId: 7 });

    expect(await screen.findByText('当前会话')).toBeInTheDocument();
    expect(screen.queryByTestId('ai-conversation-link-7')).not.toBeInTheDocument();
    expect(screen.getByTestId('ai-conversation-link-8')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('ai-conversation-link-8'));
    expect(onSelect).toHaveBeenCalledWith(8);
  });

  it('showsAnEmptyStateWhenTheOwnerHasNoConversationsTest', async () => {
    listMock.mockResolvedValue(page([], 0, 1));
    renderModal();

    expect(await screen.findByText('暂无会话')).toBeInTheDocument();
  });

  it('offersARetryWhenTheListFailsTest', async () => {
    listMock.mockRejectedValueOnce(new Error('boom'));
    renderModal();

    expect(await screen.findByText('会话列表加载失败')).toBeInTheDocument();
    expect(screen.getByText('boom')).toBeInTheDocument();

    listMock.mockResolvedValueOnce(page([conversation(7, '检查集群状态')], 1, 1));
    fireEvent.click(screen.getByRole('button', { name: /重\s*试/ }));
    expect(await screen.findByText('检查集群状态')).toBeInTheDocument();
  });

  it('deletesASingleConversationFromItsRowActionTest', async () => {
    const user = userEvent.setup();
    listMock
      .mockResolvedValueOnce(
        page([conversation(7, '检查集群状态'), conversation(8, '另一个会话')], 2, 1),
      )
      .mockResolvedValue(page([conversation(8, '另一个会话')], 1, 1));
    deleteMock.mockResolvedValue(undefined);
    renderModal();
    await screen.findByText('检查集群状态');

    await user.click(screen.getByTestId('ai-conversation-row-delete-7'));
    await confirmDelete(user);

    await waitFor(() => expect(deleteMock).toHaveBeenCalledWith(7));
    expect(deleteMock).toHaveBeenCalledTimes(1);
    expect(await screen.findByText('已删除 1 条会话')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText('检查集群状态')).not.toBeInTheDocument());
    expect(screen.getByText('另一个会话')).toBeInTheDocument();
  });

  it('deletesTheSelectedConversationsTest', async () => {
    const user = userEvent.setup();
    listMock
      .mockResolvedValueOnce(
        page([conversation(7, '检查集群状态'), conversation(8, '另一个会话')], 2, 1),
      )
      .mockResolvedValue(page([], 0, 1));
    deleteMock.mockResolvedValue(undefined);
    renderModal();
    await screen.findByText('检查集群状态');

    // Index 0 is the header's select-all; the rows follow in order.
    const checkboxes = screen.getAllByRole('checkbox');
    await user.click(checkboxes[1]);
    await user.click(checkboxes[2]);
    await user.click(screen.getByTestId('ai-conversation-selected-delete'));
    await confirmDelete(user);

    await waitFor(() => expect(deleteMock).toHaveBeenCalledTimes(2));
    expect(deleteMock).toHaveBeenNthCalledWith(1, 7);
    expect(deleteMock).toHaveBeenNthCalledWith(2, 8);
    expect(await screen.findByText('已删除 2 条会话')).toBeInTheDocument();
    // Nothing survived, so nothing may stay selected: a stale selection would offer a second
    // "delete selected" for rows that no longer exist.
    await waitFor(() =>
      expect(screen.queryByTestId('ai-conversation-selected-delete')).not.toBeInTheDocument(),
    );
  });

  it('dropsTheSelectionWhenThePageChangesTest', async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    listMock.mockImplementation(async (params) =>
      params?.page === 2
        ? page([conversation(21, '会话二十一')], 45, 2)
        : page([conversation(1, '会话一')], 45, 1),
    );
    renderModal();
    await screen.findByText('会话一');

    // Index 0 is the header's select-all; the rows follow in order.
    await user.click(screen.getAllByRole('checkbox')[1]);
    expect(screen.getByTestId('ai-conversation-selected-delete')).toHaveTextContent('删除 (1)');

    fireEvent.click(screen.getByTitle('2'));
    await screen.findByText('会话二十一');

    // The ticked row is on page 1 and antd renders the checkbox column for the rows it is given, so
    // a selection that survived the page change is a row the operator cannot see — and the batch
    // delete would destroy it.
    expect(screen.queryByTestId('ai-conversation-selected-delete')).not.toBeInTheDocument();
    expect(screen.getAllByRole('checkbox')[1]).not.toBeChecked();
  });

  it('dropsTheSelectionWhenTheSearchChangesTest', async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    listMock.mockImplementation(async (params) =>
      params?.search === '会话二十一'
        ? page([conversation(21, '会话二十一')], 1, 1)
        : page([conversation(1, '会话一')], 1, 1),
    );
    renderModal();
    await screen.findByText('会话一');

    await user.click(screen.getAllByRole('checkbox')[1]);
    expect(screen.getByTestId('ai-conversation-selected-delete')).toBeInTheDocument();

    await user.type(screen.getByLabelText('搜索会话标题'), '会话二十一{enter}');
    await screen.findByText('会话二十一');

    expect(screen.queryByTestId('ai-conversation-selected-delete')).not.toBeInTheDocument();
    expect(screen.getAllByRole('checkbox')[1]).not.toBeChecked();
  });

  it('dropsTheSelectionWhenTheScopeChangesTest', async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    listMock.mockImplementation(async (params) =>
      params?.archived
        ? page([conversation(21, '已归档的会话')], 1, 1)
        : page([conversation(1, '会话一')], 1, 1),
    );
    renderModal();
    await screen.findByText('会话一');

    await user.click(screen.getAllByRole('checkbox')[1]);
    expect(screen.getByTestId('ai-conversation-selected-delete')).toBeInTheDocument();

    await user.click(screen.getByText('已归档'));
    await screen.findByText('已归档的会话');

    expect(screen.queryByTestId('ai-conversation-selected-delete')).not.toBeInTheDocument();
    expect(screen.getAllByRole('checkbox')[1]).not.toBeChecked();
  });

  it('deletesEveryConversationOnThePageTest', async () => {
    const user = userEvent.setup();
    listMock
      .mockResolvedValueOnce(
        page([conversation(7, '检查集群状态'), conversation(8, '另一个会话')], 2, 1),
      )
      .mockResolvedValue(page([], 0, 1));
    deleteMock.mockResolvedValue(undefined);
    renderModal();
    await screen.findByText('检查集群状态');

    await user.click(screen.getByTestId('ai-conversation-page-delete'));
    // "All" means the page in front of the operator, not every conversation the owner has: the
    // wording is what makes that scope visible before it is confirmed.
    expect(await screen.findByText('删除本页全部 2 条会话？')).toBeInTheDocument();
    await confirmDelete(user);

    await waitFor(() => expect(deleteMock).toHaveBeenCalledTimes(2));
    expect(deleteMock).toHaveBeenNthCalledWith(1, 7);
    expect(deleteMock).toHaveBeenNthCalledWith(2, 8);
  });

  it('keepsTheRowsWhoseDeleteFailedSelectedTest', async () => {
    const user = userEvent.setup();
    listMock
      .mockResolvedValueOnce(
        page([conversation(7, '检查集群状态'), conversation(8, '另一个会话')], 2, 1),
      )
      .mockResolvedValue(page([conversation(8, '另一个会话')], 1, 1));
    deleteMock.mockImplementation(async (conversationId) => {
      if (conversationId === 8) throw new Error('workspace is busy');
    });
    renderModal();
    await screen.findByText('检查集群状态');

    const checkboxes = screen.getAllByRole('checkbox');
    await user.click(checkboxes[1]);
    await user.click(checkboxes[2]);
    await user.click(screen.getByTestId('ai-conversation-selected-delete'));
    await confirmDelete(user);

    expect(await screen.findByText('已删除 1 条会话，1 条删除失败')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByTestId('ai-conversation-selected-delete')).toHaveTextContent('删除 (1)'),
    );
    expect(screen.getByText('另一个会话')).toBeInTheDocument();
  });

  it('leavesTheScreenWhenTheOpenConversationIsDeletedTest', async () => {
    const user = userEvent.setup();
    listMock.mockResolvedValue(page([conversation(7, '检查集群状态')], 1, 1));
    deleteMock.mockResolvedValue(undefined);
    const { onActiveDeleted } = renderModal({ activeConversationId: 7 });
    await screen.findByText('当前会话');

    await user.click(screen.getByTestId('ai-conversation-row-delete-7'));
    await confirmDelete(user);

    await waitFor(() => expect(onActiveDeleted).toHaveBeenCalledTimes(1));
    expect(deleteMock).toHaveBeenCalledWith(7);
  });

  it('stepsBackAPageWhenTheDeleteEmptiesItTest', async () => {
    const user = userEvent.setup();
    listMock.mockImplementation(async (params) =>
      params?.page === 2
        ? page([conversation(21, '会话二十一')], 21, 2)
        : page([conversation(1, '会话一')], 21, 1),
    );
    deleteMock.mockResolvedValue(undefined);
    renderModal();
    await screen.findByText('会话一');

    fireEvent.click(screen.getByTitle('2'));
    await screen.findByText('会话二十一');
    await user.click(screen.getByTestId('ai-conversation-row-delete-21'));
    await confirmDelete(user);

    // Page 2 has nothing left on it, so the list has to show page 1 rather than an empty page the
    // pager still counts.
    await waitFor(() =>
      expect(listConversations).toHaveBeenLastCalledWith({
        page: 1,
        size: 15,
        search: undefined,
        archived: false,
      }),
    );
    expect(await screen.findByText('会话一')).toBeInTheDocument();
  });

  it('disablesDeleteAllWhenThePageHasNothingToDeleteTest', async () => {
    listMock.mockResolvedValue(page([], 0, 1));
    renderModal();

    await screen.findByText('暂无会话');
    expect(screen.getByTestId('ai-conversation-page-delete')).toBeDisabled();
  });
});
