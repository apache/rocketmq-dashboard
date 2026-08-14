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

import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Table,
  Tabs,
  Tag,
  Button,
  Input,
  Select,
  Modal,
  Form,
  Radio,
  Switch,
  InputNumber,
  Progress,
  Descriptions,
  Flex,
  Space,
  Typography,
  Card,
  Alert,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ReloadOutlined, SettingOutlined, EyeOutlined, PlusOutlined } from '@ant-design/icons';
import { Cpu, HardDrives, Globe } from '@phosphor-icons/react';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import { countClusterComponents } from './clusterStats';
import type {
  BrokerInfo,
  ProxyInfo,
  NameServerInfo,
  ClusterConfig,
  ClusterInfo,
  ClusterProbeResult,
} from '../../api/cluster';
import {
  listClusters,
  testClusterConnection,
  updateClusterConfig,
} from '../../services/clusterService';
import { listInstances } from '../../services/instanceService';
import { supportsApacheRuntime, type Instance } from '../../api/instance';

const { Text } = Typography;

const REFRESH_INTERVAL_MS = 2000;

type RefreshSource = 'initial' | 'manual' | 'operation' | 'background';

type ProxyDetail = ProxyInfo & { clusterId: string; clusterName: string; nsClusterName: string };

const safeText = (value: string | null | undefined) => value ?? '';
const searchText = (value: string | null | undefined) => safeText(value).toLowerCase();
const compareText = (left: string | null | undefined, right: string | null | undefined) =>
  safeText(left).localeCompare(safeText(right));

// ─── Page ─────────────────────────────────────────────────────────────────────

