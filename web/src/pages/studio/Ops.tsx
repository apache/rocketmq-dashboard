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

import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Flex,
  Input,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { FloppyDisk, Plus, Trash } from '@phosphor-icons/react';
import { useLang } from '../../i18n/LangContext';
import useAuthStore from '../../stores/authStore';
import {
  addNameSvrAddr,
  deleteNameSvrAddr,
  queryOpsHomePage,
  updateIsVIPChannel,
  updateNameSvrAddr,
  updateUseTLS,
} from '../../api/ops';
import {
  analyzeOpsNameServerPreflight,
  type NameServerAddressDiagnostic,
  type OpsNameServerPreflight,
  type OpsNameServerReadiness,
  type OpsNameServerSeverity,
} from '../../utils/opsNameServerPreflight';
import { tableScrollX } from '../../utils/table';

const { Text } = Typography;

const readinessAlertType: Record<OpsNameServerReadiness, 'success' | 'warning' | 'error'> = {
  ready: 'success',
  warning: 'warning',
  blocked: 'error',
};

const severityColor: Record<OpsNameServerSeverity, string> = {
  critical: 'red',
  warning: 'orange',
  info: 'blue',
};

const candidateStatusColor: Record<OpsNameServerPreflight['newAddress']['status'], string> = {
  empty: 'default',
  ready: 'green',
  invalid: 'red',
  duplicate: 'orange',
};

const candidateStatusText: Record<OpsNameServerPreflight['newAddress']['status'], string> = {
  empty: '未输入',
  ready: '可新增',
  invalid: '格式错误',
  duplicate: '已存在',
};

const statusText = (address: NameServerAddressDiagnostic) => {
  if (!address.valid) return '格式错误';
  if (address.duplicate) return '重复';
  return '有效';
};

