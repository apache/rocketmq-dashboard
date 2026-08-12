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
  Card,
  Table,
  Tag,
  Button,
  Space,
  Modal,
  Spin,
  Row,
  Col,
  Statistic,
  Progress,
  Descriptions,
  Tooltip,
  App,
  Select,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowClockwise,
  GearSix,
  Gauge,
  CheckCircle,
  XCircle,
  Warning,
} from '@phosphor-icons/react';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import { queryProxyHomePage, reloadProxyConfig, type ProxyNode } from '../../api/proxy';
import type { Instance } from '../../api/instance';
import { listInstances } from '../../services/instanceService';

const { Text } = Typography;

const ProxyPage: React.FC = () => {
  const { t } = useLang();
  const { message } = App.useApp();

  const [loading, setLoading] = useState(false);
  const [proxyNodes, setProxyNodes] = useState<ProxyNode[]>([]);
  const [selectedNode, setSelectedNode] = useState<ProxyNode | null>(null);
  const [configModalOpen, setConfigModalOpen] = useState(false);
  const [instances, setInstances] = useState<Instance[]>([]);
  const [selectedInstanceId, setSelectedInstanceId] = useState('');
  const loadRequestId = useRef(0);

  const [clusterStats, setClusterStats] = useState({
    totalNodes: 0,
    healthyNodes: null as number | null,
    totalConnections: null as number | null,
    totalTPS: null as number | null,
  });

  useEffect(() => {
    let cancelled = false;
    void listInstances({ type: 'PROXY' })
      .then((nextInstances) => {
        if (cancelled) return;
        setInstances(nextInstances);
        setSelectedInstanceId((current) =>
          nextInstances.some((instance) => instance.id === current)
            ? current
            : (nextInstances[0]?.id ?? ''),
        );
      })
      .catch(() => {
        if (!cancelled) {
          message.error(t('proxy.fetchListFailed'));
        }
      });
    return () => {
      cancelled = true;
    };
  }, [message, t]);

  const loadProxyNodes = useCallback(async () => {
    if (!selectedInstanceId) {
      setProxyNodes([]);
      setClusterStats({
        totalNodes: 0,
        healthyNodes: null,
        totalConnections: null,
        totalTPS: null,
      });
      return false;
    }
    const requestId = ++loadRequestId.current;
    setLoading(true);
    try {
      const { proxyAddrList, currentProxyAddr } = await queryProxyHomePage(selectedInstanceId);
      if (requestId !== loadRequestId.current) return false;
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

      return true;
    } catch {
      if (requestId !== loadRequestId.current) return false;
      message.error(t('proxy.fetchListFailed'));
      return false;
    } finally {
      if (requestId === loadRequestId.current) {
        setLoading(false);
      }
    }
  }, [message, selectedInstanceId, t]);

  useEffect(() => {
    const requestId = loadRequestId.current;
    // The state updates are performed by the asynchronous Proxy API request, not by this effect itself.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadProxyNodes();
    return () => {
      loadRequestId.current = requestId + 1;
    };
  }, [loadProxyNodes]);

  const handleViewConfig = (node: ProxyNode) => {
    setSelectedNode(node);
    setConfigModalOpen(true);
  };

  const handleRefresh = async () => {
    if (await loadProxyNodes()) {
      message.success(t('common.refreshSuccess'));
    }
  };

  const handleReloadConfig = async (node: ProxyNode) => {
    try {
      const result = await reloadProxyConfig(node.address);
      if (result.success) {
        message.success(t('proxy.reloadSuccess'));
      } else {
        message.warning(t('proxy.reloadFailed'));
      }
    } catch {
      message.error(t('proxy.reloadFailed'));
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
      error: {
        color: 'error',
        icon: <XCircle size={12} weight="fill" />,
        label: t('proxy.statusError'),
      },
      offline: {
        color: 'default',
        icon: null,
        label: t('proxy.statusOffline'),
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
          <Tooltip title={t('proxy.reloadConfig')}>
            <Button
              type="link"
              size="small"
              icon={<ArrowClockwise size={14} />}
              aria-label={t('proxy.reloadConfig')}
              onClick={() => handleReloadConfig(record)}
            />
          </Tooltip>
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
            <Select
              aria-label="Proxy instance"
              placeholder="选择 Proxy 实例"
              value={selectedInstanceId || undefined}
              onChange={setSelectedInstanceId}
              options={instances.map((instance) => ({ value: instance.id, label: instance.name }))}
              style={{ width: 220 }}
              notFoundContent="暂无 Proxy 实例"
            />
            <Button type="primary" icon={<ArrowClockwise size={14} />} onClick={handleRefresh}>
              {t('common.refresh')}
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
        <Card
          title={t('proxy.nodes')}
          bordered={false}
          style={{ borderRadius: 8, marginBottom: 24 }}
        >
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
    </div>
  );
};

export default ProxyPage;
