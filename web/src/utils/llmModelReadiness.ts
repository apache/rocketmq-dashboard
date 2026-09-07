/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import type { LlmConfig, LlmModelItem, LlmModelsResult } from '../api/llm';

export type LlmReadinessSeverity = 'INFO' | 'WARNING' | 'ERROR';
export type LlmCatalogSource = 'provider' | 'builtin' | 'fallback' | 'unknown';

export interface LlmReadinessIssue {
  code: string;
  severity: LlmReadinessSeverity;
  detail: string;
}

export interface LlmCatalogRow {
  id: string;
  name: string;
  selected: boolean;
}

export interface LlmModelReadinessReport {
  provider: string;
  engine: string;
  selectedModel: string;
  source: LlmCatalogSource;
  endpointHost: string;
  enabled: boolean;
  serverReady: boolean | null;
  apiKeyConfigured: boolean;
  selectedInCatalog: boolean;
  catalog: LlmCatalogRow[];
  warningCode: string;
  warning: string;
  hint: string;
  issues: LlmReadinessIssue[];
  summary: {
    models: number;
    errors: number;
    warnings: number;
    ready: boolean;
  };
}

const API_KEY_PROVIDERS = new Set(['tongyi', 'openai', 'anthropic', 'azure', 'deepseek']);
const safeText = (value?: string | null, fallback = '-') => value?.trim() || fallback;

export const endpointHost = (value?: string | null): string => {
  if (!value?.trim()) return '-';
  try {
    return new URL(value).host || '-';
  } catch {
    return 'invalid endpoint';
  }
};

export const normalizeLlmCatalog = (
  items: LlmModelItem[] | undefined,
  selectedModel?: string | null,
): LlmCatalogRow[] => {
  const selected = safeText(selectedModel, '');
  const models = new Map<string, string>();
  (items ?? []).forEach((item) => {
    const id = safeText(item.id, '');
    if (!id || models.has(id)) return;
    models.set(id, safeText(item.name, id));
  });
  return [...models.entries()]
    .map(([id, name]) => ({ id, name, selected: id === selected }))
    .sort((left, right) => left.id.localeCompare(right.id));
};

const issue = (code: string, severity: LlmReadinessSeverity, detail = ''): LlmReadinessIssue => ({
  code,
  severity,
  detail,
});

/** 根据服务端已保存配置和模型目录响应生成只读就绪度报告，任何密钥值均不会进入结果。 */
export const buildLlmModelReadiness = (
  config: LlmConfig,
  models: LlmModelsResult,
): LlmModelReadinessReport => {
  const provider = safeText(config.provider, 'unknown').toLowerCase();
  const selectedModel = safeText(config.model, '');
  const catalog = normalizeLlmCatalog(models.data, selectedModel);
  const selectedInCatalog = Boolean(selectedModel) && catalog.some((model) => model.selected);
  const source = models.source ?? 'unknown';
  const issues: LlmReadinessIssue[] = [];

  if (!config.enabled) issues.push(issue('DISABLED', 'ERROR'));
  if (config.ready === false) issues.push(issue('SERVER_NOT_READY', 'ERROR'));
  if (!selectedModel) issues.push(issue('MODEL_MISSING', 'ERROR'));
  if (!safeText(config.apiBase, '')) issues.push(issue('ENDPOINT_MISSING', 'ERROR'));
  else if (endpointHost(config.apiBase) === 'invalid endpoint') {
    issues.push(issue('ENDPOINT_INVALID', 'ERROR'));
  }
  if (API_KEY_PROVIDERS.has(provider) && !config.apiKeyConfigured) {
    issues.push(issue('CREDENTIAL_MISSING', 'ERROR'));
  }
  if (provider === 'azure' && !safeText(config.deploymentName, '')) {
    issues.push(issue('AZURE_DEPLOYMENT_MISSING', 'ERROR'));
  }
  if (provider === 'azure' && !safeText(config.apiVersion, '')) {
    issues.push(issue('AZURE_API_VERSION_MISSING', 'ERROR'));
  }
  if (provider === 'bedrock' && !safeText(config.awsRegion, '')) {
    issues.push(issue('BEDROCK_REGION_MISSING', 'ERROR'));
  }
  if (models.status !== 0)
    issues.push(issue('CATALOG_REQUEST_FAILED', 'ERROR', String(models.status)));
  if (source === 'fallback') {
    issues.push(issue('CATALOG_FALLBACK', 'WARNING', models.warningCode || models.warning || ''));
  }
  if (source === 'builtin') issues.push(issue('CATALOG_BUILTIN', 'INFO'));
  if (models.status === 0 && catalog.length === 0) issues.push(issue('CATALOG_EMPTY', 'WARNING'));
  if (models.status === 0 && catalog.length > 0 && selectedModel && !selectedInCatalog) {
    issues.push(issue('MODEL_NOT_IN_CATALOG', 'WARNING', selectedModel));
  }
  if (models.warningCode && source !== 'fallback') {
    issues.push(issue('CATALOG_WARNING', 'WARNING', models.warningCode));
  }

  const errors = issues.filter((entry) => entry.severity === 'ERROR').length;
  const warnings = issues.filter((entry) => entry.severity === 'WARNING').length;
  return {
    provider,
    engine: safeText(config.engine, 'http'),
    selectedModel: selectedModel || '-',
    source,
    endpointHost: endpointHost(config.apiBase),
    enabled: Boolean(config.enabled),
    serverReady: config.ready ?? null,
    apiKeyConfigured: Boolean(config.apiKeyConfigured),
    selectedInCatalog,
    catalog,
    warningCode: safeText(models.warningCode, ''),
    warning: safeText(models.warning, ''),
    hint: safeText(models.hint, ''),
    issues,
    summary: { models: catalog.length, errors, warnings, ready: errors === 0 },
  };
};
