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

import { useEffect, useState } from 'react';
import { App, AutoComplete, Button, Card, Form, Select, Table } from 'antd';
import { MagnifyingGlass } from '@phosphor-icons/react';
import { useLang } from '../../i18n/LangContext';
import {
  fetchProducerGroups,
  fetchTopicList,
  queryProducerConnection,
  type ProducerConnection,
} from '../../api/producer';
import type { Instance } from '../../api/instance';
import { listInstances } from '../../services/instanceService';

const ProducerPage = () => {
  const [form] = Form.useForm();
  const [topicList, setTopicList] = useState<string[]>([]);
  const [producerGroups, setProducerGroups] = useState<string[]>([]);
  const [connectionList, setConnectionList] = useState<ProducerConnection[]>([]);
  const [instances, setInstances] = useState<Instance[]>([]);
  const [selectedInstanceId, setSelectedInstanceId] = useState('');
  const [loading, setLoading] = useState(false);
  const { t } = useLang();
  const { message } = App.useApp();
  const fetchTopicFailedMessage = t('producer.fetchTopicFailed');

  useEffect(() => {
    let cancelled = false;

    void listInstances()
      .then((nextInstances) => {
        if (cancelled) return;
        setInstances(nextInstances);
        setSelectedInstanceId((current) => current || nextInstances[0]?.id || '');
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
    setSelectedInstanceId(instanceId);
    setTopicList([]);
    setProducerGroups([]);
    setConnectionList([]);
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
          message.error(fetchTopicFailedMessage);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [fetchTopicFailedMessage, form, message, selectedInstanceId]);

  useEffect(() => {
    let cancelled = false;

    if (!selectedInstanceId) {
      return () => {
        cancelled = true;
      };
    }

    void fetchProducerGroups(selectedInstanceId)
      .then((groups) => {
        if (!cancelled) {
          setProducerGroups(groups);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setProducerGroups([]);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [selectedInstanceId]);

  const onFinish = async (values: { selectedTopic: string; producerGroup: string }) => {
    if (!selectedInstanceId) {
      message.error('Select an instance before querying producer connections.');
      return;
    }
    setLoading(true);
    try {
      const connections = await queryProducerConnection(
        selectedInstanceId,
        values.selectedTopic,
        values.producerGroup,
      );
      setConnectionList(connections);
      if (connections.length === 0) {
        message.info(t('producer.noConnections'));
      }
    } catch {
      message.error(t('producer.fetchConnectionFailed'));
    } finally {
      setLoading(false);
    }
  };

  const columns = [
    { title: 'Client ID', dataIndex: 'clientId', key: 'clientId', align: 'center' as const },
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

      <Card bordered={false} style={{ borderRadius: 8, boxShadow: '0 1px 6px rgba(0,0,0,0.04)' }}>
        <Form form={form} layout="inline" onFinish={onFinish} style={{ marginBottom: 20 }}>
          <Form.Item label="INSTANCE">
            <Select
              aria-label="Instance"
              value={selectedInstanceId || undefined}
              onChange={handleInstanceChange}
              placeholder="Select instance"
              style={{ width: 220 }}
              options={instances.map((instance) => ({ value: instance.id, label: instance.name }))}
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
              options={topicList.map((topic) => ({ value: topic, label: topic }))}
            />
          </Form.Item>
          <Form.Item
            label="PRODUCER GROUP"
            name="producerGroup"
            rules={[{ required: true, whitespace: true, message: t('producer.inputGroup') }]}
          >
            <AutoComplete
              allowClear
              placeholder={t('producer.inputGroup')}
              style={{ width: 300 }}
              options={producerGroups.map((group) => ({ value: group }))}
              filterOption={(inputValue, option) =>
                option?.value.toLowerCase().includes(inputValue.toLowerCase()) ?? false
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

        <Table
          dataSource={connectionList}
          columns={columns}
          rowKey="clientId"
          pagination={false}
          bordered
          size="middle"
        />
      </Card>
    </div>
  );
};

export default ProducerPage;
