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

import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  MAX_CUSTOM_PROMPT_TEMPLATES,
  MAX_PROMPT_TEMPLATE_BODY_LENGTH,
  PROMPT_TEMPLATE_STORAGE_KEY,
  applyPromptTemplate,
  buildPromptTemplatePreview,
  builtinPromptTemplates,
  deleteCustomPromptTemplate,
  filterPromptTemplates,
  loadPromptTemplateCatalog,
  saveCustomPromptTemplate,
  type PromptTemplate,
} from './promptTemplates';

describe('AI prompt templates', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('loads builtin templates when no custom templates are stored', () => {
    const catalog = loadPromptTemplateCatalog();

    expect(catalog.storageAvailable).toBe(true);
    expect(catalog.customCount).toBe(0);
    expect(catalog.templates).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          id: 'builtin-diagnose-consumer-lag',
          scope: 'builtin',
          mode: 'diagnose',
          enhance: true,
        }),
      ]),
    );
  });

  it('saves a custom template before builtin templates', () => {
    const result = saveCustomPromptTemplate(
      {
        title: '  自定义巡检  ',
        description: '  每日巡检  ',
        mode: 'diagnose',
        enhance: true,
        tags: 'Daily, Broker, daily',
        body: '  检查 Broker 状态  ',
      },
      undefined,
      1_787_878_787,
    );

    expect(result.ok).toBe(true);
    const catalog = loadPromptTemplateCatalog();
    expect(catalog.customCount).toBe(1);
    expect(catalog.templates[0]).toMatchObject({
      scope: 'custom',
      title: '自定义巡检',
      description: '每日巡检',
      mode: 'diagnose',
      enhance: true,
      tags: ['daily', 'broker'],
      body: '检查 Broker 状态',
      createdAt: 1_787_878_787,
    });
    expect(catalog.templates[1].scope).toBe('builtin');
  });

  it('rejects empty titles or bodies without mutating storage', () => {
    expect(saveCustomPromptTemplate({ title: ' ', body: 'Inspect lag' })).toEqual({
      ok: false,
      reason: 'empty_title',
    });
    expect(saveCustomPromptTemplate({ title: 'Inspect', body: ' ' })).toEqual({
      ok: false,
      reason: 'empty_body',
    });

    expect(localStorage.getItem(PROMPT_TEMPLATE_STORAGE_KEY)).toBeNull();
  });

  it('sanitizes malformed persisted templates during catalog loading', () => {
    localStorage.setItem(
      PROMPT_TEMPLATE_STORAGE_KEY,
      JSON.stringify([
        { id: 'legacy', title: 'Legacy', body: 'Use me', mode: 'unknown', tags: ['A'] },
        { id: 'broken', title: '', body: '' },
        'not-object',
      ]),
    );

    const catalog = loadPromptTemplateCatalog();

    expect(catalog.invalidCustomCount).toBe(2);
    expect(catalog.templates[0]).toMatchObject({
      id: 'custom-legacy',
      scope: 'custom',
      title: 'Legacy',
      mode: 'chat',
      tags: ['a'],
    });
  });

  it('bounds custom template count and body size', () => {
    for (let index = 0; index < MAX_CUSTOM_PROMPT_TEMPLATES + 2; index += 1) {
      saveCustomPromptTemplate({
        title: `Template ${index}`,
        body: `${index}-${'x'.repeat(MAX_PROMPT_TEMPLATE_BODY_LENGTH + 20)}`,
      });
    }

    const custom = loadPromptTemplateCatalog().templates.filter(
      (template) => template.scope === 'custom',
    );
    expect(custom).toHaveLength(MAX_CUSTOM_PROMPT_TEMPLATES);
    expect(custom[0].title).toBe(`Template ${MAX_CUSTOM_PROMPT_TEMPLATES + 1}`);
    expect(custom[0].body).toHaveLength(MAX_PROMPT_TEMPLATE_BODY_LENGTH);
  });

  it('deletes only custom templates and leaves builtin templates available', () => {
    const saved = saveCustomPromptTemplate({ title: 'Temporary', body: 'Inspect temporary state' });
    expect(saved.template).toBeDefined();

    expect(deleteCustomPromptTemplate(saved.template!.id)).toBe(true);

    const catalog = loadPromptTemplateCatalog();
    expect(catalog.customCount).toBe(0);
    expect(catalog.templates).toHaveLength(builtinPromptTemplates.length);
    expect(localStorage.getItem(PROMPT_TEMPLATE_STORAGE_KEY)).toBeNull();
  });

  it('filters templates by keyword and mode', () => {
    const templates: PromptTemplate[] = [
      {
        id: 'one',
        scope: 'builtin',
        title: 'Broker health',
        description: '',
        mode: 'diagnose',
        enhance: true,
        tags: ['broker'],
        body: 'Inspect broker status',
      },
      {
        id: 'two',
        scope: 'builtin',
        title: 'Topic change',
        description: '',
        mode: 'manage',
        enhance: true,
        tags: ['topic'],
        body: 'Change topic',
      },
    ];

    expect(filterPromptTemplates(templates, 'broker', 'all').map((item) => item.id)).toEqual([
      'one',
    ]);
    expect(filterPromptTemplates(templates, '', 'manage').map((item) => item.id)).toEqual(['two']);
  });

  it('applies a template by replacing or appending to the current prompt', () => {
    const template = builtinPromptTemplates[0];

    expect(applyPromptTemplate(template, 'current', 'replace')).toBe(template.body);
    expect(applyPromptTemplate(template, 'current', 'append')).toBe(`current\n\n${template.body}`);
    expect(applyPromptTemplate(template, '   ', 'append')).toBe(template.body);
  });

  it('returns compact previews for multiline template bodies', () => {
    expect(buildPromptTemplatePreview('line 1\n\nline 2')).toBe('line 1 line 2');
    expect(buildPromptTemplatePreview('x'.repeat(20), 8)).toBe('xxxxxxxx...');
  });

  it('reports storage as unavailable when localStorage throws', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });

    expect(loadPromptTemplateCatalog()).toMatchObject({
      customCount: 0,
      storageAvailable: false,
    });
    expect(saveCustomPromptTemplate({ title: 'Blocked', body: 'Blocked body' })).toEqual({
      ok: false,
      reason: 'storage_unavailable',
    });
  });
});
