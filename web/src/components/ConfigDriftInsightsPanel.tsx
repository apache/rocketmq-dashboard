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

import { useMemo } from 'react';
import {
  Alert,
  App,
  Button,
  Card,
  Flex,
  Progress,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ClipboardText } from '@phosphor-icons/react';
import type { BrokerConfigDiffResult, NameServerConfigDiffResult } from '../api/cluster';
import {
  buildBrokerConfigDriftInsights,
  buildNameServerConfigDriftInsights,
  type ConfigDriftFieldInsight,
  type ConfigDriftKind,
  type ConfigDriftStatus,
} from '../utils/configDriftInsights';
import { tableScrollX } from '../utils/table';
import { useLang } from '../i18n/LangContext';

const { Text } = Typography;

type ConfigDriftPanelProps =
  | {
      kind: 'broker';
      result: BrokerConfigDiffResult;
      fieldLabel: (field: string) => string;
    }
  | {
      kind: 'nameserver';
      result: NameServerConfigDiffResult;
      fieldLabel?: never;
    };

const statusType: Record<ConfigDriftStatus, 'success' | 'warning' | 'error'> = {
  clean: 'success',
  review: 'warning',
  blocked: 'error',
};

const fieldStatusColor: Record<ConfigDriftFieldInsight['status'], string> = {
  alignable: 'gold',
  unconfigured: 'orange',
  ambiguous: 'red',
};

const kindLabel = (kind: ConfigDriftKind, lang: 'zh' | 'en') =>
  lang === 'zh'
    ? kind === 'broker'
      ? 'Broker'
      : 'NameServer'
    : kind === 'broker'
      ? 'Broker'
      : 'NameServer';

const panelCopy = (lang: 'zh' | 'en') =>
  lang === 'zh'
    ? {
        title: '配置漂移修复建议',
        status: {
          clean: '未发现需要修复的配置漂移',
          review: '检测到可评审的配置漂移',
          blocked: '检测结果不完整，先恢复不可达节点',
        },
        statusDescription: {
          clean: '所有已比较字段在可达节点上保持一致。',
          review: '以下建议只给出排查顺序和参考值，提交变更前仍需确认源配置。',
          blocked: '不可达节点可能隐藏更多差异，建议先恢复管理链路后重新检测。',
        },
        totalTargets: '检测目标',
        reachableTargets: '可达目标',
        comparedFields: '比较字段',
        driftFields: '差异字段',
        field: '字段',
        statusColumn: '状态',
        values: '已发现值',
        targetAction: '目标处理',
        referenceValue: '参考多数值',
        noReference: '需人工选择',
        alignable: '可对齐',
        unconfigured: '含未配置',
        ambiguous: '需确认源值',
        configured: '已配置',
        unconfiguredTargets: '未配置',
        outlierTargets: '少数值',
        noTargetAction: '无少数值目标',
        recommendations: '建议顺序',
        copyPlan: '复制检查清单',
        copied: '检查清单已复制',
        copyFailed: '复制失败',
        unreachablePrefix: '不可达',
      }
    : {
        title: 'Configuration Drift Remediation',
        status: {
          clean: 'No remediation needed',
          review: 'Configuration drift requires review',
          blocked: 'Incomplete check: restore unreachable targets first',
        },
        statusDescription: {
          clean: 'All compared fields are aligned on reachable targets.',
          review:
            'The suggestions show triage order and reference values; confirm the source of truth before applying changes.',
          blocked:
            'Unreachable targets can hide additional drift. Restore management connectivity and run the check again.',
        },
        totalTargets: 'Targets',
        reachableTargets: 'Reachable',
        comparedFields: 'Compared fields',
        driftFields: 'Drift fields',
        field: 'Field',
        statusColumn: 'Status',
        values: 'Observed values',
        targetAction: 'Target action',
        referenceValue: 'Majority reference',
        noReference: 'Choose manually',
        alignable: 'Alignable',
        unconfigured: 'Unconfigured',
        ambiguous: 'Needs source of truth',
        configured: 'Configured',
        unconfiguredTargets: 'Unconfigured',
        outlierTargets: 'Outliers',
        noTargetAction: 'No outlier targets',
        recommendations: 'Recommended order',
        copyPlan: 'Copy checklist',
        copied: 'Checklist copied',
        copyFailed: 'Copy failed',
        unreachablePrefix: 'Unreachable',
      };

const renderTargetList = (title: string, targets: string[], emptyText: string) =>
  targets.length > 0 ? (
    <Space size={[0, 4]} wrap>
      <Text type="secondary">{title}:</Text>
      {targets.map((target) => (
        <Tag key={target}>{target}</Tag>
      ))}
    </Space>
  ) : (
    <Text type="secondary">{emptyText}</Text>
  );

const copyText = async (value: string) => {
  if (!navigator.clipboard) {
    throw new Error('Clipboard API unavailable');
  }
  await navigator.clipboard.writeText(value);
};

