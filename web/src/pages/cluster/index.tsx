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

import { useState, useEffect } from 'react';
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
  Flex,
  Space,
  Typography,
  Card,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ReloadOutlined,
  SettingOutlined,
  EyeOutlined,
  EditOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { Cpu, HardDrives, Globe } from '@phosphor-icons/react';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import clusters, {
  type BrokerInfo,
  type ProxyInfo,
  type NameServerInfo,
  type ClusterConfig,
  type ClusterInfo,
} from '../../mock/clusters';

const { Text } = Typography;

// ─── Page ─────────────────────────────────────────────────────────────────────

const ClusterPage = () => {
  const { t } = useLang();
  const [nsSearch, setNsSearch] = useState('');
  const [brokerSearch, setBrokerSearch] = useState('');
  const [brokerNsClusterFilter, setBrokerNsClusterFilter] = useState<string>('');
  const [proxySearch, setProxySearch] = useState('');

  const [configModalOpen, setConfigModalOpen] = useState(false);
  const [selectedCluster, setSelectedCluster] = useState<ClusterInfo | null>(null);
  const [nsModalOpen, setNsModalOpen] = useState(false);
  const [nsModalMode, setNsModalMode] = useState<'create' | 'edit'>('create');
  const [nsForm] = Form.useForm();
  const [configForm] = Form.useForm();

  // ─── Auto-refresh TPS / connections every 2s ──────────────────────────────
  const [autoRefresh, setAutoRefresh] = useState(true);
  // Initialize from mock base values
  const initBrokerTps = (): Record<string, { tpsIn: number; tpsOut: number }> => {
    const m: Record<string, { tpsIn: number; tpsOut: number }> = {};
    clusters.forEach((c) =>
      c.brokers.forEach((b) => {
        m[b.addr] = { tpsIn: b.tpsIn, tpsOut: b.tpsOut };
      }),
    );
    return m;
  };
  const initProxyConn = (): Record<string, number> => {
    const m: Record<string, number> = {};
    clusters.forEach((c) =>
      c.proxies.forEach((p) => {
        m[p.addr] = p.connections;
      }),
    );
    return m;
  };

  const [brokerTpsMap, setBrokerTpsMap] = useState(initBrokerTps);
  const [proxyConnMap, setProxyConnMap] = useState(initProxyConn);

  useEffect(() => {
    if (!autoRefresh) return;

    const timer = setInterval(() => {
      setBrokerTpsMap((prev) => {
        const next = { ...prev };
        clusters.forEach((c) => {
          c.brokers.forEach((b) => {
            const cur = next[b.addr] ?? { tpsIn: b.tpsIn, tpsOut: b.tpsOut };
            const fluctuate = (base: number, v: number) =>
              base === 0 ? 0 : Math.max(0, Math.round(v + (Math.random() - 0.5) * base * 0.12));
            next[b.addr] = {
              tpsIn: fluctuate(b.tpsIn, cur.tpsIn),
              tpsOut: fluctuate(b.tpsOut, cur.tpsOut),
            };
          });
        });
        return next;
      });

      setProxyConnMap((prev) => {
        const next = { ...prev };
        clusters.forEach((c) => {
          c.proxies.forEach((p) => {
            const cur = prev[p.addr] ?? p.connections;
            next[p.addr] = Math.max(
              0,
              Math.round(cur + (Math.random() - 0.5) * p.connections * 0.08),
            );
          });
        });
        return next;
      });
    }, 2000);

    return () => clearInterval(timer);
  }, [autoRefresh]);

  // Broker config handler
  const handleConfigOpen = (cluster: ClusterInfo) => {
    const cfg: ClusterConfig = cluster.config;
    setSelectedCluster(cluster);
    configForm.setFieldsValue({
      flushDiskType: cfg.flushDiskType ?? 'ASYNC_FLUSH',
      autoCreateTopicEnable: cfg.autoCreateTopicEnable ?? false,
      autoCreateSubscriptionGroup: cfg.autoCreateSubscriptionGroup ?? false,
      maxMessageSizeMB: Math.round(cfg.maxMessageSize / 1048576),
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

    const allBrokers: BrokerWithCluster[] = clusters.flatMap((c) =>
      c.brokers
        .filter((b) => {
          const matchSearch =
            !brokerSearch ||
            b.name.toLowerCase().includes(brokerSearch.toLowerCase()) ||
            b.addr.toLowerCase().includes(brokerSearch.toLowerCase());
          const matchNsCluster =
            !brokerNsClusterFilter || c.nsClusterName === brokerNsClusterFilter;
          return matchSearch && matchNsCluster;
        })
        .map((b) => {
          const tpsOverride = brokerTpsMap[b.addr];
          return {
            ...b,
            tpsIn: tpsOverride?.tpsIn ?? b.tpsIn,
            tpsOut: tpsOverride?.tpsOut ?? b.tpsOut,
            clusterName: c.name,
            nsClusterName: c.nsClusterName,
            cluster: c,
          };
        }),
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
        sorter: (a, b) => a.version.localeCompare(b.version),
        render: (v: string) => <span style={{ fontSize: 13 }}>{v}</span>,
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
        sorter: (a, b) => a.addr.localeCompare(b.addr),
        render: (addr: string) => <span style={{ fontSize: 13 }}>{addr}</span>,
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
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => message.info(t('cluster.createClusterWip'))}
          >
            {t('cluster.createCluster')}
          </Button>
        </Flex>
        <Card bodyStyle={{ padding: 0 }}>
          <Table
            columns={brokerColumns}
            dataSource={allBrokers}
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
              configForm.validateFields().then(() => {
                message.success(t('cluster.configUpdated'));
                setConfigModalOpen(false);
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
    const filteredClusters = clusters
      .map((c) => {
        const nameServers = c.nameServers.filter((ns) => {
          const matchSearch = !nsSearch || ns.addr.toLowerCase().includes(nsSearch.toLowerCase());
          return matchSearch;
        });
        return { ...c, filteredNameServers: nameServers };
      })
      .filter((c) => c.filteredNameServers.length > 0);

    const getNsSubColumns = (clusterId: string): ColumnsType<NameServerInfo> => [
      {
        title: t('common.address'),
        dataIndex: 'addr',
        key: 'addr',
        sorter: (a, b) => a.addr.localeCompare(b.addr),
        render: (addr: string) => (
          <Text code style={{ fontSize: 12 }}>
            {addr}
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
        title: t('common.actions'),
        key: 'action',
        width: 100,
        render: (_: unknown, record: NameServerInfo) => (
          <Flex gap={6}>
            <Button
              size="small"
              icon={<EditOutlined />}
              style={{ borderColor: '#722ed1', color: '#722ed1' }}
              onClick={() => {
                setNsModalMode('edit');
                nsForm.setFieldsValue({ clusterId, addr: record.addr, newAddr: '' });
                setNsModalOpen(true);
              }}
            >
              {t('common.edit')}
            </Button>
          </Flex>
        ),
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
            <Input.Search
              placeholder={t('cluster.searchNs')}
              allowClear
              onSearch={setNsSearch}
              onChange={(e) => !e.target.value && setNsSearch('')}
              style={{ width: 240 }}
            />
          </Space>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setNsModalMode('create');
              nsForm.resetFields();
              setNsModalOpen(true);
            }}
          >
            {t('cluster.createNameServer')}
          </Button>
        </Flex>
        <Card bodyStyle={{ padding: 0 }}>
          <Table
            columns={clusterColumns}
            dataSource={filteredClusters}
            rowKey="id"
            pagination={{ pageSize: 20 }}
            size="small"
            expandable={{
              expandedRowRender: (record) => (
                <div style={{ padding: '8px 0' }}>
                  <Table
                    columns={getNsSubColumns(record.id)}
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
    type ProxyRow = ProxyInfo & { clusterName: string; nsClusterName: string };

    const allProxies: ProxyRow[] = clusters
      .filter((c) => c.proxies.length > 0)
      .flatMap((c) =>
        c.proxies
          .filter((p) => {
            const matchSearch =
              !proxySearch || p.addr.toLowerCase().includes(proxySearch.toLowerCase());
            return matchSearch;
          })
          .map((p) => ({
            ...p,
            connections: proxyConnMap[p.addr] ?? p.connections,
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
        sorter: (a, b) => a.addr.localeCompare(b.addr),
        render: (addr: string) => (
          <Text code style={{ fontSize: 12 }}>
            {addr}
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
              onClick={() => message.info(t('cluster.viewDetail', { addr: record.addr }))}
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
                  onOk: () =>
                    message.success(t('cluster.restartProxySubmitted', { addr: record.addr })),
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
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => message.info(t('cluster.createClusterWip'))}
          >
            {t('cluster.createCluster')}
          </Button>
        </Flex>
        <Card bodyStyle={{ padding: 0 }}>
          <Table
            columns={proxyColumns}
            dataSource={allProxies}
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

  const totalBrokers = clusters.reduce((s, c) => s + c.brokers.length, 0);
  const totalNameServers = clusters.reduce((s, c) => s + c.nameServers.length, 0);
  const totalProxies = clusters.reduce((s, c) => s + c.proxies.length, 0);

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title={t('cluster.title')}
        subtitle={`${t('common.total')} ${clusters.length} ${t('cluster.title')} · ${totalBrokers} Broker · ${totalNameServers} NameServer · ${totalProxies} Proxy`}
        extra={
          <Flex align="center" gap={6}>
            {autoRefresh && (
              <span
                style={{
                  width: 6,
                  height: 6,
                  borderRadius: '50%',
                  background: '#52c41a',
                  display: 'inline-block',
                  animation: 'livePulse 1.5s ease-in-out infinite',
                }}
              />
            )}
            <Text type="secondary" style={{ fontSize: 12 }}>
              {autoRefresh ? t('common.liveRefresh') : t('common.autoRefresh')}
            </Text>
            <Switch size="small" checked={autoRefresh} onChange={setAutoRefresh} />
          </Flex>
        }
      />
      <style>{`
        @keyframes livePulse {
          0%, 100% { opacity: 1; box-shadow: 0 0 0 0 rgba(82, 196, 26, 0.4); }
          50% { opacity: 0.6; box-shadow: 0 0 0 4px rgba(82, 196, 26, 0); }
        }
      `}</style>
      {/* ─── NameServer Create/Edit Modal ─── */}
      <Modal
        title={
          nsModalMode === 'create' ? t('cluster.createNameServer') : t('cluster.editNameServer')
        }
        open={nsModalOpen}
        onCancel={() => setNsModalOpen(false)}
        onOk={() => {
          nsForm.validateFields().then((values: Record<string, string>) => {
            if (nsModalMode === 'create') {
              message.success(`${t('cluster.nsCreated')}: ${values.addr}`);
            } else {
              message.success(
                `${t('cluster.nsUpdated')}: ${values.addr}${values.newAddr ? ` → ${values.newAddr}` : ''}`,
              );
            }
            setNsModalOpen(false);
          });
        }}
        okText={t('common.confirm')}
        cancelText={t('common.cancel')}
        destroyOnClose
      >
        <Form form={nsForm} layout="vertical" style={{ marginTop: 16 }}>
          {nsModalMode === 'create' && (
            <Form.Item
              name="clusterId"
              label={t('cluster.selectCluster')}
              rules={[{ required: true, message: t('cluster.selectCluster') }]}
            >
              <Select
                placeholder={t('cluster.selectCluster')}
                options={clusters.map((c) => ({ label: c.name, value: c.id }))}
              />
            </Form.Item>
          )}
          <Form.Item
            name="addr"
            label={t('cluster.nsAddr')}
            rules={[{ required: true, message: t('cluster.nsAddr') }]}
          >
            <Input placeholder={t('cluster.nsAddrPlaceholder')} disabled={nsModalMode === 'edit'} />
          </Form.Item>
          {nsModalMode === 'edit' && (
            <Form.Item name="newAddr" label={t('cluster.newAddr')}>
              <Input placeholder={t('cluster.newAddrPlaceholder')} />
            </Form.Item>
          )}
        </Form>
      </Modal>
      <Tabs items={tabItems} defaultActiveKey="broker" />
    </div>
  );
};

export default ClusterPage;
