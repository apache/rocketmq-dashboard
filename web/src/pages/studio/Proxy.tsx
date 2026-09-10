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

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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
  Typography,
  Input,
  Popconfirm,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowClockwise,
  GearSix,
  Gauge,
  CheckCircle,
  XCircle,
  Warning,
  Plus,
  Trash,
  MagnifyingGlass,
  ListBullets,
} from '@phosphor-icons/react';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import {
  addProxyAddress,
  addProxyAddresses,
  getProxyTopology,
  removeProxyAddress,
  queryProxyHomePage,
  reloadProxyConfig,
  type ProxyAddressBatchItem,
  type ProxyAddressBatchStatus,
  type ProxyAddressBatchResult,
  type ProxyHomePageData,
  type ProxyNode,
} from '../../api/proxy';
import { readLocalStorage, writeLocalStorage } from '../../utils/browserStorage';
import { tableScrollX } from '../../utils/table';

const { Text } = Typography;

const persistProxyAddress = (address?: string) => {
  if (!address) return;
  writeLocalStorage('proxyAddr', address);
};

const parseBatchProxyAddresses = (value: string): string[] =>
  value
    .split(/[\s,;]+/)
    .map((item) => item.trim())
    .filter(Boolean);

const ProxyPage: React.FC = () => {
  const { t } = useLang();
  const { message } = App.useApp();

  const [loading, setLoading] = useState(false);
  const [proxyNodes, setProxyNodes] = useState<ProxyNode[]>([]);
  const [selectedNode, setSelectedNode] = useState<ProxyNode | null>(null);
  const [configModalOpen, setConfigModalOpen] = useState(false);
  const [newProxyAddress, setNewProxyAddress] = useState('');
  const [batchModalOpen, setBatchModalOpen] = useState(false);
  const [batchProxyInput, setBatchProxyInput] = useState('');
  const [batchResult, setBatchResult] = useState<ProxyAddressBatchResult | null>(null);
  const [batchSubmitting, setBatchSubmitting] = useState(false);
  const [nodeFilter, setNodeFilter] = useState('');
  const [addressMutationLoading, setAddressMutationLoading] = useState(false);
  const addressMutationInFlight = useRef(false);
  const [removingProxyAddress, setRemovingProxyAddress] = useState<string | null>(null);
  const [clusterId, setClusterId] = useState<string>(
    readLocalStorage('clusterId') || 'DefaultCluster',
  );
  const loadRequestId = useRef(0);

  const [clusterStats, setClusterStats] = useState({
    totalNodes: 0,
    healthyNodes: null as number | null,
    totalConnections: null as number | null,
    totalTPS: null as number | null,
  });

  const applyProxyHome = useCallback(
    async ({ proxyAddrList, currentProxyAddr }: ProxyHomePageData, requestId: number) => {
      const baseNodes: ProxyNode[] = (proxyAddrList || []).map((addr) => ({
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
      let nodes = baseNodes;

      // Overlay the live TCP health view (UP/PARTIAL/DOWN) when the backend exposes it.
      try {
        const topology = await getProxyTopology();
        if (requestId !== loadRequestId.current) return false;
        const statusByAddr = new Map(topology.map((node) => [node.proxyAddr, node.status]));
        nodes = baseNodes.map((node) => {
          const probeStatus = statusByAddr.get(node.address);
          const status: ProxyNode['status'] =
            probeStatus === 'UP'
              ? 'healthy'
              : probeStatus === 'PARTIAL'
                ? 'warning'
                : probeStatus === 'DOWN'
                  ? 'unhealthy'
                  : node.status;
          return { ...node, status };
        });
      } catch {
        // Health probing is best-effort; keep the unknown status when it is unavailable.
      }
      setProxyNodes(nodes);

      setClusterStats({
        totalNodes: nodes.length,
        healthyNodes: null,
        totalConnections: null,
        totalTPS: null,
      });

      persistProxyAddress(currentProxyAddr || proxyAddrList?.[0]);
      return true;
    },
    [],
  );

  const loadProxyNodes = useCallback(async () => {
    const requestId = ++loadRequestId.current;
    setLoading(true);
    try {
      const home = await queryProxyHomePage();
      if (requestId !== loadRequestId.current) return false;
      return await applyProxyHome(home, requestId);
    } catch {
      if (requestId !== loadRequestId.current) return false;
      message.error(t('proxy.fetchListFailed'));
      return false;
    } finally {
      if (requestId === loadRequestId.current) {
        setLoading(false);
      }
    }
  }, [applyProxyHome, message, t]);

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
    if (addressMutationInFlight.current) return;
    if (await loadProxyNodes()) {
      message.success(t('common.refreshSuccess'));
    }
  };

  const handleClusterIdChange = (value: string) => {
    setClusterId(value);
    if (value) {
      writeLocalStorage('clusterId', value);
    }
  };

  const handleAddProxyAddress = async () => {
    if (addressMutationInFlight.current) return;
    const addr = newProxyAddress.trim();
    if (!addr) {
      message.warning(t('proxy.addressRequired'));
      return;
    }
    addressMutationInFlight.current = true;
    const requestId = ++loadRequestId.current;
    setAddressMutationLoading(true);
    setLoading(true);
    try {
      const home = await addProxyAddress(addr);
      if (requestId !== loadRequestId.current) return;
      await applyProxyHome(home, requestId);
      setNewProxyAddress('');
      message.success(t('proxy.addAddressSuccess'));
    } catch {
      if (requestId === loadRequestId.current) {
        message.error(t('proxy.addAddressFailed'));
      }
    } finally {
      addressMutationInFlight.current = false;
      if (requestId === loadRequestId.current) {
        setAddressMutationLoading(false);
        setLoading(false);
      }
    }
  };

  const handleBatchAddProxyAddresses = async () => {
    if (addressMutationInFlight.current) return;
    const addrs = parseBatchProxyAddresses(batchProxyInput);
    if (addrs.length === 0) {
      message.warning(t('proxy.batchAddressRequired'));
      return;
    }
    if (addrs.length > 50) {
      message.warning(t('proxy.batchTooManyAddresses', { count: 50 }));
      return;
    }

    addressMutationInFlight.current = true;
    const requestId = ++loadRequestId.current;
    setBatchSubmitting(true);
    setLoading(true);
    try {
      const result = await addProxyAddresses(addrs);
      if (requestId !== loadRequestId.current) return;
      setBatchResult(result);
      await applyProxyHome(result.home, requestId);
      if (result.added > 0) {
        message.success(t('proxy.batchAddSuccess', { count: result.added }));
      } else {
        message.warning(t('proxy.batchAddNoNewAddress'));
      }
    } catch {
      if (requestId === loadRequestId.current) {
        message.error(t('proxy.batchAddFailed'));
      }
    } finally {
      addressMutationInFlight.current = false;
      if (requestId === loadRequestId.current) {
        setBatchSubmitting(false);
        setLoading(false);
      }
    }
  };

  const closeBatchModal = () => {
    if (batchSubmitting) return;
    setBatchModalOpen(false);
  };

  const handleRemoveProxyAddress = async (addr: string) => {
    if (addressMutationInFlight.current) return;
    addressMutationInFlight.current = true;
    const requestId = ++loadRequestId.current;
    setRemovingProxyAddress(addr);
    setLoading(true);
    try {
      const home = await removeProxyAddress(addr);
      if (requestId !== loadRequestId.current) return;
      await applyProxyHome(home, requestId);
      if (selectedNode?.address === addr) {
        setSelectedNode(null);
        setConfigModalOpen(false);
      }
      message.success(t('proxy.removeAddressSuccess'));
    } catch {
      if (requestId === loadRequestId.current) {
        message.error(t('proxy.removeAddressFailed'));
      }
    } finally {
      addressMutationInFlight.current = false;
      if (requestId === loadRequestId.current) {
        setRemovingProxyAddress(null);
        setLoading(false);
      }
    }
  };

  const handleReloadConfig = async (node: ProxyNode) => {
    try {
      const result = await reloadProxyConfig(clusterId, node.address);
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

  const proxyStatusLabel = useCallback(
    (status: string) => {
      const map: Record<string, string> = {
        healthy: t('proxy.healthy'),
        unhealthy: t('proxy.unhealthy'),
        warning: t('proxy.warning'),
        error: t('proxy.statusError'),
        offline: t('proxy.statusOffline'),
        unknown: t('common.na'),
      };
      return map[status] || map.unknown;
    },
    [t],
  );

  const filteredProxyNodes = useMemo(() => {
    const keyword = nodeFilter.trim().toLowerCase();
    if (!keyword) return proxyNodes;
    return proxyNodes.filter((node) =>
      [
        node.address,
        node.status,
        proxyStatusLabel(node.status),
        node.version,
        node.uptime,
        node.isSelected ? t('proxy.current') : '',
      ]
        .filter((value): value is string => Boolean(value))
        .some((value) => value.toLowerCase().includes(keyword)),
    );
  }, [nodeFilter, proxyNodes, proxyStatusLabel, t]);

  const addressControlsDisabled =
    addressMutationLoading || batchSubmitting || removingProxyAddress !== null;

  const renderUnavailable = () => <Text type="secondary">{t('common.na')}</Text>;

  const renderNumberMetric = (value: number | null) =>
    value == null ? renderUnavailable() : value.toLocaleString();

  const compareNullable = (left: number | null, right: number | null) =>
    (left ?? Number.NEGATIVE_INFINITY) - (right ?? Number.NEGATIVE_INFINITY);

  const batchStatusLabel = (status: ProxyAddressBatchStatus) => {
    const labels: Record<ProxyAddressBatchStatus, string> = {
      ADDED: t('proxy.batchStatusAdded'),
      EXISTING: t('proxy.batchStatusExisting'),
      DUPLICATE: t('proxy.batchStatusDuplicate'),
      INVALID: t('proxy.batchStatusInvalid'),
    };
    return labels[status];
  };

  const batchStatusColor = (status: ProxyAddressBatchStatus) => {
    const colors: Record<ProxyAddressBatchStatus, string> = {
      ADDED: 'green',
      EXISTING: 'blue',
      DUPLICATE: 'gold',
      INVALID: 'red',
    };
    return colors[status];
  };

  const batchRowMessage = (record: ProxyAddressBatchItem) => {
    const messages: Partial<Record<ProxyAddressBatchStatus, string>> = {
      ADDED: t('proxy.batchMessageAdded'),
      EXISTING: t('proxy.batchMessageExisting'),
      DUPLICATE: t('proxy.batchMessageDuplicate'),
    };
    return messages[record.status] ?? record.message;
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
          <Popconfirm
            title={t('proxy.removeAddressConfirm', { addr: record.address })}
            okText={t('common.confirm')}
            cancelText={t('common.cancel')}
            disabled={removingProxyAddress === record.address}
            onConfirm={() => void handleRemoveProxyAddress(record.address)}
          >
            <Button
              type="link"
              size="small"
              danger
              icon={<Trash size={14} />}
              aria-label={t('common.delete')}
              loading={removingProxyAddress === record.address}
            />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const batchResultColumns: ColumnsType<ProxyAddressBatchItem> = [
    {
      title: t('proxy.batchRow'),
      dataIndex: 'rowNumber',
      key: 'rowNumber',
      width: 80,
    },
    {
      title: t('proxy.batchInput'),
      dataIndex: 'input',
      key: 'input',
      width: 180,
      ellipsis: true,
    },
    {
      title: t('proxy.batchNormalizedAddress'),
      dataIndex: 'addr',
      key: 'addr',
      width: 190,
      render: (value: string | null) => value || renderUnavailable(),
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: ProxyAddressBatchStatus) => (
        <Tag color={batchStatusColor(status)}>{batchStatusLabel(status)}</Tag>
      ),
      filters: [
        { text: t('proxy.batchStatusAdded'), value: 'ADDED' },
        { text: t('proxy.batchStatusExisting'), value: 'EXISTING' },
        { text: t('proxy.batchStatusDuplicate'), value: 'DUPLICATE' },
        { text: t('proxy.batchStatusInvalid'), value: 'INVALID' },
      ],
      onFilter: (value, record) => record.status === value,
    },
    {
      title: t('common.message'),
      dataIndex: 'message',
      key: 'message',
      width: 240,
      ellipsis: true,
      render: (_: string, record) => batchRowMessage(record),
    },
  ];

  const parsedBatchProxyAddresses = parseBatchProxyAddresses(batchProxyInput);

  // ─── Render ──────────────────────────────────────────────────

  return (
    <div style={{ padding: 0 }}>
      <PageHeader
        title={t('proxy.title')}

        extra={
          <Space>
            <Input
              placeholder={t('proxy.addressPlaceholder')}
              value={newProxyAddress}
              onChange={(e) => setNewProxyAddress(e.target.value)}
              onPressEnter={() => void handleAddProxyAddress()}
              style={{ width: 220 }}
              aria-label={t('proxy.address')}
              disabled={addressControlsDisabled}
            />
            <Button
              icon={<Plus size={14} />}
              onClick={() => void handleAddProxyAddress()}
              loading={addressMutationLoading}
              disabled={addressControlsDisabled && !addressMutationLoading}
            >
              {t('common.add')}
            </Button>
            <Button
              icon={<ListBullets size={14} />}
              onClick={() => {
                setBatchResult(null);
                setBatchModalOpen(true);
              }}
              disabled={addressControlsDisabled}
            >
              {t('proxy.batchAdd')}
            </Button>
            <Input
              placeholder={t('proxy.clusterIdPlaceholder')}
              value={clusterId}
              onChange={(e) => handleClusterIdChange(e.target.value)}
              style={{ width: 200 }}
              aria-label={t('proxy.clusterId')}
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
          variant="borderless"
          style={{ borderRadius: 8, marginBottom: 24 }}
          extra={
            <Input
              allowClear
              aria-label={t('proxy.nodeFilter')}
              placeholder={t('proxy.nodeFilterPlaceholder')}
              prefix={<MagnifyingGlass size={14} />}
              value={nodeFilter}
              onChange={(event) => setNodeFilter(event.target.value)}
              style={{ width: 260 }}
            />
          }
        >
          <Table
            columns={columns}
            dataSource={filteredProxyNodes}
            pagination={false}
            size="middle"
            scroll={{ x: tableScrollX(columns) }}
          />
        </Card>
      </Spin>

      <Modal
        title={t('proxy.batchAdd')}
        open={batchModalOpen}
        onCancel={closeBatchModal}
        width={820}
        destroyOnHidden
        footer={[
          <Button key="cancel" onClick={closeBatchModal} disabled={batchSubmitting}>
            {t('common.close')}
          </Button>,
          <Button
            key="submit"
            type="primary"
            loading={batchSubmitting}
            onClick={() => void handleBatchAddProxyAddresses()}
          >
            {t('proxy.batchAddSubmit')}
          </Button>,
        ]}
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Input.TextArea
            aria-label={t('proxy.batchAddresses')}
            placeholder={t('proxy.batchAddressPlaceholder')}
            value={batchProxyInput}
            onChange={(event) => {
              setBatchProxyInput(event.target.value);
              setBatchResult(null);
            }}
            autoSize={{ minRows: 6, maxRows: 10 }}
            disabled={batchSubmitting}
          />
          <Text type="secondary">
            {t('proxy.batchParsedCount', { count: parsedBatchProxyAddresses.length })}
          </Text>

          {batchResult && (
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <Row gutter={[12, 12]}>
                <Col xs={12} md={6}>
                  <Statistic title={t('proxy.batchTotal')} value={batchResult.total} />
                </Col>
                <Col xs={12} md={6}>
                  <Statistic title={t('proxy.batchAdded')} value={batchResult.added} />
                </Col>
                <Col xs={12} md={6}>
                  <Statistic
                    title={t('proxy.batchSkipped')}
                    value={batchResult.existing + batchResult.duplicate}
                  />
                </Col>
                <Col xs={12} md={6}>
                  <Statistic title={t('proxy.batchInvalid')} value={batchResult.invalid} />
                </Col>
              </Row>
              <Table
                rowKey={(record) => `${record.rowNumber}-${record.status}-${record.input}`}
                columns={batchResultColumns}
                dataSource={batchResult.items}
                pagination={false}
                size="small"
                scroll={{ x: tableScrollX(batchResultColumns), y: 260 }}
              />
            </Space>
          )}
        </Space>
      </Modal>

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
