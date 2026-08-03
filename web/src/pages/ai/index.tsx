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

import { useState, useRef, useEffect, useCallback } from 'react';
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
  Select,
  Alert,
  Input,
  Modal,
  Space,
  theme,
  message,
} from 'antd';
import { ArrowUp, Sparkle, SlidersHorizontal, CaretDown } from '@phosphor-icons/react';
import type { ColumnsType } from 'antd/es/table';
import { useLang } from '../../i18n/LangContext';
import { AiStreamError, chatStream, executeTool, listTools, type McpTool } from '../../api/ai';
import { listClusters } from '../../api/cluster';
import { getLlmConfig, getLlmModels, type LlmConfig } from '../../api/llm';
import { getChatDraft } from './chatDraft';

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
  text?: string;
  toolCall?: ToolCallTag;
  tableData?: TopicRow[];
  tableColumns?: ColumnsType<TopicRow>;
  stats?: StatItem[];
  descriptions?: DescriptionItem[];
  summary?: string;
  actions?: { label: string; type?: 'primary' | 'default' }[];
}

/* ─── Mock Data ─── */

const initialMessages: Message[] = [];

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

const UserBubble = ({ text }: { text: string }) => (
  <Flex justify="flex-end" style={{ marginBottom: 16 }}>
    <div
      style={{
        maxWidth: '70%',
        padding: '10px 16px',
        background: '#e6f4ff',
        borderRadius: 16,
        borderTopRightRadius: 4,
        lineHeight: 1.6,
        fontSize: 14,
      }}
    >
      {text}
    </div>
  </Flex>
);

