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

import { useMemo, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Drawer,
  Empty,
  Flex,
  Input,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { DownloadOutlined, SwapOutlined } from '@ant-design/icons';
import type { Instance } from '../api/instance';
import type { AclRule, AclUser } from '../api/acl';
import { listAclRules, pageAclUsers } from '../services/aclService';
import { useLang } from '../i18n/LangContext';
import { buildCsv, downloadCsv, type CsvColumn } from '../utils/download';
import {
  compareAclPolicies,
  filterAclPolicyComparisonRows,
  formatAclDifferences,
  type AclComparableField,
  type AclComparisonKind,
  type AclComparisonStatus,
  type AclPolicyComparisonResult,
  type AclPolicyComparisonRow,
} from '../utils/aclPolicyComparison';

interface AclPolicyComparisonDrawerProps {
  open: boolean;
  instances: Instance[];
  currentInstanceId?: string;
  onClose: () => void;
}

const PAGE_SIZE = 100;
const MAX_PAGES = 100;

const loadAllRules = async (instanceId: string): Promise<AclRule[]> => {
  const rules: AclRule[] = [];
  for (let page = 1; page <= MAX_PAGES; page += 1) {
    const result = await listAclRules({ instanceId, page, pageSize: PAGE_SIZE });
    rules.push(...result.items);
    if (result.items.length === 0 || rules.length >= result.total) return rules;
  }
  throw new Error(`ACL rule inventory exceeded ${MAX_PAGES} pages`);
};

const loadAllUsers = async (instanceId: string): Promise<AclUser[]> => {
  const users: AclUser[] = [];
  for (let page = 1; page <= MAX_PAGES; page += 1) {
    const result = await pageAclUsers({ instanceId, page, pageSize: PAGE_SIZE });
    users.push(...result.items);
    if (result.items.length === 0 || users.length >= result.total) return users;
  }
  throw new Error(`ACL user inventory exceeded ${MAX_PAGES} pages`);
};

const STATUS_COLORS: Record<AclComparisonStatus, string> = {
  MATCH: 'success',
  DRIFT: 'warning',
  ONLY_SOURCE: 'blue',
  ONLY_TARGET: 'purple',
};

const STATUS_LABELS: Record<AclComparisonStatus, string> = {
  MATCH: 'aclCompare.statusMatch',
  DRIFT: 'aclCompare.statusDrift',
  ONLY_SOURCE: 'aclCompare.statusOnlySource',
  ONLY_TARGET: 'aclCompare.statusOnlyTarget',
};

const KIND_LABELS: Record<AclComparisonKind, string> = {
  USER: 'aclCompare.kindUser',
  RULE: 'aclCompare.kindRule',
};

const FIELD_LABELS: Record<AclComparableField, string> = {
  admin: 'aclCompare.fieldAdmin',
  clusters: 'aclCompare.fieldClusters',
  permRead: 'aclCompare.fieldRead',
  permWrite: 'aclCompare.fieldWrite',
  actions: 'aclCompare.fieldActions',
  decision: 'aclCompare.fieldDecision',
  aclVersion: 'aclCompare.fieldVersion',
};

const CSV_COLUMNS: CsvColumn<AclPolicyComparisonRow>[] = [
  { header: 'Kind', value: (row) => row.kind },
  { header: 'Identity', value: (row) => row.identity },
  { header: 'Status', value: (row) => row.status },
  { header: 'Differences', value: (row) => formatAclDifferences(row.differences) },
  { header: 'Source Admin', value: (row) => row.sourceUser?.admin },
  { header: 'Target Admin', value: (row) => row.targetUser?.admin },
  { header: 'Source Clusters', value: (row) => row.sourceUser?.clusters.join(';') },
  { header: 'Target Clusters', value: (row) => row.targetUser?.clusters.join(';') },
  { header: 'Source Actions', value: (row) => row.sourceRule?.actions.join(';') },
  { header: 'Target Actions', value: (row) => row.targetRule?.actions.join(';') },
  { header: 'Source Decision', value: (row) => row.sourceRule?.decision },
  { header: 'Target Decision', value: (row) => row.targetRule?.decision },
  { header: 'Source ACL Version', value: (row) => row.sourceRule?.aclVersion },
  { header: 'Target ACL Version', value: (row) => row.targetRule?.aclVersion },
];

const AclPolicyComparisonDrawer = ({
  open,
  instances,
  currentInstanceId,
  onClose,
}: AclPolicyComparisonDrawerProps) => {
  const { t } = useLang();
  const initialSource =
    currentInstanceId && instances.some((instance) => instance.name === currentInstanceId)
      ? currentInstanceId
      : instances[0]?.name;
  const [sourceInstanceId, setSourceInstanceId] = useState<string | undefined>(initialSource);
  const [targetInstanceId, setTargetInstanceId] = useState<string | undefined>(
    instances.find((instance) => instance.name !== initialSource)?.name,
  );
  const [result, setResult] = useState<AclPolicyComparisonResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [kindFilter, setKindFilter] = useState<AclComparisonKind | 'ALL'>('ALL');
  const [statusFilter, setStatusFilter] = useState<AclComparisonStatus | 'ALL'>('ALL');
  const [search, setSearch] = useState('');
  const requestIdRef = useRef(0);

  const options = instances.map((instance) => ({ value: instance.name, label: instance.name }));
  const visibleRows = useMemo(
    () => filterAclPolicyComparisonRows(result?.rows ?? [], kindFilter, statusFilter, search),
    [kindFilter, result, search, statusFilter],
  );

  const runComparison = async () => {
    if (!sourceInstanceId || !targetInstanceId || sourceInstanceId === targetInstanceId) {
      message.warning(t('aclCompare.selectDifferentInstances'));
      return;
    }
    const requestId = ++requestIdRef.current;
    setLoading(true);
    try {
      const [sourceUsers, targetUsers, sourceRules, targetRules] = await Promise.all([
        loadAllUsers(sourceInstanceId),
        loadAllUsers(targetInstanceId),
        loadAllRules(sourceInstanceId),
        loadAllRules(targetInstanceId),
      ]);
      if (requestId === requestIdRef.current) {
        setResult(compareAclPolicies(sourceUsers, targetUsers, sourceRules, targetRules));
        setKindFilter('ALL');
        setStatusFilter('ALL');
        setSearch('');
      }
    } catch {
      if (requestId === requestIdRef.current) message.error(t('aclCompare.loadFailed'));
    } finally {
      if (requestId === requestIdRef.current) setLoading(false);
    }
  };

  const swapInstances = () => {
    setSourceInstanceId(targetInstanceId);
    setTargetInstanceId(sourceInstanceId);
    setResult(null);
  };

  const exportComparison = () => {
    if (!result || !sourceInstanceId || !targetInstanceId) return;
    downloadCsv(
      `rocketmq-acl-policy-${sourceInstanceId}-vs-${targetInstanceId}.csv`,
      buildCsv(CSV_COLUMNS, visibleRows),
    );
    message.success(t('aclCompare.exported', { count: visibleRows.length }));
  };

  const columns: TableColumnsType<AclPolicyComparisonRow> = [
    {
      title: t('aclCompare.kind'),
      dataIndex: 'kind',
      key: 'kind',
      width: 110,
      render: (kind: AclComparisonKind) => <Tag>{t(KIND_LABELS[kind])}</Tag>,
    },
    {
      title: t('aclCompare.identity'),
      dataIndex: 'identity',
      key: 'identity',
      ellipsis: true,
      sorter: (left, right) => left.identity.localeCompare(right.identity),
      render: (identity: string) => <span title={identity}>{identity}</span>,
    },
    {
      title: t('aclCompare.status'),
      dataIndex: 'status',
      key: 'status',
      width: 140,
      render: (status: AclComparisonStatus) => (
        <Tag color={STATUS_COLORS[status]}>{t(STATUS_LABELS[status])}</Tag>
      ),
    },
    {
      title: t('aclCompare.differenceCount'),
      key: 'differenceCount',
      width: 130,
      render: (_, row) => row.differences.length,
    },
  ];

  return (
    <Drawer
      title={t('aclCompare.title')}
      open={open}
      onClose={onClose}
      width={1040}
      destroyOnHidden
    >
      <Flex vertical gap={16}>
        <Alert type="info" showIcon message={t('aclCompare.secretNotice')} />
        <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
          {t('aclCompare.description')}
        </Typography.Paragraph>
        <Flex gap={8} align="end" wrap="wrap">
          <label style={{ flex: 1, minWidth: 220 }}>
            <Typography.Text>{t('aclCompare.sourceInstance')}</Typography.Text>
            <Select
              aria-label={t('aclCompare.sourceInstance')}
              value={sourceInstanceId}
              options={options}
              style={{ width: '100%', marginTop: 4 }}
              onChange={(value) => {
                setSourceInstanceId(value);
                setResult(null);
              }}
            />
          </label>
          <Button
            aria-label={t('aclCompare.swap')}
            icon={<SwapOutlined />}
            onClick={swapInstances}
          />
          <label style={{ flex: 1, minWidth: 220 }}>
            <Typography.Text>{t('aclCompare.targetInstance')}</Typography.Text>
            <Select
              aria-label={t('aclCompare.targetInstance')}
              value={targetInstanceId}
              options={options}
              style={{ width: '100%', marginTop: 4 }}
              onChange={(value) => {
                setTargetInstanceId(value);
                setResult(null);
              }}
            />
          </label>
          <Button
            type="primary"
            loading={loading}
            disabled={instances.length < 2}
            onClick={() => void runComparison()}
          >
            {t('aclCompare.compare')}
          </Button>
        </Flex>

        {instances.length < 2 && <Empty description={t('aclCompare.needTwoInstances')} />}

        {result && (
          <>
            <Flex gap={12} wrap="wrap">
              <Card size="small" style={{ flex: 1, minWidth: 125 }}>
                <Statistic title={t('aclCompare.users')} value={result.summary.users} />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 125 }}>
                <Statistic title={t('aclCompare.rules')} value={result.summary.rules} />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 125 }}>
                <Statistic title={t('aclCompare.matches')} value={result.summary.matches} />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 125 }}>
                <Statistic title={t('aclCompare.drifted')} value={result.summary.drifted} />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 125 }}>
                <Statistic
                  title={t('aclCompare.missing')}
                  value={result.summary.onlySource + result.summary.onlyTarget}
                />
              </Card>
            </Flex>
            <Flex justify="space-between" gap={8} wrap="wrap">
              <Space wrap>
                <Input.Search
                  allowClear
                  aria-label={t('aclCompare.search')}
                  placeholder={t('aclCompare.search')}
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  style={{ width: 280 }}
                />
                <Select
                  aria-label={t('aclCompare.kindFilter')}
                  value={kindFilter}
                  onChange={setKindFilter}
                  style={{ width: 150 }}
                  options={[
                    { value: 'ALL', label: t('aclCompare.kindAll') },
                    { value: 'USER', label: t('aclCompare.kindUser') },
                    { value: 'RULE', label: t('aclCompare.kindRule') },
                  ]}
                />
                <Select
                  aria-label={t('aclCompare.statusFilter')}
                  value={statusFilter}
                  onChange={setStatusFilter}
                  style={{ width: 170 }}
                  options={[
                    { value: 'ALL', label: t('aclCompare.statusAll') },
                    ...Object.keys(STATUS_LABELS).map((status) => ({
                      value: status,
                      label: t(STATUS_LABELS[status as AclComparisonStatus]),
                    })),
                  ]}
                />
              </Space>
              <Button icon={<DownloadOutlined />} onClick={exportComparison}>
                {t('aclCompare.export')}
              </Button>
            </Flex>
            <Table<AclPolicyComparisonRow>
              rowKey="key"
              columns={columns}
              dataSource={visibleRows}
              pagination={{ pageSize: 20, showSizeChanger: false }}
              expandable={{
                rowExpandable: (row) => row.differences.length > 0,
                expandedRowRender: (row) => (
                  <Table
                    rowKey="field"
                    size="small"
                    pagination={false}
                    dataSource={row.differences}
                    columns={[
                      {
                        title: t('aclCompare.field'),
                        dataIndex: 'field',
                        render: (field: AclComparableField) => t(FIELD_LABELS[field]),
                      },
                      { title: sourceInstanceId, dataIndex: 'sourceValue' },
                      { title: targetInstanceId, dataIndex: 'targetValue' },
                    ]}
                  />
                ),
              }}
            />
          </>
        )}
      </Flex>
    </Drawer>
  );
};

export default AclPolicyComparisonDrawer;
