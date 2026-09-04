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
  Checkbox,
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
import { DownloadOutlined, SearchOutlined } from '@ant-design/icons';
import type { Instance, InstanceVendor } from '../api/instance';
import { listAllTopics } from '../services/topicService';
import { listAllConsumerGroups } from '../services/consumerService';
import { useLang } from '../i18n/LangContext';
import { buildCsv, downloadCsv, type CsvColumn } from '../utils/download';
import { tableScrollX } from '../utils/table';
import {
  buildFleetResourceInventory,
  filterFleetResourceRows,
  summarizeVisibleFleetResources,
  type FleetResourceInventory,
  type FleetResourceKind,
  type FleetResourceRow,
} from '../utils/fleetResourceInventory';

interface FleetResourceInventoryDrawerProps {
  open: boolean;
  instances: Instance[];
  onClose: () => void;
}

const LOAD_BATCH_SIZE = 4;

const CSV_COLUMNS: CsvColumn<FleetResourceRow>[] = [
  { header: 'Resource Type', value: (row) => row.kind },
  { header: 'Name', value: (row) => row.name },
  { header: 'Instance', value: (row) => row.instanceId },
  { header: 'Vendor', value: (row) => row.vendor },
  { header: 'Cluster', value: (row) => row.clusterId },
  { header: 'Namespace', value: (row) => row.namespace },
  { header: 'Configuration', value: (row) => row.configuration },
  { header: 'Instance Count', value: (row) => row.occurrenceCount },
  { header: 'Other Instances', value: (row) => row.otherInstances.join(';') },
];

interface InstanceLoadResult {
  instance: Instance;
  topics: Awaited<ReturnType<typeof listAllTopics>>;
  groups: Awaited<ReturnType<typeof listAllConsumerGroups>>;
  failures: string[];
}

const loadInstanceResources = async (instance: Instance): Promise<InstanceLoadResult> => {
  const [topicsResult, groupsResult] = await Promise.allSettled([
    listAllTopics({ instanceId: instance.name }),
    listAllConsumerGroups({ instanceId: instance.name }),
  ]);
  return {
    instance,
    topics: topicsResult.status === 'fulfilled' ? topicsResult.value : [],
    groups: groupsResult.status === 'fulfilled' ? groupsResult.value : [],
    failures: [
      ...(topicsResult.status === 'rejected' ? ['TOPIC'] : []),
      ...(groupsResult.status === 'rejected' ? ['CONSUMER_GROUP'] : []),
    ],
  };
};

const loadInBatches = async (instances: Instance[]) => {
  const results: InstanceLoadResult[] = [];
  for (let start = 0; start < instances.length; start += LOAD_BATCH_SIZE) {
    const batch = instances.slice(start, start + LOAD_BATCH_SIZE);
    results.push(...(await Promise.all(batch.map(loadInstanceResources))));
  }
  return results;
};

