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

import type { ChatMode } from './chatDraft';

export type PromptTemplateScope = 'builtin' | 'custom';
export type PromptTemplateApplyMode = 'replace' | 'append';

export interface PromptTemplate {
  id: string;
  scope: PromptTemplateScope;
  i18nKey?: string;
  title: string;
  description: string;
  mode: ChatMode;
  enhance: boolean;
  tags: string[];
  body: string;
  createdAt?: number;
  updatedAt?: number;
}

export interface PromptTemplateDraft {
  title: string;
  description?: string;
  mode?: ChatMode;
  enhance?: boolean;
  tags?: string[] | string;
  body: string;
}

export interface PromptTemplateCatalog {
  templates: PromptTemplate[];
  customCount: number;
  storageAvailable: boolean;
  invalidCustomCount: number;
}

export interface SavePromptTemplateResult {
  ok: boolean;
  template?: PromptTemplate;
  reason?: 'empty_title' | 'empty_body' | 'storage_unavailable';
}

type Translate = (key: string) => string;

export const PROMPT_TEMPLATE_STORAGE_KEY = 'rocketmq-studio-ai-prompt-templates';
export const MAX_CUSTOM_PROMPT_TEMPLATES = 20;
export const MAX_PROMPT_TEMPLATE_BODY_LENGTH = 6000;
export const MAX_PROMPT_TEMPLATE_TITLE_LENGTH = 80;
export const MAX_PROMPT_TEMPLATE_DESCRIPTION_LENGTH = 200;
export const MAX_PROMPT_TEMPLATE_TAGS = 6;
export const MAX_PROMPT_TEMPLATE_TAG_LENGTH = 24;

const CHAT_MODES = new Set<ChatMode>(['chat', 'diagnose', 'manage', 'query']);

export const builtinPromptTemplates: PromptTemplate[] = [
  {
    id: 'builtin-diagnose-consumer-lag',
    scope: 'builtin',
    i18nKey: 'ai.promptTemplates.consumerLag',
    title: '消费延迟诊断',
    description: '排查消费堆积、在线客户端、订阅和 Broker 进度返回状态。',
    mode: 'diagnose',
    enhance: true,
    tags: ['consumer', 'lag', 'diagnose'],
    body: [
      '请诊断当前 RocketMQ 实例中的消费延迟问题。',
      '',
      '检查范围：',
      '1. 找出堆积最高的消费组和 Topic。',
      '2. 对比消费组在线客户端、订阅关系、重试队列和队列级 offset。',
      '3. 判断是生产突增、消费者离线、订阅漂移、Broker 进度不可查，还是单队列热点。',
      '4. 给出可执行的恢复步骤，并标注哪些步骤只读、哪些步骤会修改集群状态。',
    ].join('\n'),
  },
  {
    id: 'builtin-query-message-trace',
    scope: 'builtin',
    i18nKey: 'ai.promptTemplates.messageTrace',
    title: '消息轨迹排查',
    description: '按 Message ID 或 Key 收集发送、存储、消费和重试线索。',
    mode: 'query',
    enhance: true,
    tags: ['message', 'trace', 'query'],
    body: [
      '请协助排查一条消息的完整轨迹。',
      '',
      '已知信息：',
      '- Message ID：',
      '- Topic：',
      '- Key 或业务单号：',
      '- 大致发送时间：',
      '',
      '请先说明需要哪些查询条件，然后按发送结果、Broker 存储、消费结果、重试/DLQ 四部分输出结论。',
    ].join('\n'),
  },
  {
    id: 'builtin-manage-topic-change',
    scope: 'builtin',
    i18nKey: 'ai.promptTemplates.topicChange',
    title: 'Topic 变更预案',
    description: '生成 Topic 队列、权限、TTL 或保留策略变更前的检查清单。',
    mode: 'manage',
    enhance: true,
    tags: ['topic', 'change', 'precheck'],
    body: [
      '请为一次 RocketMQ Topic 变更生成执行预案。',
      '',
      '变更目标：',
      '- Topic：',
      '- 期望调整：',
      '- 影响窗口：',
      '',
      '请输出：变更前检查、风险判断、执行步骤、回滚步骤、验证方式。涉及写操作时先列出需要人工确认的命令或接口。',
    ].join('\n'),
  },
  {
    id: 'builtin-diagnose-broker-health',
    scope: 'builtin',
    i18nKey: 'ai.promptTemplates.brokerHealth',
    title: 'Broker 健康巡检',
    description: '汇总 Broker 可用性、磁盘水位、TPS、队列分布和异常告警。',
    mode: 'diagnose',
    enhance: true,
    tags: ['broker', 'health', 'ops'],
    body: [
      '请对当前 RocketMQ 集群做一次 Broker 健康巡检。',
      '',
      '请关注：',
      '1. Broker 可用性和主从状态。',
      '2. 磁盘水位、TPS、消息量和异常告警。',
      '3. Topic 队列分布是否存在热点或倾斜。',
      '4. 最近是否有失败的告警通知或未确认系统告警。',
      '',
      '输出时请区分“已验证事实”“需要进一步查询”“建议处理动作”。',
    ].join('\n'),
  },
  {
    id: 'builtin-chat-release-summary',
    scope: 'builtin',
    i18nKey: 'ai.promptTemplates.opsSummary',
    title: '运维变更摘要',
    description: '把已执行的查询结果整理成面向值班和复盘的摘要。',
    mode: 'chat',
    enhance: false,
    tags: ['summary', 'handoff'],
    body: [
      '请把本次 RocketMQ 运维排查过程整理成简洁摘要。',
      '',
      '摘要结构：',
      '- 背景：',
      '- 已执行查询：',
      '- 关键发现：',
      '- 已完成操作：',
      '- 未完成风险：',
      '- 下一步：',
    ].join('\n'),
  },
  {
    id: 'builtin-query-acl-risk',
    scope: 'builtin',
    i18nKey: 'ai.promptTemplates.aclRisk',
    title: 'ACL 风险核对',
    description: '检查用户、策略、实例绑定和高危权限是否符合预期。',
    mode: 'query',
    enhance: true,
    tags: ['acl', 'security', 'query'],
    body: [
      '请核对当前 RocketMQ Studio 的 ACL 风险。',
      '',
      '请检查：',
      '1. 是否存在管理员权限、通配资源或写权限过宽的账号。',
      '2. 用户和实例绑定是否符合最小权限。',
      '3. 云厂商实例与 Apache 实例的 ACL 字段差异。',
      '4. 哪些发现需要立即处理，哪些只是建议优化。',
    ].join('\n'),
  },
];

type PromptTemplateStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const getStorage = (): PromptTemplateStorage | undefined => {
  if (typeof localStorage === 'undefined') return undefined;
  return localStorage;
};

const nowMs = () => Date.now();

const newTemplateId = (): string => {
  const random =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return `custom-${random}`;
};

const normalizeText = (value: unknown, maxLength: number): string => {
  const text = typeof value === 'string' ? value.trim() : '';
  return text.length > maxLength ? text.slice(0, maxLength) : text;
};

const normalizeMode = (value: unknown): ChatMode =>
  typeof value === 'string' && CHAT_MODES.has(value as ChatMode) ? (value as ChatMode) : 'chat';

const normalizeTags = (value: unknown): string[] => {
  const source = Array.isArray(value) ? value : typeof value === 'string' ? value.split(',') : [];
  const normalized = source
    .map((tag) => normalizeText(tag, MAX_PROMPT_TEMPLATE_TAG_LENGTH).toLowerCase())
    .filter(Boolean);
  return [...new Set(normalized)].slice(0, MAX_PROMPT_TEMPLATE_TAGS);
};

const sanitizeCustomTemplate = (value: unknown, fallbackTime = nowMs()): PromptTemplate | null => {
  if (!isRecord(value)) return null;
  const title = normalizeText(value.title, MAX_PROMPT_TEMPLATE_TITLE_LENGTH);
  const body = normalizeText(value.body, MAX_PROMPT_TEMPLATE_BODY_LENGTH);
  if (!title || !body) return null;
  const id = normalizeText(value.id, 120) || newTemplateId();
  return {
    id: id.startsWith('custom-') ? id : `custom-${id}`,
    scope: 'custom',
    title,
    description: normalizeText(value.description, MAX_PROMPT_TEMPLATE_DESCRIPTION_LENGTH),
    mode: normalizeMode(value.mode),
    enhance: value.enhance === true,
    tags: normalizeTags(value.tags),
    body,
    createdAt: typeof value.createdAt === 'number' ? value.createdAt : fallbackTime,
    updatedAt: typeof value.updatedAt === 'number' ? value.updatedAt : fallbackTime,
  };
};

const translatedText = (
  translate: Translate | undefined,
  key: string | undefined,
  field: 'title' | 'description' | 'body',
  fallback: string,
): string => {
  if (!translate || !key) return fallback;
  const translated = translate(`${key}.${field}`);
  return translated === `${key}.${field}` ? fallback : translated;
};

function getBuiltinPromptTemplates(translate?: Translate): PromptTemplate[] {
  return builtinPromptTemplates.map((template) => ({
    ...template,
    title: translatedText(translate, template.i18nKey, 'title', template.title),
    description: translatedText(translate, template.i18nKey, 'description', template.description),
    body: translatedText(translate, template.i18nKey, 'body', template.body),
  }));
}

