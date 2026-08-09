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
  getGrafanaDashboard,
  exportGrafanaDashboard,
  listGrafanaDashboards,
} from '../services/grafanaService';
import type { GrafanaDashboardInfo } from '../api/metrics';

const { Paragraph, Text } = Typography;

export const GrafanaDashboardList: React.FC = () => {
  const { t } = useLang();
  const { message } = App.useApp();
  const [dashboards, setDashboards] = useState<GrafanaDashboardInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [viewing, setViewing] = useState<GrafanaDashboardInfo | null>(null);
  const [viewContent, setViewContent] = useState('');
  const [viewLoading, setViewLoading] = useState(false);
  const [exportingUids, setExportingUids] = useState<Set<string>>(() => new Set());

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const data = await listGrafanaDashboards();
        if (!cancelled) setDashboards(data);
      } catch {
        if (!cancelled) message.error(t('grafana.loadFailed'));
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [t, message]);

  const handleView = async (info: GrafanaDashboardInfo) => {
    setViewing(info);
    setViewLoading(true);
    try {
      const model = await getGrafanaDashboard(info.uid);
      setViewContent(JSON.stringify(model, null, 2));
    } catch {
      message.error(t('grafana.loadFailed'));
    } finally {
      setViewLoading(false);
    }
  };

  const triggerDownload = (uid: string, content: Blob | string) => {
    const blob =
      typeof content === 'string' ? new Blob([content], { type: 'application/json' }) : content;
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${uid}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleExport = async (info: GrafanaDashboardInfo) => {
    setExportingUids((current) => new Set(current).add(info.uid));
    try {
      const blob = await exportGrafanaDashboard(info.uid);
      triggerDownload(info.uid, blob);
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
      <Table
        columns={columns}
        dataSource={dashboards}
        loading={loading}
        rowKey="uid"
        pagination={false}
        size="small"
      />

      <Modal
        title={viewing ? viewing.title : t('grafana.title')}
        open={viewing !== null}
        footer={<Button onClick={() => setViewing(null)}>{t('common.close')}</Button>}
        onCancel={() => setViewing(null)}
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
                fontSize: 12,
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
