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
import {
  Table,
  Button,
  Tag,
  Tabs,
  Card,
  Space,
  Switch,
  Progress,
  Tooltip,
  Spin,
  App,
  Modal,
} from 'antd';
import {
  ArrowClockwise,
  ArrowsClockwise,
  Cloud,
  ChartBar,
  PlugsConnected,
} from '@phosphor-icons/react';
import { useLang } from '../../i18n/LangContext';
import { listClusters, restartBroker } from '../../services/clusterService';
import type { ClusterInfo } from '../../api/cluster';

// ─── Types ──────────────────────────────────────────────────────
type NodeStatus = 'running' | 'readonly' | 'maintenance';

const REFRESH_INTERVAL_MS = 2000;

interface BrokerRecord {
  key: string;
  clusterId: string;
  k8sCluster: string;
  brokerName: string;
  status: NodeStatus;
  version: string;
  diskUsage: number | null;
  address: string;
  tpsIn: number | null;
  tpsOut: number | null;
}

interface NameServerRecord {
  key: string;
  k8sCluster: string;
  name: string;
  status: NodeStatus;
  version: string;
  address: string;
  connections: number;
}

interface ProxyRecord {
  key: string;
  k8sCluster: string;
  name: string;
  status: NodeStatus;
  version: string;
  address: string;
  grpcPort: string;
  connections: number;
}

// ─── Helpers ────────────────────────────────────────────────────
const normalizeStatus = (status: string): NodeStatus => {
  const value = (status || '').toLowerCase();
  if (value === 'readonly' || value === 'warning') return 'readonly';
  if (value === 'maintenance' || value === 'error' || value === 'offline') return 'maintenance';
  return 'running';
};

const hostOf = (addr: string): string => addr.split(':')[0] ?? addr;

function mapClusters(clusters: ClusterInfo[]): {
  brokers: BrokerRecord[];
  nameServers: NameServerRecord[];
  proxies: ProxyRecord[];
} {
  const brokers: BrokerRecord[] = [];
  const nameServers: NameServerRecord[] = [];
  const proxies: ProxyRecord[] = [];

  clusters.forEach((cluster) => {
    const clusterLabel = cluster.nsClusterName || cluster.name || cluster.id;

    cluster.brokers.forEach((broker, index) => {
      brokers.push({
        key: `${cluster.id}-broker-${broker.addr || index}`,
        clusterId: cluster.id,
        k8sCluster: clusterLabel,
        brokerName: broker.name || broker.addr,
        status: normalizeStatus(broker.status),
        version: broker.version || '-',
        diskUsage: broker.runtimeStatsAvailable === false ? null : (broker.diskUsage ?? 0),
        address: broker.addr,
        tpsIn: broker.runtimeStatsAvailable === false ? null : (broker.tpsIn ?? 0),
        tpsOut: broker.runtimeStatsAvailable === false ? null : (broker.tpsOut ?? 0),
      });
    });

    cluster.nameServers.forEach((nameServer, index) => {
      nameServers.push({
        key: `${cluster.id}-ns-${nameServer.addr || index}`,
        k8sCluster: clusterLabel,
        name: nameServer.addr,
        status: normalizeStatus(nameServer.status),
        version: cluster.version,
        address: nameServer.addr,
        connections: 0,
      });
    });

    cluster.proxies.forEach((proxy, index) => {
      const host = hostOf(proxy.addr);
      proxies.push({
        key: `${cluster.id}-proxy-${proxy.addr || index}`,
        k8sCluster: clusterLabel,
        name: proxy.addr,
        status: normalizeStatus(proxy.status),
        version: cluster.version,
        address: proxy.addr,
        grpcPort: proxy.grpcPort ? `${host}:${proxy.grpcPort}` : '-',
        connections: proxy.connections ?? 0,
      });
    });
  });

  return { brokers, nameServers, proxies };
}

