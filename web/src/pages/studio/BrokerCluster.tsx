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
import { Table, Button, Tag, Tabs, Card, Space, Switch, Progress, Tooltip } from 'antd';
import {
  Plus,
  ArrowClockwise,
  GearSix,
  ArrowsClockwise,
  Cloud,
  ChartBar,
  PlugsConnected,
} from '@phosphor-icons/react';
import { useLang } from '../../i18n/LangContext';

// ─── Types ──────────────────────────────────────────────────────
interface BrokerRecord {
  key: string;
  k8sCluster: string;
  brokerName: string;
  status: 'running' | 'readonly' | 'maintenance';
  version: string;
  diskUsage: number;
  address: string;
  tpsIn: string;
  tpsOut: string;
}

interface NameServerRecord {
  key: string;
  k8sCluster: string;
  name: string;
  status: 'running' | 'readonly' | 'maintenance';
  version: string;
  address: string;
  connections: number;
}

interface ProxyRecord {
  key: string;
  k8sCluster: string;
  name: string;
  status: 'running' | 'readonly' | 'maintenance';
  version: string;
  address: string;
  grpcPort: string;
  connections: number;
}

// ─── Mock Data ──────────────────────────────────────────────────
const brokerData: BrokerRecord[] = [
  {
    key: '1',
    k8sCluster: 'prod-cn-east-1',
    brokerName: 'broker-a',
    status: 'running',
    version: '5.3.0',
    diskUsage: 62,
    address: '10.0.1.10:10911',
    tpsIn: '12,580',
    tpsOut: '8,340',
  },
  {
    key: '2',
    k8sCluster: 'prod-cn-east-1',
    brokerName: 'broker-b',
    status: 'readonly',
    version: '5.3.0',
    diskUsage: 89,
    address: '10.0.1.11:10911',
    tpsIn: '0',
    tpsOut: '3,120',
  },
  {
    key: '3',
    k8sCluster: 'prod-cn-east-1',
    brokerName: 'broker-c',
    status: 'running',
    version: '5.2.0',
    diskUsage: 45,
    address: '10.0.1.12:10911',
    tpsIn: '9,750',
    tpsOut: '6,280',
  },
  {
    key: '4',
    k8sCluster: 'prod-cn-south-1',
    brokerName: 'broker-d',
    status: 'maintenance',
    version: '5.3.0',
    diskUsage: 33,
    address: '10.0.2.10:10911',
    tpsIn: '0',
    tpsOut: '0',
  },
  {
    key: '5',
    k8sCluster: 'prod-cn-south-1',
    brokerName: 'broker-e',
    status: 'running',
    version: '5.3.0',
    diskUsage: 51,
    address: '10.0.2.11:10911',
    tpsIn: '7,890',
    tpsOut: '5,430',
  },
  {
    key: '6',
    k8sCluster: 'staging-cn-east-1',
    brokerName: 'broker-staging-a',
    status: 'running',
    version: '5.3.1',
    diskUsage: 28,
    address: '10.0.10.10:10911',
    tpsIn: '1,230',
    tpsOut: '980',
  },
];

const nameServerData: NameServerRecord[] = [
  {
    key: '1',
    k8sCluster: 'prod-cn-east-1',
    name: 'nameserver-a',
    status: 'running',
    version: '5.3.0',
    address: '10.0.1.20:9876',
    connections: 156,
  },
  {
    key: '2',
    k8sCluster: 'prod-cn-east-1',
    name: 'nameserver-b',
    status: 'running',
    version: '5.3.0',
    address: '10.0.1.21:9876',
    connections: 148,
  },
  {
    key: '3',
    k8sCluster: 'prod-cn-south-1',
    name: 'nameserver-c',
    status: 'running',
    version: '5.3.0',
    address: '10.0.2.20:9876',
    connections: 92,
  },
];

const proxyData: ProxyRecord[] = [
  {
    key: '1',
    k8sCluster: 'prod-cn-east-1',
    name: 'proxy-a',
    status: 'running',
    version: '5.3.0',
    address: '10.0.1.30:8080',
    grpcPort: '10.0.1.30:8081',
    connections: 2340,
  },
  {
    key: '2',
    k8sCluster: 'prod-cn-south-1',
    name: 'proxy-b',
    status: 'running',
    version: '5.3.0',
    address: '10.0.2.30:8080',
    grpcPort: '10.0.2.30:8081',
    connections: 1560,
  },
];

// ─── Component ──────────────────────────────────────────────────
const BrokerClusterPage = () => {
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [activeTab, setActiveTab] = useState('broker');
  const { t } = useLang();

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

  const renderDiskUsage = (percent: number) => {
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
      render: (text: string) => <span style={{ fontWeight: 500 }}>{text}</span>,
      sorter: (a: BrokerRecord, b: BrokerRecord) =>
        parseFloat(a.tpsIn.replace(/,/g, '')) - parseFloat(b.tpsIn.replace(/,/g, '')),
    },
    {
      title: t('brokerCluster.tpsOut'),
      dataIndex: 'tpsOut',
      key: 'tpsOut',
      render: (text: string) => <span style={{ fontWeight: 500 }}>{text}</span>,
      sorter: (a: BrokerRecord, b: BrokerRecord) =>
        parseFloat(a.tpsOut.replace(/,/g, '')) - parseFloat(b.tpsOut.replace(/,/g, '')),
    },
    {
      title: t('common.actions'),
      key: 'action',
      render: () => (
        <Space size="small">
          <Tooltip title={t('brokerCluster.config')}>
            <Button type="link" size="small" icon={<GearSix size={14} />}>
              {t('brokerCluster.config')}
            </Button>
          </Tooltip>
          <Tooltip title={t('brokerCluster.restart')}>
            <Button type="link" size="small" icon={<ArrowsClockwise size={14} />}>
              {t('brokerCluster.restart')}
            </Button>
          </Tooltip>
        </Space>
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
      render: (text: number) => <span style={{ fontWeight: 500 }}>{text}</span>,
    },
    {
      title: t('common.actions'),
      key: 'action',
      render: () => (
        <Space size="small">
          <Button type="link" size="small" icon={<GearSix size={14} />}>
            {t('brokerCluster.config')}
          </Button>
          <Button type="link" size="small" icon={<ArrowsClockwise size={14} />}>
            {t('brokerCluster.restart')}
          </Button>
        </Space>
      ),
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
      render: (text: number) => <span style={{ fontWeight: 500 }}>{text}</span>,
    },
    {
      title: t('common.actions'),
      key: 'action',
      render: () => (
        <Space size="small">
          <Button type="link" size="small" icon={<GearSix size={14} />}>
            {t('brokerCluster.config')}
          </Button>
          <Button type="link" size="small" icon={<ArrowsClockwise size={14} />}>
            {t('brokerCluster.restart')}
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
          <Button icon={<ArrowClockwise size={14} />} size="small">
            {t('common.reset')}
          </Button>
          <Button type="primary" icon={<Plus size={14} />}>
            {t('brokerCluster.createCluster')}
          </Button>
        </Space>
      </div>

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
    </div>
  );
};

export default BrokerClusterPage;