const OpsNameServerPreflightPanel = ({ preflight }: { preflight: OpsNameServerPreflight }) => {
  const columns: ColumnsType<NameServerAddressDiagnostic> = [
    {
      title: 'NameServer 地址',
      dataIndex: 'raw',
      key: 'raw',
      width: 260,
      ellipsis: true,
      render: (value: string) => <Text code>{value || '-'}</Text>,
    },
    {
      title: 'Endpoint',
      dataIndex: 'endpoints',
      key: 'endpoints',
      width: 260,
      render: (endpoints: NameServerAddressDiagnostic['endpoints']) =>
        endpoints.length === 0 ? (
          <Text type="secondary">-</Text>
        ) : (
          <Space size={[0, 4]} wrap>
            {endpoints.map((endpoint) => (
              <Tag
                key={`${endpoint.raw}-${endpoint.normalized}`}
                color={endpoint.valid ? 'blue' : 'red'}
              >
                {endpoint.valid ? endpoint.normalized : endpoint.raw || '-'}
              </Tag>
            ))}
          </Space>
        ),
    },
    {
      title: '角色',
      key: 'role',
      width: 160,
      render: (_: unknown, address) => (
        <Space size={[0, 4]} wrap>
          {address.current && <Tag color="green">当前使用</Tag>}
          {address.selected && <Tag color="purple">已选择</Tag>}
          {!address.current && !address.selected && <Text type="secondary">-</Text>}
        </Space>
      ),
    },
    {
      title: '状态',
      key: 'status',
      width: 120,
      render: (_: unknown, address) => (
        <Tag color={!address.valid ? 'red' : address.duplicate ? 'orange' : 'green'}>
          {statusText(address)}
        </Tag>
      ),
    },
    {
      title: '说明',
      dataIndex: 'issueText',
      key: 'issueText',
      render: (issues: string[]) =>
        issues.length === 0 ? (
          <Text type="secondary">-</Text>
        ) : (
          <Space direction="vertical" size={2}>
            {issues.map((issue) => (
              <Text key={issue} type="secondary" style={{ fontSize: 14 }}>
                {issue}
              </Text>
            ))}
          </Space>
        ),
    },
  ];

  return (
    <Card
      size="small"
      title="NameServer 配置预检"
      style={{ marginBottom: 24 }}
      styles={{ body: { padding: 16 } }}
    >
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Alert
          showIcon
          type={readinessAlertType[preflight.readiness]}
          message={preflight.statusText}
          description={preflight.statusDescription}
        />

        <Descriptions size="small" bordered column={{ xs: 1, sm: 2, md: 4 }}>
          <Descriptions.Item label="地址数">
            {preflight.stats.configuredAddresses}
          </Descriptions.Item>
          <Descriptions.Item label="有效地址">{preflight.stats.validAddresses}</Descriptions.Item>
          <Descriptions.Item label="有效 endpoint">
            {preflight.stats.validEndpointCount}/{preflight.stats.endpointCount}
          </Descriptions.Item>
          <Descriptions.Item label="待新增地址">
            <Tag color={candidateStatusColor[preflight.newAddress.status]}>
              {candidateStatusText[preflight.newAddress.status]}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="重复地址">
            {preflight.stats.duplicateAddresses}
          </Descriptions.Item>
          <Descriptions.Item label="无效地址">{preflight.stats.invalidAddresses}</Descriptions.Item>
          <Descriptions.Item label="当前地址在列表内">
            <Tag color={preflight.currentAddressKnown ? 'green' : 'red'}>
              {preflight.currentAddressKnown ? '是' : '否'}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="选中地址可更新">
            <Tag color={preflight.selectedAddressValid ? 'green' : 'red'}>
              {preflight.selectedAddressValid ? '是' : '否'}
            </Tag>
          </Descriptions.Item>
        </Descriptions>

        {preflight.issues.length > 0 && (
          <Flex gap={8} wrap>
            {preflight.issues.map((issue) => (
              <Tooltip key={issue.id} title={issue.description}>
                <Tag color={severityColor[issue.severity]}>{issue.title}</Tag>
              </Tooltip>
            ))}
          </Flex>
        )}

        {preflight.pendingChanges.length > 0 && (
          <div style={{ border: '1px solid #f0f0f0', borderRadius: 8, padding: 12 }}>
            <Text strong style={{ display: 'block', marginBottom: 8 }}>
              待确认变更
            </Text>
            <Space direction="vertical" size={8} style={{ width: '100%' }}>
              {preflight.pendingChanges.map((change) => (
                <Flex key={change.id} justify="space-between" gap={12} wrap>
                  <Space>
                    <Tag color={severityColor[change.severity]}>{change.title}</Tag>
                    <Text>{change.description}</Text>
                  </Space>
                </Flex>
              ))}
            </Space>
          </div>
        )}

        <Table
          columns={columns}
          dataSource={preflight.addresses}
          rowKey="key"
          pagination={false}
          size="small"
          scroll={{ x: tableScrollX(columns) }}
        />

        <Space direction="vertical" size={4}>
          {preflight.recommendations.map((recommendation) => (
            <Text key={recommendation} type="secondary" style={{ fontSize: 14 }}>
              {recommendation}
            </Text>
          ))}
        </Space>
      </Space>
    </Card>
  );
};

