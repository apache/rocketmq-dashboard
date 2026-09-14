/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Empty, Flex, Input, Popconfirm, Space, Table, Tag, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DeleteOutlined, EditOutlined, PlayCircleOutlined, SaveOutlined } from '@ant-design/icons';
import { useLang } from '../i18n/LangContext';
import useAuthStore from '../stores/authStore';
import {
  SAVED_MESSAGE_QUERY_NAME_LIMIT,
  addSavedMessageQuery,
  describeSavedMessageQuery,
  listSavedMessageQueries,
  loadSavedMessageQueries,
  removeSavedMessageQuery,
  renameSavedMessageQuery,
  type SavedMessageQuery,
  type SavedMessageQueryDraft,
} from '../utils/savedMessageQueries';

interface Props {
  open: boolean;
  instanceId?: string;
  currentQuery?: SavedMessageQueryDraft;
  onClose: () => void;
  onApply: (query: SavedMessageQuery) => void;
}

const modeColor: Record<SavedMessageQuery['mode'], string> = {
  topic: 'blue',
  key: 'purple',
  msgid: 'cyan',
};

const SavedMessageQueriesPanel = ({ open, instanceId, currentQuery, onClose, onApply }: Props) => {
  const { t } = useLang();
  const canEdit = useAuthStore((state) => state.admin !== false);
  const [queries, setQueries] = useState<SavedMessageQuery[]>([]);
  const [search, setSearch] = useState('');
  const [newName, setNewName] = useState('');
  const [editingId, setEditingId] = useState<string>();
  const [editingName, setEditingName] = useState('');
  const [error, setError] = useState(false);
  const [loading, setLoading] = useState(false);
  const [mutating, setMutating] = useState(false);
  const requestId = useRef(0);
  const mutationPending = useRef(false);

  const reload = useCallback(async () => {
    const id = ++requestId.current;
    setQueries([]);
    setError(false);
    setLoading(true);
    try {
      const next = instanceId ? await loadSavedMessageQueries(instanceId) : [];
      if (id === requestId.current) setQueries(next);
    } catch {
      if (id === requestId.current) setError(true);
    } finally {
      if (id === requestId.current) setLoading(false);
    }
  }, [instanceId]);

  useEffect(() => {
    // 每次打开或切换实例均从服务端刷新，避免跨浏览器交接读取旧的本地副本。
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (open) void reload();
    return () => {
      requestId.current += 1;
    };
  }, [open, reload]);

  const visibleQueries = useMemo(
    () => listSavedMessageQueries(queries, instanceId, search),
    [instanceId, queries, search],
  );

  const mutate = async (operation: () => Promise<void>, successKey: string) => {
    if (!canEdit || mutationPending.current) return false;
    mutationPending.current = true;
    setMutating(true);
    const id = requestId.current;
    try {
      await operation();
      if (id !== requestId.current) return false;
      message.success(t(successKey));
      await reload();
      return true;
    } catch {
      if (id === requestId.current) message.error(t('message.saved.storageFailed'));
      return false;
    } finally {
      mutationPending.current = false;
      setMutating(false);
    }
  };

  const saveCurrent = async () => {
    if (!canEdit || !currentQuery || currentQuery.instanceId !== instanceId || !newName.trim())
      return;
    if (await mutate(() => addSavedMessageQuery(newName, currentQuery), 'message.saved.saved')) {
      setNewName('');
    }
  };

  const startRename = (query: SavedMessageQuery) => {
    setEditingId(query.id);
    setEditingName(query.name);
  };

  const finishRename = async () => {
    if (!canEdit || !editingId || !instanceId || !editingName.trim()) return;
    if (
      await mutate(
        () => renameSavedMessageQuery(instanceId, editingId, editingName),
        'message.saved.renamed',
      )
    ) {
      setEditingId(undefined);
      setEditingName('');
    }
  };

  const columns: ColumnsType<SavedMessageQuery> = [
    {
      title: t('message.saved.name'),
      dataIndex: 'name',
      width: 210,
      render: (name: string, query) =>
        editingId === query.id ? (
          <Input
            autoFocus
            aria-label={t('message.saved.renameInput')}
            maxLength={SAVED_MESSAGE_QUERY_NAME_LIMIT}
            value={editingName}
            onChange={(event) => setEditingName(event.target.value)}
            onPressEnter={finishRename}
            onBlur={finishRename}
          />
        ) : (
          <span title={name}>{name}</span>
        ),
    },
    {
      title: t('message.saved.mode'),
      dataIndex: 'mode',
      width: 110,
      render: (mode: SavedMessageQuery['mode']) => (
        <Tag color={modeColor[mode]}>{t(`message.saved.mode.${mode}`)}</Tag>
      ),
    },
    {
      title: t('message.saved.criteria'),
      key: 'criteria',
      ellipsis: true,
      render: (_, query) => (
        <span title={describeSavedMessageQuery(query)}>{describeSavedMessageQuery(query)}</span>
      ),
    },
    {
      title: t('message.saved.updatedAt'),
      dataIndex: 'updatedAt',
      width: 180,
      render: (updatedAt: number) => new Date(updatedAt).toLocaleString(),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 230,
      render: (_, query) => (
        <Space size={4}>
          <Button
            size="small"
            type="primary"
            icon={<PlayCircleOutlined />}
            onClick={() => {
              onApply(query);
              onClose();
            }}
          >
            {t('message.saved.apply')}
          </Button>
          <Button
            disabled={!canEdit || mutating}
            size="small"
            icon={<EditOutlined />}
            onClick={() => startRename(query)}
          >
            {t('common.edit')}
          </Button>
          <Popconfirm
            title={t('message.saved.deleteConfirm')}
            okText={t('common.confirm')}
            cancelText={t('common.cancel')}
            onConfirm={() =>
              mutate(() => removeSavedMessageQuery(instanceId!, query.id), 'message.saved.deleted')
            }
          >
            <Button
              disabled={!canEdit || mutating}
              size="small"
              danger
              icon={<DeleteOutlined />}
              aria-label={t('common.delete')}
            />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Alert
        type="info"
        showIcon
        message={t('message.saved.scopeTitle')}
        description={
          instanceId
            ? t('message.saved.scopeDescription', { instance: instanceId })
            : t('message.saved.selectInstance')
        }
      />
      {error && <Alert type="error" showIcon message={t('message.saved.loadFailed')} />}
      <Button onClick={() => void reload()} disabled={mutating} loading={loading}>
        {t('common.refresh')}
      </Button>
      <Flex gap={8} wrap>
        <Input
          aria-label={t('message.saved.nameInput')}
          placeholder={t('message.saved.namePlaceholder')}
          maxLength={SAVED_MESSAGE_QUERY_NAME_LIMIT}
          showCount
          value={newName}
          onChange={(event) => setNewName(event.target.value)}
          onPressEnter={saveCurrent}
          style={{ width: 360 }}
        />
        <Button
          type="primary"
          icon={<SaveOutlined />}
          loading={mutating}
          disabled={!canEdit || loading || !instanceId || !currentQuery || !newName.trim()}
          title={!currentQuery ? t('message.saved.incomplete') : undefined}
          onClick={saveCurrent}
        >
          {t('message.saved.saveCurrent')}
        </Button>
      </Flex>
      <Input.Search
        allowClear
        aria-label={t('message.saved.search')}
        placeholder={t('message.saved.searchPlaceholder')}
        value={search}
        onChange={(event) => setSearch(event.target.value)}
        style={{ width: 420 }}
      />
      <Table
        loading={loading || mutating}
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={visibleQueries}
        locale={{ emptyText: <Empty description={t('message.saved.empty')} /> }}
        pagination={{ pageSize: 10, hideOnSinglePage: true }}
        scroll={{ x: 930 }}
      />
    </Space>
  );
};

export default SavedMessageQueriesPanel;
