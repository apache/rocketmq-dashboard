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

import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { Button, Flex, Table, Tag, Tooltip, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ArrowClockwise } from '@phosphor-icons/react';
import type { AuditRecord } from '../../api/ops';
import { listAuditRecords } from '../../api/ops';
import InfoBanner from '../../components/InfoBanner';
import { useLang } from '../../i18n/LangContext';
import { formatUtcDateTime } from '../../utils/format';
import { tableScrollX } from '../../utils/table';
import { getAuditOperationPresentation, getAuditResultPresentation } from './auditPresentation';

// 集群事件 = 运维审计里已记录的控制面变更：Broker / 集群配置修改、Broker 重启、
// Proxy 配置重载与地址增删。复用审计存储，不另建事件表。
export const CLUSTER_EVENT_OPERATIONS = [
  'UPDATE_BROKER_CONFIG',
  'UPDATE_CLUSTER_CONFIG',
  'RESTART_BROKER',
  'RELOAD_PROXY_CONFIG',
  'ADD_PROXY_ADDRESS',
  'REMOVE_PROXY_ADDRESS',
];

const PAGE_SIZE_OPTIONS = [20, 50, 100];

const ClusterEventsTab = () => {
  const { t } = useLang();
  const [items, setItems] = useState<AuditRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [refreshVersion, setRefreshVersion] = useState(0);

  useEffect(() => {
    let active = true;
    // setLoading 用微任务 defer，规避 react-hooks/set-state-in-effect（同 UserManagement 手法）。
    Promise.resolve().then(() => {
      if (active) setLoading(true);
    });
    listAuditRecords({ operationTypes: CLUSTER_EVENT_OPERATIONS, page, pageSize })
      .then((result) => {
        if (!active) return;
        setItems(result.items);
        setTotal(result.total);
      })
      .catch(() => {
        if (active) message.error(t('clusterEvents.loadFailed'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [page, pageSize, refreshVersion, t]);

  const columns: ColumnsType<AuditRecord> = [
    {
      title: t('clusterEvents.time'),
      dataIndex: 'timestamp',
      width: 170,
      align: 'right',
      render: (value: string) => formatUtcDateTime(value),
    },
    {
      title: t('clusterEvents.event'),
      dataIndex: 'operationType',
      width: 150,
      align: 'center',
      render: (value: string) => {
        const presentation = getAuditOperationPresentation(value);
        return (
          <Tag color={presentation.color}>
            {presentation.labelKey ? t(presentation.labelKey) : presentation.label}
          </Tag>
        );
      },
    },
    {
      title: t('clusterEvents.target'),
      dataIndex: 'target',
      width: 200,
      ellipsis: true,
      render: (value: string | null) => value || '-',
    },
    {
      title: t('clusterEvents.cluster'),
      dataIndex: 'clusterId',
      width: 120,
      ellipsis: true,
      render: (value: string | null) => value || '-',
    },
    {
      title: t('clusterEvents.operator'),
      dataIndex: 'operator',
      width: 120,
      ellipsis: true,
    },
    {
      title: t('clusterEvents.result'),
      dataIndex: 'result',
      width: 90,
      align: 'center',
      render: (value: string) => {
        const presentation = getAuditResultPresentation(value);
        return (
          <Tag color={presentation.color}>
            {presentation.labelKey ? t(presentation.labelKey) : presentation.label}
          </Tag>
        );
      },
    },
    {
      title: t('clusterEvents.detail'),
      dataIndex: 'detail',
      ellipsis: true,
      render: (value: string) => (
        <Tooltip title={value} placement="topLeft">
          <span>{value || '-'}</span>
        </Tooltip>
      ),
    },
  ];

  const refreshButton = (
    <Button
      icon={<ArrowClockwise size={16} />}
      loading={loading}
      onClick={() => setRefreshVersion((value) => value + 1)}
    >
      {t('common.refresh')}
    </Button>
  );

  // 刷新跟其余 Tab 的操作区一样挂在 Tab 栏右侧，不单独占一行。目标容器首帧还不存在，
  // setState 用定时器 defer 并规避 set-state-in-effect。
  const [tabExtraTarget, setTabExtraTarget] = useState<HTMLElement | null>(null);
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setTabExtraTarget(document.getElementById('cluster-alerts-tab-extra'));
    }, 0);
    return () => window.clearTimeout(timer);
  }, []);

  return (
    <Flex vertical gap={12}>
      {tabExtraTarget && createPortal(refreshButton, tabExtraTarget)}
      <InfoBanner
        title={t('clusterEvents.bannerTitle')}
        description={t('clusterEvents.bannerDescription')}
      />
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={items}
        tableLayout="fixed"
        scroll={{ x: tableScrollX(columns) }}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          pageSizeOptions: PAGE_SIZE_OPTIONS.map(String),
          showTotal: (count) => t('clusterEvents.total', { count }),
          onChange: (nextPage, nextPageSize) => {
            if (nextPageSize !== pageSize) {
              setPage(1);
              setPageSize(nextPageSize);
            } else {
              setPage(nextPage);
            }
          },
        }}
      />
    </Flex>
  );
};

export default ClusterEventsTab;