function readCustomPromptTemplates(storage = getStorage()): {
  templates: PromptTemplate[];
  storageAvailable: boolean;
  invalidCustomCount: number;
} {
  if (!storage) return { templates: [], storageAvailable: false, invalidCustomCount: 0 };
  try {
    const raw = storage.getItem(PROMPT_TEMPLATE_STORAGE_KEY);
    if (!raw) return { templates: [], storageAvailable: true, invalidCustomCount: 0 };
    const parsed = JSON.parse(raw) as unknown;
    const items = Array.isArray(parsed) ? parsed : [];
    const templates = items
      .map((item) => sanitizeCustomTemplate(item))
      .filter((item): item is PromptTemplate => item !== null)
      .slice(0, MAX_CUSTOM_PROMPT_TEMPLATES);
    return {
      templates,
      storageAvailable: true,
      invalidCustomCount: Math.max(0, items.length - templates.length),
    };
  } catch {
    return { templates: [], storageAvailable: false, invalidCustomCount: 0 };
  }
}

function writeCustomPromptTemplates(templates: PromptTemplate[], storage = getStorage()): boolean {
  if (!storage) return false;
  try {
    storage.setItem(
      PROMPT_TEMPLATE_STORAGE_KEY,
      JSON.stringify(templates.slice(0, MAX_CUSTOM_PROMPT_TEMPLATES)),
    );
    return true;
  } catch {
    return false;
  }
}

export function loadPromptTemplateCatalog(
  storage = getStorage(),
  translate?: Translate,
): PromptTemplateCatalog {
  const custom = readCustomPromptTemplates(storage);
  return {
    templates: [...custom.templates, ...getBuiltinPromptTemplates(translate)],
    customCount: custom.templates.length,
    storageAvailable: custom.storageAvailable,
    invalidCustomCount: custom.invalidCustomCount,
  };
}

export function saveCustomPromptTemplate(
  draft: PromptTemplateDraft,
  storage = getStorage(),
  timestamp = nowMs(),
): SavePromptTemplateResult {
  const title = normalizeText(draft.title, MAX_PROMPT_TEMPLATE_TITLE_LENGTH);
  const body = normalizeText(draft.body, MAX_PROMPT_TEMPLATE_BODY_LENGTH);
  if (!title) return { ok: false, reason: 'empty_title' };
  if (!body) return { ok: false, reason: 'empty_body' };

  const current = readCustomPromptTemplates(storage);
  if (!current.storageAvailable) return { ok: false, reason: 'storage_unavailable' };

  const template: PromptTemplate = {
    id: newTemplateId(),
    scope: 'custom',
    title,
    description: normalizeText(draft.description, MAX_PROMPT_TEMPLATE_DESCRIPTION_LENGTH),
    mode: normalizeMode(draft.mode),
    enhance: draft.enhance === true,
    tags: normalizeTags(draft.tags),
    body,
    createdAt: timestamp,
    updatedAt: timestamp,
  };
  const next = [template, ...current.templates].slice(0, MAX_CUSTOM_PROMPT_TEMPLATES);
  if (!writeCustomPromptTemplates(next, storage)) {
    return { ok: false, reason: 'storage_unavailable' };
  }
  return { ok: true, template };
}

export function deleteCustomPromptTemplate(id: string, storage = getStorage()): boolean {
  const current = readCustomPromptTemplates(storage);
  if (!current.storageAvailable) return false;
  const next = current.templates.filter((template) => template.id !== id);
  if (next.length === current.templates.length) return true;
  if (next.length === 0) {
    try {
      storage?.removeItem(PROMPT_TEMPLATE_STORAGE_KEY);
      return true;
    } catch {
      return false;
    }
  }
  return writeCustomPromptTemplates(next, storage);
}

export function applyPromptTemplate(
  template: PromptTemplate,
  currentText: string,
  mode: PromptTemplateApplyMode,
): string {
  if (mode === 'replace') return template.body;
  const current = currentText.trim();
  return current ? `${current}\n\n${template.body}` : template.body;
}

export function filterPromptTemplates(
  templates: PromptTemplate[],
  search: string,
  mode?: ChatMode | 'all',
): PromptTemplate[] {
  const keyword = search.trim().toLowerCase();
  return templates.filter((template) => {
    const modeMatched = !mode || mode === 'all' || template.mode === mode;
    if (!modeMatched) return false;
    if (!keyword) return true;
    return [template.title, template.description, template.body, ...template.tags]
      .join('\n')
      .toLowerCase()
      .includes(keyword);
  });
}

export function buildPromptTemplatePreview(body: string, maxLength = 160): string {
  const compact = body.replace(/\s+/g, ' ').trim();
  return compact.length > maxLength ? `${compact.slice(0, maxLength)}...` : compact;
}
