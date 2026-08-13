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
import { Alert, App, Button, Empty, Flex, Select, Table, Tag, Tooltip, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { ArrowsClockwise, DownloadSimple } from '@phosphor-icons/react';
import {
  type ClusterInfo,
  type NameServerConfigDifference,
  type NameServerConfigDiffResult,
} from '../../api/cluster';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import { getNameServerConfigDiff, listClusters } from '../../services/clusterService';
import { listInstances } from '../../services/instanceService';
import type { Instance } from '../../api/instance';

const { Text, Title } = Typography;

const NameServerConfigDriftPage = () => {
  const { t } = useLang();
  const { message } = App.useApp();
  const requestSequence = useRef(0);
  const [instances, setInstances] = useState<Instance[]>([]);
  const [selectedInstanceId, setSelectedInstanceId] = useState<string>();
  const [clusters, setClusters] = useState<ClusterInfo[]>([]);
  const [selectedClusterId, setSelectedClusterId] = useState<string>();
  const [clustersLoading, setClustersLoading] = useState(true);
  const [checking, setChecking] = useState(false);
  const [result, setResult] = useState<NameServerConfigDiffResult>();

  const runCheck = useCallback(
    async (clusterId: string, instanceId: string) => {
      const sequence = ++requestSequence.current;
      setChecking(true);
      try {
        const nextResult = await getNameServerConfigDiff(clusterId, instanceId);
        if (sequence === requestSequence.current) setResult(nextResult);
      } catch {
        if (sequence === requestSequence.current) {
          setResult(undefined);
          message.error(t('nameServerDrift.checkFailed'));
        }
      } finally {
        if (sequence === requestSequence.current) setChecking(false);
      }
    },
    [message, t],
  );

  useEffect(() => {
    let cancelled = false;
    void listInstances()
      .then(async (items) => {
        if (cancelled) return;
        const apacheInstances = items.filter((instance) => instance.vendor === 'APACHE');
        setInstances(apacheInstances);
        const firstInstanceId = apacheInstances[0]?.id;
        setSelectedInstanceId(firstInstanceId);
        if (!firstInstanceId) {
          setClusters([]);
          return;
        }
        const clustersForInstance = await listClusters(firstInstanceId);
        if (cancelled) return;
        setClusters(clustersForInstance);
        const firstClusterId = clustersForInstance[0]?.id;
        setSelectedClusterId(firstClusterId);
        if (firstClusterId) void runCheck(firstClusterId, firstInstanceId);
      })
      .catch(() => {
        if (!cancelled) message.error(t('nameServerDrift.loadClustersFailed'));
      })
      .finally(() => {
        if (!cancelled) setClustersLoading(false);
      });
    return () => {
      cancelled = true;
      requestSequence.current += 1;
    };
  }, [message, runCheck, t]);

  const selectInstance = async (instanceId: string) => {
    const sequence = ++requestSequence.current;
    setSelectedInstanceId(instanceId);
    setSelectedClusterId(undefined);
    setClusters([]);
    setResult(undefined);
    setClustersLoading(true);
    try {
      const nextClusters = await listClusters(instanceId);
      if (sequence !== requestSequence.current) return;
      setClusters(nextClusters);
      const firstClusterId = nextClusters[0]?.id;
      setSelectedClusterId(firstClusterId);
      if (firstClusterId) void runCheck(firstClusterId, instanceId);
    } catch {
      if (sequence === requestSequence.current)
        message.error(t('nameServerDrift.loadClustersFailed'));
    } finally {
      if (sequence === requestSequence.current) setClustersLoading(false);
    }
  };

  const selectCluster = (clusterId: string) => {
    setSelectedClusterId(clusterId);
    setResult(undefined);
    if (selectedInstanceId) void runCheck(clusterId, selectedInstanceId);
  };

  const columns = useMemo<TableColumnsType<NameServerConfigDifference>>(() => {
    const nodeAddresses = result?.nodes.map((node) => node.address) ?? [];
    return [
      {
        title: t('nameServerDrift.configKey'),
        dataIndex: 'key',
        key: 'key',
        fixed: 'left',
        width: 220,
        render: (key: string) => <Text code>{key}</Text>,
      },
      ...nodeAddresses.map((address) => ({
        title: address,
        key: address,
        width: 220,
        render: (_: unknown, difference: NameServerConfigDifference) => {
          const config = difference.values.find((value) => value.address === address);
          if (!config?.configured)
            return <Text type="secondary">{t('nameServerDrift.notConfigured')}</Text>;
          return <Text>{config.value}</Text>;
        },
      })),
    ];
  }, [result, t]);

  const exportResult = () => {
    if (!result) return;
    const blob = new Blob([JSON.stringify(result, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `nameserver-config-drift-${result.cluster.replace(/[^a-zA-Z0-9._-]/g, '_')}.json`;
    link.click();
    URL.revokeObjectURL(url);
  };

  const statusAlert = result ? (
    !result.complete ? (
      <Alert
        showIcon
        type="warning"
        message={t('nameServerDrift.incomplete')}
        description={t('nameServerDrift.incompleteDescription', {
          reachable: result.reachableNodeCount,
          total: result.nodeCount,
        })}
      />
    ) : result.driftDetected ? (
      <Alert
        showIcon
        type="warning"
        message={t('nameServerDrift.driftDetected')}
        description={t('nameServerDrift.driftDescription', { count: result.differences.length })}
      />
    ) : (
      <Alert
        showIcon
        type="success"
        message={t('nameServerDrift.consistent')}
        description={t('nameServerDrift.consistentDescription', {
          keys: result.comparedKeys.length,
        })}
      />
    )
  ) : null;

  return (
    <div style={{ padding: 24 }}>
      <PageHeader title={t('nameServerDrift.title')} />

      <Flex wrap gap={8} align="center" style={{ marginBottom: 20 }}>
        <Select
          aria-label="NameServer drift instance"
          loading={clustersLoading}
          value={selectedInstanceId}
          onChange={(instanceId) => void selectInstance(instanceId)}
          placeholder={t('common.selectInstance')}
          options={instances.map((instance) => ({ label: instance.name, value: instance.name }))}
          style={{ width: 'min(100%, 280px)' }}
        />
        <Select
          aria-label={t('nameServerDrift.cluster')}
          loading={clustersLoading}
          value={selectedClusterId}
          onChange={selectCluster}
          placeholder={t('nameServerDrift.selectCluster')}
          options={clusters.map((cluster) => ({
            label: cluster.name || cluster.id,
            value: cluster.id,
          }))}
          style={{ width: 'min(100%, 360px)' }}
        />
        <Tooltip title={t('nameServerDrift.refresh')}>
          <Button
            aria-label={t('nameServerDrift.refresh')}
            icon={<ArrowsClockwise size={16} />}
            loading={checking}
            disabled={!selectedClusterId || !selectedInstanceId}
            onClick={() =>
              selectedClusterId &&
              selectedInstanceId &&
              void runCheck(selectedClusterId, selectedInstanceId)
            }
          />
        </Tooltip>
        <Tooltip title={t('nameServerDrift.export')}>
          <Button
            aria-label={t('nameServerDrift.export')}
            icon={<DownloadSimple size={16} />}
            disabled={!result}
            onClick={exportResult}
          />
        </Tooltip>
      </Flex>

      {!clustersLoading && clusters.length === 0 && (
        <Empty description={t('nameServerDrift.noClusters')} />
      )}

      {clusters.length > 0 && (
        <Flex vertical gap={24}>
          {statusAlert}

          {result && (
            <section>
              <Title level={5}>{t('nameServerDrift.nodes')}</Title>
              <Flex wrap gap={8}>
                {result.nodes.map((node) => (
                  <Tag key={node.address} color={node.reachable ? 'success' : 'error'}>
                    {node.address} ·{' '}
                    {node.reachable
                      ? t('nameServerDrift.reachable')
                      : t('nameServerDrift.unreachable')}
                  </Tag>
                ))}
              </Flex>
            </section>
          )}

          {result && result.differences.length > 0 && (
            <section>
              <Title level={5}>{t('nameServerDrift.differences')}</Title>
              <Table
                rowKey="key"
                size="small"
                loading={checking}
                columns={columns}
                dataSource={result.differences}
                pagination={false}
                scroll={{ x: 'max-content' }}
              />
            </section>
          )}
        </Flex>
      )}
    </div>
  );
};

export default NameServerConfigDriftPage;
