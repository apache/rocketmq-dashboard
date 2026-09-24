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

import { useCallback, useEffect, useMemo, useState, type CSSProperties } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Flex,
  Input,
  Modal,
  Popconfirm,
  Row,
  Segmented,
  Space,
  Tag,
  Typography,
  message,
} from 'antd';
import { Plus, Trash } from '@phosphor-icons/react';
import { useLang } from '../../../i18n/LangContext';
import type { ChatMode } from '../chatDraft';
import {
  deleteCustomPromptTemplate,
  filterPromptTemplates,
  loadPromptTemplateCatalog,
  saveCustomPromptTemplate,
  type PromptTemplate,
  type PromptTemplateApplyMode,
} from '../promptTemplates';

/**
 * The prompt template catalog: built-in runbooks plus the operator's own saved prompts.
 *
 * Templates are the cheapest way to make the agent useful to somebody who does not know what to ask
 * it — a 消费延迟诊断 template carries the mode and the enhancement flag along with the wording, so
 * applying one sets all three.
 *
 * Storage stays where it was (`promptTemplates.ts`, `localStorage`): a template is a personal
 * snippet, not conversation history, and it is the one AI artefact that legitimately outlives a
 * session. Custom templates therefore survive the move of history to the server untouched.
 *
 * ─── Expand / collapse ─────────────────────────────────────────
 * A template body is clamped to two lines by default and expands on demand. Long runbook prompts
 * (the ACL risk checklist is ~40 lines) otherwise push every other card out of the modal, and a
 * scrollable grid of cards nobody can compare is worse than a clamped one. Exactly one template is
 * expanded at a time — {@link expandedTemplateId} — so reading a long body never means scrolling
 * past five other expanded ones.
 */

export interface PromptTemplateModalProps {
  open: boolean;
  onClose: () => void;
  /** Composer draft: the body of a template saved from it, and the target of an `append`. */
  inputValue: string;
  /** Current chat mode; recorded on a template saved from the current draft. */
  mode: ChatMode;
  /** Current enhancement flag; recorded on a template saved from the current draft. */
  enhance: boolean;
  /**
   * Apply a template to the composer. The caller owns the draft, the mode and the enhancement flag,
   * and closes the modal — applying is a navigation of focus back to the textarea, which only the
   * page can do.
   */
  onApply: (template: PromptTemplate, applyMode: PromptTemplateApplyMode) => void;
}

