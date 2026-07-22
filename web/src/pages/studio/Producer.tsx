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
import { Button, Form, Input, Select, Table, Card, App } from 'antd';
import { MagnifyingGlass } from '@phosphor-icons/react';
import { useLang } from '../../i18n/LangContext';
import {
  fetchTopicList,
  queryProducerConnection,
  type ProducerConnection,
} from '../../api/producer';

const ProducerPage = () => {
  const [form] = Form.useForm();
  const [topicList, setTopicList] = useState<string[]>([]);
  const [connectionList, setConnectionList] = useState<ProducerConnection[]>([]);
  const [loading, setLoading] = useState(false);
  const { t } = useLang();
  const { message } = App.useApp();

  // Load topic list on mount (once)
  const initialized = useRef<boolean | null>(null);
  if (initialized.current == null) {
    initialized.current = true;
    const loadTopics = async () => {
      try {
        const topics = await fetchTopicList();
        setTopicList(topics);
      } catch {
        message.error(t('producer.fetchTopicFailed'));
      }
    };
    loadTopics();
  }

  const onFinish = async (values: { selectedTopic: string; producerGroup: string }) => {
    setLoading(true);
    try {
      const connections = await queryProducerConnection(values.selectedTopic, values.producerGroup);
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
            rules={[{ required: true, message: t('producer.inputGroup') }]}
          >
            <Input style={{ width: 300 }} />
          </Form.Item>
          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              loading={loading}
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