const ConfigDriftInsightsPanel = (props: ConfigDriftPanelProps) => {
  const { lang } = useLang();
  const { message } = App.useApp();
  const copy = panelCopy(lang);
  const insights = useMemo(
    () =>
      props.kind === 'broker'
        ? buildBrokerConfigDriftInsights(props.result, props.fieldLabel)
        : buildNameServerConfigDriftInsights(props.result),
    [props],
  );
  const targetName = kindLabel(props.kind, lang);
  const reachabilityPercent =
    insights.summary.targetCount === 0
      ? 0
      : Math.round((insights.summary.reachableCount / insights.summary.targetCount) * 100);

  const columns: ColumnsType<ConfigDriftFieldInsight> = [
    {
      title: copy.field,
      dataIndex: 'label',
      key: 'label',
      width: 190,
      render: (_: string, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.label}</Text>
          {record.property ? (
            <Text code type="secondary">
              {record.property}
            </Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: copy.statusColumn,
      dataIndex: 'status',
      key: 'status',
      width: 140,
      render: (status: ConfigDriftFieldInsight['status']) => {
        const label =
          status === 'alignable'
            ? copy.alignable
            : status === 'unconfigured'
              ? copy.unconfigured
              : copy.ambiguous;
        return <Tag color={fieldStatusColor[status]}>{label}</Tag>;
      },
    },
    {
      title: copy.values,
      dataIndex: 'valueGroups',
      key: 'valueGroups',
      render: (_: ConfigDriftFieldInsight['valueGroups'], record) => (
        <Space size={[0, 4]} wrap>
          {record.valueGroups.map((group) => (
            <Tag
              key={group.label}
              color={group.value === record.majorityValue ? 'blue' : 'default'}
            >
              {`${group.label} x ${group.count}`}
            </Tag>
          ))}
          {record.unconfiguredCount > 0 ? (
            <Tag color="orange">{`${copy.unconfiguredTargets} x ${record.unconfiguredCount}`}</Tag>
          ) : null}
        </Space>
      ),
    },
    {
      title: copy.targetAction,
      key: 'targetAction',
      width: 300,
      render: (_: unknown, record) => (
        <Space direction="vertical" size={4}>
          <Text type="secondary">
            {copy.referenceValue}: {record.majorityValue ?? copy.noReference}
          </Text>
          {record.minorityTargets.length > 0
            ? renderTargetList(copy.outlierTargets, record.minorityTargets, copy.noTargetAction)
            : null}
          {record.unconfiguredTargets.length > 0
            ? renderTargetList(
                copy.unconfiguredTargets,
                record.unconfiguredTargets,
                copy.noTargetAction,
              )
            : null}
          {record.minorityTargets.length === 0 && record.unconfiguredTargets.length === 0 ? (
            <Text type="secondary">{copy.noTargetAction}</Text>
          ) : null}
        </Space>
      ),
    },
  ];

  const handleCopyPlan = async () => {
    try {
      await copyText(insights.remediationPlan);
      message.success(copy.copied);
    } catch {
      message.error(copy.copyFailed);
    }
  };

  return (
    <Card
      size="small"
      title={`${targetName} ${copy.title}`}
      extra={
        <Button
          size="small"
          icon={<ClipboardText size={14} />}
          onClick={() => void handleCopyPlan()}
        >
          {copy.copyPlan}
        </Button>
      }
      style={{ marginBottom: 16 }}
    >
      <Alert
        showIcon
        type={statusType[insights.status]}
        message={copy.status[insights.status]}
        description={copy.statusDescription[insights.status]}
        style={{ marginBottom: 12 }}
      />
      <Flex gap={16} wrap="wrap" align="stretch" style={{ marginBottom: 12 }}>
        <Statistic title={copy.totalTargets} value={insights.summary.targetCount} />
        <Statistic
          title={copy.reachableTargets}
          value={`${insights.summary.reachableCount}/${insights.summary.targetCount}`}
        />
        <Statistic title={copy.comparedFields} value={insights.summary.comparedFieldCount} />
        <Statistic title={copy.driftFields} value={insights.summary.differenceCount} />
        <div style={{ minWidth: 180, flex: '1 1 180px' }}>
          <Text type="secondary">{copy.reachableTargets}</Text>
          <Progress
            percent={reachabilityPercent}
            size="small"
            status={insights.summary.unreachableCount > 0 ? 'exception' : 'success'}
          />
        </div>
      </Flex>
      {insights.unreachableTargets.length > 0 ? (
        <Space size={[0, 4]} wrap style={{ marginBottom: 12 }}>
          <Text type="secondary">{`${copy.unreachablePrefix} ${targetName}:`}</Text>
          {insights.unreachableTargets.map((target) => (
            <Tag color="red" key={target.key}>
              {target.label}
            </Tag>
          ))}
        </Space>
      ) : null}
      <Space direction="vertical" size={4} style={{ width: '100%', marginBottom: 12 }}>
        <Text strong>{copy.recommendations}</Text>
        {insights.recommendations.map((recommendation) => (
          <Text key={recommendation} type="secondary">
            {recommendation}
          </Text>
        ))}
      </Space>
      {insights.fields.length > 0 ? (
        <Table<ConfigDriftFieldInsight>
          columns={columns}
          dataSource={insights.fields}
          rowKey="key"
          pagination={false}
          size="small"
          tableLayout="fixed"
          scroll={{ x: tableScrollX(columns) }}
        />
      ) : null}
    </Card>
  );
};

export default ConfigDriftInsightsPanel;
