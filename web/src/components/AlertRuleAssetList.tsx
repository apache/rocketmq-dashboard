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
  Alert,
  App,
  Button,
  Card,
  Col,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ArrowClockwise, DownloadSimple, Eye } from '@phosphor-icons/react';
import { useLang } from '../i18n/LangContext';
import {
  exportAlertRuleAsset,
  getAlertRuleAsset,
  listAlertRuleAssets,
} from '../services/alertRuleAssetService';
import type { AlertRuleAssetInfo } from '../api/alertRuleAssets';
import { downloadBlob, downloadCsv } from '../utils/download';
import { tableScrollX } from '../utils/table';
import {
  buildAlertRuleAssetCatalogCsv,
  buildAlertRuleAssetCatalogFilename,
  buildAlertRuleAssetInsights,
  formatAlertRuleAssetPercent,
  type AlertRuleAssetDomain,
  type AlertRuleAssetDomainInsight,
  type AlertRuleAssetHealthLevel,
  type AlertRuleAssetIssue,
} from '../utils/alertRuleAssetInsights';

const { Text } = Typography;

const SEVERITY_COLORS: Record<string, string> = {
  critical: 'red',
  warning: 'orange',
  info: 'blue',
};

const HEALTH_COLORS: Record<AlertRuleAssetHealthLevel, string> = {
  healthy: 'success',
  notice: 'processing',
  warning: 'warning',
  critical: 'error',
};

const DOMAIN_COLORS: Record<AlertRuleAssetDomain, string> = {
  broker: 'blue',
  consumer: 'cyan',
  producer: 'geekblue',
  topic: 'purple',
  proxy: 'green',
  client: 'gold',
  dlq: 'volcano',
  error: 'red',
  runtime: 'magenta',
  unknown: 'default',
};

const domainTextKey = (domain: AlertRuleAssetDomain) => `alertAssets.domain.${domain}`;

const issueTextKey = (issue: AlertRuleAssetIssue) => {
  switch (issue.code) {
    case 'NO_ASSETS':
      return 'alertAssets.issueNoAssets';
    case 'DUPLICATE_ASSET_NAME':
      return 'alertAssets.issueDuplicateName';
    case 'EMPTY_RULE_ASSET':
      return 'alertAssets.issueEmptyAsset';
    case 'MISSING_SEVERITY':
      return 'alertAssets.issueMissingSeverity';
    case 'DOMAIN_UNCOVERED':
      return 'alertAssets.issueDomainUncovered';
    case 'DOMAIN_WITHOUT_CRITICAL':
      return 'alertAssets.issueDomainWithoutCritical';
    default:
      return 'alertAssets.issueUnknown';
  }
};

