/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ReloadOutlined } from '@ant-design/icons';
import { useLang } from '../i18n/LangContext';
import {
  type LlmCatalogRow,
  type LlmModelReadinessReport,
  type LlmReadinessIssue,
  type LlmReadinessSeverity,
} from '../utils/llmModelReadiness';

interface Props {
  open: boolean;
  loading: boolean;
  report?: LlmModelReadinessReport;
  onRefresh: () => void;
  onClose: () => void;
}
const issueColors: Record<LlmReadinessSeverity, string> = {
  INFO: 'blue',
  WARNING: 'gold',
  ERROR: 'red',
};
const sourceColors = {
  provider: 'green',
  builtin: 'blue',
  fallback: 'gold',
  unknown: 'default',
} as const;

export const LlmModelReadinessDrawer = ({ open, loading, report, onRefresh, onClose }: Props) => {
  const { t } = useLang();

  const issueColumns: ColumnsType<LlmReadinessIssue> = [
    {
      title: t('llmReadiness.severity'),
      dataIndex: 'severity',
      key: 'severity',
      width: 100,
      render: (value: LlmReadinessSeverity) => (
        <Tag color={issueColors[value]}>{t(`llmReadiness.${value}`)}</Tag>
      ),
    },
    {
      title: t('llmReadiness.issue'),
      dataIndex: 'code',
      key: 'code',
      width: 240,
      render: (value: string) => t(`llmReadiness.issue.${value}`),
    },
    {
      title: t('llmReadiness.detail'),
      dataIndex: 'detail',
      key: 'detail',
      render: (value: string) => value || '-',
    },
  ];
  const modelColumns: ColumnsType<LlmCatalogRow> = [
    {
      title: t('llmReadiness.modelId'),
      dataIndex: 'id',
      key: 'id',
      render: (value: string, row) => (
        <Space>
          <Typography.Text code>{value}</Typography.Text>
          {row.selected && <Tag color="green">{t('llmReadiness.selected')}</Tag>}
        </Space>
      ),
    },
    { title: t('llmReadiness.modelName'), dataIndex: 'name', key: 'name' },
  ];
  const cards = [
    ['llmReadiness.models', report?.summary.models ?? 0],
    ['llmReadiness.errors', report?.summary.errors ?? 0],
    ['llmReadiness.warnings', report?.summary.warnings ?? 0],
  ] as const;

  return (
    <Drawer
      title={t('llmReadiness.title')}
      open={open}
      onClose={onClose}
      width={980}
      destroyOnHidden
      extra={
        <Button icon={<ReloadOutlined />} loading={loading} onClick={onRefresh}>
          {t('common.refresh')}
        </Button>
      }
    >
      {report && (
        <Alert
          showIcon
          type={report.summary.ready ? 'success' : 'error'}
          message={report.summary.ready ? t('llmReadiness.ready') : t('llmReadiness.notReady')}
          description={t('llmReadiness.description')}
          style={{ marginBottom: 16 }}
        />
      )}
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        {cards.map(([key, value]) => (
          <Col xs={8} key={key}>
            <Card size="small" loading={loading && !report}>
              <Statistic title={t(key)} value={value} />
            </Card>
          </Col>
        ))}
      </Row>
      {report && (
        <>
          <Descriptions bordered size="small" column={2} style={{ marginBottom: 16 }}>
            <Descriptions.Item label={t('llmReadiness.provider')}>
              {report.provider}
            </Descriptions.Item>
            <Descriptions.Item label={t('llmReadiness.engine')}>{report.engine}</Descriptions.Item>
            <Descriptions.Item label={t('llmReadiness.selectedModel')}>
              {report.selectedModel}
            </Descriptions.Item>
            <Descriptions.Item label={t('llmReadiness.source')}>
              <Tag color={sourceColors[report.source]}>
                {t(`llmReadiness.source.${report.source}`)}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t('llmReadiness.endpoint')}>
              {report.endpointHost}
            </Descriptions.Item>
            <Descriptions.Item label={t('llmReadiness.credential')}>
              {report.apiKeyConfigured ? t('common.yes') : t('common.no')}
            </Descriptions.Item>
            <Descriptions.Item label={t('llmReadiness.enabled')}>
              {report.enabled ? t('common.yes') : t('common.no')}
            </Descriptions.Item>
            <Descriptions.Item label={t('llmReadiness.serverReady')}>
              {report.serverReady === null
                ? '-'
                : report.serverReady
                  ? t('common.yes')
                  : t('common.no')}
            </Descriptions.Item>
          </Descriptions>
          <Typography.Title level={5}>{t('llmReadiness.issues')}</Typography.Title>
          <Table
            rowKey={(row) => `${row.code}-${row.detail}`}
            columns={issueColumns}
            dataSource={report.issues}
            pagination={false}
            size="small"
            locale={{ emptyText: t('llmReadiness.noIssues') }}
            style={{ marginBottom: 20 }}
          />
          <Typography.Title level={5}>{t('llmReadiness.catalog')}</Typography.Title>
          <Table
            rowKey="id"
            columns={modelColumns}
            dataSource={report.catalog}
            size="small"
            pagination={{ pageSize: 20 }}
            locale={{ emptyText: t('llmReadiness.emptyCatalog') }}
          />
        </>
      )}
    </Drawer>
  );
};

export default LlmModelReadinessDrawer;
