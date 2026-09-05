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

import { describe, expect, it } from 'vitest';
import type { LlmConfig, LlmModelsResult } from '../api/llm';
import { buildLlmModelReadiness, endpointHost, normalizeLlmCatalog } from './llmModelReadiness';

const config = (overrides: Partial<LlmConfig> = {}): LlmConfig => ({
  provider: 'openai',
  engine: 'http',
  apiKeyConfigured: true,
  apiBase: 'https://api.openai.example.test/v1',
  model: 'gpt-ready',
  maxTokens: 4096,
  temperature: 0.7,
  enabled: true,
  ready: true,
  ...overrides,
});

const models = (overrides: Partial<LlmModelsResult> = {}): LlmModelsResult => ({
  status: 0,
  data: [
    { id: 'gpt-ready', name: 'GPT Ready' },
    { id: 'gpt-small', name: 'GPT Small' },
  ],
  source: 'provider',
  ...overrides,
});

describe('endpointHost', () => {
  it('returns only the host and port from a valid endpoint', () => {
    expect(endpointHost('https://api.example.test:8443/private/v1?token=secret')).toBe(
      'api.example.test:8443',
    );
  });

  it('returns stable labels for blank and invalid endpoints', () => {
    expect(endpointHost('')).toBe('-');
    expect(endpointHost()).toBe('-');
    expect(endpointHost('not a url')).toBe('invalid endpoint');
  });
});

describe('normalizeLlmCatalog', () => {
  it('sorts models, preserves names, and marks the selected model', () => {
    expect(
      normalizeLlmCatalog(
        [
          { id: 'z-model', name: 'Zed' },
          { id: 'a-model', name: 'Alpha' },
        ],
        'z-model',
      ),
    ).toEqual([
      { id: 'a-model', name: 'Alpha', selected: false },
      { id: 'z-model', name: 'Zed', selected: true },
    ]);
  });

  it('uses the id when the provider omits a display name', () => {
    expect(normalizeLlmCatalog([{ id: 'model-a' }], 'model-a')).toEqual([
      { id: 'model-a', name: 'model-a', selected: true },
    ]);
  });

  it('drops blank identifiers and duplicate model rows', () => {
    expect(
      normalizeLlmCatalog([
        { id: '' },
        { name: 'missing id' },
        { id: 'model-a', name: 'First' },
        { id: 'model-a', name: 'Duplicate' },
      ]),
    ).toEqual([{ id: 'model-a', name: 'First', selected: false }]);
  });

  it('returns an empty catalog for an absent response list', () => {
    expect(normalizeLlmCatalog(undefined, 'model-a')).toEqual([]);
  });
});

