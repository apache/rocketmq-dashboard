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
import { Alert, App, Button, Input, Modal, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ArrowClockwise, DownloadSimple, Eye } from '@phosphor-icons/react';
import { useLang } from '../i18n/LangContext';
import {
  exportAlertRuleAsset,
  getAlertRuleAsset,
  listAlertRuleAssets,
} from '../services/alertRuleAssetService';
import type { AlertRuleAssetInfo } from '../api/alertRuleAssets';
import { downloadBlob } from '../utils/download';

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
            loading={exportingNames.has(record.name)}
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

      <Table
        columns={columns}
        dataSource={filteredAssets}
        loading={loading}
        rowKey="name"
        pagination={false}
        size="small"
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
