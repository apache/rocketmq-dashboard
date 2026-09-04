/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Drawer,
  Empty,
  Flex,
  Input,
  Popconfirm,
  Space,
  Table,
  Tag,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DeleteOutlined, EditOutlined, PlayCircleOutlined, SaveOutlined } from '@ant-design/icons';
import { useLang } from '../i18n/LangContext';
import {
  SAVED_MESSAGE_QUERIES_STORAGE_KEY,
  SAVED_MESSAGE_QUERY_NAME_LIMIT,
  addSavedMessageQuery,
  describeSavedMessageQuery,
  listSavedMessageQueries,
  loadSavedMessageQueries,
  removeSavedMessageQuery,
  renameSavedMessageQuery,
  type SavedMessageQuery,
  type SavedMessageQueryDraft,
  type SavedMessageQueryMutation,
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

const SavedMessageQueriesDrawer = ({ open, instanceId, currentQuery, onClose, onApply }: Props) => {
  const { t } = useLang();
  const [queries, setQueries] = useState<SavedMessageQuery[]>(loadSavedMessageQueries);
  const [search, setSearch] = useState('');
  const [newName, setNewName] = useState('');
  const [editingId, setEditingId] = useState<string>();
  const [editingName, setEditingName] = useState('');
  const [storageUnavailable, setStorageUnavailable] = useState(false);

  const reload = useCallback(() => {
    setQueries(loadSavedMessageQueries());
    setStorageUnavailable(false);
  }, []);

  useEffect(() => {
    const handleStorage = (event: StorageEvent) => {
      if (event.key === SAVED_MESSAGE_QUERIES_STORAGE_KEY) reload();
    };
    window.addEventListener('storage', handleStorage);
    return () => window.removeEventListener('storage', handleStorage);
  }, [reload]);

  const visibleQueries = useMemo(
    () => listSavedMessageQueries(queries, instanceId, search),
    [instanceId, queries, search],
  );

  const applyMutation = (result: SavedMessageQueryMutation, successKey: string): boolean => {
    if (result.ok) {
      setQueries(result.queries);
      setStorageUnavailable(false);
      message.success(t(successKey));
      return true;
    }
    if (result.reason === 'storage') {
      setStorageUnavailable(true);
      message.error(t('message.saved.storageFailed'));
    } else if (result.reason === 'duplicate') {
      message.warning(t('message.saved.duplicateName'));
    } else if (result.reason === 'not-found') {
      reload();
      message.warning(t('message.saved.notFound'));
    } else {
      message.warning(t('message.saved.invalid'));
    }
    return false;
  };

  const saveCurrent = () => {
    if (!currentQuery) {
      message.warning(t('message.saved.incomplete'));
      return;
    }
    if (
      applyMutation(addSavedMessageQuery(queries, newName, currentQuery), 'message.saved.saved')
    ) {
      setNewName('');
    }
  };

  const startRename = (query: SavedMessageQuery) => {
    setEditingId(query.id);
    setEditingName(query.name);
  };

  const finishRename = () => {
    if (!editingId) return;
    if (
      applyMutation(
        renameSavedMessageQuery(queries, editingId, editingName),
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
          <Button size="small" icon={<EditOutlined />} onClick={() => startRename(query)}>
            {t('common.edit')}
          </Button>
          <Popconfirm
            title={t('message.saved.deleteConfirm')}
            okText={t('common.confirm')}
            cancelText={t('common.cancel')}
            onConfirm={() =>
              applyMutation(removeSavedMessageQuery(queries, query.id), 'message.saved.deleted')
            }
          >
            <Button size="small" danger icon={<DeleteOutlined />} aria-label={t('common.delete')} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Drawer
      title={t('message.saved.title')}
      width={980}
      open={open}
      onClose={onClose}
      afterOpenChange={(visible) => {
        if (visible) reload();
      }}
      destroyOnHidden
    >
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
        {storageUnavailable && (
          <Alert type="error" showIcon message={t('message.saved.storageFailed')} />
        )}
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
            disabled={!instanceId || !currentQuery || !newName.trim()}
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
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={visibleQueries}
          locale={{ emptyText: <Empty description={t('message.saved.empty')} /> }}
          pagination={{ pageSize: 10, hideOnSinglePage: true }}
          scroll={{ x: 930 }}
        />
      </Space>
    </Drawer>
  );
};

export default SavedMessageQueriesDrawer;
