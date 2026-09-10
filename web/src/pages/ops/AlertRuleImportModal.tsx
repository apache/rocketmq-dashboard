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

import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Flex,
  message,
  Modal,
  Segmented,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type {
  AlertRuleDomain,
  AlertRuleImportConflictStrategy,
  AlertRuleImportPreview,
  AlertRuleImportPreviewItem,
  AlertRuleImportResult,
  AlertRuleTransfer,
} from '../../api/ops';
import { applyAlertRulesImport, previewAlertRulesImport } from '../../services/opsService';
import { useLang } from '../../i18n/LangContext';

interface AlertRuleImportModalProps {
  open: boolean;
  transfer: AlertRuleTransfer | null;
  domain: AlertRuleDomain;
  onClose: () => void;
  onApplied: (result: AlertRuleImportResult) => void;
}

const statusColors: Record<AlertRuleImportPreviewItem['status'], string> = {
  NEW: 'success',
  DUPLICATE: 'warning',
  INVALID: 'error',
};

const AlertRuleImportModal = ({
  open,
  transfer,
  domain,
  onClose,
  onApplied,
}: AlertRuleImportModalProps) => {
  const { t } = useLang();
  const [strategySelection, setStrategySelection] = useState<{
    transfer: AlertRuleTransfer | null;
    value: AlertRuleImportConflictStrategy;
  }>({ transfer: null, value: 'FAIL' });
  const [previewAttempt, setPreviewAttempt] = useState(0);
  const [settledPreview, setSettledPreview] = useState<{
    transfer: AlertRuleTransfer;
    domain: AlertRuleDomain;
    attempt: number;
    preview: AlertRuleImportPreview | null;
    failed: boolean;
  } | null>(null);
  const [applying, setApplying] = useState(false);
  const previewRequestVersion = useRef(0);
  const strategy = strategySelection.transfer === transfer ? strategySelection.value : 'FAIL';
  const previewStateIsCurrent =
    settledPreview?.transfer === transfer &&
    settledPreview.domain === domain &&
    settledPreview.attempt === previewAttempt;
  const preview = previewStateIsCurrent ? settledPreview.preview : null;
  const previewFailed = previewStateIsCurrent ? settledPreview.failed : false;
  const loading = open && transfer !== null && !previewStateIsCurrent;

  useEffect(() => {
    const requestVersion = ++previewRequestVersion.current;
    if (open && transfer) {
      void previewAlertRulesImport(transfer, domain)
        .then((result) => {
          if (requestVersion === previewRequestVersion.current) {
            setSettledPreview({
              transfer,
              domain,
              attempt: previewAttempt,
              preview: result,
              failed: false,
            });
          }
        })
        .catch(() => {
          if (requestVersion === previewRequestVersion.current) {
            setSettledPreview({
              transfer,
              domain,
              attempt: previewAttempt,
              preview: null,
              failed: true,
            });
          }
        });
    }
    return () => {
      previewRequestVersion.current += 1;
    };
  }, [domain, open, previewAttempt, transfer]);

  const columns = useMemo<ColumnsType<AlertRuleImportPreviewItem>>(
    () => [
      {
        title: t('alerts.importRow'),
        dataIndex: 'rowNumber',
        width: 72,
      },
      {
        title: t('alerts.ruleName'),
        dataIndex: 'name',
        render: (value: string | null | undefined) => value || '-',
      },
      {
        title: t('alerts.metric'),
        dataIndex: 'metric',
        render: (value: string | null | undefined) => value || '-',
      },
      {
        title: t('common.status'),
        dataIndex: 'status',
        width: 110,
        render: (status: AlertRuleImportPreviewItem['status']) => (
          <Tag color={statusColors[status]}>{t(`alerts.importStatus${status}`)}</Tag>
        ),
      },
      {
        title: t('alerts.importDetails'),
        render: (_, item) => {
          if (item.status === 'DUPLICATE') {
            return t('alerts.importDuplicateOf', {
              name: item.existingRuleName || String(item.existingRuleId ?? '-'),
            });
          }
          return item.error || '-';
        },
      },
    ],
    [t],
  );

  const applyBlocked =
    !preview || preview.invalidCount > 0 || (strategy === 'FAIL' && preview.duplicateCount > 0);

  const handleApply = async () => {
    if (!transfer || applyBlocked || applying) return;
    setApplying(true);
    try {
      const result = await applyAlertRulesImport(transfer, strategy, domain);
      message.success(
        t('alerts.importApplySuccess', {
          created: result.createdCount,
          replaced: result.replacedCount,
          skipped: result.skippedCount,
        }),
      );
      onApplied(result);
      onClose();
    } catch {
      message.error(t('alerts.importApplyFailed'));
      setPreviewAttempt((current) => current + 1);
    } finally {
      setApplying(false);
    }
  };

  return (
    <Modal
      title={t('alerts.importPreviewTitle')}
      open={open}
      width={880}
      style={{ maxWidth: 'calc(100vw - 32px)' }}
      onCancel={() => {
        if (!applying) onClose();
      }}
      onOk={() => void handleApply()}
      okText={t('alerts.importApply')}
      cancelText={t('common.cancel')}
      confirmLoading={applying}
      okButtonProps={{ disabled: loading || previewFailed || applyBlocked }}
      cancelButtonProps={{ disabled: applying }}
      maskClosable={!applying}
    >
      {loading && (
        <Flex justify="center" align="center" gap={12} style={{ minHeight: 220 }}>
          <Spin />
          <Typography.Text>{t('alerts.importPreviewLoading')}</Typography.Text>
        </Flex>
      )}

      {!loading && previewFailed && (
        <Alert
          type="error"
          showIcon
          message={t('alerts.importPreviewFailed')}
          action={
            <Button size="small" onClick={() => setPreviewAttempt((current) => current + 1)}>
              {t('common.retry')}
            </Button>
          }
        />
      )}

      {!loading && preview && (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Flex gap={8} wrap="wrap">
            <Tag>{t('alerts.importTotalCount', { count: preview.totalCount })}</Tag>
            <Tag color="success">{t('alerts.importNewCount', { count: preview.newCount })}</Tag>
            <Tag color="warning">
              {t('alerts.importDuplicateCount', { count: preview.duplicateCount })}
            </Tag>
            <Tag color="error">
              {t('alerts.importInvalidCount', { count: preview.invalidCount })}
            </Tag>
          </Flex>

          <Table<AlertRuleImportPreviewItem>
            columns={columns}
            dataSource={preview.items}
            rowKey="rowNumber"
            size="small"
            pagination={false}
            scroll={{ x: 680, y: 320 }}
          />

          <Flex align="center" gap={12} wrap="wrap">
            <Typography.Text strong>{t('alerts.importConflictStrategy')}</Typography.Text>
            <Segmented<AlertRuleImportConflictStrategy>
              value={strategy}
              onChange={(value) => setStrategySelection({ transfer, value })}
              disabled={applying}
              options={[
                { label: t('alerts.importStrategyFail'), value: 'FAIL' },
                { label: t('alerts.importStrategySkip'), value: 'SKIP' },
                { label: t('alerts.importStrategyReplace'), value: 'REPLACE' },
              ]}
            />
          </Flex>

          {preview.invalidCount > 0 && (
            <Alert type="error" showIcon message={t('alerts.importInvalidBlocked')} />
          )}
          {preview.invalidCount === 0 && strategy === 'FAIL' && preview.duplicateCount > 0 && (
            <Alert type="warning" showIcon message={t('alerts.importDuplicateBlocked')} />
          )}
        </Space>
      )}
    </Modal>
  );
};

export default AlertRuleImportModal;