export const AlertRuleAssetList: React.FC = () => {
  const { t } = useLang();
  const { message } = App.useApp();
  const [assets, setAssets] = useState<AlertRuleAssetInfo[]>([]);
  const [searchText, setSearchText] = useState('');
  const [selectedSeverities, setSelectedSeverities] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [viewing, setViewing] = useState<AlertRuleAssetInfo | null>(null);
  const [viewContent, setViewContent] = useState('');
  const [viewLoading, setViewLoading] = useState(false);
  const mountedRef = useRef(true);
  const listRequestId = useRef(0);
  const viewRequestId = useRef(0);
  const exportingNamesRef = useRef<Set<string>>(new Set());
  const [exportingNames, setExportingNames] = useState<Set<string>>(() => new Set());

  const severityOptions = useMemo(
    () =>
      Array.from(new Set(assets.flatMap((asset) => asset.severities || [])))
        .sort((a, b) => a.localeCompare(b))
        .map((severity) => ({ label: severity.toUpperCase(), value: severity })),
    [assets],
  );

  const filteredAssets = useMemo(() => {
    const normalizedSearch = searchText.trim().toLowerCase();
    return assets.filter((asset) => {
      const matchesSearch =
        !normalizedSearch ||
        [asset.name, asset.group]
          .filter(Boolean)
          .some((value) => value.toLowerCase().includes(normalizedSearch));
      const matchesSeverity =
        selectedSeverities.length === 0 ||
        selectedSeverities.some((severity) => (asset.severities || []).includes(severity));

      return matchesSearch && matchesSeverity;
    });
  }, [assets, searchText, selectedSeverities]);

  const catalogInsights = useMemo(() => buildAlertRuleAssetInsights(assets), [assets]);
  const visibleIssueTexts = catalogInsights.issues.slice(0, 4).map((issue) =>
    t(issueTextKey(issue), {
      asset: issue.assetName || '-',
      domain: issue.domain ? t(domainTextKey(issue.domain)) : '-',
      value: issue.value ?? '-',
    }),
  );

  const loadAssets = useCallback(async () => {
    const requestId = ++listRequestId.current;
    setLoading(true);
    setLoadError(false);
    try {
      const data = await listAlertRuleAssets();
      if (mountedRef.current && requestId === listRequestId.current) {
        setAssets(data);
      }
    } catch {
      if (mountedRef.current && requestId === listRequestId.current) {
        setLoadError(true);
        message.error(t('alertAssets.loadFailed'));
      }
    } finally {
      if (mountedRef.current && requestId === listRequestId.current) {
        setLoading(false);
      }
    }
  }, [message, t]);

  useEffect(() => {
    mountedRef.current = true;
    const timeoutId = window.setTimeout(() => void loadAssets());
    return () => {
      window.clearTimeout(timeoutId);
      mountedRef.current = false;
    };
  }, [loadAssets]);

  const handleView = async (info: AlertRuleAssetInfo) => {
    const requestId = ++viewRequestId.current;
    setViewing(info);
    setViewContent('');
    setViewLoading(true);
    try {
      const yaml = await getAlertRuleAsset(info.name);
      if (mountedRef.current && requestId === viewRequestId.current) {
        setViewContent(yaml);
      }
    } catch {
      if (mountedRef.current && requestId === viewRequestId.current) {
        message.error(t('alertAssets.loadFailed'));
      }
    } finally {
      if (mountedRef.current && requestId === viewRequestId.current) {
        setViewLoading(false);
      }
    }
  };

  const closeView = () => {
    viewRequestId.current += 1;
    setViewing(null);
    setViewContent('');
    setViewLoading(false);
  };

  const handleExport = async (info: AlertRuleAssetInfo) => {
    if (exportingNamesRef.current.has(info.name)) return;
    exportingNamesRef.current.add(info.name);
    setExportingNames(new Set(exportingNamesRef.current));
    try {
      const blob = await exportAlertRuleAsset(info.name);
      downloadBlob(blob, `${info.name}.yaml`);
      message.success(t('alertAssets.exported'));
    } catch {
      message.error(t('alertAssets.exportFailed'));
    } finally {
      exportingNamesRef.current.delete(info.name);
      if (mountedRef.current) setExportingNames(new Set(exportingNamesRef.current));
    }
  };

  const handleExportCatalog = () => {
    downloadCsv(
      buildAlertRuleAssetCatalogFilename(),
      buildAlertRuleAssetCatalogCsv(filteredAssets),
    );
    message.success(t('alertAssets.catalogExported'));
  };

  const renderSeverityTags = (severities: string[]) => (
    <Space size={[0, 4]} wrap>
      {(severities || []).map((severity) => (
        <Tag key={severity} color={SEVERITY_COLORS[severity] || 'default'}>
          {severity.toUpperCase()}
        </Tag>
      ))}
    </Space>
  );

  const columns: ColumnsType<AlertRuleAssetInfo> = [
    {
      title: t('alertAssets.name'),
      dataIndex: 'name',
      key: 'name',
      minWidth: 220,
      ellipsis: true,
    },
    {
      title: t('alertAssets.group'),
      dataIndex: 'group',
      key: 'group',
      width: 180,
      ellipsis: true,
      render: (group: string) => <Tag color="blue">{group}</Tag>,
    },
    {
      title: t('alertAssets.ruleCount'),
      dataIndex: 'ruleCount',
      key: 'ruleCount',
      width: 110,
    },
    {
      title: t('alertAssets.severity'),
      dataIndex: 'severities',
      key: 'severities',
      width: 180,
      render: renderSeverityTags,
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 180,
      render: (_: unknown, record: AlertRuleAssetInfo) => (
        <Space size="small">
          <Button size="small" icon={<Eye size={16} />} onClick={() => handleView(record)}>
            {t('common.view')}
          </Button>
          <Button
            size="small"
            icon={<DownloadSimple size={16} />}
            loading={exportingNames.has(record.name)}
            onClick={() => handleExport(record)}
          >
            {t('common.export')}
          </Button>
        </Space>
      ),
    },
  ];

  const domainColumns: ColumnsType<AlertRuleAssetDomainInsight> = [
    {
      title: t('alertAssets.domain'),
      dataIndex: 'domain',
      key: 'domain',
      minWidth: 140,
      render: (domain: AlertRuleAssetDomain) => (
        <Tag color={DOMAIN_COLORS[domain]}>{t(domainTextKey(domain))}</Tag>
      ),
    },
    {
      title: t('alertAssets.assetCount'),
      dataIndex: 'assetCount',
      key: 'assetCount',
      width: 110,
    },
    {
      title: t('alertAssets.ruleCount'),
      dataIndex: 'ruleCount',
      key: 'ruleCount',
      width: 110,
    },
    {
      title: t('alertAssets.coverageShare'),
      dataIndex: 'coveragePercent',
      key: 'coveragePercent',
      width: 120,
      render: (value: number) => formatAlertRuleAssetPercent(value),
    },
    {
      title: t('alertAssets.criticalAssets'),
      dataIndex: 'criticalAssetCount',
      key: 'criticalAssetCount',
      width: 130,
    },
    {
      title: t('alertAssets.groups'),
      dataIndex: 'groups',
      key: 'groups',
      width: 220,
      ellipsis: true,
      render: (groups: string[]) => (groups.length === 0 ? '-' : groups.join(', ')),
    },
  ];

  const summaryCards = [
    {
      key: 'assets',
      title: t('alertAssets.assetCount'),
      value: catalogInsights.totalAssets,
      detail: t('alertAssets.visibleAssets', { n: filteredAssets.length }),
    },
    {
      key: 'rules',
      title: t('alertAssets.totalRules'),
      value: catalogInsights.totalRules,
      detail: t('alertAssets.groupCount', { n: catalogInsights.groupCount }),
    },
    {
      key: 'domains',
      title: t('alertAssets.domainCoverage'),
      value: `${catalogInsights.coveredExpectedDomainCount}/${catalogInsights.expectedDomainCount}`,
      detail: formatAlertRuleAssetPercent(catalogInsights.coveragePercent),
    },
    {
      key: 'score',
      title: t('alertAssets.readinessScore'),
      value: catalogInsights.score,
      suffix: '/100',
      detail: t(`alertAssets.level.${catalogInsights.level}`),
    },
  ];
  const showCatalogInsights = assets.length > 0 || (!loading && !loadError);

  return (
    <div>
      <Space style={{ marginBottom: 12 }}>
        <Input.Search
          allowClear
          placeholder={t('alertAssets.searchPlaceholder')}
          value={searchText}
          onChange={(event) => setSearchText(event.target.value)}
          onSearch={setSearchText}
          style={{ width: 280 }}
        />
        <Select
          allowClear
          mode="multiple"
          maxTagCount="responsive"
          options={severityOptions}
          placeholder={t('alertAssets.allSeverities')}
          value={selectedSeverities}
          onChange={setSelectedSeverities}
          style={{ minWidth: 220 }}
        />
        <Button
          icon={<DownloadSimple size={16} />}
          disabled={loading || filteredAssets.length === 0}
          onClick={handleExportCatalog}
        >
          {t('alertAssets.exportCatalog')}
        </Button>
      </Space>

      {loadError && (
        <Alert
          showIcon
          type="error"
          message={t('alertAssets.loadFailed')}
          action={
            <Button
              size="small"
              icon={<ArrowClockwise size={16} />}
              onClick={() => void loadAssets()}
            >
              {t('common.retry')}
            </Button>
          }
          style={{ marginBottom: 12 }}
        />
      )}

      {showCatalogInsights ? (
        <Card
          size="small"
          title={t('alertAssets.catalogInsights')}
          extra={
            <Tag color={HEALTH_COLORS[catalogInsights.level]}>
              {t(`alertAssets.level.${catalogInsights.level}`)}
            </Tag>
          }
          style={{ marginBottom: 12 }}
        >
          <Row gutter={[12, 12]} style={{ marginBottom: 12 }}>
            {summaryCards.map((card) => (
              <Col xs={12} lg={6} key={card.key}>
                <div
                  style={{
                    border: '1px solid #f0f0f0',
                    borderRadius: 6,
                    padding: 12,
                    minHeight: 92,
                    background: '#fafafa',
                  }}
                >
                  <Statistic title={card.title} value={card.value} suffix={card.suffix} />
                  <Text type="secondary" ellipsis={{ tooltip: card.detail }}>
                    {card.detail}
                  </Text>
                </div>
              </Col>
            ))}
          </Row>

          {visibleIssueTexts.length > 0 ? (
            <Alert
              showIcon
              type={
                catalogInsights.level === 'critical'
                  ? 'error'
                  : catalogInsights.level === 'warning'
                    ? 'warning'
                    : 'info'
              }
              message={`${t('alertAssets.findings')}: ${visibleIssueTexts.join('; ')}`}
              style={{ marginBottom: 12 }}
            />
          ) : null}

          <Table
            columns={domainColumns}
            dataSource={catalogInsights.domainRows}
            rowKey="domain"
            pagination={false}
            size="small"
            tableLayout="fixed"
            scroll={{ x: tableScrollX(domainColumns) }}
          />
        </Card>
      ) : null}

      <Table
        columns={columns}
        dataSource={filteredAssets}
        loading={loading}
        rowKey="name"
        pagination={false}
        size="small"
        tableLayout="fixed"
        scroll={{ x: tableScrollX(columns) }}
      />

      <Modal
        title={viewing ? viewing.name : t('alertAssets.title')}
        open={viewing !== null}
        footer={<Button onClick={closeView}>{t('common.close')}</Button>}
        onCancel={closeView}
        width={760}
        destroyOnHidden
      >
        {viewLoading ? (
          <Text type="secondary">{t('common.loading')}</Text>
        ) : (
          <pre
            style={{
              maxHeight: 480,
              overflow: 'auto',
              background: '#f5f5f5',
              padding: 16,
              borderRadius: 6,
              fontSize: 14,
            }}
          >
            {viewContent}
          </pre>
        )}
      </Modal>
    </div>
  );
};

export default AlertRuleAssetList;
