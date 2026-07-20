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

import { useState, useRef } from 'react';
import {
  Card,
  Table,
  Tag,
  Button,
  Space,
  Modal,
  Form,
  Input,
  Spin,
  Row,
  Col,
  Statistic,
  Progress,
  Descriptions,
  Tooltip,
  Popconfirm,
  App,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowClockwise,
  Plus,
  Trash,
  GearSix,
  Dashboard,
  CheckCircle,
  XCircle,
  Warning,
} from '@phosphor-icons/react';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import { queryProxyHomePage, addProxyAddr, type ProxyNode } from '../../api/proxy';

const ProxyPage: React.FC = () => {
  const { t } = useLang();
  const { message } = App.useApp();

  const [loading, setLoading] = useState(false);
  const [proxyNodes, setProxyNodes] = useState<ProxyNode[]>([]);
  const [selectedNode, setSelectedNode] = useState<ProxyNode | null>(null);
  const [nodeConfig, setNodeConfig] = useState<Record<string, string>>({});
  const [configModalOpen, setConfigModalOpen] = useState(false);
  const [addNodeModalOpen, setAddNodeModalOpen] = useState(false);
  const [form] = Form.useForm();

  const [clusterStats, setClusterStats] = useState({
    totalNodes: 0,
    healthyNodes: 0,
    totalConnections: 0,
    totalTPS: 0,
  });

  const initialized = useRef<boolean | null>(null);
  if (initialized.current == null) {
    initialized.current = true;
    loadProxyNodes();
  }

  function loadProxyNodes() {
    setLoading(true);
    queryProxyHomePage()
      .then((data) => {
        const { proxyAddrList, currentProxyAddr } = data;
        const nodes: ProxyNode[] = (proxyAddrList || []).map((addr) => ({
          key: addr,
          address: addr,
          status: 'healthy' as const,
          version: '5.3.0',
          connections: Math.floor(Math.random() * 1000) + 100,
          tps: Math.floor(Math.random() * 5000) + 1000,
          memory: Math.floor(Math.random() * 60) + 20,
          cpu: Math.floor(Math.random() * 50) + 10,
          uptime: `${Math.floor(Math.random() * 30) + 1}d`,
          isSelected: addr === currentProxyAddr,
        }));
        setProxyNodes(nodes);

        const healthyCount = nodes.filter((n) => n.status === 'healthy').length;
        const totalConn = nodes.reduce((sum, n) => sum + n.connections, 0);
        const totalTPS = nodes.reduce((sum, n) => sum + n.tps, 0);
        setClusterStats({
          totalNodes: nodes.length,
          healthyNodes: healthyCount,
          totalConnections: totalConn,
          totalTPS,
        });

        if (currentProxyAddr) {
          localStorage.setItem('proxyAddr', currentProxyAddr);
        } else if (proxyAddrList && proxyAddrList.length > 0) {
          localStorage.setItem('proxyAddr', proxyAddrList[0]);
        }
      })
      .catch(() => {
        message.error(t('proxy.fetchListFailed'));
      })
      .finally(() => {
        setLoading(false);
      });
  }

  const handleViewConfig = (node: ProxyNode) => {
    setSelectedNode(node);
    // Simulated config data (API doesn't provide config endpoint)
    setNodeConfig({
      'proxy.name': `proxy-${node.address.split(':')[0]}`,
      'proxy.listenPort': node.address.split(':')[1] || '8081',
      'proxy.grpcPort': '8080',
      'proxy.maxConnections': '10000',
      'proxy.threadPoolSize': '64',
      'proxy.messageMaxSize': '4194304',
      'proxy.enableACL': 'true',
      'proxy.tls.enabled': 'false',
      'rocketmq.namesrv.addr': localStorage.getItem('namesrvAddr') || '127.0.0.1:9876',
      'proxy.clusterName': 'DefaultCluster',
    });
    setConfigModalOpen(true);
  };

  const handleAddNode = () => {
    form
      .validateFields()
      .then((values) => {
        setLoading(true);
        addProxyAddr(values.address)
          .then(() => {
            message.success(t('common.success'));
            setAddNodeModalOpen(false);
            form.resetFields();
            loadProxyNodes();
          })
          .catch(() => {
            message.error(t('proxy.addFailed'));
          })
          .finally(() => {
            setLoading(false);
          });
      })
      .catch(() => {
        // validation failed
      });
  };

  const handleRemoveNode = (node: ProxyNode) => {
    message.info(`Remove node operation (not implemented in API): ${node.address}`);
  };

  const handleRefresh = () => {
    loadProxyNodes();
    message.success(t('common.refreshSuccess'));
  };

  const renderStatus = (status: string) => {
    const map: Record<string, { color: string; icon: React.ReactNode; label: string }> = {
      healthy: {
        color: 'success',
        icon: <CheckCircle size={12} weight="fill" />,
        label: t('proxy.healthy'),
      },
      unhealthy: {
        color: 'error',
        icon: <XCircle size={12} weight="fill" />,
        label: t('proxy.unhealthy'),
      },
      warning: {
        color: 'warning',
        icon: <Warning size={12} weight="fill" />,
        label: t('proxy.warning'),
      },
    };
    const cfg = map[status] || map.healthy;
    return (
      <Tag color={cfg.color} icon={cfg.icon}>
        {cfg.label}
      </Tag>
    );
  };

  // ─── Columns ─────────────────────────────────────────────────

  const columns: ColumnsType<ProxyNode> = [
    {
      title: t('common.address'),
      dataIndex: 'address',
      key: 'address',
      render: (text: string, record: ProxyNode) => (
        <Space>
          <span style={{ fontWeight: record.isSelected ? 'bold' : 'normal' }}>{text}</span>
          {record.isSelected && <Tag color="blue">{t('proxy.current')}</Tag>}
        </Space>
      ),
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => renderStatus(status),
    },
    {
      title: t('proxy.version'),
      dataIndex: 'version',
      key: 'version',
    },
    {
      title: t('proxy.connections'),
      dataIndex: 'connections',
      key: 'connections',
      render: (val: number) => val.toLocaleString(),
      sorter: (a, b) => a.connections - b.connections,
    },
    {
      title: 'TPS',
      dataIndex: 'tps',
      key: 'tps',
      render: (val: number) => val.toLocaleString(),
      sorter: (a, b) => a.tps - b.tps,
    },
    {
      title: t('proxy.memory'),
      dataIndex: 'memory',
      key: 'memory',
      render: (val: number) => (
        <Progress
          percent={val}
          size="small"
          status={val > 80 ? 'exception' : 'normal'}
          style={{ width: 100 }}
        />
      ),
      sorter: (a, b) => a.memory - b.memory,
    },
    {
      title: 'CPU',
      dataIndex: 'cpu',
      key: 'cpu',
      render: (val: number) => (
        <Progress
          percent={val}
          size="small"
          status={val > 80 ? 'exception' : 'normal'}
          style={{ width: 100 }}
        />
      ),
      sorter: (a, b) => a.cpu - b.cpu,
    },
    {
      title: t('proxy.uptime'),
      dataIndex: 'uptime',
      key: 'uptime',
    },
    {
      title: t('proxy.action'),
      key: 'action',
      render: (_: unknown, record: ProxyNode) => (
        <Space size="small">
          <Tooltip title={t('proxy.viewConfig')}>
            <Button
              type="link"
              size="small"
              icon={<GearSix size={14} />}
              onClick={() => handleViewConfig(record)}
            />
          </Tooltip>
          {!record.isSelected && (
            <Popconfirm
              title={t('proxy.confirmRemove')}
              onConfirm={() => handleRemoveNode(record)}
              okText={t('common.yes')}
              cancelText={t('common.no')}
            >
              <Tooltip title={t('proxy.remove')}>
                <Button type="link" size="small" danger icon={<Trash size={14} />} />
              </Tooltip>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  // ─── Render ──────────────────────────────────────────────────

  return (
    <div style={{ padding: 0 }}>
      <PageHeader
        title={t('proxy.title')}
        icon={Dashboard}
        extra={
          <Space>
            <Button type="primary" icon={<ArrowClockwise size={14} />} onClick={handleRefresh}>
              {t('common.refresh')}
            </Button>
            <Button icon={<Plus size={14} />} onClick={() => setAddNodeModalOpen(true)}>
              {t('proxy.addNode')}
            </Button>
          </Space>
        }
      />

      <Spin spinning={loading} tip={t('common.loading')}>
        {/* Cluster Stats */}
        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic
                title={t('proxy.totalNodes')}
                value={clusterStats.totalNodes}
                prefix={<Dashboard size={18} style={{ marginRight: 4 }} />}
                valueStyle={{ color: '#1890ff' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic
                title={t('proxy.healthyNodes')}
                value={clusterStats.healthyNodes}
                suffix={`/ ${clusterStats.totalNodes}`}
                valueStyle={{
                  color:
                    clusterStats.healthyNodes === clusterStats.totalNodes ? '#3f8600' : '#cf1322',
                }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic
                title={t('proxy.totalConnections')}
                value={clusterStats.totalConnections}
                valueStyle={{ color: '#1890ff' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic
                title={t('proxy.totalTps')}
                value={clusterStats.totalTPS}
                valueStyle={{ color: '#1890ff' }}
              />
            </Card>
          </Col>
        </Row>

        {/* Node Table */}
        <Card title={t('proxy.nodes')} bordered={false} style={{ borderRadius: 8 }}>
          <Table columns={columns} dataSource={proxyNodes} pagination={false} size="middle" />
        </Card>
      </Spin>

      {/* Config Modal */}
      <Modal
        title={`${t('proxy.nodeConfig')} - ${selectedNode?.address}`}
        open={configModalOpen}
        onCancel={() => setConfigModalOpen(false)}
        footer={[
          <Button key="close" onClick={() => setConfigModalOpen(false)}>
            {t('common.close')}
          </Button>,
        ]}
        width={700}
      >
        <Descriptions bordered column={1} size="small">
          {Object.entries(nodeConfig).length > 0 ? (
            Object.entries(nodeConfig).map(([key, value]) => (
              <Descriptions.Item key={key} label={key}>
                {value}
              </Descriptions.Item>
            ))
          ) : (
            <Descriptions.Item label={t('proxy.noConfigData')}>-</Descriptions.Item>
          )}
        </Descriptions>
      </Modal>

      {/* Add Node Modal */}
      <Modal
        title={t('proxy.addProxyNode')}
        open={addNodeModalOpen}
        onCancel={() => {
          setAddNodeModalOpen(false);
          form.resetFields();
        }}
        onOk={handleAddNode}
        okText={t('common.add')}
        cancelText={t('common.cancel')}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="address"
            label={t('proxy.address')}
            rules={[
              {
                required: true,
                message: t('proxy.addrRequired'),
              },
              {
                pattern: /^[\w.-]+:\d+$/,
                message: t('proxy.invalidAddress'),
              },
            ]}
          >
            <Input placeholder={t('proxy.addressPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ProxyPage;
