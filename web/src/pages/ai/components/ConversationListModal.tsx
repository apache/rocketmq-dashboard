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

import { useCallback, useMemo, useState, type Key } from 'react';
import {
  Alert,
  Button,
  Flex,
  Input,
  Modal,
  Popconfirm,
  Segmented,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { Archive, TrayArrowUp, Trash } from '@phosphor-icons/react';
import type { ColumnsType } from 'antd/es/table';
import { useLang } from '../../../i18n/LangContext';
import type { AiConversationListItemVO, RunStatus } from '../../../api/aiEvents';
import { deleteConversation, updateConversation } from '../../../api/aiConversations';
import { useConversationList } from '../hooks/useConversationList';
import { formatUtcDateTime } from '../../../utils/format';
import { tableScrollX } from '../../../utils/table';
import ModelBadge from './ModelBadge';

/**
 * Server-paged conversation history.
 *
 * Replaces the `sessionStorage` recents list: conversations now live in `rmq_ai_conversation` and
 * are owned per user, so paging, the title filter and the archived scope are all server-side and this
 * component is a thin view over `useConversationList`.
 *
 * ─── Panel is a child component, not an inline block ───────────
 * The hook must not run while the modal is closed: it would fire a list request on every page load
 * of `/ai` and again on every unrelated re-render of the page. antd does not mount modal children
 * until the first open, and `destroyOnHidden` unmounts them again on close, so the query is re-run
 * each time the operator opens history — which is exactly the freshness you want from a list somebody
 * else's tab may have changed.
 *
 * ─── Project table rules that bind here ────────────────────────
 * `tableScrollX(columns)` derives `scroll.x` from the declared widths (never a magic number) and the
 * modal is sized from the same value so the container is at least as wide as the table and no
 * horizontal scrollbar appears at a normal window width. Navigation happens from the TITLE cell only
 * — black, bold, pointer — and never from a whole-row `onClick`, which is how every other list in
 * Studio behaves and which keeps an accidental click from teleporting the reader. Timestamps go
 * through `formatUtcDateTime` because the backend writes `Clock.systemUTC()` `LocalDateTime` values
 * with no offset.
 *
 * Column widths are the *measured* width of the widest value each column carries, not a guess:
 * `index.css` sets `.ant-table-cell { white-space: nowrap }` project-wide, so a value wider than its
 * column does not wrap — it spills out of the cell, inflates the table's `scrollWidth` past the
 * `scroll.x` above, and buys a horizontal scrollbar even when the modal is wide enough. A `claude-code`
 * engine tag is 109px and `2026-09-20 17:22:57` is 166px; anything narrower than that overflows by
 * the difference. Columns whose values are unbounded instead of merely wide (title, instance) keep
 * `ellipsis`, which clips inside the cell and therefore cannot spill.
 */

/** antd's modal body padding (24px per side) that the table width has to fit inside. */
const MODAL_BODY_PADDING = 48;

/**
 * Room the table body's own vertical scrollbar takes, reserved in the modal width.
 *
 * Setting `scroll.y` makes antd render the body as a separate scrolling table with
 * `overflow-y: scroll` — always a track, not `auto` — so the body's content box is one scrollbar
 * narrower than the modal body it sits in. Without this allowance a table sized to exactly fit the
 * modal overflows by that scrollbar and the horizontal one comes straight back.
 *
 * 20px is an upper bound, not a measurement: the widest classic scrollbar this has to survive is
 * Windows Chrome's 17px (Linux Chrome and Firefox are 15px, macOS overlay scrollbars take nothing),
 * and antd sizes the header's filler cell from the value it measures at runtime, so the two cannot
 * disagree. Whatever is left over goes to the flexible title column, which is why over-reserving
 * costs a sliver of title width and under-reserving costs a scrollbar.
 */
const VERTICAL_SCROLLBAR_WIDTH = 20;

/**
 * Everything the modal needs besides the table body, measured in the browser: 52 header, 40 content
 * padding, 44 toolbar, 40 pagination, 39 table header row, 24 the gap antd's `centered` modal keeps
 * at the bottom of the viewport. Subtracting it from the viewport caps the modal at the screen
 * height, so the overlay itself never scrolls — a full 15-row page is ~796px tall and fits a 900px
 * window, and on a shorter one the cap binds instead of the overlay scrolling away the search bar
 * and the pager.
 */
const MODAL_CHROME_HEIGHT = 239;

/** The rows scroll here; the header, the toolbar and the pager above and below them do not. */
const TABLE_BODY_HEIGHT = `calc(100vh - ${MODAL_CHROME_HEIGHT}px)`;

/**
 * Declared column widths, the single source of truth for both the table and the modal width: the
 * modal has to be sized before the panel mounts, so measuring the rendered table is not an option,
 * and a second hard-coded list would drift the first time a column is added.
 *
 * `title` is a `minWidth`: it is the one column allowed to grow, so a window wider than the table
 * does not inflate every other column proportionally.
 */
const COLUMN_WIDTHS = {
  title: 180,
  engine: 112,
  model: 132,
  mode: 80,
  instance: 120,
  status: 106,
  updatedAt: 176,
  action: 88,
} as const;

const TABLE_SCROLL_X = tableScrollX(
  [
    { minWidth: COLUMN_WIDTHS.title },
    { width: COLUMN_WIDTHS.engine },
    { width: COLUMN_WIDTHS.model },
    { width: COLUMN_WIDTHS.mode },
    { width: COLUMN_WIDTHS.instance },
    { width: COLUMN_WIDTHS.status },
    { width: COLUMN_WIDTHS.updatedAt },
    { width: COLUMN_WIDTHS.action },
  ],
  { selection: true },
);

const RUN_STATUS_TAG_COLOR: Record<RunStatus, string> = {
  QUEUED: 'default',
  RUNNING: 'processing',
  COMPLETED: 'success',
  STOPPED: 'warning',
  FAILED: 'error',
};

const CHAT_MODE_KEYS = new Set(['chat', 'diagnose', 'manage', 'query']);

export interface ConversationListModalProps {
  open: boolean;
  onClose: () => void;
  /** Conversation on screen; marked as current and not offered as a navigation target. */
  activeConversationId?: number | null;
  /** Fired from the title cell only. The caller navigates and closes the modal. */
  onSelect: (conversationId: number) => void;
  /**
   * Fired when a delete removed the conversation currently on screen. Its transcript is gone, so the
   * caller has to leave `/ai/c/{id}` — this component does not know the router.
   */
  onActiveDeleted?: () => void;
}

const ConversationListPanel = ({
  activeConversationId,
  onSelect,
  onActiveDeleted,
}: Omit<ConversationListModalProps, 'open' | 'onClose'>) => {
  const { t } = useLang();
  const list = useConversationList();
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([]);
  const [deletingIds, setDeletingIds] = useState<readonly number[]>([]);
  const [archivingIds, setArchivingIds] = useState<readonly number[]>([]);
  const deleting = deletingIds.length > 0;
  // Pulled out of `list` for the callback below: the hook returns a fresh object every render, so
  // depending on it would rebuild `columns` — and with it every cell — on each one.
  const { items, page, setPage, reload, archived } = list;
  const rowCount = items.length;

  /**
   * Drops every ticked row. The selection belongs to the page and the scope it was made on — antd
   * renders the checkbox column for the rows it is given — so a key that survived a page, search or
   * scope change is a row the operator cannot see any more, and the batch delete would act on it.
   * Same defect and same fix as the topic and consumer group lists (see "clear hidden resource
   * selections").
   */
  const clearSelection = useCallback(() => setSelectedRowKeys([]), []);

  /**
   * Flips the row's archived flag (PATCH) and reloads the scope: an archived conversation leaves
   * the active list and the other way round, so the row disappearing IS the feedback — the toast
   * only names what happened. Archiving the conversation currently on screen is allowed: the
   * transcript stays open, the flag only files it out of the active list.
   */
  const toggleArchive = useCallback(
    async (id: number) => {
      setArchivingIds((ids) => [...ids, id]);
      try {
        await updateConversation(id, { archived: !archived });
        // The row leaves the scope it was listed in, so its tick goes with it. Keeping it would put
        // the list back where `clearSelection` keeps it out of: a batch delete acting on a row the
        // operator can no longer see. The other ticks still refer to visible rows and stay.
        setSelectedRowKeys((keys) => keys.filter((key) => Number(key) !== id));
        message.success(archived ? t('ai.list.unarchivedToast') : t('ai.list.archivedToast'));
        reload();
      } catch {
        message.error(t('ai.list.archiveFailed'));
      } finally {
        setArchivingIds((ids) => ids.filter((entry) => entry !== id));
      }
    },
    [archived, reload, t],
  );

  /**
   * Hard-deletes conversations one request at a time.
   *
   * There is no batch endpoint on this surface, and at one page of 15 rows there does not need to be:
   * each delete stops an active run, clears three tables and unlinks the agent workspace, so firing
   * 15 of them concurrently would only contend on the same directory tree. Failures are collected
   * rather than thrown — the caller asked for N deletions and deserves to know which of the N
   * survived, exactly as the topic list's batch delete reports `{deleted, failed}`.
   */
  const remove = useCallback(
    async (ids: readonly number[]) => {
      if (ids.length === 0) return;
      setDeletingIds(ids);
      const failed: number[] = [];
      for (const id of ids) {
        try {
          await deleteConversation(id);
        } catch {
          failed.push(id);
        }
      }
      const deletedCount = ids.length - failed.length;
      setDeletingIds([]);
      setSelectedRowKeys((keys) => keys.filter((key) => failed.includes(Number(key))));
      if (failed.length === 0) {
        message.success(t('ai.list.deleted', { count: deletedCount }));
      } else if (deletedCount > 0) {
        message.warning(
          t('ai.list.deletePartial', { deleted: deletedCount, failed: failed.length }),
        );
      } else {
        message.error(t('ai.list.deleteFailed'));
      }
      if (activeConversationId != null && ids.includes(activeConversationId)) {
        onActiveDeleted?.();
        return;
      }
      // A page emptied by the delete has to fall back one, or the pager keeps offering a page that
      // no longer exists; `setPage` re-queries through the hook, `reload` covers every other case.
      if (deletedCount > 0 && deletedCount >= rowCount && page > 1) {
        setPage(page - 1);
      } else {
        reload();
      }
    },
    [activeConversationId, onActiveDeleted, page, reload, rowCount, setPage, t],
  );

  const columns = useMemo<ColumnsType<AiConversationListItemVO>>(
    () => [
      {
        title: t('ai.list.columnTitle'),
        dataIndex: 'title',
        key: 'title',
        // `minWidth` rather than `width`: see COLUMN_WIDTHS.
        minWidth: COLUMN_WIDTHS.title,
        ellipsis: { showTitle: false },
        render: (title: string, row) =>
          row.id === activeConversationId ? (
            <Flex align="center" gap={8}>
              <Typography.Text strong style={{ fontSize: 14 }} title={title}>
                {title}
              </Typography.Text>
              <Tag style={{ marginInlineEnd: 0, fontSize: 14 }}>{t('ai.list.current')}</Tag>
            </Flex>
          ) : (
            <Typography.Text
              strong
              style={{ fontSize: 14, cursor: 'pointer' }}
              title={title}
              data-testid={`ai-conversation-link-${row.id}`}
              onClick={() => onSelect(row.id)}
            >
              {title}
            </Typography.Text>
          ),
      },
      {
        title: t('ai.list.columnEngine'),
        dataIndex: 'engine',
        key: 'engine',
        width: COLUMN_WIDTHS.engine,
        render: (engine: string) => (
          <Tag style={{ marginInlineEnd: 0, fontSize: 14 }}>{engine}</Tag>
        ),
      },
      {
        title: t('ai.list.columnModel'),
        dataIndex: 'model',
        key: 'model',
        width: COLUMN_WIDTHS.model,
        // The same brand mark the composer's model selector shows, so a conversation's model
        // reads at a glance; the name truncates inside the fixed column width.
        render: (model: string) => (
          <Flex align="center" gap={6} style={{ minWidth: 0 }}>
            <ModelBadge model={model ?? ''} />
            <span
              style={{
                fontSize: 14,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
              title={model}
            >
              {model}
            </span>
          </Flex>
        ),
      },
      {
        title: t('ai.list.columnMode'),
        dataIndex: 'mode',
        key: 'mode',
        width: COLUMN_WIDTHS.mode,
        render: (mode: string) => (CHAT_MODE_KEYS.has(mode) ? t(`ai.mode.${mode}`) : mode),
      },
      {
        title: t('ai.list.columnInstance'),
        dataIndex: 'instanceId',
        key: 'instanceId',
        width: COLUMN_WIDTHS.instance,
        ellipsis: true,
        render: (instanceId?: string | null) => instanceId || '-',
      },
      {
        title: t('ai.list.columnStatus'),
        dataIndex: 'lastRunStatus',
        key: 'lastRunStatus',
        width: COLUMN_WIDTHS.status,
        render: (status?: RunStatus | null) =>
          status ? (
            <Tag
              color={RUN_STATUS_TAG_COLOR[status] ?? 'default'}
              style={{ marginInlineEnd: 0, fontSize: 14 }}
            >
              {t(`ai.runStatus.${status}`)}
            </Tag>
          ) : (
            '-'
          ),
      },
      {
        title: t('ai.list.columnUpdatedAt'),
        dataIndex: 'updatedAt',
        key: 'updatedAt',
        width: COLUMN_WIDTHS.updatedAt,
        // No zone name: it is the viewer's own and identical on every row, so it would only make
        // this the widest column in the table to say something the reader already knows.
        render: (updatedAt: string) => formatUtcDateTime(updatedAt, undefined, { zone: false }),
      },
      {
        title: t('ai.list.columnAction'),
        key: 'action',
        width: COLUMN_WIDTHS.action,
        render: (_: unknown, row) => (
          <Flex align="center" gap={0}>
            <Button
              type="text"
              size="small"
              loading={archivingIds.includes(row.id)}
              aria-label={
                archived
                  ? t('ai.list.unarchiveAria', { title: row.title })
                  : t('ai.list.archiveAria', { title: row.title })
              }
              title={
                archived
                  ? t('ai.list.unarchiveAria', { title: row.title })
                  : t('ai.list.archiveAria', { title: row.title })
              }
              data-testid={`ai-conversation-row-archive-${row.id}`}
              icon={archived ? <TrayArrowUp size={16} /> : <Archive size={16} />}
              onClick={() => void toggleArchive(row.id)}
            />
            <Popconfirm
              title={t('ai.list.deleteConfirm')}
              description={t('ai.list.deleteHint')}
              okText={t('common.delete')}
              cancelText={t('common.cancel')}
              okButtonProps={{ danger: true }}
              onConfirm={() => void remove([row.id])}
            >
              <Button
                type="text"
                size="small"
                danger
                loading={deletingIds.includes(row.id)}
                aria-label={t('ai.list.deleteAria', { title: row.title })}
                data-testid={`ai-conversation-row-delete-${row.id}`}
                icon={<Trash size={16} />}
              />
            </Popconfirm>
          </Flex>
        ),
      },
    ],
    [activeConversationId, archivingIds, archived, deletingIds, onSelect, remove, t, toggleArchive],
  );

  return (
    <Flex vertical gap={12}>
      <Flex gap={12} wrap="wrap" align="center" justify="space-between">
        <Flex gap={12} wrap="wrap" align="center" style={{ flex: '1 1 320px' }}>
          <Input.Search
            aria-label={t('ai.list.search')}
            allowClear
            placeholder={t('ai.list.search')}
            // `setSearch` jumps back to page 1 inside the hook: page 7 of a different search does not
            // exist, and asking for it would render an empty table with a stale pagination bar.
            onSearch={(value) => {
              clearSelection();
              list.setSearch(value.trim());
            }}
            style={{ flex: '1 1 240px' }}
          />
          <Segmented
            value={list.archived ? 'archived' : 'active'}
            onChange={(value) => {
              clearSelection();
              list.setArchived(value === 'archived');
            }}
            options={[
              { value: 'active', label: t('ai.list.scopeActive') },
              { value: 'archived', label: t('ai.list.scopeArchived') },
            ]}
          />
        </Flex>
        <Flex gap={8} align="center">
          {/* Multi-select first: it is the narrower of the two destructive actions, and the checkbox
              column antd renders selects the current page only — so the two never disagree about
              what "all" means. */}
          {selectedRowKeys.length > 0 && (
            <Popconfirm
              title={t('ai.list.deleteSelectedConfirm', { count: selectedRowKeys.length })}
              description={t('ai.list.deleteHint')}
              okText={t('common.delete')}
              cancelText={t('common.cancel')}
              okButtonProps={{ danger: true }}
              onConfirm={() => void remove(selectedRowKeys.map(Number))}
            >
              <Button danger loading={deleting} data-testid="ai-conversation-selected-delete">
                {t('ai.list.deleteSelected', { count: selectedRowKeys.length })}
              </Button>
            </Popconfirm>
          )}
          <Popconfirm
            title={t('ai.list.deletePageConfirm', { count: rowCount })}
            description={t('ai.list.deleteHint')}
            okText={t('common.delete')}
            cancelText={t('common.cancel')}
            okButtonProps={{ danger: true }}
            disabled={rowCount === 0}
            onConfirm={() => void remove(items.map((item) => item.id))}
          >
            <Button
              danger
              disabled={rowCount === 0}
              loading={deleting}
              data-testid="ai-conversation-page-delete"
            >
              {t('ai.list.deletePage')}
            </Button>
          </Popconfirm>
        </Flex>
      </Flex>

      {list.error && (
        <Alert
          type="error"
          showIcon
          message={t('ai.list.loadFailed')}
          description={list.error}
          action={
            <Button size="small" onClick={list.reload}>
              {t('common.retry')}
            </Button>
          }
        />
      )}

      <Table<AiConversationListItemVO>
        rowKey="id"
        size="small"
        loading={list.loading}
        columns={columns}
        dataSource={items}
        tableLayout="fixed"
        rowSelection={{ selectedRowKeys, onChange: setSelectedRowKeys }}
        scroll={{ x: TABLE_SCROLL_X, y: TABLE_BODY_HEIGHT }}
        locale={{ emptyText: t('ai.list.empty') }}
        pagination={{
          current: list.page,
          pageSize: list.pageSize,
          total: list.total,
          showSizeChanger: false,
          onChange: (nextPage) => {
            clearSelection();
            list.setPage(nextPage);
          },
        }}
      />
    </Flex>
  );
};

const ConversationListModal = ({
  open,
  onClose,
  activeConversationId = null,
  onSelect,
  onActiveDeleted,
}: ConversationListModalProps) => {
  const { t } = useLang();

  return (
    <Modal
      title={t('ai.history.title')}
      open={open}
      onCancel={onClose}
      footer={null}
      centered
      width={TABLE_SCROLL_X + MODAL_BODY_PADDING + VERTICAL_SCROLLBAR_WIDTH}
      destroyOnHidden
    >
      <ConversationListPanel
        activeConversationId={activeConversationId}
        onSelect={onSelect}
        onActiveDeleted={onActiveDeleted}
      />
    </Modal>
  );
};

export default ConversationListModal;
