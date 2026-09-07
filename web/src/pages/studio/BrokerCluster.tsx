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
import { Table, Button, Tag, Tabs, Card, Space, Switch, Progress, Spin, App, Select } from 'antd';
import {
  ArrowClockwise,
  Cloud,
  ChartBar,
  DownloadSimple,
  PlugsConnected,
} from '@phosphor-icons/react';
import { useLang } from '../../i18n/LangContext';
import { listClusters } from '../../services/clusterService';
import { isMockMode } from '../../services/dataMode';
import type { ClusterInfo } from '../../api/cluster';
import { supportsApacheRuntime, type Instance } from '../../api/instance';
import { listInstances } from '../../services/instanceService';
import { useVisiblePolling } from '../../hooks/useVisiblePolling';
import { buildCsv, downloadCsv, type CsvColumn } from '../../utils/download';

// ─── Types ──────────────────────────────────────────────────────
type NodeStatus = 'running' | 'readonly' | 'maintenance' | 'unknown';
type ClusterTabKey = 'nameserver' | 'broker' | 'proxy';

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

const BROKER_EXPORT_COLUMNS: CsvColumn<BrokerRecord>[] = [
  { header: 'Cluster', value: (broker) => broker.k8sCluster },
  { header: 'Broker Name', value: (broker) => broker.brokerName },
  { header: 'Status', value: (broker) => broker.status },
  { header: 'Version', value: (broker) => broker.version },
  { header: 'Disk Usage', value: (broker) => broker.diskUsage },
  { header: 'Address', value: (broker) => broker.address },
  { header: 'TPS In', value: (broker) => broker.tpsIn },
  { header: 'TPS Out', value: (broker) => broker.tpsOut },
];

const NAMESERVER_EXPORT_COLUMNS: CsvColumn<NameServerRecord>[] = [
  { header: 'Cluster', value: (nameServer) => nameServer.k8sCluster },
  { header: 'NameServer Name', value: (nameServer) => nameServer.name },
  { header: 'Status', value: (nameServer) => nameServer.status },
  { header: 'Version', value: (nameServer) => nameServer.version },
  { header: 'Address', value: (nameServer) => nameServer.address },
  { header: 'Connections', value: (nameServer) => nameServer.connections },
];

const PROXY_EXPORT_COLUMNS: CsvColumn<ProxyRecord>[] = [
  { header: 'Cluster', value: (proxy) => proxy.k8sCluster },
  { header: 'Proxy Name', value: (proxy) => proxy.name },
  { header: 'Status', value: (proxy) => proxy.status },
  { header: 'Version', value: (proxy) => proxy.version },
  { header: 'HTTP Address', value: (proxy) => proxy.address },
  { header: 'gRPC Address', value: (proxy) => proxy.grpcPort },
  { header: 'Connections', value: (proxy) => proxy.connections },
];

// ─── Helpers ────────────────────────────────────────────────────
const normalizeStatus = (status: string): NodeStatus => {
  const value = (status || '').toLowerCase();
  if (value === 'readonly' || value === 'warning') return 'readonly';
  if (value === 'maintenance' || value === 'error' || value === 'offline') return 'maintenance';
  if (value === 'running' || value === 'healthy') return 'running';
  return 'unknown';
};