const PromptTemplateModal = ({
  open,
  onClose,
  inputValue,
  mode,
  enhance,
  onApply,
}: PromptTemplateModalProps) => {
  const { t } = useLang();
  const [templates, setTemplates] = useState<PromptTemplate[]>([]);
  const [storageAvailable, setStorageAvailable] = useState(true);
  const [search, setSearch] = useState('');
  const [modeFilter, setModeFilter] = useState<ChatMode | 'all'>('all');
  const [expandedTemplateId, setExpandedTemplateId] = useState<string | null>(null);
  const [customTemplateTitle, setCustomTemplateTitle] = useState('');
  const [customTemplateTags, setCustomTemplateTags] = useState('');

  const chatModeOptions = useMemo<Array<{ value: ChatMode; label: string }>>(
    () => [
      { value: 'chat', label: t('ai.mode.chat') },
      { value: 'diagnose', label: t('ai.mode.diagnose') },
      { value: 'manage', label: t('ai.mode.manage') },
      { value: 'query', label: t('ai.mode.query') },
    ],
    [t],
  );

  const filterOptions = useMemo<Array<{ value: ChatMode | 'all'; label: string }>>(
    () => [{ value: 'all', label: t('common.all') }, ...chatModeOptions],
    [chatModeOptions, t],
  );

  const chatModeLabels = useMemo(
    () => Object.fromEntries(chatModeOptions.map((item) => [item.value, item.label])),
    [chatModeOptions],
  );

  const visibleTemplates = useMemo(
    () => filterPromptTemplates(templates, search, modeFilter),
    [modeFilter, search, templates],
  );

  const refreshPromptTemplates = useCallback(() => {
    const catalog = loadPromptTemplateCatalog(undefined, t);
    setTemplates(catalog.templates);
    setStorageAvailable(catalog.storageAvailable);
  }, [t]);

  // The catalog is read from localStorage, so it is refreshed on every open: a template saved in
  // another tab (or by the previous open of this modal) has to show up.
  useEffect(() => {
    if (!open) return;
    // Synchronous on purpose: the catalog is a localStorage read, not I/O, and it has to be in place
    // for the very first paint of the open modal rather than a frame later.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    refreshPromptTemplates();
    setExpandedTemplateId(null);
  }, [open, refreshPromptTemplates]);

  const handleSaveCurrentPromptTemplate = useCallback(() => {
    const result = saveCustomPromptTemplate({
      title: customTemplateTitle,
      tags: customTemplateTags,
      mode,
      enhance,
      body: inputValue,
    });
    if (!result.ok) {
      const errorMessage =
        result.reason === 'empty_title'
          ? t('ai.promptTemplates.titleRequired')
          : result.reason === 'empty_body'
            ? t('ai.promptTemplates.bodyRequired')
            : t('ai.promptTemplates.storageSaveFailed');
      message.error(errorMessage);
      return;
    }
    message.success(t('ai.promptTemplates.saved'));
    setCustomTemplateTitle('');
    setCustomTemplateTags('');
    refreshPromptTemplates();
  }, [
    customTemplateTags,
    customTemplateTitle,
    enhance,
    inputValue,
    mode,
    refreshPromptTemplates,
    t,
  ]);

  const handleDeletePromptTemplate = useCallback(
    (template: PromptTemplate) => {
      if (template.scope !== 'custom') return;
      if (!deleteCustomPromptTemplate(template.id)) {
        message.error(t('ai.promptTemplates.storageDeleteFailed'));
        return;
      }
      message.success(t('ai.promptTemplates.deleted'));
      refreshPromptTemplates();
    },
    [refreshPromptTemplates, t],
  );

  return (
    <Modal
      title={t('ai.promptTemplates.title')}
      open={open}
      onCancel={onClose}
      footer={null}
      width={1080}
      styles={{
        // maxHeight only guards small viewports; at normal heights the three-column grid keeps the
        // whole catalog inside the body, so no vertical scrollbar shows by default.
        body: { maxHeight: 'calc(100vh - 240px)', overflowY: 'auto', overflowX: 'hidden' },
      }}
    >
      <Flex vertical gap={16} style={{ paddingTop: 8 }}>
        {!storageAvailable && (
          <Alert type="warning" showIcon message={t('ai.promptTemplates.storageUnavailable')} />
        )}
        <Flex gap={12} wrap="wrap" align="center">
          <Input.Search
            aria-label={t('ai.promptTemplates.searchAria')}
            allowClear
            placeholder={t('ai.promptTemplates.searchPlaceholder')}
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            style={{ flex: '1 1 260px' }}
          />
          <Segmented
            value={modeFilter}
            options={filterOptions}
            onChange={(value) => setModeFilter(value as ChatMode | 'all')}
          />
        </Flex>

        {visibleTemplates.length === 0 ? (
          <Empty description={t('ai.promptTemplates.empty')} style={{ margin: '24px 0' }} />
        ) : (
          <Row gutter={[12, 12]}>
            {visibleTemplates.map((template) => (
              <Col key={template.id} xs={24} md={12} lg={8}>
                <Card
                  size="small"
                  style={{ height: '100%', borderRadius: 8 }}
                  styles={{ body: { padding: 12 } }}
                >
                  <Flex vertical gap={8} style={{ height: '100%' }}>
                    <Flex align="center" gap={8} wrap="wrap">
                      <Typography.Text strong style={{ fontSize: 14 }}>
                        {template.title}
                      </Typography.Text>
                      <Tag style={{ marginInlineEnd: 0, fontSize: 14 }}>
                        {chatModeLabels[template.mode]}
                      </Tag>
                      {template.enhance && (
                        <Tag color="purple" style={{ marginInlineEnd: 0, fontSize: 14 }}>
                          {t('ai.promptEnhance')}
                        </Tag>
                      )}
                      {template.scope === 'custom' && (
                        <Tag color="blue" style={{ marginInlineEnd: 0, fontSize: 14 }}>
                          {t('ai.promptTemplates.custom')}
                        </Tag>
                      )}
                    </Flex>
                    {template.description && (
                      <Typography.Text type="secondary" style={{ fontSize: 14 }}>
                        {template.description}
                      </Typography.Text>
                    )}
                    {/* 正文默认两行 CSS 截断，显式展开/收起，避免长模板撑爆弹窗 */}
                    {(() => {
                      const expanded = expandedTemplateId === template.id;
                      const bodyStyle: CSSProperties = expanded
                        ? { whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }
                        : {
                            display: '-webkit-box',
                            WebkitBoxOrient: 'vertical',
                            WebkitLineClamp: 2,
                            overflow: 'hidden',
                            whiteSpace: 'pre-wrap',
                            overflowWrap: 'anywhere',
                          };
                      return (
                        <div>
                          <div
                            style={bodyStyle}
                            data-testid={`ai-prompt-template-body-${template.id}`}
                          >
                            {template.body}
                          </div>
                          <Button
                            type="link"
                            size="small"
                            style={{ padding: 0, height: 'auto', fontSize: 14 }}
                            onClick={() => setExpandedTemplateId(expanded ? null : template.id)}
                          >
                            {expanded
                              ? t('ai.promptTemplates.collapse')
                              : t('ai.promptTemplates.expand')}
                          </Button>
                        </div>
                      );
                    })()}
                    <Flex
                      justify="space-between"
                      align="center"
                      gap={8}
                      style={{ marginTop: 'auto' }}
                    >
                      <Space size={4} wrap>
                        {template.tags.slice(0, 3).map((tag) => (
                          <Tag key={tag} style={{ marginInlineEnd: 0, fontSize: 14 }}>
                            {tag}
                          </Tag>
                        ))}
                      </Space>
                      <Space size={4}>
                        <Button
                          size="small"
                          type="primary"
                          onClick={() => onApply(template, 'replace')}
                        >
                          {t('ai.promptTemplates.use')}
                        </Button>
                        <Button size="small" onClick={() => onApply(template, 'append')}>
                          {t('ai.promptTemplates.append')}
                        </Button>
                        {template.scope === 'custom' && (
                          <Popconfirm
                            title={t('ai.promptTemplates.deleteConfirm')}
                            okText={t('common.delete')}
                            cancelText={t('common.cancel')}
                            onConfirm={() => handleDeletePromptTemplate(template)}
                          >
                            <Button
                              danger
                              type="text"
                              size="small"
                              aria-label={t('ai.promptTemplates.deleteAria', {
                                title: template.title,
                              })}
                              icon={<Trash size={16} />}
                            />
                          </Popconfirm>
                        )}
                      </Space>
                    </Flex>
                  </Flex>
                </Card>
              </Col>
            ))}
          </Row>
        )}

        {/* 保存当前输入是次要操作，下沉到弹窗底部并弱化呈现 */}
        <div
          style={{
            background: '#fafafa',
            border: '1px solid #f0f0f0',
            borderRadius: 8,
            padding: 12,
          }}
        >
          <Typography.Text strong style={{ fontSize: 14 }}>
            {t('ai.promptTemplates.saveCurrent')}
          </Typography.Text>
          <Flex gap={8} wrap="wrap" style={{ marginTop: 8 }}>
            <Input
              aria-label={t('ai.promptTemplates.titleInput')}
              placeholder={t('ai.promptTemplates.titleInput')}
              value={customTemplateTitle}
              onChange={(event) => setCustomTemplateTitle(event.target.value)}
              style={{ flex: '1 1 220px' }}
            />
            <Input
              aria-label={t('ai.promptTemplates.tagsInput')}
              placeholder={t('ai.promptTemplates.tagsPlaceholder')}
              value={customTemplateTags}
              onChange={(event) => setCustomTemplateTags(event.target.value)}
              style={{ flex: '1 1 220px' }}
            />
            <Button
              icon={<Plus size={16} />}
              disabled={!inputValue.trim()}
              onClick={handleSaveCurrentPromptTemplate}
            >
              {t('common.save')}
            </Button>
          </Flex>
        </div>
      </Flex>
    </Modal>
  );
};

export default PromptTemplateModal;
