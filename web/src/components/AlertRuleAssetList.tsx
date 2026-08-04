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
import { App, Button, Modal, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DownloadSimple, Eye } from '@phosphor-icons/react';
import { useLang } from '../i18n/LangContext';
import {
  exportAlertRuleAsset,
  getAlertRuleAsset,
  listAlertRuleAssets,
} from '../services/alertRuleAssetService';
import type { AlertRuleAssetInfo } from '../api/alertRuleAssets';

const { Text } = Typography;

const SEVERITY_COLORS: Record<string, string> = {
  critical: 'red',
  warning: 'orange',
  info: 'blue',
};

export const AlertRuleAssetList: React.FC = () => {
  const { t } = useLang();
  const { message } = App.useApp();
  const [assets, setAssets] = useState<AlertRuleAssetInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [viewing, setViewing] = useState<AlertRuleAssetInfo | null>(null);
  const [viewContent, setViewContent] = useState('');
  const [viewLoading, setViewLoading] = useState(false);
  const [exportingName, setExportingName] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const data = await listAlertRuleAssets();
        if (!cancelled) setAssets(data);
      } catch {
        if (!cancelled) message.error(t('alertAssets.loadFailed'));
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [t, message]);

  const handleView = async (info: AlertRuleAssetInfo) => {
    setViewing(info);
    setViewLoading(true);
    try {
      const yaml = await getAlertRuleAsset(info.name);
      setViewContent(yaml);
    } catch {
      message.error(t('alertAssets.loadFailed'));
    } finally {
      setViewLoading(false);
    }
  };

  const triggerDownload = (name: string, content: Blob | string) => {
    const blob = typeof content === 'string' ? new Blob([content], { type: 'text/yaml' }) : content;
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${name}.yaml`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleExport = async (info: AlertRuleAssetInfo) => {
    setExportingName(info.name);
    try {
      const blob = await exportAlertRuleAsset(info.name);
      triggerDownload(info.name, blob);
      message.success(t('alertAssets.exported'));
    } catch {
      message.error(t('alertAssets.exportFailed'));
    } finally {
      setExportingName(null);
    }
  };

  const columns: ColumnsType<AlertRuleAssetInfo> = [
    {
      title: t('alertAssets.name'),
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: t('alertAssets.group'),
      dataIndex: 'group',
      key: 'group',
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
      render: (severities: string[]) => (
        <Space size={[0, 4]} wrap>
          {(severities || []).map((severity) => (
            <Tag key={severity} color={SEVERITY_COLORS[severity] || 'default'}>
              {severity.toUpperCase()}
            </Tag>
          ))}
        </Space>
      ),
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
            loading={exportingName === record.name}
            onClick={() => handleExport(record)}
          >
            {t('common.export')}
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Table
        columns={columns}
        dataSource={assets}
        loading={loading}
        rowKey="name"
        pagination={false}
        size="small"
      />

      <Modal
        title={viewing ? viewing.name : t('alertAssets.title')}
        open={viewing !== null}
        footer={<Button onClick={() => setViewing(null)}>{t('common.close')}</Button>}
        onCancel={() => setViewing(null)}
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
              fontSize: 12,
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