// ─── Component ──────────────────────────────────────────────────
const BrokerClusterPage = () => {
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [activeTab, setActiveTab] = useState('broker');
  const [loading, setLoading] = useState(false);
  const [brokerData, setBrokerData] = useState<BrokerRecord[]>([]);
  const [nameServerData, setNameServerData] = useState<NameServerRecord[]>([]);
  const [proxyData, setProxyData] = useState<ProxyRecord[]>([]);
  const loadRequestId = useRef(0);
  const { t } = useLang();
  const { message } = App.useApp();

  const loadData = useCallback(async () => {
    const requestId = ++loadRequestId.current;
    setLoading(true);
    try {
      const clusters = await listClusters();
      if (requestId !== loadRequestId.current) return;
      const mapped = mapClusters(clusters);
      setBrokerData(mapped.brokers);
      setNameServerData(mapped.nameServers);
      setProxyData(mapped.proxies);
    } catch {
      if (requestId !== loadRequestId.current) return;
      message.error(t('common.refreshFailed'));
    } finally {
      if (requestId === loadRequestId.current) {
        setLoading(false);
      }
    }
  }, [message, t]);

  const handleRestartBroker = async (broker: BrokerRecord) => {
    try {
      const result = await restartBroker(broker.clusterId, broker.brokerName);
      if (!result.success) {
        message.error(result.message || t('common.failure'));
        return;
      }
      await loadData();
      message.success(
        result.message || t('cluster.restartBrokerSubmitted', { name: broker.brokerName }),
      );
    } catch {
      message.error(t('common.failure'));
    }
  };

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void loadData();
    });
    return () => {
      window.clearTimeout(timeoutId);
      ++loadRequestId.current;
    };
  }, [loadData]);

  useEffect(() => {
    if (!autoRefresh) return;

    const intervalId = window.setInterval(() => {
      void loadData();
    }, REFRESH_INTERVAL_MS);
    return () => window.clearInterval(intervalId);
  }, [autoRefresh, loadData]);

  const renderStatus = (status: string) => {
    const config: Record<string, { color: string; label: string }> = {
      running: { color: 'success', label: t('brokerCluster.statusRunning') },
      readonly: { color: 'warning', label: t('brokerCluster.statusReadonly') },
      maintenance: {
        color: 'error',
        label: t('brokerCluster.statusMaintenance'),
      },
    };
    const { color, label } = config[status] || config.running;
    return <Tag color={color}>{label}</Tag>;
  };

  const renderDiskUsage = (percent: number | null) => {
    if (percent == null) return '-';
    let status: 'normal' | 'active' | 'exception' = 'normal';
    let color = '#52c41a';
    if (percent > 85) {
      status = 'exception';
      color = '#ff4d4f';
    } else if (percent > 70) {
      status = 'active';
      color = '#fa8c16';
    }
    return (
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <Progress
          percent={percent}
          size="small"
          status={status}
          style={{ width: 80, margin: 0 }}
          strokeColor={color}
        />
        <span style={{ fontSize: 12, color, fontWeight: 500 }}>{percent}%</span>
      </div>
    );
  };

  const brokerColumns = [
    {
      title: t('brokerCluster.k8sCluster'),
      dataIndex: 'k8sCluster',
      key: 'k8sCluster',
      render: (text: string) => <span style={{ fontWeight: 500 }}>{text}</span>,
    },
    {
      title: t('brokerCluster.brokerName'),
      dataIndex: 'brokerName',
      key: 'brokerName',
      render: (text: string) => <span style={{ color: '#1677ff', fontWeight: 500 }}>{text}</span>,
    },
    {
      title: t('brokerCluster.status'),
      dataIndex: 'status',
      key: 'status',
      render: renderStatus,
    },
    { title: t('brokerCluster.version'), dataIndex: 'version', key: 'version' },
    {
      title: t('brokerCluster.diskUsage'),
      dataIndex: 'diskUsage',
      key: 'diskUsage',
      render: renderDiskUsage,
      width: 160,
    },
    {
      title: t('common.address'),
      dataIndex: 'address',
      key: 'address',
      render: (text: string) => (
        <code
          style={{
            fontSize: 12,
            background: '#f5f5f5',
            padding: '2px 6px',
            borderRadius: 4,
          }}
        >
          {text}
        </code>
      ),
    },
    {
      title: t('brokerCluster.tpsIn'),
      dataIndex: 'tpsIn',
      key: 'tpsIn',
      render: (value: number | null) => (
        <span style={{ fontWeight: 500 }}>{value?.toLocaleString() ?? '-'}</span>
      ),
      sorter: (a: BrokerRecord, b: BrokerRecord) => (a.tpsIn ?? -1) - (b.tpsIn ?? -1),
    },
    {
      title: t('brokerCluster.tpsOut'),
      dataIndex: 'tpsOut',
      key: 'tpsOut',
      render: (value: number | null) => (
        <span style={{ fontWeight: 500 }}>{value?.toLocaleString() ?? '-'}</span>
      ),
      sorter: (a: BrokerRecord, b: BrokerRecord) => (a.tpsOut ?? -1) - (b.tpsOut ?? -1),
    },
    {
      title: t('common.actions'),
      key: 'action',
      render: (_: unknown, record: BrokerRecord) => (
        <Tooltip title={t('brokerCluster.restart')}>
          <Button
            type="link"
            size="small"
            icon={<ArrowsClockwise size={14} />}
            onClick={() => {
              Modal.confirm({
                title: t('cluster.confirmRestart'),
                content: t('cluster.restartBrokerConfirm', { name: record.brokerName }),
                okText: t('common.confirm'),
                cancelText: t('common.cancel'),
                onOk: () => handleRestartBroker(record),
              });
            }}
          >
            {t('brokerCluster.restart')}
          </Button>
        </Tooltip>
      ),
    },
  ];

  const nsColumns = [
    {
      title: t('brokerCluster.k8sCluster'),
      dataIndex: 'k8sCluster',
      key: 'k8sCluster',
      render: (text: string) => <span style={{ fontWeight: 500 }}>{text}</span>,
    },
    {
      title: t('brokerCluster.nsName'),
      dataIndex: 'name',
      key: 'name',
      render: (text: string) => <span style={{ color: '#1677ff', fontWeight: 500 }}>{text}</span>,
    },
    {
      title: t('brokerCluster.status'),
      dataIndex: 'status',
      key: 'status',
      render: renderStatus,
    },
    { title: t('brokerCluster.version'), dataIndex: 'version', key: 'version' },
    {
      title: t('common.address'),
      dataIndex: 'address',
      key: 'address',
      render: (text: string) => (
        <code
          style={{
            fontSize: 12,
            background: '#f5f5f5',
            padding: '2px 6px',
            borderRadius: 4,
          }}
        >
          {text}
        </code>
      ),
    },
    {
      title: t('brokerCluster.connections'),
      dataIndex: 'connections',
      key: 'connections',
      render: (text: number) => <span style={{ fontWeight: 500 }}>{text.toLocaleString()}</span>,
    },
  ];

  const proxyColumns = [
    {
      title: t('brokerCluster.k8sCluster'),
      dataIndex: 'k8sCluster',
      key: 'k8sCluster',
      render: (text: string) => <span style={{ fontWeight: 500 }}>{text}</span>,
    },
    {
      title: t('brokerCluster.proxyName'),
      dataIndex: 'name',
      key: 'name',
      render: (text: string) => <span style={{ color: '#1677ff', fontWeight: 500 }}>{text}</span>,
    },
    {
      title: t('brokerCluster.status'),
      dataIndex: 'status',
      key: 'status',
      render: renderStatus,
    },
    { title: t('brokerCluster.version'), dataIndex: 'version', key: 'version' },
    {
      title: t('brokerCluster.httpAddr'),
      dataIndex: 'address',
      key: 'address',
      render: (text: string) => (
        <code
          style={{
            fontSize: 12,
            background: '#f5f5f5',
            padding: '2px 6px',
            borderRadius: 4,
          }}
        >
          {text}
        </code>
      ),
    },
    {
      title: t('brokerCluster.grpcAddr'),
      dataIndex: 'grpcPort',
      key: 'grpcPort',
      render: (text: string) => (
        <code
          style={{
            fontSize: 12,
            background: '#f5f5f5',
            padding: '2px 6px',
            borderRadius: 4,
          }}
        >
          {text}
        </code>
      ),
    },
    {
      title: t('brokerCluster.connections'),
      dataIndex: 'connections',
      key: 'connections',
      render: (text: number) => <span style={{ fontWeight: 500 }}>{text.toLocaleString()}</span>,
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
          <Cloud size={22} style={{ marginRight: 8, color: '#1677ff' }} />
          {t('brokerCluster.title')}
        </h2>
        <Space size="middle">
          <Switch
            checked={autoRefresh}
            onChange={setAutoRefresh}
            checkedChildren={t('common.liveRefresh')}
            unCheckedChildren={t('brokerCluster.manual')}
            size="small"
          />
          <Button icon={<ArrowClockwise size={14} />} size="small" onClick={() => void loadData()}>
            {t('common.reset')}
          </Button>
        </Space>
      </div>

      <Spin spinning={loading} tip={t('common.loading')}>
        <Card bordered={false} style={{ borderRadius: 8, boxShadow: '0 1px 6px rgba(0,0,0,0.04)' }}>
          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            items={[
              {
                key: 'nameserver',
                label: (
                  <span>
                    <ChartBar size={16} style={{ marginRight: 4, verticalAlign: 'middle' }} />
                    {t('brokerCluster.nsManagement')}
                  </span>
                ),
                children: (
                  <Table
                    columns={nsColumns}
                    dataSource={nameServerData}
                    pagination={false}
                    size="middle"
                  />
                ),
              },
              {
                key: 'broker',
                label: (
                  <span>
                    <Cloud size={16} style={{ marginRight: 4, verticalAlign: 'middle' }} />
                    {t('brokerCluster.brokerManagement')}
                  </span>
                ),
                children: (
                  <Table
                    columns={brokerColumns}
                    dataSource={brokerData}
                    pagination={{
                      pageSize: 10,
                      showTotal: (total) => `${t('common.total')} ${total} Broker`,
                    }}
                    size="middle"
                  />
                ),
              },
              {
                key: 'proxy',
                label: (
                  <span>
                    <PlugsConnected size={16} style={{ marginRight: 4, verticalAlign: 'middle' }} />
                    {t('brokerCluster.proxyManagement')}
                  </span>
                ),
                children: (
                  <Table
                    columns={proxyColumns}
                    dataSource={proxyData}
                    pagination={false}
                    size="middle"
                  />
                ),
              },
            ]}
          />
        </Card>
      </Spin>
    </div>
  );
};

export default BrokerClusterPage;