const hostOf = (addr: string): string => {
  const value = addr.trim();
  if (value.startsWith('[')) {
    const closingBracket = value.indexOf(']');
    return closingBracket >= 0 ? value.slice(0, closingBracket + 1) : value;
  }
  const firstColon = value.indexOf(':');
  const lastColon = value.lastIndexOf(':');
  return firstColon >= 0 && firstColon === lastColon ? value.slice(0, lastColon) : value;
};

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

    (cluster.brokers ?? []).forEach((broker, index) => {
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

    (cluster.nameServers ?? []).forEach((nameServer, index) => {
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

    (cluster.proxies ?? []).forEach((proxy, index) => {
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
  const [activeTab, setActiveTab] = useState<ClusterTabKey>('broker');
  const [loading, setLoading] = useState(false);
  const [brokerData, setBrokerData] = useState<BrokerRecord[]>([]);
  const [nameServerData, setNameServerData] = useState<NameServerRecord[]>([]);
  const [proxyData, setProxyData] = useState<ProxyRecord[]>([]);
  const [instances, setInstances] = useState<Instance[]>([]);
  const [selectedInstanceId, setSelectedInstanceId] = useState<string | undefined>(undefined);
  const mountedRef = useRef(true);
  const loadRequestId = useRef(0);
  const { t } = useLang();
  const { message } = App.useApp();

  const clearData = useCallback(() => {
    setBrokerData([]);
    setNameServerData([]);
    setProxyData([]);
  }, []);

  const loadData = useCallback(async () => {
    if (!selectedInstanceId && !isMockMode()) {
      clearData();
      return;
    }
    const requestId = ++loadRequestId.current;
    setLoading(true);
    try {
      const clusters = await listClusters(selectedInstanceId);
      if (!mountedRef.current || requestId !== loadRequestId.current) return;
      const mapped = mapClusters(clusters);
      setBrokerData(mapped.brokers);
      setNameServerData(mapped.nameServers);
      setProxyData(mapped.proxies);
    } catch {
      if (!mountedRef.current || requestId !== loadRequestId.current) return;
      clearData();
      message.error(t('common.refreshFailed'));
    } finally {
      if (mountedRef.current && requestId === loadRequestId.current) {
        setLoading(false);
      }
    }
  }, [clearData, message, selectedInstanceId, t]);

  useEffect(() => {
    let active = true;
    void listInstances()
      .then((nextInstances) => {
        if (!active) return;
        const apacheInstances = nextInstances.filter(supportsApacheRuntime);
        setInstances(apacheInstances);
        setSelectedInstanceId(apacheInstances[0]?.name);
      })
      .catch(() => {
        if (!active) return;
        clearData();
        message.error(t('common.fetchDataFailed'));
      });
    return () => {
      active = false;
    };
  }, [clearData, message, t]);

  useEffect(() => {
    mountedRef.current = true;
    const requestId = loadRequestId.current;
    void Promise.resolve().then(() => {
      loadData();
    });
    return () => {
      loadRequestId.current = requestId + 1;
      mountedRef.current = false;
    };
  }, [loadData]);

  useVisiblePolling(autoRefresh, REFRESH_INTERVAL_MS, loadData);

  const renderStatus = (status: string) => {
    const config: Record<string, { color: string; label: string }> = {
      running: { color: 'success', label: t('brokerCluster.statusRunning') },
      readonly: { color: 'warning', label: t('brokerCluster.statusReadonly') },
      maintenance: {
        color: 'error',
        label: t('brokerCluster.statusMaintenance'),
      },
      unknown: { color: 'default', label: t('common.na') },
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
        <span style={{ fontSize: 14, color, fontWeight: 500 }}>{percent}%</span>
      </div>
    );
  };

  function handleExport() {
    const today = new Date().toISOString().slice(0, 10);
    if (activeTab === 'nameserver') {
      downloadCsv(
        `rocketmq-nameserver-topology-${today}.csv`,
        buildCsv(NAMESERVER_EXPORT_COLUMNS, nameServerData),
      );
      return;
    }
    if (activeTab === 'proxy') {
      downloadCsv(
        `rocketmq-proxy-topology-${today}.csv`,
        buildCsv(PROXY_EXPORT_COLUMNS, proxyData),
      );
      return;
    }
    downloadCsv(
      `rocketmq-broker-topology-${today}.csv`,
      buildCsv(BROKER_EXPORT_COLUMNS, brokerData),
    );
  }
  const exportDisabled =
    (activeTab === 'nameserver' && nameServerData.length === 0) ||
    (activeTab === 'proxy' && proxyData.length === 0) ||
    (activeTab === 'broker' && brokerData.length === 0);

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
            fontSize: 14,
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
            fontSize: 14,
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
            fontSize: 14,
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
            fontSize: 14,
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
          <Select
            aria-label="选择实例"
            value={selectedInstanceId}
            onChange={setSelectedInstanceId}
            placeholder="选择实例"
            style={{ minWidth: 180 }}
            options={instances.map((instance) => ({ value: instance.name, label: instance.name }))}
          />
          <Button
            icon={<DownloadSimple size={14} />}
            size="small"
            disabled={exportDisabled}
            onClick={handleExport}
          >
            {t('common.export')}
          </Button>
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
        <Card
          variant="borderless"
          style={{ borderRadius: 8, boxShadow: '0 1px 6px rgba(0,0,0,0.04)' }}
        >
          <Tabs
            activeKey={activeTab}
            onChange={(key) => setActiveTab(key as ClusterTabKey)}
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
