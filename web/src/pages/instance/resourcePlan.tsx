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

import { useMemo, useState } from 'react';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Input,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlayCircleOutlined } from '@ant-design/icons';
import PageHeader from '../../components/PageHeader';
import { useInstanceFilter } from '../../hooks/useInstanceFilter';
import type { ResourcePlanEntry } from '../../services/resourcePlanService';
import {
  RESOURCE_PLAN_SAMPLE,
  parseResourceBundle,
  previewResourcePlan,
} from '../../services/resourcePlanService';

const { Text, Paragraph } = Typography;
const { TextArea } = Input;

const ACTION_COLOR: Record<string, string> = {
  CREATE: 'green',
  UPDATE: 'blue',
  SKIP: 'default',
  CONFLICT: 'orange',
  INVALID: 'red',
};

const RESOURCE_LABEL: Record<string, string> = {
  TOPIC: 'Topic',
  CONSUMER_GROUP: 'Consumer Group',
};

const ResourcePlanPage = () => {
  const { message } = App.useApp();
  const { selectedInstanceId, selectedInstance, selectInstance, instanceOptions } =
    useInstanceFilter();
  const [bundleText, setBundleText] = useState(RESOURCE_PLAN_SAMPLE);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [plan, setPlan] = useState<Awaited<ReturnType<typeof previewResourcePlan>> | null>(null);

  const columns = useMemo<ColumnsType<ResourcePlanEntry>>(
    () => [
      {
        title: '资源类型',
        dataIndex: 'resourceType',
        width: 150,
        render: (value: ResourcePlanEntry['resourceType']) => RESOURCE_LABEL[value] ?? value,
      },
      {
        title: '名称',
        dataIndex: 'name',
        width: 220,
        render: (value: string) => value || <Text type="secondary">未命名</Text>,
      },
      {
        title: '行号',
        dataIndex: 'rowIndex',
        width: 80,
      },
      {
        title: '动作',
        dataIndex: 'action',
        width: 110,
        render: (action: ResourcePlanEntry['action']) => (
          <Tag color={ACTION_COLOR[action]}>{action}</Tag>
        ),
      },
      {
        title: '可应用',
        dataIndex: 'applicable',
        width: 100,
        render: (applicable: boolean) =>
          applicable ? <Tag color="green">是</Tag> : <Tag color="default">否</Tag>,
      },
      {
        title: '原因',
        dataIndex: 'reason',
        width: 260,
      },
      {
        title: '差异',
        dataIndex: 'changes',
        render: (changes: ResourcePlanEntry['changes']) =>
          changes.length ? (
            <Space direction="vertical" size={2}>
              {changes.map((change) => (
                <Text code key={`${change.field}-${change.currentValue}-${change.desiredValue}`}>
                  {change.field}: {change.currentValue ?? '∅'} → {change.desiredValue ?? '∅'}
                </Text>
              ))}
            </Space>
          ) : (
            <Text type="secondary">无差异</Text>
          ),
      },
    ],
    [],
  );

  const runPreview = async () => {
    if (!selectedInstanceId) {
      message.warning('请先选择实例');
      return;
    }
    setPreviewLoading(true);
    try {
      const bundle = parseResourceBundle(bundleText);
      const request = { instanceId: selectedInstanceId, ...bundle };
      const nextPlan = await previewResourcePlan(request);
      setPlan(nextPlan);
      message.success('资源变更计划已生成');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '资源变更计划生成失败');
    } finally {
      setPreviewLoading(false);
    }
  };

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="资源变更计划"
        subtitle="导入前只读预检 Topic 与 Consumer Group 配置，先看差异再决定是否手动调整"
        extra={
          <Space>
            <Select
              value={selectedInstanceId || undefined}
              placeholder="选择实例"
              style={{ width: 220 }}
              options={instanceOptions}
              onChange={selectInstance}
            />
            <Button onClick={() => setBundleText(RESOURCE_PLAN_SAMPLE)}>填充示例</Button>
            <Button
              type="primary"
              icon={<PlayCircleOutlined />}
              loading={previewLoading}
              onClick={runPreview}
            >
              生成计划
            </Button>
          </Space>
        }
      />

      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="资源包预检不会直接修改集群"
        description={
          <Paragraph style={{ marginBottom: 0 }}>
            Topic 已存在且配置不同会标记为 UPDATE；Consumer Group
            当前没有更新接口，已存在但配置不同会标记为
            CONFLICT。页面只生成计划，不会创建、更新或删除任何资源。
          </Paragraph>
        }
      />

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={9}>
          <Card
            title="资源包 JSON"
            extra={
              selectedInstance ? (
                <Text type="secondary">当前实例：{selectedInstance.name}</Text>
              ) : (
                <Text type="secondary">未选择实例</Text>
              )
            }
          >
            <TextArea
              value={bundleText}
              onChange={(event) => setBundleText(event.target.value)}
              autoSize={{ minRows: 20, maxRows: 30 }}
              spellCheck={false}
              style={{ fontFamily: 'Menlo, Monaco, Consolas, monospace', fontSize: 12 }}
            />
          </Card>
        </Col>
        <Col xs={24} lg={15}>
          {plan && (
            <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic title="总资源" value={plan.summary.total} />
                </Card>
              </Col>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic
                    title="可应用"
                    value={plan.summary.applicable}
                    valueStyle={{ color: '#389e0d' }}
                  />
                </Card>
              </Col>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic
                    title="冲突"
                    value={plan.summary.conflicts}
                    valueStyle={{ color: '#d46b08' }}
                  />
                </Card>
              </Col>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic
                    title="无效"
                    value={plan.summary.invalids}
                    valueStyle={{ color: '#cf1322' }}
                  />
                </Card>
              </Col>
            </Row>
          )}
          <Card title="预检结果">
            <Table
              rowKey={(record) => `${record.resourceType}-${record.name}-${record.rowIndex}`}
              loading={previewLoading}
              columns={columns}
              dataSource={plan?.entries ?? []}
              pagination={{ pageSize: 10, showSizeChanger: true }}
              scroll={{ x: 1080 }}
              locale={{
                emptyText: '粘贴资源包后点击“生成计划”',
              }}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default ResourcePlanPage;
