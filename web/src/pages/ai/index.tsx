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

import { useState, useRef, useEffect, useCallback, useMemo, type CSSProperties } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Card,
  Button,
  Tag,
  Typography,
  Table,
  Statistic,
  Row,
  Col,
  Descriptions,
  Flex,
  Divider,
  Drawer,
  Empty,
  Select,
  Alert,
  Input,
  Modal,
  Space,
  theme,
  message,
} from 'antd';
import {
  ArrowUp,
  CaretDown,
  ClockCounterClockwise,
  SlidersHorizontal,
  Sparkle,
} from '@phosphor-icons/react';
import type { ColumnsType } from 'antd/es/table';
import { useLang } from '../../i18n/LangContext';
import { AiStreamError, chatStream, executeTool, listTools, type McpTool } from '../../api/ai';
import { listClusters } from '../../api/cluster';
import { getLlmConfig, getLlmModels, type LlmConfig } from '../../api/llm';
import { formatRelativeTime, formatTimeOfDay } from '../../utils/format';
import { useDataModeStore } from '../../stores/dataModeStore';
import { useEngineStore } from '../../stores/engineStore';
import InfoBanner from '../../components/InfoBanner';
import useAuthStore from '../../stores/authStore';
import {
  getRecentAiChatConversations,
  flushAiChatHistoryPersistence,
  type AiChatDataMode,
  useAiChatHistoryStore,
} from '../../stores/aiChatHistoryStore';
import { getChatDraft, type ChatMode } from './chatDraft';

const { Text } = Typography;

/* ─── Types ─── */

interface ToolCallTag {
  name: string;
  label: string;
}

interface TopicRow {
  key: string;
  name: string;
  type: string;
  queues: number;
}

interface StatItem {
  title: string;
  value: string | number;
  suffix?: string;
  color: string;
}

interface DescriptionItem {
  label: string;
  value: string;
}

interface Message {
  id: string;
  role: 'user' | 'ai';
  createdAt?: number;
  text?: string;
  toolCall?: ToolCallTag;
  tableData?: TopicRow[];
  tableColumns?: ColumnsType<TopicRow>;
  stats?: StatItem[];
  descriptions?: DescriptionItem[];
  summary?: string;
  thinking?: string;
  pending?: boolean;
  actions?: { label: string; type?: 'primary' | 'default' }[];
}

/* ─── Mock Data ─── */

/* ─── Quick Actions ─── */

const quickActions = [
  '查看集群状态',
  'Topic 堆积 Top10',
  '诊断消费延迟',
  '创建 Topic',
  '消息轨迹查询',
  '扩缩容评估',
];

const GLOBAL_TOOL_SCOPE = '__global__';