const ClusterPage = () => {
  const { t } = useLang();
  const [searchParams] = useSearchParams();
  const requestedInstanceId = searchParams.get('instanceId') ?? '';
  const [clusters, setClusters] = useState<ClusterInfo[]>([]);
  const [instances, setInstances] = useState<Instance[]>([]);
  const [selectedInstanceId, setSelectedInstanceId] = useState('');
  const [instanceLoadError, setInstanceLoadError] = useState<string | null>(null);
  const [instanceLoadKey, setInstanceLoadKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [nsSearch, setNsSearch] = useState('');
  const [brokerSearch, setBrokerSearch] = useState('');
  const [brokerNsClusterFilter, setBrokerNsClusterFilter] = useState<string>('');
  const [proxySearch, setProxySearch] = useState('');

  const [configModalOpen, setConfigModalOpen] = useState(false);
  const [selectedCluster, setSelectedCluster] = useState<ClusterInfo | null>(null);
  const [selectedProxy, setSelectedProxy] = useState<ProxyDetail | null>(null);
  const [configForm] = Form.useForm();

  // ─── Connection test ──────────────────────────────────────────────────────
  const [connectModalOpen, setConnectModalOpen] = useState(false);
  const [connectTesting, setConnectTesting] = useState(false);
  const [probeResult, setProbeResult] = useState<ClusterProbeResult | null>(null);
  const [connectForm] = Form.useForm();

  const openConnectModal = useCallback(() => {
    setProbeResult(null);
    setConnectModalOpen(true);
  }, []);

  const closeConnectModal = useCallback(() => {
    setConnectModalOpen(false);
    setConnectTesting(false);
    setProbeResult(null);
    connectForm.resetFields();
  }, [connectForm]);

  const handleTestConnection = useCallback(async () => {
    let namesrvAddr: string;
    try {
      ({ namesrvAddr } = await connectForm.validateFields());
    } catch {
      return;
    }
    setConnectTesting(true);
    setProbeResult(null);
    try {
      const result = await testClusterConnection(namesrvAddr);
      setProbeResult(result);
      message.success(t('cluster.testConnectionSuccess'));
    } catch {
      message.error(t('cluster.testConnectionFailed'));
    } finally {
      setConnectTesting(false);
    }
  }, [connectForm, t]);

  // ─── Cluster refresh coordinator ──────────────────────────────────────────
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [refreshFailed, setRefreshFailed] = useState(false);
  const mountedRef = useRef(false);
  const autoRefreshRef = useRef(true);
  const refreshTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const inFlightRefreshRef = useRef<Promise<void> | null>(null);
  const queuedForegroundRef = useRef<RefreshSource | null>(null);
  const queuedBackgroundRef = useRef(false);
  const requestRefreshRef = useRef<(source: RefreshSource) => Promise<void>>(() =>
    Promise.resolve(),
  );
  const tRef = useRef(t);
  const selectedInstanceIdRef = useRef('');

  useEffect(() => {
    let cancelled = false;
    void listInstances()
      .then((nextInstances) => {
        if (cancelled) return;
        const apacheInstances = nextInstances.filter(supportsApacheRuntime);
        setInstances(apacheInstances);
        const initialInstanceId = apacheInstances.some(
          (instance) => instance.id === requestedInstanceId,
        )
          ? requestedInstanceId
          : (apacheInstances[0]?.id ?? '');
        selectedInstanceIdRef.current = initialInstanceId;
        setSelectedInstanceId(initialInstanceId);
        setInstanceLoadError(null);
        if (initialInstanceId) void requestRefreshRef.current('manual');
      })
      .catch(() => {
        if (cancelled) return;
        selectedInstanceIdRef.current = '';
        setInstances([]);
        setSelectedInstanceId('');
        setClusters([]);
        setSelectedProxy(null);
        setInstanceLoadError(tRef.current('common.fetchDataFailed'));
        setLoading(false);
        setAutoRefresh(false);
        autoRefreshRef.current = false;
      });
    return () => {
      cancelled = true;
    };
  }, [instanceLoadKey, requestedInstanceId]);

  const clearRefreshTimer = useCallback(() => {
    if (refreshTimerRef.current !== null) {
      clearTimeout(refreshTimerRef.current);
      refreshTimerRef.current = null;
    }
  }, []);

  const requestRefresh = useCallback(
    (source: RefreshSource): Promise<void> => {
      clearRefreshTimer();
      if (!mountedRef.current) return Promise.resolve();

      if (!selectedInstanceIdRef.current) {
        setClusters([]);
        setLoading(false);
        return Promise.resolve();
      }

      if (source !== 'background') setLoading(true);

      if (inFlightRefreshRef.current) {
        if (source === 'background') {
          if (autoRefreshRef.current) queuedBackgroundRef.current = true;
        } else if (source !== 'initial' && !queuedForegroundRef.current) {
          queuedForegroundRef.current = source;
        }
        return inFlightRefreshRef.current;
      }

      const runRefreshes = async () => {
        let currentSource: RefreshSource | null = source;

        while (currentSource && mountedRef.current) {
          try {
            const nextClusters = await listClusters(selectedInstanceIdRef.current || undefined);
            if (!mountedRef.current) return;
            setClusters(nextClusters);
            setSelectedProxy((current) => {
              if (!current) return null;
              const cluster = nextClusters.find((item) => item.id === current.clusterId);
              const proxy = cluster?.proxies?.find((item) => item.addr === current.addr);
              if (!cluster || !proxy) return null;
              return {
                ...proxy,
                clusterId: cluster.id,
                clusterName: cluster.name,
                nsClusterName: cluster.nsClusterName,
              };
            });
            setRefreshFailed(false);
          } catch {
            if (!mountedRef.current) return;
            setRefreshFailed(true);
            if (currentSource !== 'background') {
              message.error(tRef.current('common.fetchDataFailed'));
            }
          }

          if (!mountedRef.current) return;
          if (queuedForegroundRef.current) {
            currentSource = queuedForegroundRef.current;
            queuedForegroundRef.current = null;
            queuedBackgroundRef.current = false;
          } else if (queuedBackgroundRef.current && autoRefreshRef.current) {
            currentSource = 'background';
            queuedBackgroundRef.current = false;
          } else {
            queuedBackgroundRef.current = false;
            currentSource = null;
          }
        }
      };

      const refreshCycle = runRefreshes().finally(() => {
        inFlightRefreshRef.current = null;
        if (!mountedRef.current) return;

        if (queuedForegroundRef.current) {
          const queuedSource = queuedForegroundRef.current;
          queuedForegroundRef.current = null;
          queuedBackgroundRef.current = false;
          return requestRefreshRef.current(queuedSource);
        }
        if (queuedBackgroundRef.current && autoRefreshRef.current) {
          queuedBackgroundRef.current = false;
          return requestRefreshRef.current('background');
        }

        queuedBackgroundRef.current = false;
        setLoading(false);
        if (autoRefreshRef.current) {
          refreshTimerRef.current = setTimeout(() => {
            refreshTimerRef.current = null;
            void requestRefreshRef.current('background');
          }, REFRESH_INTERVAL_MS);
        }
      });
      inFlightRefreshRef.current = refreshCycle;
      return refreshCycle;
    },
    [clearRefreshTimer],
  );

  useEffect(() => {
    tRef.current = t;
  }, [t]);

  useEffect(() => {
    requestRefreshRef.current = requestRefresh;
  }, [requestRefresh]);

  useEffect(() => {
    mountedRef.current = true;
    autoRefreshRef.current = true;
    void requestRefreshRef.current('initial');
    return () => {
      mountedRef.current = false;
      clearRefreshTimer();
      queuedForegroundRef.current = null;
      queuedBackgroundRef.current = false;
    };
  }, [clearRefreshTimer]);

  const handleAutoRefreshChange = (checked: boolean) => {
    autoRefreshRef.current = checked;
    setAutoRefresh(checked);
    clearRefreshTimer();
    if (!checked) {
      queuedBackgroundRef.current = false;
      return;
    }
    void requestRefresh('background');
  };

  // Broker config handler
  const handleConfigOpen = (cluster: ClusterInfo) => {
    const cfg: ClusterConfig = cluster.config ?? ({} as ClusterConfig);
    setSelectedCluster(cluster);
    configForm.setFieldsValue({
      flushDiskType: cfg.flushDiskType ?? 'ASYNC_FLUSH',
      autoCreateTopicEnable: cfg.autoCreateTopicEnable ?? false,
      autoCreateSubscriptionGroup: cfg.autoCreateSubscriptionGroup ?? false,
      maxMessageSizeMB: Math.round((cfg.maxMessageSize ?? 4194304) / 1048576),
      fileReservedTime: cfg.fileReservedTime ?? 72,
      writeQueueNums: cfg.writeQueueNums ?? 8,
      readQueueNums: cfg.readQueueNums ?? 8,
      brokerPermission: cfg.brokerPermission ?? 6,
    });
    setConfigModalOpen(true);
  };

  const tabItems = [
    {
      key: 'nameserver',
      label: (
        <Flex align="center" gap={4}>
          <Cpu size={16} />
          <span>{t('cluster.nameserver')}</span>
        </Flex>
      ),
      children: renderNameServerTab(),
    },
    {
      key: 'broker',
      label: (
        <Flex align="center" gap={4}>
          <HardDrives size={16} />
          <span>{t('cluster.broker')}</span>
        </Flex>
      ),
      children: renderBrokerTab(),
    },
    {
      key: 'proxy',
      label: (
        <Flex align="center" gap={4}>
          <Globe size={16} />
          <span>{t('cluster.proxy')}</span>
        </Flex>
      ),
      children: renderProxyTab(),
    },
  ];

  // ─── Tab 2: Broker 管理 (flat table) ────────────────────────────────────────

  function renderBrokerTab() {
    type BrokerWithCluster = BrokerInfo & {
      clusterName: string;
      nsClusterName: string;
      cluster: ClusterInfo;
    };
    const brokerSearchText = searchText(brokerSearch);

    const allBrokers: BrokerWithCluster[] = clusters.flatMap((c) =>
      (c.brokers ?? [])
        .filter((b) => {
          const matchSearch =
            !brokerSearchText ||
            searchText(b.name).includes(brokerSearchText) ||
            searchText(b.addr).includes(brokerSearchText);
          const matchNsCluster =
            !brokerNsClusterFilter || c.nsClusterName === brokerNsClusterFilter;
          return matchSearch && matchNsCluster;
        })
        .map((b) => ({
          ...b,
          clusterName: c.name,
          nsClusterName: c.nsClusterName,
          cluster: c,
        })),
    );

    const brokerColumns: ColumnsType<BrokerWithCluster> = [
      {
        title: t('cluster.k8sName'),
        dataIndex: 'clusterName',
        key: 'clusterName',
        width: 160,
        sorter: (a, b) => a.clusterName.localeCompare(b.clusterName),
        render: (name: string) => (
          <Text strong style={{ fontSize: 13 }}>
            {name}
          </Text>
        ),
      },
      {
        title: t('cluster.brokerName'),
        dataIndex: 'name',
        key: 'name',
        width: 170,
        sorter: (a, b) => a.name.localeCompare(b.name),
        render: (name: string) => (
          <Text strong style={{ fontSize: 13 }}>
            {name}
          </Text>
        ),
      },
      {
        title: t('common.status'),
        dataIndex: 'status',
        key: 'status',
        width: 90,
        sorter: (a, b) => a.status.localeCompare(b.status),
        render: (status: string) => {
          const map: Record<string, { color: string; label: string }> = {
            running: { color: 'green', label: t('cluster.running') },
            readonly: { color: 'gold', label: t('cluster.readonly') },
            maintenance: { color: 'red', label: t('cluster.maintenance') },
          };
          const cfg = map[status] ?? { color: 'default', label: status };
          return <Tag color={cfg.color}>{cfg.label}</Tag>;
        },
      },
      {
        title: t('common.version'),
        dataIndex: 'version',
        key: 'version',
        width: 80,
        align: 'right',
        sorter: (a, b) => (a.version || '').localeCompare(b.version || ''),
        render: (v?: string | null) => <span style={{ fontSize: 13 }}>{v || '-'}</span>,
      },
      {
        title: t('cluster.diskUsage'),
        dataIndex: 'diskUsage',
        key: 'diskUsage',
        width: 150,
        sorter: (a, b) => a.diskUsage - b.diskUsage,
        render: (v: number) => (
          <Progress
            percent={v}
            size="small"
            strokeColor={v > 85 ? '#ff4d4f' : v > 70 ? '#faad14' : '#1677ff'}
          />
        ),
      },
      {
        title: t('common.address'),
        dataIndex: 'addr',
        key: 'addr',
        width: 170,
        align: 'right',
        sorter: (a, b) => compareText(a.addr, b.addr),
        render: (addr: string | null) => <span style={{ fontSize: 13 }}>{safeText(addr)}</span>,
      },
      {
        title: 'TPS In',
        dataIndex: 'tpsIn',
        key: 'tpsIn',
        width: 90,
        align: 'right',
        sorter: (a, b) => a.tpsIn - b.tpsIn,
        render: (v: number) => v.toLocaleString(),
      },
      {
        title: 'TPS Out',
        dataIndex: 'tpsOut',
        key: 'tpsOut',
        width: 90,
        align: 'right',
        sorter: (a, b) => a.tpsOut - b.tpsOut,
        render: (v: number) => v.toLocaleString(),
      },
      {
        title: t('common.actions'),
        key: 'action',
        width: 160,
        render: (_: unknown, record: BrokerWithCluster) => (
          <Flex gap={6}>
            <Button
              size="small"
              icon={<SettingOutlined />}
              style={{ borderColor: '#1677ff', color: '#1677ff' }}
              onClick={() => handleConfigOpen(record.cluster)}
            >
              {t('cluster.config')}
            </Button>
            <Button
              size="small"
              icon={<ReloadOutlined />}
              danger
              style={{ borderColor: '#ff4d4f', color: '#ff4d4f' }}
              onClick={() => message.warning(t('cluster.restartNotSupported'))}
            >
              {t('cluster.restart')}
            </Button>
          </Flex>
        ),
      },
    ];

    const nsClusterOptions = [
      { value: '', label: t('common.all') },
      ...clusters.map((c) => ({ value: c.nsClusterName, label: c.nsClusterName })),
    ];

    return (
      <div>
        <Flex justify="space-between" style={{ marginBottom: 16 }}>
          <Space>
            <Input.Search
              placeholder={t('cluster.searchBroker')}
              allowClear
              onSearch={setBrokerSearch}
              onChange={(e) => !e.target.value && setBrokerSearch('')}
              style={{ width: 240 }}
            />
            <Select
              value={brokerNsClusterFilter}
              onChange={setBrokerNsClusterFilter}
              style={{ width: 180 }}
              options={nsClusterOptions}
            />
          </Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={openConnectModal}>
            {t('cluster.createCluster')}
          </Button>
        </Flex>
        <Card styles={{ body: { padding: 0 } }}>
          <Table
            columns={brokerColumns}
            dataSource={allBrokers}
            loading={loading}
            rowKey="addr"
            pagination={{ pageSize: 20 }}
            size="small"
          />
        </Card>

        {selectedCluster && (
          <Modal
            title={t('cluster.configTitle', { name: selectedCluster.name })}
            open={configModalOpen}
            onCancel={() => setConfigModalOpen(false)}
            onOk={() => {
              configForm.validateFields().then(async (values) => {
                if (!selectedCluster) return;
                try {
                  const { maxMessageSizeMB, ...configValues } = values;
                  const nextConfig: ClusterConfig = {
                    ...(selectedCluster.config ?? {}),
                    ...configValues,
                    maxMessageSize: maxMessageSizeMB * 1048576,
                  };
                  const result = await updateClusterConfig({
                    id: selectedCluster.id,
                    instanceId: selectedInstanceIdRef.current || undefined,
                    ...nextConfig,
                  });
                  if (result.status === 'SUCCESS') {
                    await requestRefresh('operation');
                    message.success(t('cluster.configUpdated'));
                    setConfigModalOpen(false);
                    return;
                  }

                  const failedAddresses = result.failedBrokers
                    .map((failure) => failure.address)
                    .join(', ');
                  if (result.status === 'PARTIAL') {
                    await requestRefresh('operation');
                    message.warning(
                      t('cluster.configPartiallyUpdated', { brokers: failedAddresses }),
                    );
                    return;
                  }
                  message.error(t('cluster.configUpdateFailed', { brokers: failedAddresses }));
                } catch {
                  message.error(t('cluster.configUpdateFailed', { brokers: '' }));
                }
              });
            }}
            width={560}
          >
            <Form form={configForm} layout="vertical">
              <Form.Item label={t('cluster.flushDiskType')} name="flushDiskType">
                <Radio.Group>
                  <Radio value="SYNC_FLUSH">{t('cluster.syncFlush')}</Radio>
                  <Radio value="ASYNC_FLUSH">{t('cluster.asyncFlush')}</Radio>
                </Radio.Group>
              </Form.Item>
              <Form.Item
                label={t('cluster.autoCreateTopic')}
                name="autoCreateTopicEnable"
                valuePropName="checked"
              >
                <Switch />
              </Form.Item>
              <Form.Item
                label={t('cluster.autoCreateSubGroup')}
                name="autoCreateSubscriptionGroup"
                valuePropName="checked"
              >
                <Switch />
              </Form.Item>
              <Form.Item label={t('cluster.maxMessageSize')} name="maxMessageSizeMB">
                <InputNumber min={1} max={128} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item label={t('cluster.fileReservedTime')} name="fileReservedTime">
                <InputNumber min={1} max={720} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item label={t('cluster.writeQueues')} name="writeQueueNums">
                <InputNumber min={1} max={256} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item label={t('cluster.readQueues')} name="readQueueNums">
                <InputNumber min={1} max={256} style={{ width: '100%' }} />
              </Form.Item>
              <Text type="secondary" style={{ display: 'block', marginTop: -16, marginBottom: 16 }}>
                RocketMQ Broker uses one default Topic queue count; read and write values must
                match.
              </Text>
              <Form.Item label={t('cluster.brokerPermission')} name="brokerPermission">
                <InputNumber min={0} max={7} style={{ width: '100%' }} />
              </Form.Item>
            </Form>
          </Modal>
        )}
      </div>
    );
  }

  // ─── Tab 2: NameServer 管理 (nested by cluster) ────────────────────────────

  function renderNameServerTab() {
    const nsSearchText = searchText(nsSearch);
    const filteredClusters = clusters
      .map((c) => {
        const nameServers = (c.nameServers ?? []).filter((ns) => {
          const matchSearch = !nsSearchText || searchText(ns.addr).includes(nsSearchText);
          return matchSearch;
        });
        return { ...c, filteredNameServers: nameServers };
      })
      .filter((c) => c.filteredNameServers.length > 0);

    const getNsSubColumns = (): ColumnsType<NameServerInfo> => [
      {
        title: t('common.address'),
        dataIndex: 'addr',
        key: 'addr',
        sorter: (a, b) => compareText(a.addr, b.addr),
        render: (addr: string | null) => (
          <Text code style={{ fontSize: 12 }}>
            {safeText(addr)}
          </Text>
        ),
      },
      {
        title: t('common.status'),
        dataIndex: 'status',
        key: 'status',
        width: 90,
        sorter: (a, b) => a.status.localeCompare(b.status),
        render: (status: string) => {
          const map: Record<string, { color: string; label: string }> = {
            healthy: { color: 'green', label: t('cluster.running') },
            warning: { color: 'gold', label: t('cluster.warning') },
            error: { color: 'red', label: t('cluster.error') },
            offline: { color: 'default', label: t('cluster.offline') },
          };
          const cfg = map[status] ?? { color: 'default', label: status };
          return <Tag color={cfg.color}>{cfg.label}</Tag>;
        },
      },
    ];

    const clusterColumns: ColumnsType<ClusterInfo & { filteredNameServers: NameServerInfo[] }> = [
      {
        title: t('cluster.k8sName'),
        dataIndex: 'name',
        key: 'name',
        width: 180,
        render: (name: string) => (
          <Text strong style={{ fontSize: 14 }}>
            {name}
          </Text>
        ),
      },
      {
        title: t('cluster.nsClusterName'),
        dataIndex: 'nsClusterName',
        key: 'nsClusterName',
        width: 180,
        render: (name: string) => (
          <Text strong style={{ fontSize: 13 }}>
            {name}
          </Text>
        ),
      },
      {
        title: t('common.status'),
        dataIndex: 'status',
        key: 'status',
        width: 90,
        render: (status: string) => {
          const map: Record<string, { color: string; label: string }> = {
            healthy: { color: 'green', label: t('cluster.running') },
            warning: { color: 'gold', label: t('cluster.warning') },
            error: { color: 'red', label: t('cluster.error') },
            offline: { color: 'default', label: t('cluster.offline') },
          };
          const cfg = map[status] ?? { color: 'default', label: status };
          return <Tag color={cfg.color}>{cfg.label}</Tag>;
        },
      },
      {
        title: t('common.version'),
        dataIndex: 'version',
        key: 'version',
        width: 80,
        render: (v: string) => <Tag>{v}</Tag>,
      },
      {
        title: t('cluster.count'),
        key: 'nsCount',
        width: 80,
        align: 'center',
        render: (_: unknown, record: ClusterInfo & { filteredNameServers: NameServerInfo[] }) =>
          record.filteredNameServers.length,
      },
    ];

    return (
      <div>
        <Flex justify="space-between" style={{ marginBottom: 16 }}>
          <Space>
            <Select
              value={selectedInstanceId || undefined}
              onChange={(instanceId) => {
                selectedInstanceIdRef.current = instanceId;
                setSelectedInstanceId(instanceId);
                void requestRefresh('manual');
              }}
              placeholder="Select instance"
              style={{ width: 180 }}
              options={instances.map((instance) => ({
                value: instance.name,
                label: instance.name,
              }))}
            />
            <Input.Search
              placeholder={t('cluster.searchNs')}
              allowClear
              onSearch={setNsSearch}
              onChange={(e) => !e.target.value && setNsSearch('')}
              style={{ width: 240 }}
            />
          </Space>
        </Flex>
        <Card styles={{ body: { padding: 0 } }}>
          <Table
            columns={clusterColumns}
            dataSource={filteredClusters}
            loading={loading}
            rowKey="id"
            pagination={{ pageSize: 20 }}
            size="small"
            expandable={{
              expandedRowRender: (record) => (
                <div style={{ padding: '8px 0' }}>
                  <Table
                    columns={getNsSubColumns()}
                    dataSource={record.filteredNameServers}
                    rowKey="addr"
                    pagination={false}
                    size="small"
                  />
                </div>
              ),
            }}
          />
        </Card>
      </div>
    );
  }

  // ─── Tab 3: Proxy 管理 (flat table) ────────────────────────────────────────

  function renderProxyTab() {
    type ProxyRow = ProxyDetail;
    const proxySearchText = searchText(proxySearch);

    const allProxies: ProxyRow[] = clusters
      .filter((c) => (c.proxies?.length ?? 0) > 0)
      .flatMap((c) =>
        (c.proxies ?? [])
          .filter((p) => {
            const matchSearch = !proxySearchText || searchText(p.addr).includes(proxySearchText);
            return matchSearch;
          })
          .map((p) => ({
            ...p,
            clusterId: c.id,
            clusterName: c.name,
            nsClusterName: c.nsClusterName,
          })),
      );

    const proxyColumns: ColumnsType<ProxyRow> = [
      {
        title: t('cluster.k8sName'),
        dataIndex: 'clusterName',
        key: 'clusterName',
        width: 160,
        sorter: (a, b) => a.clusterName.localeCompare(b.clusterName),
        render: (name: string) => (
          <Text strong style={{ fontSize: 13 }}>
            {name}
          </Text>
        ),
      },
      {
        title: t('cluster.proxyAddr'),
        dataIndex: 'addr',
        key: 'addr',
        width: 200,
        sorter: (a, b) => compareText(a.addr, b.addr),
        render: (addr: string | null) => (
          <Text code style={{ fontSize: 12 }}>
            {safeText(addr)}
          </Text>
        ),
      },
      {
        title: t('common.status'),
        dataIndex: 'status',
        key: 'status',
        width: 90,
        sorter: (a, b) => a.status.localeCompare(b.status),
        render: (status: string) => {
          const map: Record<string, { color: string; label: string }> = {
            healthy: { color: 'green', label: t('cluster.running') },
            warning: { color: 'gold', label: t('cluster.warning') },
            error: { color: 'red', label: t('cluster.error') },
            offline: { color: 'default', label: t('cluster.offline') },
          };
          const cfg = map[status] ?? { color: 'default', label: status };
          return <Tag color={cfg.color}>{cfg.label}</Tag>;
        },
      },
      {
        title: t('cluster.connections'),
        dataIndex: 'connections',
        key: 'connections',
        width: 100,
        align: 'right',
        sorter: (a, b) => a.connections - b.connections,
        render: (v: number) => v.toLocaleString(),
      },
      {
        title: t('cluster.grpcPort'),
        dataIndex: 'grpcPort',
        key: 'grpcPort',
        width: 100,
        align: 'center',
        sorter: (a, b) => a.grpcPort - b.grpcPort,
      },
      {
        title: t('cluster.remotingPort'),
        dataIndex: 'remotingPort',
        key: 'remotingPort',
        width: 120,
        align: 'center',
        sorter: (a, b) => a.remotingPort - b.remotingPort,
      },
      {
        title: t('common.actions'),
        key: 'action',
        width: 160,
        render: (_: unknown, record: ProxyRow) => (
          <Flex gap={6}>
            <Button
              size="small"
              icon={<EyeOutlined />}
              style={{ borderColor: '#1677ff', color: '#1677ff' }}
              onClick={() => setSelectedProxy(record)}
            >
              {t('common.detail')}
            </Button>
          </Flex>
        ),
      },
    ];

    return (
      <div>
        <Flex justify="space-between" style={{ marginBottom: 16 }}>
          <Space>
            <Input.Search
              placeholder={t('cluster.searchProxy')}
              allowClear
              onSearch={setProxySearch}
              onChange={(e) => !e.target.value && setProxySearch('')}
              style={{ width: 240 }}
            />
          </Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={openConnectModal}>
            {t('cluster.createCluster')}
          </Button>
        </Flex>
        <Card styles={{ body: { padding: 0 } }}>
          <Table
            columns={proxyColumns}
            dataSource={allProxies}
            loading={loading}
            rowKey={(r) => `${r.clusterName}-${r.addr}`}
            pagination={{ pageSize: 20 }}
            size="small"
          />
        </Card>
      </div>
    );
  }

  // ─── Tab 4: K8s 证书管理 ───────────────────────────────────────────────────

  // ─── Render ─────────────────────────────────────────────────────────────────

  const {
    brokers: totalBrokers,
    nameServers: totalNameServers,
    proxies: totalProxies,
  } = countClusterComponents(clusters);

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title={t('cluster.title')}
        subtitle={`${t('common.total')} ${clusters.length} ${t('cluster.title')} · ${totalBrokers} Broker · ${totalNameServers} NameServer · ${totalProxies} Proxy`}
        extra={
          <Flex align="center" gap={8}>
            <Button
              size="small"
              icon={<ReloadOutlined spin={loading} />}
              aria-label={t('common.refresh')}
              onClick={() => void requestRefresh('manual')}
            >
              {t('common.refresh')}
            </Button>
            <Flex align="center" gap={6}>
              {(autoRefresh || refreshFailed) && (
                <span
                  title={
                    refreshFailed
                      ? t('common.refreshFailed')
                      : autoRefresh
                        ? t('common.liveRefresh')
                        : t('common.autoRefresh')
                  }
                  style={{
                    width: 6,
                    height: 6,
                    borderRadius: '50%',
                    background: refreshFailed ? '#ff4d4f' : '#52c41a',
                    display: 'inline-block',
                    animation: refreshFailed ? undefined : 'livePulse 1.5s ease-in-out infinite',
                  }}
                />
              )}
              <Text type={refreshFailed ? 'danger' : 'secondary'} style={{ fontSize: 12 }}>
                {refreshFailed
                  ? t('common.refreshFailed')
                  : autoRefresh
                    ? t('common.liveRefresh')
                    : t('common.autoRefresh')}
              </Text>
              <Switch
                size="small"
                checked={autoRefresh}
                aria-label={t('common.autoRefresh')}
                onChange={handleAutoRefreshChange}
              />
            </Flex>
          </Flex>
        }
      />
      {instanceLoadError && (
        <Alert
          type="error"
          showIcon
          message={instanceLoadError}
          action={
            <Button size="small" onClick={() => setInstanceLoadKey((key) => key + 1)}>
              {t('common.retry')}
            </Button>
          }
          style={{ marginBottom: 16 }}
        />
      )}
      <style>{`
        @keyframes livePulse {
          0%, 100% { opacity: 1; box-shadow: 0 0 0 0 rgba(82, 196, 26, 0.4); }
          50% { opacity: 0.6; box-shadow: 0 0 0 4px rgba(82, 196, 26, 0); }
        }
      `}</style>
      <Tabs items={tabItems} defaultActiveKey="broker" />
      <Modal
        title={t('cluster.proxyDetailTitle', { addr: selectedProxy?.addr ?? '' })}
        open={Boolean(selectedProxy)}
        onCancel={() => setSelectedProxy(null)}
        footer={<Button onClick={() => setSelectedProxy(null)}>{t('common.close')}</Button>}
        width={560}
        destroyOnHidden
      >
        {selectedProxy && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label={t('cluster.k8sName')}>
              {selectedProxy.clusterName}
            </Descriptions.Item>
            <Descriptions.Item label={t('cluster.nsClusterName')}>
              {selectedProxy.nsClusterName}
            </Descriptions.Item>
            <Descriptions.Item label={t('cluster.proxyAddr')}>
              <Text copyable code>
                {selectedProxy.addr}
              </Text>
            </Descriptions.Item>
            <Descriptions.Item label={t('common.status')}>
              <Tag
                color={
                  selectedProxy.status === 'healthy'
                    ? 'green'
                    : selectedProxy.status === 'warning'
                      ? 'gold'
                      : selectedProxy.status === 'error'
                        ? 'red'
                        : 'default'
                }
              >
                {selectedProxy.status}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('cluster.connections')}>
              {selectedProxy.connections.toLocaleString()}
            </Descriptions.Item>
            <Descriptions.Item label={t('cluster.grpcPort')}>
              {selectedProxy.grpcPort}
            </Descriptions.Item>
            <Descriptions.Item label={t('cluster.remotingPort')}>
              {selectedProxy.remotingPort}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
      <Modal
        title={t('cluster.testConnectionTitle')}
        open={connectModalOpen}
        onCancel={closeConnectModal}
        okText={t('cluster.testConnection')}
        cancelText={t('common.close')}
        confirmLoading={connectTesting}
        onOk={handleTestConnection}
        width={560}
        destroyOnHidden
      >
        <Text type="secondary">{t('cluster.testConnectionDesc')}</Text>
        <Form form={connectForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="namesrvAddr"
            label={t('cluster.nsAddr')}
            rules={[{ required: true, message: t('cluster.nsAddrPlaceholder') }]}
          >
            <Input
              placeholder={t('cluster.nsAddrPlaceholder')}
              onPressEnter={handleTestConnection}
            />
          </Form.Item>
        </Form>
        {probeResult && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label={t('common.status')}>
              <Tag color={probeResult.connected ? 'green' : 'red'}>
                {probeResult.connected
                  ? t('cluster.testConnectionSuccess')
                  : t('cluster.testConnectionFailed')}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('cluster.probeClusterName')}>
              {probeResult.clusterName}
            </Descriptions.Item>
            <Descriptions.Item label={t('cluster.probeBrokerCount')}>
              {probeResult.brokerCount}
            </Descriptions.Item>
            <Descriptions.Item label={t('cluster.probeBrokers')}>
              {probeResult.brokerNames.length > 0 ? probeResult.brokerNames.join(', ') : '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('cluster.probeElapsed')}>
              {probeResult.elapsedMillis}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  );
};

export default ClusterPage;