describe('buildLlmModelReadiness', () => {
  it('builds a ready report for a configured provider catalog', () => {
    const report = buildLlmModelReadiness(config(), models());

    expect(report).toMatchObject({
      provider: 'openai',
      engine: 'http',
      selectedModel: 'gpt-ready',
      source: 'provider',
      endpointHost: 'api.openai.example.test',
      enabled: true,
      serverReady: true,
      apiKeyConfigured: true,
      selectedInCatalog: true,
      issues: [],
      summary: { models: 2, errors: 0, warnings: 0, ready: true },
    });
  });

  it('never includes the API key value in its output', () => {
    const secret = 'sk-do-not-expose';
    const report = buildLlmModelReadiness(config({ apiKey: secret }), models());

    expect(JSON.stringify(report)).not.toContain(secret);
    expect(report.apiKeyConfigured).toBe(true);
  });

  it.each(['tongyi', 'openai', 'anthropic', 'azure', 'deepseek'])(
    'requires a configured credential for %s',
    (provider) => {
      const report = buildLlmModelReadiness(
        config({
          provider,
          apiKeyConfigured: false,
          deploymentName: provider === 'azure' ? 'prod' : undefined,
          apiVersion: provider === 'azure' ? '2024-02-15-preview' : undefined,
        }),
        models(),
      );

      expect(report.issues).toContainEqual(
        expect.objectContaining({ code: 'CREDENTIAL_MISSING', severity: 'ERROR' }),
      );
      expect(report.summary.ready).toBe(false);
    },
  );

  it.each(['ollama', 'bedrock', 'custom-provider'])(
    'does not assume an API key requirement for %s',
    (provider) => {
      const report = buildLlmModelReadiness(
        config({
          provider,
          apiKeyConfigured: false,
          awsRegion: provider === 'bedrock' ? 'us-east-1' : undefined,
        }),
        models(),
      );

      expect(report.issues.map((entry) => entry.code)).not.toContain('CREDENTIAL_MISSING');
    },
  );

  it('reports disabled and server-not-ready states independently', () => {
    const report = buildLlmModelReadiness(config({ enabled: false, ready: false }), models());

    expect(report.issues.map((entry) => entry.code)).toEqual(['DISABLED', 'SERVER_NOT_READY']);
    expect(report.summary).toMatchObject({ errors: 2, ready: false });
  });

  it('distinguishes an absent server readiness signal from false', () => {
    const report = buildLlmModelReadiness(config({ ready: undefined }), models());

    expect(report.serverReady).toBeNull();
    expect(report.issues.map((entry) => entry.code)).not.toContain('SERVER_NOT_READY');
  });

  it('reports a missing selected model as an error', () => {
    const report = buildLlmModelReadiness(config({ model: ' ' }), models());

    expect(report.selectedModel).toBe('-');
    expect(report.issues).toContainEqual(expect.objectContaining({ code: 'MODEL_MISSING' }));
  });

  it('reports missing and invalid endpoints without exposing path data', () => {
    const missing = buildLlmModelReadiness(config({ apiBase: '' }), models());
    const invalid = buildLlmModelReadiness(config({ apiBase: 'not-a-url/private' }), models());

    expect(missing.issues.map((entry) => entry.code)).toContain('ENDPOINT_MISSING');
    expect(invalid.issues.map((entry) => entry.code)).toContain('ENDPOINT_INVALID');
    expect(JSON.stringify(invalid)).not.toContain('/private');
  });

  it('checks both Azure-specific fields', () => {
    const report = buildLlmModelReadiness(
      config({ provider: 'azure', deploymentName: '', apiVersion: '', apiKeyConfigured: true }),
      models(),
    );

    expect(report.issues.map((entry) => entry.code)).toEqual([
      'AZURE_DEPLOYMENT_MISSING',
      'AZURE_API_VERSION_MISSING',
    ]);
  });

  it('checks the AWS region for Bedrock', () => {
    const report = buildLlmModelReadiness(config({ provider: 'bedrock', awsRegion: '' }), models());

    expect(report.issues).toContainEqual(
      expect.objectContaining({ code: 'BEDROCK_REGION_MISSING', severity: 'ERROR' }),
    );
  });

  it('keeps a nonzero catalog status as a blocking error', () => {
    const report = buildLlmModelReadiness(config(), models({ status: 503, data: [] }));

    expect(report.issues).toContainEqual({
      code: 'CATALOG_REQUEST_FAILED',
      severity: 'ERROR',
      detail: '503',
    });
  });

  it('surfaces fallback provenance and the provider warning code', () => {
    const report = buildLlmModelReadiness(
      config(),
      models({
        source: 'fallback',
        warning: 'provider unavailable',
        warningCode: 'llm.provider.upstream_error',
        hint: 'Check the provider endpoint',
      }),
    );

    expect(report).toMatchObject({
      source: 'fallback',
      warningCode: 'llm.provider.upstream_error',
      warning: 'provider unavailable',
      hint: 'Check the provider endpoint',
    });
    expect(report.issues).toContainEqual({
      code: 'CATALOG_FALLBACK',
      severity: 'WARNING',
      detail: 'llm.provider.upstream_error',
    });
  });

  it('reports a non-fallback warning without duplicating it', () => {
    const report = buildLlmModelReadiness(config(), models({ warningCode: 'llm.catalog.partial' }));

    expect(report.issues.filter((entry) => entry.code === 'CATALOG_WARNING')).toHaveLength(1);
    expect(report.summary.warnings).toBe(1);
  });

  it('marks built-in catalogs as informational rather than degraded', () => {
    const report = buildLlmModelReadiness(config(), models({ source: 'builtin' }));

    expect(report.issues).toContainEqual({
      code: 'CATALOG_BUILTIN',
      severity: 'INFO',
      detail: '',
    });
    expect(report.summary).toMatchObject({ errors: 0, warnings: 0, ready: true });
  });

  it('warns when a successful catalog is empty', () => {
    const report = buildLlmModelReadiness(config(), models({ data: [] }));

    expect(report.issues.map((entry) => entry.code)).toContain('CATALOG_EMPTY');
    expect(report.selectedInCatalog).toBe(false);
    expect(report.summary.warnings).toBe(1);
  });

  it('warns when the configured model is absent from a nonempty catalog', () => {
    const report = buildLlmModelReadiness(config({ model: 'retired-model' }), models());

    expect(report.issues).toContainEqual({
      code: 'MODEL_NOT_IN_CATALOG',
      severity: 'WARNING',
      detail: 'retired-model',
    });
    expect(report.selectedInCatalog).toBe(false);
  });

  it('does not report catalog membership when catalog loading failed', () => {
    const report = buildLlmModelReadiness(
      config({ model: 'retired-model' }),
      models({ status: 500 }),
    );

    expect(report.issues.map((entry) => entry.code)).not.toContain('MODEL_NOT_IN_CATALOG');
  });

  it('normalizes provider casing and applies a stable engine fallback', () => {
    const report = buildLlmModelReadiness(config({ provider: ' OpenAI ', engine: '' }), models());

    expect(report.provider).toBe('openai');
    expect(report.engine).toBe('http');
  });

  it('does not mutate config or catalog response objects', () => {
    const sourceConfig = config();
    const sourceModels = models();
    const configSnapshot = structuredClone(sourceConfig);
    const modelsSnapshot = structuredClone(sourceModels);

    buildLlmModelReadiness(sourceConfig, sourceModels);

    expect(sourceConfig).toEqual(configSnapshot);
    expect(sourceModels).toEqual(modelsSnapshot);
  });
});