const FleetResourceInventoryDrawer = ({
  open,
  instances,
  onClose,
}: FleetResourceInventoryDrawerProps) => {
  const { t } = useLang();
  const [selectedInstanceIds, setSelectedInstanceIds] = useState<string[]>(
    instances.map((instance) => instance.name),
  );
  const [inventory, setInventory] = useState<FleetResourceInventory | null>(null);
  const [failedLoads, setFailedLoads] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [kindFilter, setKindFilter] = useState<FleetResourceKind | 'ALL'>('ALL');
  const [instanceFilter, setInstanceFilter] = useState<string>('ALL');
  const [vendorFilter, setVendorFilter] = useState<InstanceVendor | 'ALL'>('ALL');
  const [sharedOnly, setSharedOnly] = useState(false);
  const [search, setSearch] = useState('');
  const requestIdRef = useRef(0);

  const selectedInstances = instances.filter((instance) =>
    selectedInstanceIds.includes(instance.name),
  );
  const visibleRows = useMemo(
    () =>
      filterFleetResourceRows(inventory?.rows ?? [], {
        kind: kindFilter,
        instanceId: instanceFilter,
        vendor: vendorFilter,
        sharedOnly,
        search,
      }),
    [instanceFilter, inventory, kindFilter, search, sharedOnly, vendorFilter],
  );
  const visibleSummary = useMemo(() => summarizeVisibleFleetResources(visibleRows), [visibleRows]);

  const loadInventory = async () => {
    if (selectedInstances.length === 0) {
      message.warning(t('fleetInventory.selectInstance'));
      return;
    }
    const requestId = ++requestIdRef.current;
    setLoading(true);
    try {
      const results = await loadInBatches(selectedInstances);
      if (requestId !== requestIdRef.current) return;
      const topicsByInstance = Object.fromEntries(
        results.map((result) => [result.instance.name, result.topics]),
      );
      const groupsByInstance = Object.fromEntries(
        results.map((result) => [result.instance.name, result.groups]),
      );
      setInventory(
        buildFleetResourceInventory(selectedInstances, topicsByInstance, groupsByInstance),
      );
      setFailedLoads(
        results.flatMap((result) =>
          result.failures.map((kind) => `${result.instance.name}: ${kind}`),
        ),
      );
      setKindFilter('ALL');
      setInstanceFilter('ALL');
      setVendorFilter('ALL');
      setSharedOnly(false);
      setSearch('');
    } finally {
      if (requestId === requestIdRef.current) setLoading(false);
    }
  };

  const exportInventory = () => {
    if (!inventory) return;
    downloadCsv('rocketmq-fleet-resource-inventory.csv', buildCsv(CSV_COLUMNS, visibleRows));
    message.success(t('fleetInventory.exported', { count: visibleRows.length }));
  };

  const columns: TableColumnsType<FleetResourceRow> = [
    {
      title: t('fleetInventory.kind'),
      dataIndex: 'kind',
      key: 'kind',
      width: 140,
      render: (kind: FleetResourceKind) => (
        <Tag color={kind === 'TOPIC' ? 'blue' : 'purple'}>
          {t(kind === 'TOPIC' ? 'fleetInventory.topic' : 'fleetInventory.group')}
        </Tag>
      ),
    },
    {
      title: t('fleetInventory.name'),
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
      sorter: (left, right) => left.name.localeCompare(right.name),
      render: (name: string) => <span title={name}>{name}</span>,
    },
    {
      title: t('fleetInventory.instance'),
      dataIndex: 'instanceId',
      key: 'instanceId',
      width: 170,
      ellipsis: true,
      render: (value: string) => <span title={value}>{value}</span>,
    },
    { title: t('fleetInventory.vendor'), dataIndex: 'vendor', key: 'vendor', width: 110 },
    {
      title: t('fleetInventory.configuration'),
      dataIndex: 'configuration',
      key: 'configuration',
      width: 250,
      ellipsis: true,
      render: (value: string) => <span title={value}>{value}</span>,
    },
    {
      title: t('fleetInventory.occurrences'),
      dataIndex: 'occurrenceCount',
      key: 'occurrenceCount',
      width: 120,
      sorter: (left, right) => left.occurrenceCount - right.occurrenceCount,
      render: (count: number, row) => (
        <Tag color={count > 1 ? 'gold' : 'default'} title={row.otherInstances.join(', ')}>
          {count}
        </Tag>
      ),
    },
  ];

  return (
    <Drawer
      title={t('fleetInventory.title')}
      open={open}
      onClose={onClose}
      width={1120}
      destroyOnHidden
    >
      <Flex vertical gap={16}>
        <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
          {t('fleetInventory.description')}
        </Typography.Paragraph>
        <Flex gap={8} align="end" wrap="wrap">
          <label style={{ flex: 1, minWidth: 360 }}>
            <Typography.Text>{t('fleetInventory.instances')}</Typography.Text>
            <Select
              mode="multiple"
              allowClear
              aria-label={t('fleetInventory.instances')}
              value={selectedInstanceIds}
              maxTagCount="responsive"
              options={instances.map((instance) => ({
                value: instance.name,
                label: instance.name,
              }))}
              style={{ width: '100%', marginTop: 4 }}
              onChange={(value) => {
                setSelectedInstanceIds(value);
                setInventory(null);
                setFailedLoads([]);
              }}
            />
          </label>
          <Button
            type="primary"
            icon={<SearchOutlined />}
            aria-label={t('fleetInventory.load')}
            loading={loading}
            disabled={selectedInstanceIds.length === 0}
            onClick={() => void loadInventory()}
          >
            {t('fleetInventory.load')}
          </Button>
        </Flex>

        {instances.length === 0 && <Empty description={t('fleetInventory.noInstances')} />}
        {failedLoads.length > 0 && (
          <Alert
            type="warning"
            showIcon
            message={t('fleetInventory.partialFailure', { count: failedLoads.length })}
            description={failedLoads.join(', ')}
          />
        )}

        {inventory && (
          <>
            <Flex gap={12} wrap="wrap">
              <Card size="small" style={{ flex: 1, minWidth: 130 }}>
                <Statistic
                  title={t('fleetInventory.loadedInstances')}
                  value={inventory.summary.instances}
                />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 130 }}>
                <Statistic title={t('fleetInventory.topics')} value={inventory.summary.topics} />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 130 }}>
                <Statistic
                  title={t('fleetInventory.groups')}
                  value={inventory.summary.consumerGroups}
                />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 130 }}>
                <Statistic
                  title={t('fleetInventory.sharedNames')}
                  value={inventory.summary.sharedNames}
                />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 130 }}>
                <Statistic title={t('fleetInventory.visible')} value={visibleSummary.resources} />
              </Card>
            </Flex>
            <Flex justify="space-between" gap={8} wrap="wrap">
              <Space wrap>
                <Input
                  allowClear
                  prefix={<SearchOutlined />}
                  aria-label={t('fleetInventory.search')}
                  placeholder={t('fleetInventory.search')}
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  style={{ width: 250 }}
                />
                <Select
                  aria-label={t('fleetInventory.kindFilter')}
                  value={kindFilter}
                  onChange={setKindFilter}
                  style={{ width: 160 }}
                  options={[
                    { value: 'ALL', label: t('fleetInventory.allKinds') },
                    { value: 'TOPIC', label: t('fleetInventory.topic') },
                    { value: 'CONSUMER_GROUP', label: t('fleetInventory.group') },
                  ]}
                />
                <Select
                  aria-label={t('fleetInventory.instanceFilter')}
                  value={instanceFilter}
                  onChange={setInstanceFilter}
                  style={{ width: 180 }}
                  options={[
                    { value: 'ALL', label: t('fleetInventory.allInstances') },
                    ...selectedInstances.map((instance) => ({
                      value: instance.name,
                      label: instance.name,
                    })),
                  ]}
                />
                <Select
                  aria-label={t('fleetInventory.vendorFilter')}
                  value={vendorFilter}
                  onChange={setVendorFilter}
                  style={{ width: 150 }}
                  options={[
                    { value: 'ALL', label: t('fleetInventory.allVendors') },
                    { value: 'APACHE', label: 'Apache' },
                    { value: 'ALIYUN', label: 'Alibaba Cloud' },
                    { value: 'TENCENT', label: 'Tencent Cloud' },
                  ]}
                />
                <Checkbox
                  checked={sharedOnly}
                  onChange={(event) => setSharedOnly(event.target.checked)}
                >
                  {t('fleetInventory.sharedOnly')}
                </Checkbox>
              </Space>
              <Button icon={<DownloadOutlined />} onClick={exportInventory}>
                {t('fleetInventory.export')}
              </Button>
            </Flex>
            <Table<FleetResourceRow>
              rowKey="key"
              columns={columns}
              dataSource={visibleRows}
              pagination={{ pageSize: 20, showSizeChanger: true }}
              scroll={{ x: tableScrollX(columns) }}
            />
          </>
        )}
      </Flex>
    </Drawer>
  );
};

export default FleetResourceInventoryDrawer;