export const AiMessage = ({ msg }: { msg: Message }) => (
  <Flex gap={12} align="flex-start" style={{ marginBottom: 16 }}>
    <div
      style={{
        width: 36,
        height: 36,
        borderRadius: '50%',
        background: 'linear-gradient(135deg, #1677ff 0%, #722ed1 100%)',
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
        boxShadow: '0 1px 4px rgba(0, 0, 0, 0.06)',
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
            fontSize: 12,
            background: '#f9f0ff',
            borderColor: '#d3adf7',
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
                    <Text type="secondary" style={{ fontSize: 12 }}>
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

      {/* Summary text */}
      {msg.summary && (
        <div className="ai-markdown">
          <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.summary}</ReactMarkdown>
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
    </Card>
  </Flex>
);

/* ═══════════════════════════════════════════
   AiPage
   ═══════════════════════════════════════════ */

const AiPage = () => {
  const { t } = useLang();
  const location = useLocation();
  const navigate = useNavigate();
  const { token } = theme.useToken();
  const [messages, setMessages] = useState<Message[]>(initialMessages);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(false);
  const [llmConfig, setLlmConfig] = useState<LlmConfig | null>(null);
  const [modelOptions, setModelOptions] = useState<{ value: string; label: string }[]>([]);
  const [modelsLoading, setModelsLoading] = useState(false);
  const [selectedModel, setSelectedModel] = useState('');
  const [toolModalOpen, setToolModalOpen] = useState(false);
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
  const conversationIdRef = useRef<string | null>(null);
  const consumedDraftRef = useRef(false);

  const scrollToBottom = useCallback(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, scrollToBottom]);

  const loadLlmRuntime = useCallback(async () => {
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
      message.error(error instanceof Error ? error.message : 'AI 配置加载失败');
    } finally {
      setModelsLoading(false);
    }
  }, []);

  useEffect(() => {
    void Promise.resolve().then(loadLlmRuntime);
  }, [loadLlmRuntime]);

  useEffect(() => {
    const draft = getChatDraft(location.state);
    if (!draft || consumedDraftRef.current) return;
    consumedDraftRef.current = true;

    void Promise.resolve().then(() => {
      setInputValue(draft.prompt);
      const draftModel = draft.model;
      if (draftModel) {
        setSelectedModel(draftModel);
        setModelOptions((options) =>
          options.some((option) => option.value === draftModel)
            ? options
            : [{ value: draftModel, label: draftModel }, ...options],
        );
      }
      navigate('/ai', { replace: true, state: null });
      textareaRef.current?.focus();
    });
  }, [location.state, navigate]);

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

  const handleSend = useCallback(async () => {
    const text = inputValue.trim();
    if (!text || loading) return;
    if (!llmReady) {
      message.warning('请先配置并启用 LLM Provider');
      return;
    }

    if (!conversationIdRef.current) {
      conversationIdRef.current = `conversation-${Date.now()}`;
    }

    const userMsg: Message = {
      id: `user-${Date.now()}`,
      role: 'user',
      text,
    };

    const responseId = `ai-${Date.now()}`;
    setMessages((prev) => [...prev, userMsg, { id: responseId, role: 'ai', summary: '' }]);
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
          mode: 'chat',
          model: selectedModel,
          conversationId: conversationIdRef.current,
        },
        (chunk) => {
          setMessages((prev) =>
            prev.map((item) =>
              item.id === responseId ? { ...item, summary: `${item.summary ?? ''}${chunk}` } : item,
            ),
          );
        },
        controller.signal,
      );
    } catch (error) {
      if (controller.signal.aborted) {
        setMessages((prev) =>
          prev.map((item) =>
            item.id === responseId && !item.summary ? { ...item, summary: '回答已停止。' } : item,
          ),
        );
      } else {
        const errorMessage = error instanceof Error ? error.message : 'AI 请求失败';
        const errorHint = error instanceof AiStreamError && error.hint ? error.hint : '';
        const summary = errorHint ? `${errorMessage}\n\n> ${errorHint}` : errorMessage;
        setMessages((prev) =>
          prev.map((item) => (item.id === responseId ? { ...item, summary } : item)),
        );
        message.error(errorMessage);
      }
    } finally {
      if (abortControllerRef.current === controller) abortControllerRef.current = null;
      setLoading(false);
    }
  }, [inputValue, llmReady, loading, selectedModel]);

  const handleStop = useCallback(() => {
    abortControllerRef.current?.abort();
  }, []);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
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

  const selectTool = useCallback(
    (name: string, availableTools: McpTool[] = tools, clusterId: string = selectedClusterId) => {
      const tool = availableTools.find((item) => item.name === name);
      setSelectedToolName(name);
      setToolInput(tool ? buildToolInputTemplate(tool, clusterId) : '{}');
      setToolResult(undefined);
    },
    [selectedClusterId, tools],
  );

  const loadTools = useCallback(
    async (clusterId: string) => {
      setSelectedToolName('');
      setToolResult(undefined);
      setToolsLoading(true);
      try {
        const availableTools = await listTools(clusterId || undefined);
        setTools(availableTools);
        const firstTool = availableTools.find((tool) => !tool.deprecated);
        if (firstTool) selectTool(firstTool.name, availableTools, clusterId);
      } catch {
        setTools([]);
        message.error('AI 工具目录加载失败');
      } finally {
        setToolsLoading(false);
      }
    },
    [selectTool],
  );

  const handleOpenTools = useCallback(async () => {
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
      message.warning('集群列表加载失败，已显示全局工具');
    } finally {
      setClustersLoading(false);
    }

    await loadTools(clusterId);
  }, [clustersLoading, loadTools, tools.length, toolsLoading]);

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
    } catch {
      message.error('工具执行失败');
    } finally {
      setToolExecuting(false);
    }
  }, [selectedToolName, toolExecuting, toolInput]);

  const selectedTool = tools.find((tool) => tool.name === selectedToolName);

  return (
    <Flex vertical style={{ height: '100%', minHeight: 0, padding: 24, overflow: 'hidden' }}>
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
            <UserBubble key={msg.id} text={msg.text!} />
          ) : (
            <AiMessage key={msg.id} msg={msg} />
          ),
        )}
        {loading && (
          <Flex gap={12} align="flex-start" style={{ marginBottom: 16 }}>
            <div
              style={{
                width: 36,
                height: 36,
                borderRadius: '50%',
                background: 'linear-gradient(135deg, #1677ff 0%, #722ed1 100%)',
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
                boxShadow: '0 1px 4px rgba(0, 0, 0, 0.06)',
                borderRadius: 12,
                borderTopLeftRadius: 4,
              }}
              styles={{ body: { padding: '12px 16px' } }}
            >
              <Flex gap={4} align="center">
                <span
                  style={{
                    display: 'inline-block',
                    width: 6,
                    height: 6,
                    borderRadius: '50%',
                    background: '#722ed1',
                    animation: 'dotPulse 1.4s infinite ease-in-out',
                  }}
                />
                <span
                  style={{
                    display: 'inline-block',
                    width: 6,
                    height: 6,
                    borderRadius: '50%',
                    background: '#722ed1',
                    animation: 'dotPulse 1.4s infinite ease-in-out 0.2s',
                  }}
                />
                <span
                  style={{
                    display: 'inline-block',
                    width: 6,
                    height: 6,
                    borderRadius: '50%',
                    background: '#722ed1',
                    animation: 'dotPulse 1.4s infinite ease-in-out 0.4s',
                  }}
                />
              </Flex>
            </Card>
          </Flex>
        )}
        <div ref={chatEndRef} />
      </div>

      {/* Input Area */}
      <div className="w-full" style={{ flexShrink: 0, padding: '0 24px 4px' }}>
        {/* Quick Actions */}
        <Flex align="center" gap={8} style={{ marginBottom: 12 }} wrap="wrap">
          <Text type="secondary" style={{ fontSize: 13, flexShrink: 0 }}>
            {t('ai.commonCommands')}
          </Text>
          {quickActions.map((action) => (
            <Tag
              key={action}
              style={{
                cursor: 'pointer',
                borderRadius: 6,
                padding: '2px 10px',
                fontSize: 13,
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
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message="AI 助手未启用"
            description="请先在 Studio LLM Settings 中配置并启用 LLM Provider，启用前不会发送请求或返回 stub 回复。"
            action={
              <Button size="small" onClick={() => navigate('/studio/llm-settings')}>
                去配置
              </Button>
            }
          />
        )}

        {/* Main Input Box */}
        <div className="relative overflow-visible border-[1.5px] backdrop-blur-xl border-white rounded-2xl bg-white/80 shadow-[0_20px_60px_-20px_rgba(80,90,180,0.18)]">
          {/* Model Selector */}
          <div className="flex items-center justify-between gap-3 px-3.5 pt-4">
            <div className="flex flex-1 min-w-0 items-center gap-2">
              <Select
                size="small"
                value={selectedModel || undefined}
                onChange={(val) => setSelectedModel(val)}
                options={modelOptions}
                loading={modelsLoading}
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
          <div className="flex justify-between text-sm items-center px-3.5 py-3 border-t border-gray-100/80">
            <div className="flex flex-1 gap-1 items-center min-w-0">
              <div className="flex items-center gap-2 w-full">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 overflow-x-auto scrollbar-hide max-w-full py-2">
                    <button className="tool-btn" onClick={() => void handleOpenTools()}>
                      <SlidersHorizontal size={17} />
                      <span>工具</span>
                    </button>
                    <button className="tool-btn" disabled title="Prompt 增强暂未接入">
                      <Sparkle size={17} />
                      <span>Prompt 增强</span>
                    </button>
                  </div>
                </div>
                <div className="shrink-0 flex items-center gap-1">
                  <button
                    className="flex items-center justify-center w-9 h-9 rounded-full bg-gradient-to-r from-purple-500 to-violet-600 text-white shadow-lg hover:shadow-xl transition-all hover:scale-105"
                    onClick={handleSend}
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

      <Modal
        title="AI 工具"
        open={toolModalOpen}
        onCancel={() => setToolModalOpen(false)}
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
