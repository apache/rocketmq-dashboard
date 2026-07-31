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

import { useState } from 'react';
import {
  Table,
  Button,
  Input,
  Tag,
  Card,
  Space,
  Switch,
} from 'antd';
import { MagnifyingGlass, Plus, ArrowClockwise, Users } from '@phosphor-icons/react';
import { useLang } from '../../i18n/LangContext';

// ─── Types ──────────────────────────────────────────────────────
interface GroupRecord {
  key: string;
  group: string;
  namespace: string;
  cluster: string;
  count: number;
  consumeType: string;
  messageModel: 'CLUSTERING' | 'BROADCASTING';
  diff: number;
  status: 'running' | 'warning' | 'stopped';
}

const groupData: GroupRecord[] = [];

// ─── Component ──────────────────────────────────────────────────
const GroupManagementPage = () => {
  const [searchText, setSearchText] = useState('');
  const [autoRefresh, setAutoRefresh] = useState(false);
  const { t } = useLang();

  const normalizedSearchText = searchText.trim().toLowerCase();
  const filteredGroupData = groupData.filter(
    (record) => !normalizedSearchText || record.group.toLowerCase().includes(normalizedSearchText),
  );

  const columns = [
    {
      title: t('groupMgmt.groupName'),
      dataIndex: 'group',
      key: 'group',
      render: (text: string) => (
        <span style={{ color: '#1677ff', fontWeight: 500 }}>
          {text}
        </span>
      ),
    },
    { title: t('groupMgmt.namespace'), dataIndex: 'namespace', key: 'namespace' },
    { title: t('groupMgmt.cluster'), dataIndex: 'cluster', key: 'cluster' },
    {
      title: t('groupMgmt.onlineInstances'),
      dataIndex: 'count',
      key: 'count',
      render: (count: number) => <span style={{ fontWeight: 500 }}>{count}</span>,
    },
    {
      title: t('groupMgmt.consumeMode'),
      dataIndex: 'messageModel',
      key: 'messageModel',
      render: (mode: string) => (
        <Tag color={mode === 'CLUSTERING' ? 'blue' : 'orange'}>
          {mode === 'CLUSTERING' ? t('groupMgmt.clustering') : t('groupMgmt.broadcasting')}
        </Tag>
      ),
    },
    {
      title: t('groupMgmt.diff'),
      dataIndex: 'diff',
      key: 'diff',
      render: (diff: number) => (
        <span
          style={{
            color: diff > 10000 ? '#ff4d4f' : diff > 0 ? '#fa8c16' : '#52c41a',
            fontWeight: 500,
          }}
        >
          {diff.toLocaleString()}
        </span>
      ),
    },
    {
      title: t('brokerCluster.status'),
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => {
        const config: Record<string, { color: string; label: string }> = {
          running: { color: 'success', label: t('brokerCluster.statusRunning') },
          warning: { color: 'warning', label: t('groupMgmt.backlogAlert') },
          stopped: { color: 'error', label: t('groupMgmt.stopped') },
        };
        const { color, label } = config[status] || config.running;
        return <Tag color={color}>{label}</Tag>;
      },
    },
    {
      title: t('common.actions'),
      key: 'action',
      render: () => (
        <Space size="small">
          <Button type="link" size="small" disabled>
            {t('common.detail')}
          </Button>
          <Button type="link" size="small" disabled>
            {t('brokerCluster.config')}
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 0 }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 20,
        }}
      >
        <h2
          style={{
            fontSize: 20,
            fontWeight: 600,
            margin: 0,
            display: 'flex',
            alignItems: 'center',
          }}
        >
          <Users size={22} style={{ marginRight: 8, color: '#1677ff' }} />
          {t('groupMgmt.title')}
        </h2>
        <Space size="middle">
          <Input
            placeholder={t('groupMgmt.searchPlaceholder')}
            prefix={<MagnifyingGlass size={14} />}
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            style={{ width: 240 }}
            allowClear
          />
          <Button type="primary" icon={<Plus size={14} />}>
            {t('groupMgmt.createGroup')}
          </Button>
          <Switch
            checked={autoRefresh}
            onChange={setAutoRefresh}
            checkedChildren={t('common.autoRefresh')}
            unCheckedChildren={t('groupMgmt.manual')}
            size="small"
          />
          <Button icon={<ArrowClockwise size={14} />} size="small">
            {t('common.reset')}
          </Button>
        </Space>
      </div>

      <Card bordered={false} style={{ borderRadius: 8, boxShadow: '0 1px 6px rgba(0,0,0,0.04)' }}>
        <Table
          columns={columns}
          dataSource={filteredGroupData}
          locale={{ emptyText: t('groupMgmt.providerUnavailable') }}
          pagination={{
            pageSize: 10,
            showTotal: (total) => `${t('common.total')} ${total} Group`,
            showSizeChanger: true,
          }}
          size="middle"
        />
      </Card>
    </div>
  );
};

export default GroupManagementPage;
