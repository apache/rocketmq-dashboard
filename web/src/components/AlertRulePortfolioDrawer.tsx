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

import { useMemo, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Drawer,
  Empty,
  Flex,
  Input,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { DownloadOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import type { AlertRuleDomain } from '../api/ops';
import type { Instance } from '../api/instance';
import { listAlertRules } from '../services/opsService';
import { useLang } from '../i18n/LangContext';
import { buildCsv, downloadCsv, type CsvColumn } from '../utils/download';
import { tableScrollX } from '../utils/table';
import {
  analyzeAlertRulePortfolio,
  filterAlertPortfolioRows,
  type AlertPortfolioIssue,
  type AlertPortfolioIssueCode,
  type AlertPortfolioRuleRow,
  type AlertPortfolioSeverity,
  type AlertRulePortfolio,
} from '../utils/alertRulePortfolio';

interface AlertRulePortfolioDrawerProps {
  open: boolean;
  domain: AlertRuleDomain;
  instances: Instance[];
  onClose: () => void;
}

const RULE_CSV_COLUMNS: CsvColumn<AlertPortfolioRuleRow>[] = [
  { header: 'Rule ID', value: (row) => row.id },
  { header: 'Rule Name', value: (row) => row.name },
  { header: 'Enabled', value: (row) => row.enabled },
  { header: 'Metric', value: (row) => row.metric },
  { header: 'Scope', value: (row) => row.scope },
  { header: 'Condition', value: (row) => row.condition },
  { header: 'Channels', value: (row) => row.channels.join(';') },
  { header: 'Highest Severity', value: (row) => row.highestSeverity },
  { header: 'Issues', value: (row) => row.issueCodes.join(';') },
];

const severityColor: Record<AlertPortfolioSeverity | 'NONE', string> = {
  CRITICAL: 'error',
  WARNING: 'warning',
  INFO: 'processing',
  NONE: 'success',
};

const AlertRulePortfolioDrawer = ({
  open,
  domain,
  instances,
  onClose,
}: AlertRulePortfolioDrawerProps) => {
  const { t } = useLang();
  const [portfolio, setPortfolio] = useState<AlertRulePortfolio | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const [search, setSearch] = useState('');
  const [enabled, setEnabled] = useState<'ALL' | 'ENABLED' | 'DISABLED'>('ALL');
  const [severity, setSeverity] = useState<AlertPortfolioSeverity | 'NONE' | 'ALL'>('ALL');
  const [issueCode, setIssueCode] = useState<AlertPortfolioIssueCode | 'ALL'>('ALL');
  const requestIdRef = useRef(0);

  const visibleRows = useMemo(
    () =>
      filterAlertPortfolioRows(portfolio?.rows ?? [], {
        search,
        enabled,
        severity,
        issueCode,
      }),
    [enabled, issueCode, portfolio, search, severity],
  );

  const loadPortfolio = async () => {
    const requestId = ++requestIdRef.current;
    setLoading(true);
    setLoadError(false);
    try {
      const rules = await listAlertRules(domain);
      if (requestId !== requestIdRef.current) return;
      setPortfolio(analyzeAlertRulePortfolio(rules, instances));
      setSearch('');
      setEnabled('ALL');
      setSeverity('ALL');
      setIssueCode('ALL');
    } catch {
      if (requestId === requestIdRef.current) setLoadError(true);
    } finally {
      if (requestId === requestIdRef.current) setLoading(false);
    }
  };

  const exportVisibleRows = () => {
    if (!portfolio) return;
    downloadCsv(
      `rocketmq-${domain.toLocaleLowerCase()}-alert-rule-review.csv`,
      buildCsv(RULE_CSV_COLUMNS, visibleRows),
    );
    message.success(t('alertPortfolio.exported', { count: visibleRows.length }));
  };

  const issueLabel = (code: AlertPortfolioIssueCode) => t(`alertPortfolio.issue.${code}`);
  const severityLabel = (value: AlertPortfolioSeverity | 'NONE') =>
    t(`alertPortfolio.severity.${value}`);

  const issueColumns: TableColumnsType<AlertPortfolioIssue> = [
    {
      title: t('alertPortfolio.severity'),
      dataIndex: 'severity',
      key: 'severity',
      width: 110,
      render: (value: AlertPortfolioSeverity) => (
        <Tag color={severityColor[value]}>{severityLabel(value)}</Tag>
      ),
    },
    {
      title: t('alertPortfolio.issue'),
      dataIndex: 'code',
      key: 'code',
      width: 180,
      render: (value: AlertPortfolioIssueCode) => issueLabel(value),
    },
    {
      title: t('alertPortfolio.rules'),
      dataIndex: 'ruleNames',
      key: 'ruleNames',
      width: 220,
      ellipsis: true,
      render: (values: string[], row) => (
        <span title={`${values.join(', ')} (#${row.ruleIds.join(', #')})`}>
          {values.join(', ')}
        </span>
      ),
    },
    {
      title: t('alertPortfolio.scope'),
      dataIndex: 'scope',
      key: 'scope',
      width: 260,
      ellipsis: true,
      render: (value: string) => <span title={value}>{value}</span>,
    },
    {
      title: t('alertPortfolio.evidence'),
      dataIndex: 'evidence',
      key: 'evidence',
      ellipsis: true,
      render: (value: string) => <span title={value}>{value}</span>,
    },
  ];

  const ruleColumns: TableColumnsType<AlertPortfolioRuleRow> = [
    {
      title: t('alertPortfolio.ruleName'),
      dataIndex: 'name',
      key: 'name',
      width: 210,
      fixed: 'left',
      ellipsis: true,
      sorter: (left, right) => left.name.localeCompare(right.name),
      render: (value: string) => <span title={value}>{value}</span>,
    },
    {
      title: t('alertPortfolio.status'),
      dataIndex: 'enabled',
      key: 'enabled',
      width: 100,
      render: (value: boolean) => (
        <Tag color={value ? 'success' : 'default'}>
          {t(value ? 'common.enabled' : 'common.disabled')}
        </Tag>
      ),
    },
    {
      title: t('alertPortfolio.metric'),
      dataIndex: 'metric',
      key: 'metric',
      width: 190,
      ellipsis: true,
      render: (value: string) => <span title={value}>{value}</span>,
    },
    {
      title: t('alertPortfolio.scope'),
      dataIndex: 'scope',
      key: 'scope',
      width: 250,
      ellipsis: true,
      render: (value: string) => <span title={value}>{value}</span>,
    },
    {
      title: t('alertPortfolio.condition'),
      dataIndex: 'condition',
      key: 'condition',
      width: 190,
      ellipsis: true,
      render: (value: string) => <span title={value}>{value}</span>,
    },
    {
      title: t('alertPortfolio.channels'),
      dataIndex: 'channels',
      key: 'channels',
      width: 150,
      render: (values: string[]) => values.map((value) => <Tag key={value}>{value}</Tag>),
    },
    {
      title: t('alertPortfolio.severity'),
      dataIndex: 'highestSeverity',
      key: 'highestSeverity',
      width: 110,
      render: (value: AlertPortfolioSeverity | 'NONE') => (
        <Tag color={severityColor[value]}>{severityLabel(value)}</Tag>
      ),
    },
    {
      title: t('alertPortfolio.issue'),
      dataIndex: 'issueCodes',
      key: 'issueCodes',
      width: 240,
      render: (values: AlertPortfolioIssueCode[]) =>
        values.length > 0
          ? values.map((value) => <Tag key={value}>{issueLabel(value)}</Tag>)
          : t('alertPortfolio.noIssues'),
    },
  ];

  return (
    <Drawer
      title={t('alertPortfolio.title')}
      open={open}
      width={1200}
      destroyOnHidden
      onClose={() => {
        requestIdRef.current += 1;
        onClose();
      }}
    >
      <Flex vertical gap={16}>
        <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
          {t('alertPortfolio.description')}
        </Typography.Paragraph>
        <Flex justify="space-between" gap={12} wrap="wrap">
          <Alert
            type="info"
            showIcon
            style={{ flex: 1 }}
            message={t('alertPortfolio.fullInventory')}
          />
          <Button
            type="primary"
            icon={<SafetyCertificateOutlined />}
            aria-label={t('alertPortfolio.review')}
            loading={loading}
            onClick={() => void loadPortfolio()}
          >
            {t('alertPortfolio.review')}
          </Button>
        </Flex>

        {loadError && <Alert type="error" showIcon message={t('alertPortfolio.loadFailed')} />}

        {!portfolio && !loading && !loadError && (
          <Empty description={t('alertPortfolio.notReviewed')} />
        )}

        {portfolio && (
          <>
            <Flex gap={12} wrap="wrap">
              <Card size="small" style={{ flex: 1, minWidth: 130 }}>
                <Statistic title={t('alertPortfolio.total')} value={portfolio.summary.rules} />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 130 }}>
                <Statistic title={t('alertPortfolio.enabled')} value={portfolio.summary.enabled} />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 130 }}>
                <Statistic
                  title={t('alertPortfolio.affected')}
                  value={portfolio.summary.affectedRules}
                />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 130 }}>
                <Statistic
                  title={t('alertPortfolio.critical')}
                  value={portfolio.summary.criticalIssues}
                />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 130 }}>
                <Statistic
                  title={t('alertPortfolio.warning')}
                  value={portfolio.summary.warningIssues}
                />
              </Card>
            </Flex>

            <Flex justify="space-between" gap={8} wrap="wrap">
              <Space wrap>
                <Input.Search
                  allowClear
                  aria-label={t('alertPortfolio.search')}
                  placeholder={t('alertPortfolio.search')}
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  style={{ width: 240 }}
                />
                <Select
                  aria-label={t('alertPortfolio.stateFilter')}
                  value={enabled}
                  onChange={setEnabled}
                  style={{ width: 140 }}
                  options={[
                    { value: 'ALL', label: t('alertPortfolio.allStates') },
                    { value: 'ENABLED', label: t('common.enabled') },
                    { value: 'DISABLED', label: t('common.disabled') },
                  ]}
                />
                <Select
                  aria-label={t('alertPortfolio.severityFilter')}
                  value={severity}
                  onChange={setSeverity}
                  style={{ width: 145 }}
                  options={[
                    { value: 'ALL', label: t('alertPortfolio.allSeverities') },
                    { value: 'CRITICAL', label: severityLabel('CRITICAL') },
                    { value: 'WARNING', label: severityLabel('WARNING') },
                    { value: 'INFO', label: severityLabel('INFO') },
                    { value: 'NONE', label: severityLabel('NONE') },
                  ]}
                />
                <Select
                  aria-label={t('alertPortfolio.issueFilter')}
                  value={issueCode}
                  onChange={setIssueCode}
                  style={{ width: 190 }}
                  options={[
                    { value: 'ALL', label: t('alertPortfolio.allIssues') },
                    ...(
                      [
                        'EXACT_DUPLICATE',
                        'DUPLICATE_NAME',
                        'NO_CHANNELS',
                        'UNKNOWN_INSTANCE',
                        'DISABLED_ONLY_SCOPE',
                      ] as AlertPortfolioIssueCode[]
                    ).map((value) => ({ value, label: issueLabel(value) })),
                  ]}
                />
              </Space>
              <Button
                icon={<DownloadOutlined />}
                aria-label={t('alertPortfolio.export')}
                disabled={visibleRows.length === 0}
                onClick={exportVisibleRows}
              >
                {t('alertPortfolio.export')}
              </Button>
            </Flex>

            <Typography.Text type="secondary">
              {t('alertPortfolio.visible', { count: visibleRows.length })}
            </Typography.Text>
            <Tabs
              items={[
                {
                  key: 'issues',
                  label: t('alertPortfolio.issueTab', { count: portfolio.issues.length }),
                  children: (
                    <Table
                      rowKey="key"
                      size="small"
                      columns={issueColumns}
                      dataSource={portfolio.issues}
                      pagination={{ pageSize: 10 }}
                      scroll={{ x: tableScrollX(issueColumns) }}
                    />
                  ),
                },
                {
                  key: 'rules',
                  label: t('alertPortfolio.ruleTab', { count: visibleRows.length }),
                  children: (
                    <Table
                      rowKey="key"
                      size="small"
                      columns={ruleColumns}
                      dataSource={visibleRows}
                      pagination={{ pageSize: 20, showSizeChanger: true }}
                      scroll={{ x: tableScrollX(ruleColumns) }}
                    />
                  ),
                },
              ]}
            />
          </>
        )}
      </Flex>
    </Drawer>
  );
};

export default AlertRulePortfolioDrawer;
