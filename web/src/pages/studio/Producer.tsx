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

import { useEffect, useRef, useState } from 'react';
import {
  Alert,
  App,
  AutoComplete,
  Button,
  Card,
  Flex,
  Form,
  Select,
  Statistic,
  Table,
  Tag,
} from 'antd';
import { DownloadSimple, MagnifyingGlass } from '@phosphor-icons/react';
import { useLang } from '../../i18n/LangContext';
import {
  fetchProducerGroups,
  fetchTopicList,
  queryProducerConnection,
  type ProducerConnection,
  type ProducerConnectionSummary,
  type ProducerConnectionWarning,
  type ProducerReadiness,
} from '../../api/producer';
import { supportsApacheRuntime, type Instance } from '../../api/instance';
import { listInstances } from '../../services/instanceService';
import { buildCsv, downloadCsv, type CsvColumn } from '../../utils/download';

const readinessConfig: Record<ProducerReadiness, { color: string; type: 'success' | 'warning' }> = {
  READY: { color: 'success', type: 'success' },
  WARNING: { color: 'warning', type: 'warning' },
  UNAVAILABLE: { color: 'error', type: 'warning' },
};

interface ProducerConnectionExportRow extends ProducerConnection {
  instanceId: string;
  topic?: string;
  producerGroup?: string;
  readiness?: ProducerReadiness;
  warnings: string;
}

const PRODUCER_CONNECTION_EXPORT_COLUMNS: CsvColumn<ProducerConnectionExportRow>[] = [
  { header: 'Instance ID', value: (connection) => connection.instanceId },
  { header: 'Topic', value: (connection) => connection.topic },
  { header: 'Producer Group', value: (connection) => connection.producerGroup },
  { header: 'Readiness', value: (connection) => connection.readiness },
  { header: 'Warnings', value: (connection) => connection.warnings },
  { header: 'Client ID', value: (connection) => connection.clientId },
  { header: 'Address', value: (connection) => connection.clientAddr },
  { header: 'Language', value: (connection) => connection.language },
  { header: 'Version', value: (connection) => connection.versionDesc },
];

const PRODUCER_GROUP_SELECTOR_LIMIT = 20;

/**
 * Case-insensitive AutoComplete filter for producer-group suggestions.
 *
 * Both sides are coerced with String() and null-guaranteed so that a
 * non-string option value (e.g. a `null` group returned by the API) or a
 * missing search string cannot throw and blank out the dropdown.
 */
export const matchesProducerGroupOption = (
  inputValue: string | null | undefined,
  option?: { value?: unknown } | null,
) =>
  String(option?.value ?? '')
    .toLowerCase()
    .includes(String(inputValue ?? '').toLowerCase());

