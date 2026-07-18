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
  message,
} from 'antd';
import { ArrowUp, Sparkle, SlidersHorizontal, CaretDown } from '@phosphor-icons/react';
import type { ColumnsType } from 'antd/es/table';
import { useLang } from '../../i18n/LangContext';
import { chatStream } from '../../api/ai';

const { Text, Paragraph } = Typography;

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

/* ─── Model Options ─── */

const modelOptions = [
  {
    value: 'qwen3.7-max',
    label: (
      <span className="inline-flex items-center gap-1.5">
        Qwen3.7-Max
        <span className="px-1 py-0.5 rounded text-[0.625rem] leading-none bg-purple-50 text-purple-600 font-medium">
          推荐
        </span>
      </span>
    ),
  },
  { value: 'qwen3.7-plus', label: 'Qwen3.7-Plus' },
  { value: 'claude-opus-4.7', label: 'Claude Opus 4.7' },
  { value: 'gpt-5.4', label: 'GPT-5.4' },
];

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

const AiMessage = ({ msg }: { msg: Message }) => (
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
        <Paragraph style={{ margin: 0, fontSize: 14, color: '#595959' }}>{msg.summary}</Paragraph>
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
  const [messages, setMessages] = useState<Message[]>(initialMessages);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(false);
  const [selectedModel, setSelectedModel] = useState('qwen3.7-max');
  const chatEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const abortControllerRef = useRef<AbortController | null>(null);
  const conversationIdRef = useRef<string | null>(null);

  const scrollToBottom = useCallback(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, scrollToBottom]);

  /* ─── Auto-resize textarea ─── */
  useEffect(() => {
    const ta = textareaRef.current;
    if (!ta) return;
    const handler = () => {
      ta.style.height = 'auto';
      ta.style.height = `${Math.min(ta.scrollHeight, 300)}px`;
    };
    ta.addEventListener('input', handler);
    return () => ta.removeEventListener('input', handler);
  }, []);

  useEffect(() => {
    return () => abortControllerRef.current?.abort();
  }, []);

  const handleSend = useCallback(async () => {
    const text = inputValue.trim();
    if (!text || loading) return;

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
              item.id === responseId
                ? { ...item, summary: `${item.summary ?? ''}${chunk}` }
                : item,
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
        setMessages((prev) =>
          prev.map((item) =>
            item.id === responseId ? { ...item, summary: 'AI 服务暂时不可用，请稍后重试。' } : item,
          ),
        );
        message.error(error instanceof Error ? error.message : 'AI 请求失败');
      }
    } finally {
      if (abortControllerRef.current === controller) abortControllerRef.current = null;
      setLoading(false);
    }
  }, [inputValue, loading, selectedModel]);

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

  return (
    <Flex vertical style={{ height: 'calc(100vh - 48px)', padding: 24 }}>
      {/* Chat Area */}
      <div
        className="w-full scrollbar-hide"
        style={{
          flex: 1,
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
      <div className="w-full" style={{ flexShrink: 0, padding: '0 24px' }}>
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

        {/* Main Input Box */}
        <div className="relative overflow-visible border-[1.5px] backdrop-blur-xl border-white rounded-2xl bg-white/80 shadow-[0_20px_60px_-20px_rgba(80,90,180,0.18)]">
          {/* Model Selector */}
          <div className="flex items-center justify-between gap-3 px-3.5 pt-4">
            <div className="flex flex-1 min-w-0 items-center gap-2">
              <Select
                size="small"
                value={selectedModel}
                onChange={(val) => setSelectedModel(val)}
                options={modelOptions}
                variant="borderless"
                popupMatchSelectWidth={false}
                suffixIcon={<CaretDown size={10} color="#9CA3AF" />}
                className="model-selector"
                style={{ fontSize: '0.893rem' }}
              />
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
                    <button className="tool-btn">
                      <SlidersHorizontal size={17} />
                      <span>工具</span>
                    </button>
                    <button className="tool-btn">
                      <Sparkle size={17} />
                      <span>Prompt 增强</span>
                    </button>
                  </div>
                </div>
                <div className="shrink-0 flex items-center gap-1">
                  <button
                    className="flex items-center justify-center w-9 h-9 rounded-full bg-gradient-to-r from-purple-500 to-violet-600 text-white shadow-lg hover:shadow-xl transition-all hover:scale-105"
                    onClick={handleSend}
                    disabled={loading || !inputValue.trim()}
                    style={{
                      opacity: loading || !inputValue.trim() ? 0.5 : 1,
                      cursor: loading || !inputValue.trim() ? 'not-allowed' : 'pointer',
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
