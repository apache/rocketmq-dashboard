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

import { useCallback, useEffect, useRef, useState } from 'react';
import { Flex, Input, Modal, Select, Space, Tag, Typography, message, theme } from 'antd';
import { useLang } from '../../../i18n/LangContext';
import { executeTool, listTools, type McpTool } from '../../../api/ai';
import { listClusters } from '../../../api/cluster';
import InfoBanner from '../../../components/InfoBanner';

/**
 * The manual tool playground: pick a cluster scope, pick a tool, edit the JSON arguments, run it.
 *
 * Kept even though the agent now calls these same tools itself, for two reasons. It is the
 * human-driven escape hatch — an operator who does not trust an answer can run the exact call the
 * transcript shows and compare. And it is the only way to exercise a tool WITHOUT a model in the
 * loop, which is how a broken catalog entry gets told apart from a confused agent.
 *
 * ─── Self-contained on purpose ─────────────────────────────────
 * Cluster list, catalog, selection, arguments and result all live here; the page it was extracted
 * from carried eleven pieces of state for it. The one thing that crosses the boundary is
 * {@link ToolPlaygroundModalProps.onToolsLoaded}, because the transcript's tool blocks resolve their
 * risk level from the same catalog: it is already loaded, and fetching it again per block would be
 * both wasteful and a second source of truth.
 *
 * ─── Stale catalog guard ───────────────────────────────────────
 * Switching cluster scope fires a new `listTools` while the previous one may still be in flight. The
 * monotonic `toolLoadRequestRef` is re-read after every await, so a slow response for the OLD scope
 * can neither replace the catalog nor re-select a tool that belongs to another cluster.
 */

/** Sentinel scope for the tools that are not bound to a cluster. */
const GLOBAL_TOOL_SCOPE = '__global__';

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

/** Value a required argument is pre-filled with, derived from the tool's JSON schema. */
const defaultSchemaValue = (schema: unknown): unknown => {
  if (!isRecord(schema)) return '';
  if ('default' in schema) return schema.default;
  if (Array.isArray(schema.enum) && schema.enum.length > 0) return schema.enum[0];
  switch (schema.type) {
    case 'boolean':
      return false;
    case 'integer':
    case 'number':
      return 0;
    case 'array':
      return [];
    case 'object':
      return {};
    default:
      return '';
  }
};

/**
 * Seed the argument editor with every REQUIRED argument, so the operator edits a value instead of
 * recalling a parameter name. `instanceId` is pre-filled from the selected cluster because that is
 * the one argument the scope selector already knows.
 */
const buildToolInputTemplate = (tool: McpTool, cluster?: string): string => {
  const required = Array.isArray(tool.parameters.required)
    ? tool.parameters.required.filter((field): field is string => typeof field === 'string')
    : [];
  const properties = isRecord(tool.parameters.properties) ? tool.parameters.properties : {};
  const input = Object.fromEntries(
    required.map((field) => [
      field,
      field === 'instanceId' && cluster ? cluster : defaultSchemaValue(properties[field]),
    ]),
  );
  return JSON.stringify(input, null, 2);
};

const formatToolResult = (result: unknown): string =>
  typeof result === 'string' ? result : (JSON.stringify(result, null, 2) ?? 'null');

export interface ToolPlaygroundModalProps {
  open: boolean;
  onClose: () => void;
  /**
   * Nothing may be loaded or executed — mock mode, where the AI page does not participate. The modal
   * stays openable so the reason is visible instead of the button silently doing nothing.
   */
  disabled?: boolean;
  /** Published whenever a catalog arrives, so transcript tool blocks can show a risk level. */
  onToolsLoaded?: (tools: McpTool[]) => void;
}

