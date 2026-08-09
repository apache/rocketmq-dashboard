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

import { useCallback, useEffect, useState } from 'react';
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
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowClockwise,
  Plus,
  Trash,
  GearSix,
  Gauge,
  CheckCircle,
  XCircle,
  Warning,
} from '@phosphor-icons/react';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import { queryProxyHomePage, addProxyAddr, removeProxyAddr, type ProxyNode } from '../../api/proxy';

const { Text } = Typography;

const ProxyPage: React.FC = () => {
  const { t } = useLang();
  const { message } = App.useApp();

  const [loading, setLoading] = useState(false);
  const [proxyNodes, setProxyNodes] = useState<ProxyNode[]>([]);
  const [selectedNode, setSelectedNode] = useState<ProxyNode | null>(null);
  const [configModalOpen, setConfigModalOpen] = useState(false);
  const [addNodeModalOpen, setAddNodeModalOpen] = useState(false);
  const [form] = Form.useForm();

  const [clusterStats, setClusterStats] = useState({
    totalNodes: 0,
    healthyNodes: null as number | null,
    totalConnections: null as number | null,
    totalTPS: null as number | null,
  });

  const loadProxyNodes = useCallback(async () => {
    setLoading(true);
    try {
      const { proxyAddrList, currentProxyAddr } = await queryProxyHomePage();
      const nodes: ProxyNode[] = (proxyAddrList || []).map((addr) => ({
        key: addr,
        address: addr,
        status: 'unknown' as const,
        version: null,
        connections: null,
        tps: null,
        memory: null,
        cpu: null,
        uptime: null,
        isSelected: addr === currentProxyAddr,
      }));
      setProxyNodes(nodes);

      setClusterStats({
        totalNodes: nodes.length,
        healthyNodes: null,
        totalConnections: null,
        totalTPS: null,
      });

      if (currentProxyAddr) {
        localStorage.setItem('proxyAddr', currentProxyAddr);
      } else if (proxyAddrList && proxyAddrList.length > 0) {
        localStorage.setItem('proxyAddr', proxyAddrList[0]);
      }
      return true;
    } catch {
      message.error(t('proxy.fetchListFailed'));
      return false;
    } finally {
      setLoading(false);
    }
  }, [message, t]);

  useEffect(() => {
    // The state updates are performed by the asynchronous Proxy API request, not by this effect itself.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadProxyNodes();
  }, [loadProxyNodes]);

  const handleViewConfig = (node: ProxyNode) => {
    setSelectedNode(node);
    setConfigModalOpen(true);
  };

  const handleAddNode = async () => {
    let values: { address: string };
    try {
      values = await form.validateFields();
    } catch {
      return;
    }

    setLoading(true);
    try {
      await addProxyAddr(values.address);
      message.success(t('common.success'));
      setAddNodeModalOpen(false);
      form.resetFields();
      await loadProxyNodes();
    } catch {
      message.error(t('proxy.addFailed'));
    } finally {
      setLoading(false);
    }
  };

  const handleRemoveNode = async (node: ProxyNode) => {
    setLoading(true);
    try {
      await removeProxyAddr(node.address);
      message.success(t('common.success'));
      await loadProxyNodes();
    } catch {
      message.error(t('proxy.removeFailed'));
    } finally {
      setLoading(false);
    }
  };

  const handleRefresh = async () => {
    if (await loadProxyNodes()) {
      message.success(t('common.refreshSuccess'));
    }
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
      unknown: {
        color: 'default',
        icon: null,
        label: t('common.na'),
      },
    };
    const cfg = map[status] || map.unknown;
    return (
      <Tag color={cfg.color} icon={cfg.icon}>
        {cfg.label}
      </Tag>
    );
  };

  const renderUnavailable = () => <Text type="secondary">{t('common.na')}</Text>;

  const renderNumberMetric = (value: number | null) =>
    value == null ? renderUnavailable() : value.toLocaleString();

  const compareNullable = (left: number | null, right: number | null) =>
    (left ?? Number.NEGATIVE_INFINITY) - (right ?? Number.NEGATIVE_INFINITY);

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
      render: (value: string | null) => value || renderUnavailable(),
    },
    {
      title: t('proxy.connections'),
      dataIndex: 'connections',
      key: 'connections',
      render: renderNumberMetric,
      sorter: (a, b) => compareNullable(a.connections, b.connections),
    },
    {
      title: 'TPS',
      dataIndex: 'tps',
      key: 'tps',
      render: renderNumberMetric,
      sorter: (a, b) => compareNullable(a.tps, b.tps),
    },
    {
      title: t('proxy.memory'),
      dataIndex: 'memory',
      key: 'memory',
      render: (val: number | null) =>
        val == null ? (
          renderUnavailable()
        ) : (
          <Progress
            percent={val}
            size="small"
            status={val > 80 ? 'exception' : 'normal'}
            style={{ width: 100 }}
          />
        ),
      sorter: (a, b) => compareNullable(a.memory, b.memory),
    },
    {
      title: 'CPU',
      dataIndex: 'cpu',
      key: 'cpu',
      render: (val: number | null) =>
        val == null ? (
          renderUnavailable()
        ) : (
          <Progress
            percent={val}
            size="small"
            status={val > 80 ? 'exception' : 'normal'}
            style={{ width: 100 }}
          />
        ),
      sorter: (a, b) => compareNullable(a.cpu, b.cpu),
    },
    {
      title: t('proxy.uptime'),
      dataIndex: 'uptime',
      key: 'uptime',
      render: (value: string | null) => value || renderUnavailable(),
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
              aria-label={t('proxy.viewConfig')}
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
                prefix={<Gauge size={18} style={{ marginRight: 4 }} />}
                valueStyle={{ color: '#1890ff' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic
                title={t('proxy.healthyNodes')}
                value={clusterStats.healthyNodes ?? t('common.na')}
                suffix={
                  clusterStats.healthyNodes == null ? undefined : `/ ${clusterStats.totalNodes}`
                }
                valueStyle={{ color: clusterStats.healthyNodes == null ? undefined : '#3f8600' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic
                title={t('proxy.totalConnections')}
                value={clusterStats.totalConnections ?? t('common.na')}
                valueStyle={{ color: '#1890ff' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic
                title={t('proxy.totalTps')}
                value={clusterStats.totalTPS ?? t('common.na')}
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
          <Descriptions.Item label={t('proxy.configUnavailable')}>
            {t('proxy.configUnavailableHint')}
          </Descriptions.Item>
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
