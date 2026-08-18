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
import {
  ReloadOutlined,
  SettingOutlined,
  EyeOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { Cpu, HardDrives, Globe } from '@phosphor-icons/react';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import { countClusterComponents } from './clusterStats';
import type {
  BrokerInfo,
  ProxyInfo,
  NameserverRegistryEntry,
  ClusterConfig,
  ClusterInfo,
  ClusterProbeResult,
} from '../../api/cluster';
import {
  createNameserverRegistry,
  deleteNameserverRegistry,
  listClusters,
  listK8sCerts,
  listNameserverRegistry,
  listRegistryClusters,
  restartProxy,
  testClusterConnection,
  updateClusterConfig,
  updateNameserverRegistry,
} from '../../services/clusterService';
import { listInstances } from '../../services/instanceService';
import { isMockMode } from '../../services/dataMode';

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
  const requestedInstanceIdParam = searchParams.get('instanceId');
  const requestedInstanceId = requestedInstanceIdParam ?? undefined;
  const [clusters, setClusters] = useState<ClusterInfo[]>([]);
  const [instanceLoadError, setInstanceLoadError] = useState<string | null>(null);
  const [instanceLoadKey, setInstanceLoadKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [nsSearch, setNsSearch] = useState('');
  const [brokerSearch, setBrokerSearch] = useState('');
  const [proxySearch, setProxySearch] = useState('');

  const [configModalOpen, setConfigModalOpen] = useState(false);
  const [selectedCluster, setSelectedCluster] = useState<ClusterInfo | null>(null);
  const [nsRegistry, setNsRegistry] = useState<NameserverRegistryEntry[]>([]);
  const [selectedProxy, setSelectedProxy] = useState<ProxyDetail | null>(null);
  const [configForm] = Form.useForm();

  const loadNsRegistry = useCallback(async () => {
    try {
      setNsRegistry(await listNameserverRegistry());
    } catch {
      setNsRegistry([]);
    }
  }, []);

  useEffect(() => {
    let active = true;
    listNameserverRegistry()
      .then((entries) => {
        if (active) setNsRegistry(entries);
      })
      .catch(() => {
        if (active) setNsRegistry([]);
      });
    return () => {
      active = false;
    };
  }, []);

  const [k8sIdOptions, setK8sIdOptions] = useState<string[]>([]);

  const [registryClusters, setRegistryClusters] = useState<ClusterInfo[]>([]);
  const [registryLoading, setRegistryLoading] = useState(true);

  const loadRegistryClusters = useCallback(async () => {
    setRegistryLoading(true);
    try {
      setRegistryClusters(await listRegistryClusters());
    } catch {
      setRegistryClusters([]);
    } finally {
      setRegistryLoading(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    listRegistryClusters()
      .then((next) => {
        if (active) setRegistryClusters(next);
      })
      .catch(() => {
        if (active) setRegistryClusters([]);
      })
      .finally(() => {
        if (active) setRegistryLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let active = true;
    listK8sCerts()
      .then((certs) => {
        if (active) {
          setK8sIdOptions([...new Set(certs.map((cert) => cert.k8sId).filter(Boolean))]);
        }
      })
      .catch(() => {
        if (active) setK8sIdOptions([]);
      });
    return () => {
      active = false;
    };
  }, []);

  const [nsCreateModalOpen, setNsCreateModalOpen] = useState(false);
  const [nsModalMode, setNsModalMode] = useState<'create' | 'edit'>('create');
  const [nsEditId, setNsEditId] = useState<number | null>(null);
  const [nsCreateForm] = Form.useForm();

  const handleNsSubmit = useCallback(async () => {
    let values: Record<string, string>;
    try {
      values = await nsCreateForm.validateFields();
    } catch {
      return;
    }
    const payload = {
      name: values.name,
      namesrvAddr: values.namesrvAddr,
      k8sNamespace: values.k8sNamespace || undefined,
      k8sId: values.k8sId || undefined,
      description: values.description || undefined,
    };
    try {
      if (nsModalMode === 'edit' && nsEditId !== null) {
        await updateNameserverRegistry({ id: nsEditId, ...payload });
        message.success(t('cluster.nsUpdated'));
      } else {
        await createNameserverRegistry(payload);
        message.success(t('cluster.nsCreated'));
      }
      setNsCreateModalOpen(false);
      nsCreateForm.resetFields();
      await loadNsRegistry();
    } catch {
      message.error(t('cluster.nsOperationFailed'));
    }
  }, [loadNsRegistry, nsCreateForm, nsEditId, nsModalMode, t]);

  const openNsCreateModal = useCallback(() => {
    setNsModalMode('create');
    setNsEditId(null);
    nsCreateForm.resetFields();
    setNsCreateModalOpen(true);
  }, [nsCreateForm]);

  const openNsEditModal = useCallback(
    (entry: NameserverRegistryEntry) => {
      setNsModalMode('edit');
      setNsEditId(entry.id);
      nsCreateForm.setFieldsValue({
        name: entry.name,
        namesrvAddr: entry.namesrvAddr,
        k8sNamespace: entry.k8sNamespace ?? '',
        k8sId: entry.k8sId ?? '',
        description: entry.description ?? '',
      });
      setNsCreateModalOpen(true);
    },
    [nsCreateForm],
  );

  const handleNsDelete = useCallback(
    (entry: NameserverRegistryEntry) => {
      Modal.confirm({
        title: t('cluster.deleteNsConfirmTitle'),
        content: t('cluster.deleteNsConfirmContent', { name: entry.name }),
        okText: t('common.delete'),
        okType: 'danger',
        cancelText: t('common.cancel'),
        onOk: async () => {
          try {
            await deleteNameserverRegistry(entry.id);
            message.success(t('cluster.nsDeleted'));
            await loadNsRegistry();
          } catch {
            message.error(t('cluster.nsOperationFailed'));
          }
        },
      });
    },
    [loadNsRegistry, t],
  );

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
  const selectedInstanceIdRef = useRef<string | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;
    void listInstances()
      .then((nextInstances) => {
        if (cancelled) return;
        const apacheInstances = nextInstances.filter((instance) => instance.vendor === 'APACHE');
        const initialInstanceId = apacheInstances.some(
          (instance) => instance.name === requestedInstanceId,
        )
          ? requestedInstanceId
          : apacheInstances[0]?.name;
        selectedInstanceIdRef.current = initialInstanceId;
        setInstanceLoadError(null);
        if (initialInstanceId) void requestRefreshRef.current('manual');
      })
      .catch(() => {
        if (cancelled) return;
        selectedInstanceIdRef.current = undefined;
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

      if (!selectedInstanceIdRef.current && !isMockMode()) {
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
            const nextClusters = await listClusters(selectedInstanceIdRef.current);
            if (!mountedRef.current) return;
            setClusters(nextClusters);
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

    const allBrokers: BrokerWithCluster[] = registryClusters.flatMap((c) =>
      (c.brokers ?? [])
        .filter((b) => {
          const matchSearch =
            !brokerSearchText ||
            searchText(c.nsClusterName).includes(brokerSearchText) ||
            searchText(b.name).includes(brokerSearchText) ||
            searchText(b.addr).includes(brokerSearchText);
          return matchSearch;
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
        title: t('cluster.brokerClusterName'),
        dataIndex: 'nsClusterName',
        key: 'nsClusterName',
        width: 160,
        sorter: (a, b) => a.nsClusterName.localeCompare(b.nsClusterName),
        render: (name: string) => (
          <Text strong style={{ fontSize: 14 }}>
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
          <Text strong style={{ fontSize: 14 }}>
            {name}
          </Text>
        ),
      },
      {
        title: t('common.status'),
        dataIndex: 'status',
        key: 'status',
        width: 90,
        align: 'center',
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
        align: 'center',
        sorter: (a, b) => (a.version || '').localeCompare(b.version || ''),
        render: (v?: string | null) => <span style={{ fontSize: 14 }}>{v || '-'}</span>,
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
        render: (addr: string | null) => <span style={{ fontSize: 14 }}>{safeText(addr)}</span>,
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
          </Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={openConnectModal}>
            {t('cluster.createCluster')}
          </Button>
        </Flex>
        <Card styles={{ body: { padding: 0 } }}>
          <Table
            columns={brokerColumns}
            dataSource={allBrokers}
            loading={registryLoading}
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
                    instanceId: selectedInstanceIdRef.current,
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

  // ─── Tab 2: NameServer 管理 (rmq_nameserver 注册表) ────────────────────────

  function renderNameServerTab() {
    const nsSearchText = searchText(nsSearch);
    const filteredRegistry = nsRegistry.filter((entry) => {
      if (!nsSearchText) return true;
      return (
        searchText(entry.name).includes(nsSearchText) ||
        searchText(entry.namesrvAddr).includes(nsSearchText) ||
        searchText(entry.k8sNamespace).includes(nsSearchText) ||
        searchText(entry.k8sId).includes(nsSearchText)
      );
    });

    const registryColumns: ColumnsType<NameserverRegistryEntry> = [
      {
        title: t('common.name'),
        dataIndex: 'name',
        key: 'name',
        width: 180,
        sorter: (a, b) => compareText(a.name, b.name),
        render: (name: string | null) => (
          <Text strong style={{ fontSize: 14 }}>
            {safeText(name)}
          </Text>
        ),
      },
      {
        title: t('cluster.nsAddr'),
        dataIndex: 'namesrvAddr',
        key: 'namesrvAddr',
        sorter: (a, b) => compareText(a.namesrvAddr, b.namesrvAddr),
        render: (addr: string | null) => <Text style={{ fontSize: 14 }}>{safeText(addr)}</Text>,
      },
      {
        title: t('cluster.k8sId'),
        dataIndex: 'k8sId',
        key: 'k8sId',
        width: 180,
        sorter: (a, b) => compareText(a.k8sId, b.k8sId),
        render: (k8sId: string | null) =>
          k8sId ? (
            <Text style={{ fontFamily: 'monospace', fontSize: 14 }}>{k8sId}</Text>
          ) : (
            <Text type="secondary">-</Text>
          ),
      },
      {
        title: t('cluster.k8sNamespace'),
        dataIndex: 'k8sNamespace',
        key: 'k8sNamespace',
        width: 180,
        sorter: (a, b) => compareText(a.k8sNamespace, b.k8sNamespace),
        render: (ns: string | null) =>
          ns ? <Text style={{ fontSize: 14 }}>{ns}</Text> : <Text type="secondary">-</Text>,
      },
      {
        title: t('common.status'),
        dataIndex: 'status',
        key: 'status',
        width: 90,
        sorter: (a, b) => safeText(a.status).localeCompare(safeText(b.status)),
        render: (status: string | null) => {
          const map: Record<string, { color: string; label: string }> = {
            healthy: { color: 'green', label: t('cluster.running') },
            warning: { color: 'gold', label: t('cluster.warning') },
            error: { color: 'red', label: t('cluster.error') },
            offline: { color: 'default', label: t('cluster.offline') },
          };
          const cfg = map[status ?? ''] ?? { color: 'default', label: safeText(status) };
          return <Tag color={cfg.color}>{cfg.label}</Tag>;
        },
      },
      {
        title: t('cluster.nsDescription'),
        dataIndex: 'description',
        key: 'description',
        ellipsis: true,
        render: (desc: string | null) => safeText(desc) || <Text type="secondary">-</Text>,
      },
      {
        title: t('common.actions'),
        key: 'action',
        width: 160,
        render: (_: unknown, record: NameserverRegistryEntry) => (
          <Flex gap={6}>
            <Button
              size="small"
              icon={<EditOutlined />}
              style={{ borderColor: '#722ed1', color: '#722ed1' }}
              onClick={() => openNsEditModal(record)}
            >
              {t('common.edit')}
            </Button>
            <Button
              size="small"
              danger
              icon={<DeleteOutlined />}
              onClick={() => handleNsDelete(record)}
            >
              {t('common.delete')}
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
              placeholder={t('cluster.searchNs')}
              allowClear
              onSearch={setNsSearch}
              onChange={(e) => !e.target.value && setNsSearch('')}
              style={{ width: 240 }}
            />
          </Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={openNsCreateModal}>
            {t('cluster.createNameServer')}
          </Button>
        </Flex>
        <Card styles={{ body: { padding: 0 } }}>
          <Table
            columns={registryColumns}
            dataSource={filteredRegistry}
            rowKey="id"
            pagination={{ pageSize: 20 }}
            size="small"
          />
        </Card>
      </div>
    );
  }

  // ─── Tab 3: Proxy 管理 (flat table) ────────────────────────────────────────

  function renderProxyTab() {
    type ProxyRow = ProxyDetail;
    const proxySearchText = searchText(proxySearch);

    const allProxies: ProxyRow[] = registryClusters
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
        title: t('cluster.brokerClusterName'),
        dataIndex: 'nsClusterName',
        key: 'nsClusterName',
        width: 160,
        sorter: (a, b) => a.nsClusterName.localeCompare(b.nsClusterName),
        render: (name: string) => (
          <Text strong style={{ fontSize: 14 }}>
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
        render: (addr: string | null) => <Text style={{ fontSize: 14 }}>{safeText(addr)}</Text>,
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
        title: t('cluster.remotingPort'),
        dataIndex: 'remotingPort',
        key: 'remotingPort',
        width: 120,
        align: 'center',
        sorter: (a, b) => a.remotingPort - b.remotingPort,
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
            <Button
              size="small"
              icon={<ReloadOutlined />}
              style={{ borderColor: '#faad14', color: '#faad14' }}
              onClick={() => {
                Modal.confirm({
                  title: t('cluster.confirmRestart'),
                  content: t('cluster.restartProxyConfirm', { addr: record.addr }),
                  okText: t('common.confirm'),
                  cancelText: t('common.cancel'),
                  onOk: async () => {
                    await restartProxy({ clusterId: record.clusterId, addr: record.addr });
                    await requestRefresh('operation');
                    message.success(t('cluster.restartProxySubmitted', { addr: record.addr }));
                  },
                });
              }}
            >
              {t('cluster.restart')}
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
            loading={registryLoading}
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
              onClick={() => {
                void requestRefresh('manual');
                void loadNsRegistry();
                void loadRegistryClusters();
              }}
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
              <Text type={refreshFailed ? 'danger' : 'secondary'} style={{ fontSize: 14 }}>
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
      {/* ─── NameServer 注册表新建/编辑弹窗 ─── */}
      <Modal
        title={
          nsModalMode === 'create' ? t('cluster.createNameServer') : t('cluster.editNameServer')
        }
        open={nsCreateModalOpen}
        onCancel={() => setNsCreateModalOpen(false)}
        onOk={() => void handleNsSubmit()}
        okText={t('common.confirm')}
        cancelText={t('common.cancel')}
        destroyOnHidden
      >
        <Form form={nsCreateForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="name"
            label={t('common.name')}
            rules={[{ required: true, message: t('common.name') }]}
          >
            <Input placeholder="rocketmq1" />
          </Form.Item>
          <Form.Item
            name="namesrvAddr"
            label={t('cluster.nsAddr')}
            rules={[{ required: true, message: t('cluster.nsAddr') }]}
          >
            <Input placeholder={t('cluster.nsAddrPlaceholder')} />
          </Form.Item>
          <Form.Item name="k8sId" label={t('cluster.k8sId')} extra={t('cluster.k8sIdExtra')}>
            <Select
              allowClear
              placeholder={t('cluster.k8sIdPlaceholder')}
              options={k8sIdOptions.map((k8sId) => ({ value: k8sId, label: k8sId }))}
            />
          </Form.Item>
          <Form.Item name="k8sNamespace" label={t('cluster.k8sNamespace')}>
            <Input placeholder="rocketmq1" />
          </Form.Item>
          <Form.Item name="description" label={t('cluster.nsDescription')}>
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
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
            <Descriptions.Item label={t('cluster.remotingPort')}>
              {selectedProxy.remotingPort}
            </Descriptions.Item>
            <Descriptions.Item label={t('cluster.grpcPort')}>
              {selectedProxy.grpcPort}
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