const ToolPlaygroundModal = ({
  open,
  onClose,
  disabled = false,
  onToolsLoaded,
}: ToolPlaygroundModalProps) => {
  const { t } = useLang();
  const { token } = theme.useToken();
  const [tools, setTools] = useState<McpTool[]>([]);
  const [toolsLoading, setToolsLoading] = useState(false);
  const [clusterOptions, setClusterOptions] = useState<{ value: string; label: string }[]>([]);
  const [clustersLoading, setClustersLoading] = useState(false);
  const [selectedClusterId, setSelectedClusterId] = useState('');
  const [selectedToolName, setSelectedToolName] = useState('');
  const [toolInput, setToolInput] = useState('{}');
  const [toolResult, setToolResult] = useState<unknown>(undefined);
  const [toolExecuting, setToolExecuting] = useState(false);
  const toolLoadRequestRef = useRef(0);
  /** Invalidates an execution whose tool/scope is no longer the one on screen. */
  const toolExecuteRequestRef = useRef(0);
  /** The catalog is loaded once per mount: reopening the modal must not re-hit the endpoint. */
  const bootstrappedRef = useRef(false);

  const selectTool = useCallback(
    (name: string, availableTools: McpTool[] = tools, clusterId: string = selectedClusterId) => {
      const tool = availableTools.find((item) => item.name === name);
      toolExecuteRequestRef.current += 1;
      setSelectedToolName(name);
      setToolInput(tool ? buildToolInputTemplate(tool, clusterId) : '{}');
      setToolResult(undefined);
    },
    [selectedClusterId, tools],
  );

  const loadTools = useCallback(
    async (clusterId: string) => {
      const requestId = ++toolLoadRequestRef.current;
      toolExecuteRequestRef.current += 1;
      setSelectedToolName('');
      setToolResult(undefined);
      setToolsLoading(true);
      try {
        const availableTools = await listTools(clusterId || undefined);
        if (requestId !== toolLoadRequestRef.current) return;
        setTools(availableTools);
        onToolsLoaded?.(availableTools);
        // Skip deprecated entries: they are listed so an operator can recognise one, not to be run.
        const firstTool = availableTools.find((tool) => !tool.deprecated);
        if (firstTool) selectTool(firstTool.name, availableTools, clusterId);
      } catch {
        if (requestId === toolLoadRequestRef.current) {
          setTools([]);
          message.error(t('ai.toolCatalogLoadFailed'));
        }
      } finally {
        if (requestId === toolLoadRequestRef.current) setToolsLoading(false);
      }
    },
    [onToolsLoaded, selectTool, t],
  );

  const bootstrap = useCallback(async () => {
    let clusterId = '';
    setClustersLoading(true);
    try {
      const clusters = await listClusters();
      const options = clusters.map((cluster) => ({ value: cluster.id, label: cluster.name }));
      setClusterOptions(options);
      clusterId = options[0]?.value ?? '';
      setSelectedClusterId(clusterId);
    } catch {
      message.warning(t('ai.clusterListLoadFailed'));
    } finally {
      setClustersLoading(false);
    }

    await loadTools(clusterId);
  }, [loadTools, t]);

  // Opening the modal is what loads the catalog, and only the first time: the page may deep-link
  // straight here (the home page's 工具 button navigates with `toolsIntent`), so the load cannot be
  // tied to a click handler on the page any more.
  useEffect(() => {
    if (!open || disabled || bootstrappedRef.current) return;
    bootstrappedRef.current = true;
    // Loading is asynchronous; state updates happen after the catalog API resolves.
    void bootstrap();
  }, [bootstrap, disabled, open]);

  const handleClusterChange = useCallback(
    async (scope: string) => {
      const clusterId = scope === GLOBAL_TOOL_SCOPE ? '' : scope;
      setSelectedClusterId(clusterId);
      await loadTools(clusterId);
    },
    [loadTools],
  );

  const handleExecuteTool = useCallback(async () => {
    if (!selectedToolName || toolExecuting) return;

    let parsedInput: unknown;
    try {
      parsedInput = JSON.parse(toolInput || '{}');
    } catch {
      message.error(t('ai.tools.invalidJson'));
      return;
    }
    if (!isRecord(parsedInput)) {
      message.error(t('ai.tools.invalidJson'));
      return;
    }

    setToolExecuting(true);
    setToolResult(undefined);
    const requestId = ++toolExecuteRequestRef.current;
    try {
      const result = await executeTool(selectedToolName, parsedInput, selectedClusterId);
      // The panel belongs to whatever tool is selected now: a response for a tool the operator has
      // already switched away from (or a modal that was closed and reopened) must not repopulate it,
      // because the output pane does not name the tool that produced it.
      if (requestId !== toolExecuteRequestRef.current) return;
      setToolResult(result);
      message.success(t('ai.tools.executeSuccess'));
    } catch (error) {
      if (requestId === toolExecuteRequestRef.current) {
        message.error(error instanceof Error ? error.message : t('ai.tools.executeFailed'));
      }
    } finally {
      setToolExecuting(false);
    }
  }, [selectedClusterId, selectedToolName, t, toolExecuting, toolInput]);

  const handleClose = useCallback(() => {
    // Invalidate an in-flight catalog load: its response would otherwise land on a closed modal and
    // re-select a tool nobody is looking at.
    toolLoadRequestRef.current += 1;
    toolExecuteRequestRef.current += 1;
    setToolsLoading(false);
    onClose();
  }, [onClose]);

  const selectedTool = tools.find((tool) => tool.name === selectedToolName);

  return (
    <Modal
      title={t('ai.tools.title')}
      open={open}
      onCancel={handleClose}
      onOk={() => void handleExecuteTool()}
      okText={t('ai.tools.execute')}
      cancelText={t('common.close')}
      width={720}
      styles={{ body: { maxHeight: 'calc(100vh - 260px)', overflowY: 'auto' } }}
      okButtonProps={{
        loading: toolExecuting,
        disabled: disabled || toolsLoading || !selectedToolName,
      }}
    >
      <Flex vertical gap={16} style={{ paddingTop: 8 }}>
        {disabled && <InfoBanner description={t('ai.mockToolsUnavailable')} />}

        <Select
          aria-label={t('ai.tools.selectCluster')}
          loading={clustersLoading}
          disabled={disabled}
          value={selectedClusterId || GLOBAL_TOOL_SCOPE}
          onChange={(scope) => void handleClusterChange(scope)}
          options={[
            { value: GLOBAL_TOOL_SCOPE, label: t('ai.tools.globalScope') },
            ...clusterOptions,
          ]}
        />

        <Select
          aria-label={t('ai.tools.selectTool')}
          showSearch
          loading={toolsLoading}
          disabled={disabled}
          value={selectedToolName || undefined}
          placeholder={t('ai.tools.selectTool')}
          optionFilterProp="label"
          onChange={(name) => selectTool(name)}
          options={tools.map((tool) => ({
            value: tool.name,
            label: tool.name,
            disabled: tool.deprecated,
          }))}
        />

        {selectedTool && (
          <Flex vertical gap={8}>
            <Space size={8} wrap>
              {selectedTool.riskLevel && (
                <Tag
                  color={selectedTool.riskLevel === 'L1' ? 'green' : 'orange'}
                  style={{ fontSize: 14 }}
                >
                  {selectedTool.riskLevel}
                </Tag>
              )}
              {selectedTool.permission && (
                <Tag style={{ fontSize: 14 }}>{selectedTool.permission}</Tag>
              )}
            </Space>
            <Typography.Text type="secondary" style={{ fontSize: 14 }}>
              {selectedTool.description}
            </Typography.Text>
          </Flex>
        )}

        <div>
          <Typography.Text strong style={{ display: 'block', marginBottom: 8, fontSize: 14 }}>
            {t('ai.tools.inputLabel')}
          </Typography.Text>
          <Input.TextArea
            aria-label={t('ai.tools.inputAria')}
            value={toolInput}
            disabled={disabled}
            onChange={(event) => setToolInput(event.target.value)}
            autoSize={{ minRows: 6, maxRows: 12 }}
            spellCheck={false}
          />
        </div>

        {toolResult !== undefined && (
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 8, fontSize: 14 }}>
              {t('ai.tool.output')}
            </Typography.Text>
            <pre
              data-testid="tool-result"
              style={{
                maxHeight: 280,
                margin: 0,
                padding: 12,
                overflow: 'auto',
                color: token.colorText,
                border: `1px solid ${token.colorBorderSecondary}`,
                borderRadius: 6,
                background: token.colorFillQuaternary,
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
                fontSize: 14,
              }}
            >
              {formatToolResult(toolResult)}
            </pre>
          </div>
        )}
      </Flex>
    </Modal>
  );
};

export default ToolPlaygroundModal;