const ProducerPage = () => {
  const [form] = Form.useForm();
  const [topicList, setTopicList] = useState<string[]>([]);
  const [producerGroups, setProducerGroups] = useState<string[]>([]);
  const [connectionList, setConnectionList] = useState<ProducerConnection[]>([]);
  const [connectionSummary, setConnectionSummary] = useState<ProducerConnectionSummary | null>(
    null,
  );
  const [instances, setInstances] = useState<Instance[]>([]);
  const [selectedInstanceId, setSelectedInstanceId] = useState<string | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const { t } = useLang();
  const { message } = App.useApp();
  const fetchTopicFailedMessage = t('producer.fetchTopicFailed');
  const queryRequestIdRef = useRef(0);

  const queryInFlightRef = useRef<number | null>(null);

  const producerGroupRequestIdRef = useRef(0);
  const selectedTopic = Form.useWatch('selectedTopic', form);

  useEffect(() => {
    let cancelled = false;

    void listInstances()
      .then((nextInstances) => {
        if (cancelled) return;
        const apacheInstances = nextInstances.filter(supportsApacheRuntime);
        setInstances(apacheInstances);
        setSelectedInstanceId((current) =>
          apacheInstances.some((instance) => instance.name === current)
            ? current
            : apacheInstances[0]?.name,
        );
      })
      .catch(() => {
        if (!cancelled) {
          setInstances([]);
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const handleInstanceChange = (instanceId: string) => {
    queryRequestIdRef.current += 1;
    queryInFlightRef.current = null;
    setSelectedInstanceId(instanceId);
    setTopicList([]);
    setProducerGroups([]);
    setConnectionList([]);
    setConnectionSummary(null);
    setLoading(false);
    form.setFieldsValue({ selectedTopic: undefined, producerGroup: undefined });
  };

  useEffect(() => {
    let cancelled = false;

    if (!selectedInstanceId) {
      return () => {
        cancelled = true;
      };
    }

    form.setFieldsValue({ selectedTopic: undefined, producerGroup: undefined });

    void fetchTopicList(selectedInstanceId)
      .then((topics) => {
        if (!cancelled) {
          setTopicList(topics);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setTopicList([]);
          message.error(fetchTopicFailedMessage);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [fetchTopicFailedMessage, form, message, selectedInstanceId]);

  const handleTopicChange = () => {
    producerGroupRequestIdRef.current += 1;
    setProducerGroups([]);
    form.setFieldValue('producerGroup', undefined);
  };

  const loadProducerGroups = async (query = '') => {
    if (!selectedInstanceId || !selectedTopic) {
      setProducerGroups([]);
      return;
    }
    const requestId = ++producerGroupRequestIdRef.current;
    try {
      const groups = await fetchProducerGroups(selectedInstanceId, {
        topic: selectedTopic,
        query,
        limit: PRODUCER_GROUP_SELECTOR_LIMIT,
      });
      if (requestId === producerGroupRequestIdRef.current) {
        setProducerGroups(groups);
      }
    } catch {
      if (requestId === producerGroupRequestIdRef.current) {
        setProducerGroups([]);
      }
    }
  };

  const onFinish = async (values: { selectedTopic: string; producerGroup?: string }) => {
    if (queryInFlightRef.current !== null) return;
    if (!selectedInstanceId) {
      message.error('Select an instance before querying producer connections.');
      return;
    }
    const requestId = ++queryRequestIdRef.current;
    queryInFlightRef.current = requestId;
    setConnectionList([]);
    setConnectionSummary(null);
    setLoading(true);
    try {
      const result = await queryProducerConnection(
        selectedInstanceId,
        values.selectedTopic,
        values.producerGroup,
      );
      if (requestId !== queryRequestIdRef.current) return;
      const connections = result.connectionSet;
      setConnectionList(connections);
      setConnectionSummary(result.summary);
      if (connections.length === 0) {
        message.info(t('producer.noConnections'));
      }
    } catch {
      if (requestId === queryRequestIdRef.current) {
        message.error(t('producer.fetchConnectionFailed'));
      }
    } finally {
      if (queryInFlightRef.current === requestId) {
        queryInFlightRef.current = null;
      }
      if (requestId === queryRequestIdRef.current) {
        setLoading(false);
      }
    }
  };

  const columns = [
    { title: 'Client ID', dataIndex: 'clientId', key: 'clientId', align: 'center' as const },
    {
      title: 'Producer Group',
      dataIndex: 'producerGroup',
      key: 'producerGroup',
      align: 'center' as const,
      render: (value?: string) => value || '-',
    },
    {
      title: t('common.address'),
      dataIndex: 'clientAddr',
      key: 'clientAddr',
      align: 'center' as const,
    },
    {
      title: t('producer.language'),
      dataIndex: 'language',
      key: 'language',
      align: 'center' as const,
    },
    {
      title: t('brokerCluster.version'),
      dataIndex: 'versionDesc',
      key: 'versionDesc',
      align: 'center' as const,
    },
  ];

  const warningLabel: Record<ProducerConnectionWarning, string> = {
    NO_CONNECTIONS: t('producer.warningNoConnections'),
    DUPLICATE_CLIENT_ID: t('producer.warningDuplicateClientId'),
    MIXED_CLIENT_VERSION: t('producer.warningMixedVersion'),
    INCOMPLETE_CLIENT_METADATA: t('producer.warningIncompleteMetadata'),
  };

  const renderDistribution = (items: ProducerConnectionSummary['languages']) =>
    items.length === 0 ? (
      <Tag>{t('common.noData')}</Tag>
    ) : (
      <Flex gap={4} wrap>
        {items.map((item) => (
          <Tag key={item.value}>
            {item.value}: {item.count}
          </Tag>
        ))}
      </Flex>
    );

  const handleExport = () => {
    const { selectedTopic, producerGroup } = form.getFieldsValue([
      'selectedTopic',
      'producerGroup',
    ]) as { selectedTopic?: string; producerGroup?: string };
    const warnings = connectionSummary?.warnings.join(';') ?? '';
    const rows = connectionList.map((connection) => ({
      ...connection,
      instanceId: selectedInstanceId ?? '',
      topic: connection.topic ?? selectedTopic,
      producerGroup: connection.producerGroup ?? producerGroup,
      readiness: connectionSummary?.readiness,
      warnings,
    }));
    const filename = `rocketmq-producer-connections-${new Date().toISOString().slice(0, 10)}.csv`;
    const csv = buildCsv(PRODUCER_CONNECTION_EXPORT_COLUMNS, rows);
    downloadCsv(filename, csv);
  };
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
        <h2 style={{ fontSize: 20, fontWeight: 600, margin: 0 }}>{t('producer.title')}</h2>
      </div>

      <Card
        variant="borderless"
        style={{ borderRadius: 8, boxShadow: '0 1px 6px rgba(0,0,0,0.04)' }}
      >
        <Form form={form} layout="inline" onFinish={onFinish} style={{ marginBottom: 20 }}>
          <Form.Item label="INSTANCE">
            <Select
              aria-label="Instance"
              value={selectedInstanceId}
              onChange={handleInstanceChange}
              placeholder="Select instance"
              style={{ width: 220 }}
              options={instances.map((instance) => ({
                value: instance.name,
                label: instance.name,
              }))}
            />
          </Form.Item>
          <Form.Item
            label="TOPIC"
            name="selectedTopic"
            rules={[{ required: true, message: t('producer.selectTopic') }]}
          >
            <Select
              showSearch
              placeholder={t('producer.selectTopic')}
              style={{ width: 300 }}
              optionFilterProp="label"
              onChange={handleTopicChange}
              options={topicList.map((topic) => ({ value: topic, label: topic }))}
            />
          </Form.Item>
          <Form.Item label="PRODUCER GROUP" name="producerGroup">
            <AutoComplete
              allowClear
              placeholder={t('producer.inputGroupOptional')}
              style={{ width: 300 }}
              options={producerGroups.map((group) => ({ value: group }))}
              onFocus={() => {
                void loadProducerGroups();
              }}
              onSearch={(value) => {
                void loadProducerGroups(value);
              }}
              filterOption={(inputValue, option) =>
                matchesProducerGroupOption(inputValue, option)
              }
            />
          </Form.Item>
          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              loading={loading}
              disabled={!selectedInstanceId}
              icon={<MagnifyingGlass size={14} />}
            >
              {t('common.search')}
            </Button>
          </Form.Item>
        </Form>

        <Flex justify="flex-end" style={{ marginBottom: 16 }}>
          <Button
            icon={<DownloadSimple size={16} />}
            disabled={connectionList.length === 0}
            onClick={handleExport}
          >
            {t('common.export')}
          </Button>
        </Flex>
        {connectionSummary && (
          <div style={{ marginBottom: 20 }}>
            <Alert
              showIcon
              type={readinessConfig[connectionSummary.readiness].type}
              message={
                <Flex align="center" gap={8} wrap>
                  <span>{t('producer.readiness')}</span>
                  <Tag color={readinessConfig[connectionSummary.readiness].color}>
                    {t(`producer.readiness${connectionSummary.readiness}`)}
                  </Tag>
                  {connectionSummary.warnings.map((warning) => (
                    <Tag key={warning} color="warning">
                      {warningLabel[warning] ?? warning}
                    </Tag>
                  ))}
                </Flex>
              }
              style={{ marginBottom: 12 }}
            />
            <Flex gap={24} wrap style={{ marginBottom: 12 }}>
              <Statistic
                title={t('producer.connectionTotal')}
                value={connectionSummary.totalConnections}
              />
              <Statistic
                title={t('producer.uniqueClients')}
                value={connectionSummary.uniqueClientCount}
              />
              <Statistic
                title={t('producer.uniqueAddresses')}
                value={connectionSummary.uniqueAddressCount}
              />
              <Statistic
                title={t('producer.languageKinds')}
                value={connectionSummary.uniqueLanguageCount}
              />
              <Statistic
                title={t('producer.versionKinds')}
                value={connectionSummary.uniqueVersionCount}
              />
            </Flex>
            <Flex gap={16} wrap>
              <div>
                <div style={{ color: '#8c8c8c', fontSize: 14, marginBottom: 6 }}>
                  {t('producer.languageDistribution')}
                </div>
                {renderDistribution(connectionSummary.languages)}
              </div>
              <div>
                <div style={{ color: '#8c8c8c', fontSize: 14, marginBottom: 6 }}>
                  {t('producer.versionDistribution')}
                </div>
                {renderDistribution(connectionSummary.versions)}
              </div>
            </Flex>
          </div>
        )}

        <Table
          dataSource={connectionList}
          columns={columns}
          rowKey={(record) =>
            `${record.producerGroup ?? ''}:${record.clientId}:${record.clientAddr}`
          }
          pagination={false}
          bordered
          size="middle"
        />
      </Card>
    </div>
  );
};

export default ProducerPage;