export const normalizeAiMarkdown = (content: string): string =>
  content
    .replace(/^(#{1,6})(?=\S)/gm, '$1 ')
    .replace(/^([-+*])(?=\S)/gm, '$1 ')
    .replace(/^```(bash|sh|shell|json|ya?ml|sql|text)(?=\S)/gim, '```$1\n');

const newConversationId = (): string =>
  `conversation-${typeof crypto?.randomUUID === 'function' ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`}`;

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

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

const buildToolInputTemplate = (tool: McpTool, cluster?: string): string => {
  const required = Array.isArray(tool.parameters.required)
    ? tool.parameters.required.filter((field): field is string => typeof field === 'string')
    : [];
  const properties = isRecord(tool.parameters.properties) ? tool.parameters.properties : {};
  const input = Object.fromEntries(
    required.map((field) => [
      field,
      field === 'cluster' && cluster ? cluster : defaultSchemaValue(properties[field]),
    ]),
  );
  return JSON.stringify(input, null, 2);
};

const formatToolResult = (result: unknown): string =>
  typeof result === 'string' ? result : (JSON.stringify(result, null, 2) ?? 'null');

/* ─── Sub-components ─── */

const UserBubble = ({ text, createdAt }: Pick<Message, 'text' | 'createdAt'>) => {
  const { token } = theme.useToken();

  return (
    <Flex justify="flex-end" style={{ marginBottom: 16 }}>
      <div
        className="ai-user-bubble"
        style={{
          maxWidth: '70%',
          padding: '10px 16px',
          background: token.colorPrimaryBg,
          color: token.colorText,
          border: `1px solid ${token.colorPrimaryBorder}`,
          borderRadius: 16,
          borderTopRightRadius: 4,
          lineHeight: 1.6,
          fontSize: 14,
        }}
      >
        {text}
        {createdAt && (
          <div
            style={{
              marginTop: 4,
              color: token.colorTextTertiary,
              fontSize: 14,
              textAlign: 'right',
            }}
          >
            {formatTimeOfDay(createdAt)}
          </div>
        )}
      </div>
    </Flex>
  );
};

export const AiMessage = ({ msg }: { msg: Message }) => {
  const { token } = theme.useToken();

  return (
    <Flex gap={12} align="flex-start" style={{ marginBottom: 16 }}>
      <div
        style={{
          width: 36,
          height: 36,
          borderRadius: '50%',
          background: `linear-gradient(135deg, ${token.colorPrimary} 0%, ${token.colorPrimaryHover} 100%)`,
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          boxShadow: '0 2px 8px rgba(22, 119, 255, 0.3)',
        }}
      >
        <svg
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="white"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91-.09z" />
          <path d="m12 15-3-3a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.35 22.35 0 0 1-4 2z" />
          <path d="M9 12H4s.55-3.03 2-4c1.62-1.08 5 0 5 0" />
          <path d="M12 15v5s3.03-.55 4-2c1.08-1.62 0-5 0-5" />
        </svg>
      </div>
      <Card
        size="small"
        style={{
          maxWidth: '75%',
          background: token.colorBgElevated,
          borderColor: token.colorBorderSecondary,
          boxShadow: `0 1px 4px ${token.colorTextQuaternary}`,
          borderRadius: 12,
          borderTopLeftRadius: 4,
        }}
        styles={{ body: { padding: '12px 16px' } }}
      >
        {/* Tool call indicator */}
        {msg.toolCall && (
          <Tag
            color="purple"
            style={{
              marginBottom: 12,
              borderRadius: 6,
              fontSize: 14,
              background: token.colorPrimaryBg,
              borderColor: token.colorPrimaryBorder,
            }}
          >
            {msg.toolCall.label}
          </Tag>
        )}

        {/* Table content */}
        {msg.tableData && msg.tableColumns && (
          <Table
            dataSource={msg.tableData}
            columns={msg.tableColumns}
            rowKey="key"
            size="small"
            pagination={false}
            style={{ marginBottom: 12 }}
          />
        )}

        {/* Stat cards */}
        {msg.stats && (
          <Row gutter={12} style={{ marginBottom: 12 }}>
            {msg.stats.map((s) => (
              <Col key={s.title}>
                <Card
                  size="small"
                  style={{
                    borderRadius: 8,
                    borderTop: `3px solid ${s.color}`,
                    minWidth: 120,
                  }}
                  styles={{ body: { padding: '8px 12px' } }}
                >
                  <Statistic
                    title={
                      <Text type="secondary" style={{ fontSize: 14 }}>
                        {s.title}
                      </Text>
                    }
                    value={s.value}
                    suffix={s.suffix}
                    valueStyle={{ fontSize: 20, fontWeight: 600, color: s.color }}
                  />
                </Card>
              </Col>
            ))}
          </Row>
        )}

        {/* Descriptions */}
        {msg.descriptions && (
          <Descriptions bordered size="small" column={2} style={{ marginBottom: 12 }}>
            {msg.descriptions.map((d) => (
              <Descriptions.Item key={d.label} label={d.label}>
                <Text strong>{d.value}</Text>
              </Descriptions.Item>
            ))}
          </Descriptions>
        )}

        {/* Chain of thought (enhanced prompt) */}
        {msg.thinking && (
          <details style={{ marginBottom: 12 }}>
            <summary
              style={{
                cursor: 'pointer',
                color: token.colorPrimary,
                fontSize: 14,
                fontWeight: 500,
                userSelect: 'none',
              }}
            >
              思维链：Prompt 增强改写
            </summary>
            <div
              style={{
                marginTop: 8,
                padding: '8px 12px',
                background: token.colorFillSecondary,
                border: `1px solid ${token.colorBorderSecondary}`,
                borderRadius: 8,
                fontSize: 14,
                lineHeight: 1.7,
                color: token.colorTextSecondary,
                whiteSpace: 'pre-wrap',
              }}
            >
              {msg.thinking}
            </div>
          </details>
        )}

        {/* Waiting indicator (inside the bubble) */}
        {msg.pending && !msg.summary && (
          <Flex gap={4} align="center" style={{ padding: '2px 0' }}>
            <span
              style={{
                display: 'inline-block',
                width: 6,
                height: 6,
                borderRadius: '50%',
                background: token.colorPrimary,
                animation: 'dotPulse 1.4s infinite ease-in-out',
              }}
            />
            <span
              style={{
                display: 'inline-block',
                width: 6,
                height: 6,
                borderRadius: '50%',
                background: token.colorPrimary,
                animation: 'dotPulse 1.4s infinite ease-in-out 0.2s',
              }}
            />
            <span
              style={{
                display: 'inline-block',
                width: 6,
                height: 6,
                borderRadius: '50%',
                background: token.colorPrimary,
                animation: 'dotPulse 1.4s infinite ease-in-out 0.4s',
              }}
            />
            <Text type="secondary" style={{ fontSize: 14, marginLeft: 8 }}>
              正在思考…
            </Text>
          </Flex>
        )}

        {/* Summary text */}
        {msg.summary && (
          <div className="ai-markdown">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>
              {normalizeAiMarkdown(msg.summary)}
            </ReactMarkdown>
          </div>
        )}

        {/* Action buttons */}
        {msg.actions && (
          <>
            <Divider style={{ margin: '12px 0 8px' }} />
            <Flex gap={8}>
              {msg.actions.map((a) => (
                <Button key={a.label} type={a.type || 'default'} size="small">
                  {a.label}
                </Button>
              ))}
            </Flex>
          </>
        )}

        {msg.createdAt && (
          <div style={{ marginTop: 8, color: token.colorTextTertiary, fontSize: 14 }}>
            {formatTimeOfDay(msg.createdAt)}
          </div>
        )}
      </Card>
    </Flex>
  );
};

/* ═══════════════════════════════════════════
   AiPage
   ═══════════════════════════════════════════ */

const AiPage = () => {
  const { t, lang } = useLang();
  const location = useLocation();
  const navigate = useNavigate();
  const useMock = useDataModeStore((state) => state.useMock);
  const userId = useAuthStore((state) => state.userId);
  const admin = useAuthStore((state) => state.admin);
  const chatMode: AiChatDataMode = useMock ? 'mock' : 'real';
  const { token } = theme.useToken();
  const history = useAiChatHistoryStore((state) => state.histories[chatMode]);
  const activeConversation = useMemo(
    () => history.conversations.find((item) => item.id === history.activeConversationId),
    [history],
  );
  const messages = useMemo(() => activeConversation?.messages ?? [], [activeConversation]);
  const legacyMessageTimestamp = activeConversation?.updatedAt || undefined;
  const updateMessages = useAiChatHistoryStore((state) => state.setMessages);
  const startConversation = useAiChatHistoryStore((state) => state.startConversation);
  const selectConversation = useAiChatHistoryStore((state) => state.selectConversation);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(false);
  const [settingsHintDismissed, setSettingsHintDismissed] = useState(false);
  const [llmConfig, setLlmConfig] = useState<LlmConfig | null>(null);
  const [modelOptions, setModelOptions] = useState<{ value: string; label: string }[]>([]);
  const [modelsLoading, setModelsLoading] = useState(false);
  const [selectedModel, setSelectedModel] = useState('');
  const [toolModalOpen, setToolModalOpen] = useState(false);
  const [enhance, setEnhance] = useState(false);
  const [tools, setTools] = useState<McpTool[]>([]);
  const [toolsLoading, setToolsLoading] = useState(false);
  const [clusterOptions, setClusterOptions] = useState<{ value: string; label: string }[]>([]);
  const [clustersLoading, setClustersLoading] = useState(false);
  const [selectedClusterId, setSelectedClusterId] = useState('');
  const [selectedToolName, setSelectedToolName] = useState('');
  const [toolInput, setToolInput] = useState('{}');
  const [toolResult, setToolResult] = useState<unknown>(undefined);
  const [toolExecuting, setToolExecuting] = useState(false);
  const chatEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const abortControllerRef = useRef<AbortController | null>(null);
  const streamRequestIdRef = useRef(0);
  const previousChatModeRef = useRef(chatMode);
  const conversationIdRef = useRef<string | null>(history.activeConversationId);
  const chatInFlightRef = useRef(false);
  const toolLoadRequestRef = useRef(0);
  const consumedDraftRef = useRef(false);
  const pendingAutoSendRef = useRef<{
    prompt: string;
    model?: string;
    mode?: ChatMode;
    enhance?: boolean;
  } | null>(null);
  const canInspectLlmRuntime = !userId || admin === true;

  const scrollToBottom = useCallback(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, scrollToBottom]);

  useEffect(() => {
    if (previousChatModeRef.current !== chatMode) {
      abortControllerRef.current?.abort();
      abortControllerRef.current = null;
      streamRequestIdRef.current += 1;
      setLoading(false);
      previousChatModeRef.current = chatMode;
    }
    conversationIdRef.current =
      useAiChatHistoryStore.getState().histories[chatMode].activeConversationId;
  }, [chatMode, history.activeConversationId]);

  const loadLlmRuntime = useCallback(async () => {
    if (useMock || !canInspectLlmRuntime) {
      setLlmConfig(null);
      setModelOptions([]);
      setSelectedModel('');
      setModelsLoading(false);
      return;
    }
    setModelsLoading(true);
    try {
      const config = await getLlmConfig();
      setLlmConfig(config);
      if (config?.model) {
        setSelectedModel((current) => current || config.model);
      }
      const result = await getLlmModels();
      const models = result?.status === 0 && result.data ? result.data : [];
      const options = models
        .map((item) => item.id || item.name || '')
        .filter(Boolean)
        .map((id) => ({ value: id, label: id }));
      if (options.length > 0) {
        setModelOptions(options);
        setSelectedModel((current) => current || config?.model || options[0].value);
      } else if (config?.model) {
        setModelOptions([{ value: config.model, label: config.model }]);
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('ai.runtimeLoadFailed'));
    } finally {
      setModelsLoading(false);
    }
  }, [canInspectLlmRuntime, t, useMock]);

  useEffect(() => {
    void Promise.resolve().then(loadLlmRuntime);
  }, [loadLlmRuntime]);

  useEffect(() => {
    const draft = getChatDraft(location.state);
    if (!draft || consumedDraftRef.current) return;
    consumedDraftRef.current = true;

    void Promise.resolve().then(() => {
      if (draft.newConversation) {
        const nextConversationId = newConversationId();
        startConversation(chatMode, nextConversationId);
        conversationIdRef.current = nextConversationId;
      } else if (draft.conversationId) {
        selectConversation(chatMode, draft.conversationId);
        conversationIdRef.current = draft.conversationId;
      }
      if (draft.prompt) setInputValue(draft.prompt);
      const draftModel = draft.model;
      if (draftModel) {
        setSelectedModel(draftModel);
        setModelOptions((options) =>
          options.some((option) => option.value === draftModel)
            ? options
            : [{ value: draftModel, label: draftModel }, ...options],
        );
      }
      if (draft.prompt) {
        pendingAutoSendRef.current = {
          prompt: draft.prompt,
          model: draft.model,
          mode: draft.mode,
          enhance: draft.enhance,
        };
      }
      navigate('/ai', { replace: true, state: null });
    });
  }, [chatMode, location.state, navigate, selectConversation, startConversation]);

  /* ─── Auto-resize textarea ─── */
  useEffect(() => {
    const ta = textareaRef.current;
    if (!ta) return;
    const handler = () => {
      ta.style.height = 'auto';
      ta.style.height = `${Math.min(ta.scrollHeight, 180)}px`;
    };
    ta.addEventListener('input', handler);
    return () => ta.removeEventListener('input', handler);
  }, []);

  useEffect(() => {
    return () => abortControllerRef.current?.abort();
  }, []);

  const llmReady = Boolean((llmConfig?.ready ?? llmConfig?.enabled) && selectedModel);

  const handleSend = useCallback(
    async (
      textOverride?: string,
      modelOverride?: string,
      enhance?: boolean,
      modeOverride: ChatMode = 'chat',
    ) => {
      const text = (textOverride ?? inputValue).trim();
      const model = modelOverride ?? selectedModel;
      if (!text || loading || chatInFlightRef.current) return;
      if (!llmReady) {
        message.warning(t('ai.providerRequired'));
        return;
      }
      chatInFlightRef.current = true;

      if (!conversationIdRef.current) {
        conversationIdRef.current = newConversationId();
        startConversation(chatMode, conversationIdRef.current);
      }

      const conversationId = conversationIdRef.current;
      const requestId = ++streamRequestIdRef.current;
      const createdAt = Date.now();
      const userMsg: Message = {
        id: `user-${createdAt}`,
        role: 'user',
        text,
        createdAt,
      };

      const responseId = `ai-${Date.now()}`;
      updateMessages(chatMode, conversationId, (prev) => [
        ...prev,
        userMsg,
        { id: responseId, role: 'ai', summary: '', pending: true, createdAt },
      ]);
      setInputValue('');
      if (textareaRef.current) {
        textareaRef.current.style.height = 'auto';
      }
      setLoading(true);
      const controller = new AbortController();
      abortControllerRef.current = controller;

      try {
        await chatStream(
          {
            message: text,
            mode: modeOverride,
            model,
            engine: useEngineStore.getState().engine,
            enhance,
            conversationId,
          },
          (chunk) => {
            if (streamRequestIdRef.current !== requestId || controller.signal.aborted) return;
            updateMessages(chatMode, conversationId, (prev) =>
              prev.map((item) =>
                item.id === responseId
                  ? { ...item, summary: `${item.summary ?? ''}${chunk}` }
                  : item,
              ),
            );
          },
          controller.signal,
          (enhanceDelta) => {
            if (streamRequestIdRef.current !== requestId || controller.signal.aborted) return;
            updateMessages(chatMode, conversationId, (prev) =>
              prev.map((item) =>
                item.id === responseId
                  ? { ...item, thinking: `${item.thinking ?? ''}${enhanceDelta}` }
                  : item,
              ),
            );
          },
        );
      } catch (error) {
        if (controller.signal.aborted) {
          updateMessages(chatMode, conversationId, (prev) =>
            prev.map((item) =>
              item.id === responseId && !item.summary
                ? { ...item, summary: t('ai.responseStopped') }
                : item,
            ),
          );
        } else {
          const errorMessage = error instanceof Error ? error.message : t('ai.requestFailed');
          const errorHint = error instanceof AiStreamError && error.hint ? error.hint : '';
          const summary = errorHint ? `${errorMessage}\n\n> ${errorHint}` : errorMessage;
          updateMessages(chatMode, conversationId, (prev) =>
            prev.map((item) => (item.id === responseId ? { ...item, summary } : item)),
          );
          message.error(errorMessage);
        }
      } finally {
        chatInFlightRef.current = false;
        if (abortControllerRef.current === controller) abortControllerRef.current = null;
        updateMessages(chatMode, conversationId, (prev) =>
          prev.map((item) => (item.id === responseId ? { ...item, pending: false } : item)),
        );
        flushAiChatHistoryPersistence();
        if (streamRequestIdRef.current === requestId) setLoading(false);
      }
    },
    [chatMode, inputValue, llmReady, loading, selectedModel, startConversation, t, updateMessages],
  );

  /* ─── Auto-send the draft from the home page as soon as runtime is ready ─── */
  useEffect(() => {
    const pending = pendingAutoSendRef.current;
    if (!pending || loading || !llmReady) return;
    pendingAutoSendRef.current = null;
    void handleSend(pending.prompt, pending.model, pending.enhance, pending.mode);
  }, [llmReady, loading, handleSend]);

  const handleStop = useCallback(() => {
    abortControllerRef.current?.abort();
  }, []);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.nativeEvent.isComposing) return;
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        handleSend();
      }
    },
    [handleSend],
  );

  const handleQuickAction = useCallback((action: string) => {
    setInputValue(action);
    textareaRef.current?.focus();
  }, []);

  const recentConversations = getRecentAiChatConversations(history.conversations);

  const handleConversationSelect = (conversationId: string) => {
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
    streamRequestIdRef.current += 1;
    setLoading(false);
    selectConversation(chatMode, conversationId);
    conversationIdRef.current = conversationId;
    setHistoryOpen(false);
  };

  const selectTool = useCallback(
    (name: string, availableTools: McpTool[] = tools, clusterId: string = selectedClusterId) => {
      const tool = availableTools.find((item) => item.name === name);
      setSelectedToolName(name);
      setToolInput(tool ? buildToolInputTemplate(tool, clusterId) : '{}');
      setToolResult(undefined);
    },
    [selectedClusterId, setSelectedToolName, setToolInput, setToolResult, tools],
  );

  const loadTools = useCallback(
    async (clusterId: string) => {
      const requestId = ++toolLoadRequestRef.current;
      setSelectedToolName('');
      setToolResult(undefined);
      setToolsLoading(true);
      try {
        const availableTools = await listTools(clusterId || undefined);
        if (requestId !== toolLoadRequestRef.current) return;
        setTools(availableTools);
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
    [selectTool, t],
  );

  const handleOpenTools = useCallback(async () => {
    if (useMock) {
      message.info(t('ai.mockToolsUnavailable'));
      return;
    }
    setToolModalOpen(true);
    setToolResult(undefined);
    if (tools.length > 0 || toolsLoading || clustersLoading) return;

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
  }, [
    clustersLoading,
    loadTools,
    setClusterOptions,
    setClustersLoading,
    setSelectedClusterId,
    setToolModalOpen,
    setToolResult,
    t,
    tools.length,
    toolsLoading,
    useMock,
  ]);

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
      message.error('工具参数必须是有效的 JSON 对象');
      return;
    }
    if (!isRecord(parsedInput)) {
      message.error('工具参数必须是有效的 JSON 对象');
      return;
    }

    setToolExecuting(true);
    setToolResult(undefined);
    try {
      setToolResult(await executeTool(selectedToolName, parsedInput));
      message.success('工具执行成功');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '工具执行失败');
    } finally {
      setToolExecuting(false);
    }
  }, [selectedToolName, toolExecuting, toolInput]);

  const selectedTool = tools.find((tool) => tool.name === selectedToolName);

  return (
    <Flex
      vertical
      className="ai-page"
      style={
        {
          height: '100%',
          minHeight: 0,
          padding: 24,
          overflow: 'hidden',
          '--ai-surface': token.colorBgContainer,
          '--ai-surface-elevated': token.colorBgElevated,
          '--ai-border': token.colorBorderSecondary,
          '--ai-text': token.colorText,
          '--ai-text-secondary': token.colorTextSecondary,
          '--ai-text-tertiary': token.colorTextTertiary,
          '--ai-primary': token.colorPrimary,
          '--ai-primary-bg': token.colorPrimaryBg,
          '--ai-primary-hover': token.colorPrimaryHover,
          '--ai-fill-secondary': token.colorFillSecondary,
          '--ai-code-bg': token.colorBgSpotlight,
          '--ai-code-text': token.colorTextLightSolid,
        } as CSSProperties
      }
    >
      {/* Chat Area */}
      <div
        className="w-full scrollbar-hide"
        style={{
          flex: 1,
          minHeight: 0,
          overflowY: 'auto',
          padding: '16px 24px',
          scrollBehavior: 'smooth',
        }}
      >
        {messages.map((msg) =>
          msg.role === 'user' ? (
            <UserBubble
              key={msg.id}
              text={msg.text!}
              createdAt={msg.createdAt ?? legacyMessageTimestamp}
            />
          ) : (
            <AiMessage
              key={msg.id}
              msg={
                msg.createdAt || !legacyMessageTimestamp
                  ? msg
                  : { ...msg, createdAt: legacyMessageTimestamp }
              }
            />
          ),
        )}
        <div ref={chatEndRef} />
      </div>

      {/* Input Area */}
      <div className="w-full" style={{ flexShrink: 0, padding: '0 24px 4px' }}>
        {/* Quick Actions */}
        <Flex align="center" gap={8} style={{ marginBottom: 12 }} wrap="wrap">
          <Text type="secondary" style={{ fontSize: 14, flexShrink: 0 }}>
            {t('ai.commonCommands')}
          </Text>
          {quickActions.map((action) => (
            <Tag
              key={action}
              style={{
                cursor: 'pointer',
                borderRadius: 6,
                padding: '2px 10px',
                fontSize: 14,
                userSelect: 'none',
                transition: 'all 0.2s',
              }}
              color="blue"
              onClick={() => handleQuickAction(action)}
            >
              {action}
            </Tag>
          ))}
        </Flex>

        {llmConfig && !llmReady && (
          <InfoBanner
            title="AI 助手未启用"
            description={t('ai.providerNotReadyDescription')}
            style={{ marginBottom: 12 }}
            data-testid="ai-not-ready-banner"
          >
            <Button
              type="link"
              size="small"
              style={{ paddingLeft: 0, marginTop: 4 }}
              onClick={() => navigate('/settings?tab=ai')}
            >
              去配置
            </Button>
          </InfoBanner>
        )}
        {!settingsHintDismissed && (
          <Alert
            type="info"
            showIcon
            closable
            banner
            onClose={() => setSettingsHintDismissed(true)}
            style={{ marginBottom: 12, borderRadius: 8 }}
            message={
              <span>
                模型服务与执行引擎可在 <a onClick={() => navigate('/settings')}>设置 → AI 助手</a>{' '}
                中配置
              </span>
            }
          />
        )}
        {useMock && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            message={t('ai.mockProviderDisabled')}
            description={t('ai.mockProviderDisabledDescription')}
          />
        )}

        {/* Main Input Box */}
        <div className="ai-chat-panel relative overflow-visible border-[1.5px] backdrop-blur-xl rounded-2xl">
          {/* Model Selector */}
          <div className="flex items-center justify-between gap-3 px-3.5 pt-4">
            <div className="flex flex-1 min-w-0 items-center gap-2">
              <Select
                size="small"
                value={selectedModel || undefined}
                onChange={(val) => setSelectedModel(val)}
                options={modelOptions}
                loading={modelsLoading}
                disabled={!canInspectLlmRuntime}
                variant="borderless"
                placeholder={modelsLoading ? '加载模型中...' : '选择模型'}
                popupMatchSelectWidth={false}
                suffixIcon={<CaretDown size={10} color="#9CA3AF" />}
                className="model-selector"
                style={{ fontSize: '0.893rem' }}
              />
              {llmConfig && (
                <Tag color={llmReady ? 'green' : 'default'} style={{ borderRadius: 6 }}>
                  {llmConfig.provider || 'openai'}
                  {llmReady ? ' 已就绪' : ' 未就绪'}
                </Tag>
              )}
            </div>
            <button
              type="button"
              className="p-1 rounded-md text-gray-400 hover:text-gray-600 hover:bg-gray-50 transition-colors"
              aria-label={t('ai.history.title')}
              title={t('ai.history.title')}
              onClick={() => setHistoryOpen(true)}
            >
              <ClockCounterClockwise size={20} />
            </button>
          </div>

          {/* Textarea */}
          <div className="relative flex flex-col">
            <textarea
              ref={textareaRef}
              className="chat-input"
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="输入你的问题或指令，例如：查看集群状态、创建 Topic、诊断消费延迟..."
            />
            <Sparkle
              className="text-gray-400"
              style={{
                position: 'absolute',
                top: 18,
                left: 26,
                fontSize: 17,
              }}
            />
          </div>

          {/* Bottom Toolbar */}
          <div className="ai-chat-toolbar flex justify-between text-sm items-center px-3.5 py-3 border-t">
            <div className="flex flex-1 gap-1 items-center min-w-0">
              <div className="flex items-center gap-2 w-full">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 overflow-x-auto scrollbar-hide max-w-full py-2">
                    <button className="tool-btn" onClick={() => void handleOpenTools()}>
                      <SlidersHorizontal size={17} />
                      <span>工具</span>
                    </button>
                    <button
                      className="tool-btn"
                      onClick={() => setEnhance((value) => !value)}
                      style={{
                        borderColor: enhance ? '#1677ff' : undefined,
                        color: enhance ? '#1677ff' : undefined,
                      }}
                      title="发送前增强 Prompt"
                    >
                      <Sparkle size={17} />
                      <span>Prompt 增强</span>
                    </button>
                  </div>
                </div>
                <div className="shrink-0 flex items-center gap-1">
                  <button
                    className="ai-send-button flex items-center justify-center w-9 h-9 rounded-full text-white shadow-lg hover:shadow-xl transition-all hover:scale-105"
                    onClick={() => void handleSend(undefined, undefined, enhance)}
                    disabled={loading || !inputValue.trim() || !llmReady}
                    style={{
                      opacity: loading || !inputValue.trim() || !llmReady ? 0.5 : 1,
                      cursor:
                        loading || !inputValue.trim() || !llmReady ? 'not-allowed' : 'pointer',
                    }}
                  >
                    <ArrowUp size={19} weight="bold" />
                  </button>
                  {loading && (
                    <Button size="small" onClick={handleStop}>
                      停止
                    </Button>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <Drawer
        title={t('ai.history.title')}
        placement="right"
        width={360}
        open={historyOpen}
        onClose={() => setHistoryOpen(false)}
      >
        {recentConversations.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('ai.history.empty')} />
        ) : (
          <div className="flex flex-col gap-2">
            {recentConversations.map((conversation) => (
              <button
                key={conversation.id}
                type="button"
                onClick={() => handleConversationSelect(conversation.id)}
                className={`w-full rounded-md border px-3 py-2 text-left text-sm transition-colors ${
                  conversation.id === history.activeConversationId
                    ? 'border-blue-400 bg-blue-50 text-blue-700'
                    : 'border-gray-200 bg-white text-gray-700 hover:border-blue-300 hover:bg-blue-50'
                }`}
              >
                <span style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0 }}>
                  <span
                    style={{
                      flex: 1,
                      minWidth: 0,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {conversation.prompt}
                  </span>
                  <span
                    style={{
                      flexShrink: 0,
                      color: token.colorTextSecondary,
                      fontSize: 14,
                      fontWeight: 500,
                      lineHeight: 1.5,
                    }}
                  >
                    {formatRelativeTime(conversation.updatedAt, lang, t)}
                  </span>
                </span>
              </button>
            ))}
          </div>
        )}
      </Drawer>

      <Modal
        title="AI 工具"
        open={toolModalOpen}
        onCancel={() => {
          toolLoadRequestRef.current += 1;
          setToolsLoading(false);
          setToolModalOpen(false);
        }}
        onOk={() => void handleExecuteTool()}
        okText="执行"
        cancelText="关闭"
        width={720}
        styles={{ body: { maxHeight: 'calc(100vh - 260px)', overflowY: 'auto' } }}
        okButtonProps={{
          loading: toolExecuting,
          disabled: toolsLoading || !selectedToolName,
        }}
      >
        <Flex vertical gap={16} style={{ paddingTop: 8 }}>
          <Select
            aria-label="选择集群"
            loading={clustersLoading}
            value={selectedClusterId || GLOBAL_TOOL_SCOPE}
            onChange={(scope) => void handleClusterChange(scope)}
            options={[{ value: GLOBAL_TOOL_SCOPE, label: '全局工具' }, ...clusterOptions]}
          />

          <Select
            aria-label="选择工具"
            showSearch
            loading={toolsLoading}
            value={selectedToolName || undefined}
            placeholder="选择工具"
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
                  <Tag color={selectedTool.riskLevel === 'L1' ? 'green' : 'orange'}>
                    {selectedTool.riskLevel}
                  </Tag>
                )}
                {selectedTool.permission && <Tag>{selectedTool.permission}</Tag>}
              </Space>
              <Text type="secondary">{selectedTool.description}</Text>
            </Flex>
          )}

          <div>
            <Text strong style={{ display: 'block', marginBottom: 8 }}>
              输入参数 (JSON)
            </Text>
            <Input.TextArea
              aria-label="工具参数 JSON"
              value={toolInput}
              onChange={(event) => setToolInput(event.target.value)}
              autoSize={{ minRows: 6, maxRows: 12 }}
              spellCheck={false}
            />
          </div>

          {toolResult !== undefined && (
            <div>
              <Text strong style={{ display: 'block', marginBottom: 8 }}>
                执行结果
              </Text>
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
                }}
              >
                {formatToolResult(toolResult)}
              </pre>
            </div>
          )}
        </Flex>
      </Modal>

      {/* Keyframes for loading animation */}
      <style>{`
        @keyframes dotPulse {
          0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
          40% { opacity: 1; transform: scale(1.2); }
        }
      `}</style>
    </Flex>
  );
};

export default AiPage;