const OpsPage: React.FC = () => {
  const { t } = useLang();
  const { message } = App.useApp();
  const fetchFailedMessage = t('ops.fetchFailed');
  const userId = useAuthStore((state) => state.userId);
  const admin = useAuthStore((state) => state.admin);

  const [namesrvAddrList, setNamesrvAddrList] = useState<string[]>([]);
  const [selectedNamesrv, setSelectedNamesrv] = useState('');
  const [currentNamesrv, setCurrentNamesrv] = useState('');
  const [newNamesrvAddr, setNewNamesrvAddr] = useState('');
  const [useVIPChannel, setUseVIPChannel] = useState(false);
  const [useTLS, setUseTLS] = useState(false);
  const [namesrvUpdating, setNamesrvUpdating] = useState(false);
  const [vipUpdating, setVipUpdating] = useState(false);
  const [tlsUpdating, setTlsUpdating] = useState(false);
  const namesrvMutationInFlight = useRef(false);
  const vipUpdateInFlight = useRef(false);
  const tlsUpdateInFlight = useRef(false);
  const [configurationAvailable, setConfigurationAvailable] = useState(false);
  const [unavailableReason, setUnavailableReason] = useState('');
  const writeOperationEnabled = configurationAvailable && (!userId || admin === true);
  const nameServerPreflight = useMemo(
    () =>
      analyzeOpsNameServerPreflight({
        namesrvAddrList,
        currentNamesrv,
        selectedNamesrv,
        newNamesrvAddr,
        configurationAvailable,
        unavailableReason,
        canWrite: writeOperationEnabled,
        useVIPChannel,
        useTLS,
      }),
    [
      configurationAvailable,
      currentNamesrv,
      namesrvAddrList,
      newNamesrvAddr,
      selectedNamesrv,
      unavailableReason,
      useTLS,
      useVIPChannel,
      writeOperationEnabled,
    ],
  );
  const newAddressBlocked =
    Boolean(newNamesrvAddr.trim()) && nameServerPreflight.newAddress.status !== 'ready';
  const updateNameServerDisabled = namesrvUpdating || !nameServerPreflight.selectedAddressValid;
  const deleteNameServerDisabled =
    !selectedNamesrv ||
    selectedNamesrv === currentNamesrv ||
    namesrvAddrList.length <= 1 ||
    !nameServerPreflight.selectedAddressValid;

  useEffect(() => {
    let cancelled = false;

    const loadOpsData = async () => {
      try {
        const data = await queryOpsHomePage();
        if (!cancelled) {
          setNamesrvAddrList(data.namesvrAddrList);
          setUseVIPChannel(data.useVIPChannel);
          setUseTLS(data.useTLS);
          setSelectedNamesrv(data.currentNamesrv);
          setCurrentNamesrv(data.currentNamesrv);
          setConfigurationAvailable(data.configurationAvailable);
          setUnavailableReason(data.unavailableReason || '');
        }
      } catch {
        if (!cancelled) {
          message.error(fetchFailedMessage);
        }
      }
    };

    void loadOpsData();

    return () => {
      cancelled = true;
    };
  }, [fetchFailedMessage, message]);

  const handleUpdateNameSvrAddr = async () => {
    if (namesrvMutationInFlight.current) return;
    if (!selectedNamesrv) {
      message.warning(t('ops.selectNamesrv'));
      return;
    }
    if (!nameServerPreflight.selectedAddressValid) {
      message.warning('请选择列表中格式有效的 NameServer 地址');
      return;
    }
    namesrvMutationInFlight.current = true;
    setNamesrvUpdating(true);
    try {
      await updateNameSvrAddr(selectedNamesrv);
      setCurrentNamesrv(selectedNamesrv);
      message.success(t('common.success'));
    } catch {
      message.error(t('common.failure'));
    } finally {
      namesrvMutationInFlight.current = false;
      setNamesrvUpdating(false);
    }
  };

  const handleDeleteNameSvrAddr = async () => {
    if (namesrvMutationInFlight.current) return;
    namesrvMutationInFlight.current = true;
    setNamesrvUpdating(true);
    try {
      await deleteNameSvrAddr(selectedNamesrv);
      setNamesrvAddrList((addresses) => addresses.filter((addr) => addr !== selectedNamesrv));
      setSelectedNamesrv(currentNamesrv);
      message.success(t('common.success'));
    } catch {
      message.error(t('common.failure'));
    } finally {
      namesrvMutationInFlight.current = false;
      setNamesrvUpdating(false);
    }
  };

  const handleAddNameSvrAddr = async () => {
    if (namesrvMutationInFlight.current) return;
    const addr = newNamesrvAddr.trim();
    if (!addr) {
      message.warning(t('ops.inputNamesrvAddr'));
      return;
    }
    if (nameServerPreflight.newAddress.status !== 'ready') {
      message.warning(nameServerPreflight.newAddress.message);
      return;
    }
    namesrvMutationInFlight.current = true;
    setNamesrvUpdating(true);
    try {
      await addNameSvrAddr(addr);
      if (!namesrvAddrList.includes(addr)) {
        setNamesrvAddrList([...namesrvAddrList, addr]);
      }
      setNewNamesrvAddr('');
      message.success(t('common.success'));
    } catch {
      message.error(t('common.failure'));
    } finally {
      namesrvMutationInFlight.current = false;
      setNamesrvUpdating(false);
    }
  };

  const handleUpdateIsVIPChannel = async (checked: boolean) => {
    if (vipUpdateInFlight.current) return;
    vipUpdateInFlight.current = true;
    setVipUpdating(true);
    setUseVIPChannel(checked);
    try {
      await updateIsVIPChannel(checked);
      message.success(t('common.success'));
    } catch {
      message.error(t('common.failure'));
      setUseVIPChannel(!checked);
    } finally {
      vipUpdateInFlight.current = false;
      setVipUpdating(false);
    }
  };

  const handleUpdateUseTLS = async (checked: boolean) => {
    if (tlsUpdateInFlight.current) return;
    tlsUpdateInFlight.current = true;
    setTlsUpdating(true);
    setUseTLS(checked);
    try {
      await updateUseTLS(checked);
      message.success(t('common.success'));
    } catch {
      message.error(t('common.failure'));
      setUseTLS(!checked);
    } finally {
      tlsUpdateInFlight.current = false;
      setTlsUpdating(false);
    }
  };

  return (
    <div style={{ padding: 24 }}>
      {!configurationAvailable && (
        <Alert
          type="info"
          showIcon
          message="运行时配置不可用"
          description={unavailableReason || '当前集群不支持读取或更新 Ops 配置。'}
          style={{ marginBottom: 24 }}
        />
      )}
      {/* NameServer Address List */}
      <div style={{ marginBottom: 24 }}>
        <Typography.Title level={4}>{t('ops.nameServerAddressList')}</Typography.Title>
        <Space wrap align="start">
          <Select
            style={{ minWidth: 400, maxWidth: 500 }}
            value={selectedNamesrv || undefined}
            onChange={setSelectedNamesrv}
            disabled={!writeOperationEnabled || namesrvUpdating}
            placeholder={t('ops.selectNamesrv')}
            options={namesrvAddrList.map((addr) => ({ label: addr, value: addr }))}
          />
          {writeOperationEnabled && (
            <Button
              type="primary"
              icon={<FloppyDisk size={16} />}
              onClick={handleUpdateNameSvrAddr}
              loading={namesrvUpdating}
              disabled={updateNameServerDisabled}
            >
              {t('common.update')}
            </Button>
          )}
          {writeOperationEnabled && (
            <Popconfirm
              title={t('common.areYouSureToDelete')}
              onConfirm={handleDeleteNameSvrAddr}
              okText={t('common.confirm')}
              cancelText={t('common.cancel')}
              disabled={deleteNameServerDisabled || namesrvUpdating}
            >
              <Tooltip title={t('common.delete')}>
                <Button
                  danger
                  aria-label={t('common.delete')}
                  icon={<Trash size={16} />}
                  disabled={deleteNameServerDisabled || namesrvUpdating}
                />
              </Tooltip>
            </Popconfirm>
          )}
          {writeOperationEnabled && (
            <Space.Compact>
              <Input
                style={{ width: 300 }}
                placeholder="NamesrvAddr"
                value={newNamesrvAddr}
                onChange={(e) => setNewNamesrvAddr(e.target.value)}
                disabled={namesrvUpdating}
              />
              <Tooltip title={newAddressBlocked ? nameServerPreflight.newAddress.message : ''}>
                <Button
                  type="primary"
                  icon={<Plus size={16} />}
                  onClick={handleAddNameSvrAddr}
                  loading={namesrvUpdating}
                  disabled={namesrvUpdating || newAddressBlocked}
                >
                  {t('common.add')}
                </Button>
              </Tooltip>
            </Space.Compact>
          )}
        </Space>
      </div>

      <OpsNameServerPreflightPanel preflight={nameServerPreflight} />

      {/* VIP Channel */}
      <div style={{ marginBottom: 24 }}>
        <Typography.Title level={4}>{t('ops.isUseVIPChannel')}</Typography.Title>
        <Space align="center">
          <Switch
            checked={useVIPChannel}
            onChange={handleUpdateIsVIPChannel}
            disabled={!writeOperationEnabled || vipUpdating}
            loading={vipUpdating}
          />
          {writeOperationEnabled && (
            <Button
              type="primary"
              icon={<FloppyDisk size={16} />}
              onClick={() => handleUpdateIsVIPChannel(useVIPChannel)}
              loading={vipUpdating}
              disabled={vipUpdating}
            >
              {t('common.update')}
            </Button>
          )}
        </Space>
      </div>

      {/* Use TLS */}
      <div style={{ marginBottom: 24 }}>
        <Typography.Title level={4}>{t('ops.useTLS')}</Typography.Title>
        <Space align="center">
          <Switch
            checked={useTLS}
            onChange={handleUpdateUseTLS}
            disabled={!writeOperationEnabled || tlsUpdating}
            loading={tlsUpdating}
          />
          {writeOperationEnabled && (
            <Button
              type="primary"
              icon={<FloppyDisk size={16} />}
              onClick={() => handleUpdateUseTLS(useTLS)}
              loading={tlsUpdating}
              disabled={tlsUpdating}
            >
              {t('common.update')}
            </Button>
          )}
        </Space>
      </div>
    </div>
  );
};

export default OpsPage;
