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
  getGrafanaDashboard,
  exportGrafanaDashboard,
  exportGrafanaDashboards,
  listGrafanaDashboards,
} from '../services/grafanaService';
import type { GrafanaDashboardInfo } from '../api/metrics';
import { downloadBlob } from '../utils/download';

const { Paragraph, Text } = Typography;

export const GrafanaDashboardList: React.FC = () => {
  const { t } = useLang();
  const { message } = App.useApp();
  const [dashboards, setDashboards] = useState<GrafanaDashboardInfo[]>([]);
  const [searchText, setSearchText] = useState('');
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [viewing, setViewing] = useState<GrafanaDashboardInfo | null>(null);
  const [viewContent, setViewContent] = useState('');
  const [viewLoading, setViewLoading] = useState(false);
  const mountedRef = useRef(true);
  const listRequestId = useRef(0);
  const viewRequestId = useRef(0);
  const [exportingUids, setExportingUids] = useState<Set<string>>(() => new Set());
  const [exportingAll, setExportingAll] = useState(false);

  const tagOptions = useMemo(
    () =>
      Array.from(new Set(dashboards.flatMap((dashboard) => dashboard.tags || [])))
        .sort((a, b) => a.localeCompare(b))
        .map((tag) => ({ label: tag, value: tag })),
    [dashboards],
  );

  const filteredDashboards = useMemo(() => {
    const normalizedSearch = searchText.trim().toLowerCase();
    return dashboards.filter((dashboard) => {
      const matchesSearch =
        !normalizedSearch ||
        [dashboard.uid, dashboard.title, dashboard.description, ...(dashboard.tags || [])]
          .filter(Boolean)
          .some((value) => value.toLowerCase().includes(normalizedSearch));
      const matchesTags =
        selectedTags.length === 0 ||
        selectedTags.every((tag) => (dashboard.tags || []).includes(tag));

      return matchesSearch && matchesTags;
    });
  }, [dashboards, searchText, selectedTags]);

  const loadDashboards = useCallback(async () => {
    const requestId = ++listRequestId.current;
    setLoading(true);
    setLoadError(false);
    try {
      const data = await listGrafanaDashboards();
      if (mountedRef.current && requestId === listRequestId.current) {
        setDashboards(data);
      }
    } catch {
      if (mountedRef.current && requestId === listRequestId.current) {
        setLoadError(true);
        message.error(t('grafana.loadFailed'));
      }
    } finally {
      if (mountedRef.current && requestId === listRequestId.current) {
        setLoading(false);
      }
    }
  }, [message, t]);

  useEffect(() => {
    mountedRef.current = true;
    const timeoutId = window.setTimeout(() => void loadDashboards());
    return () => {
      window.clearTimeout(timeoutId);
      mountedRef.current = false;
    };
  }, [loadDashboards]);

  const handleView = async (info: GrafanaDashboardInfo) => {
    const requestId = ++viewRequestId.current;
    setViewing(info);
    setViewContent('');
    setViewLoading(true);
    try {
      const model = await getGrafanaDashboard(info.uid);
      if (mountedRef.current && requestId === viewRequestId.current) {
        setViewContent(JSON.stringify(model, null, 2));
      }
    } catch {
      if (mountedRef.current && requestId === viewRequestId.current) {
        message.error(t('grafana.loadFailed'));
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

  const handleExport = async (info: GrafanaDashboardInfo) => {
    setExportingUids((current) => new Set(current).add(info.uid));
    try {
      const blob = await exportGrafanaDashboard(info.uid);
      downloadBlob(blob, `${info.uid}.json`);
      message.success(t('grafana.exported'));
    } catch {
      message.error(t('grafana.exportFailed'));
    } finally {
      setExportingUids((current) => {
        const next = new Set(current);
        next.delete(info.uid);
        return next;
      });
    }
  };

  const handleExportAll = async () => {
    setExportingAll(true);
    try {
      const download = await exportGrafanaDashboards();
      downloadBlob(download.blob, download.filename);
      message.success(t('grafana.exportAllDone'));
    } catch {
      message.error(t('grafana.exportAllFailed'));
    } finally {
      setExportingAll(false);
    }
  };

  const columns: ColumnsType<GrafanaDashboardInfo> = [
    {
      title: t('grafana.title'),
      dataIndex: 'title',
      key: 'title',
    },
    {
      title: t('grafana.description'),
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
    {
      title: t('grafana.tags'),
      dataIndex: 'tags',
      key: 'tags',
      width: 140,
      render: (tags: string[]) => (
        <Space size={[0, 4]} wrap>
          {(tags || []).map((tag) => (
            <Tag key={tag} color="blue">
              {tag}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 180,
      render: (_: unknown, record: GrafanaDashboardInfo) => (
        <Space size="small">
          <Button size="small" icon={<Eye size={16} />} onClick={() => handleView(record)}>
            {t('common.view')}
          </Button>
          <Button
            size="small"
            icon={<DownloadSimple size={16} />}
            loading={exportingUids.has(record.uid)}
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
          placeholder={t('grafana.searchPlaceholder')}
          value={searchText}
          onChange={(event) => setSearchText(event.target.value)}
          onSearch={setSearchText}
          style={{ width: 280 }}
        />
        <Select
          allowClear
          mode="multiple"
          maxTagCount="responsive"
          options={tagOptions}
          placeholder={t('grafana.allTags')}
          value={selectedTags}
          onChange={setSelectedTags}
          style={{ minWidth: 220 }}
        />
        <Button
          icon={<DownloadSimple size={16} />}
          loading={exportingAll}
          disabled={loading || dashboards.length === 0}
          onClick={handleExportAll}
        >
          {t('grafana.exportAll')}
        </Button>
      </Space>

      {loadError && (
        <Alert
          showIcon
          type="error"
          message={t('grafana.loadFailed')}
          action={
            <Button
              size="small"
              icon={<ArrowClockwise size={16} />}
              onClick={() => void loadDashboards()}
            >
              {t('common.retry')}
            </Button>
          }
          style={{ marginBottom: 12 }}
        />
      )}

      <Table
        columns={columns}
        dataSource={filteredDashboards}
        loading={loading}
        rowKey="uid"
        pagination={false}
        size="small"
      />

      <Modal
        title={viewing ? viewing.title : t('grafana.title')}
        open={viewing !== null}
        footer={<Button onClick={closeView}>{t('common.close')}</Button>}
        onCancel={closeView}
        width={760}
        destroyOnHidden
      >
        {viewLoading ? (
          <Text type="secondary">{t('common.loading')}</Text>
        ) : (
          <Paragraph>
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
          </Paragraph>
        )}
      </Modal>
    </div>
  );
};

export default GrafanaDashboardList;
