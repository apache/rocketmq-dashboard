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
  promptTemplateStorageKey,
  saveCustomPromptTemplate,
  type PromptTemplate,
  type PromptTemplateOwner,
} from './promptTemplates';

const SYSTEM_OWNER: PromptTemplateOwner = {};
const ALICE_OWNER: PromptTemplateOwner = { userId: 7, username: 'alice' };
const BOB_OWNER: PromptTemplateOwner = { userId: 8, username: 'bob' };

describe('AI prompt templates', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('loads builtin templates when no custom templates are stored', () => {
    const catalog = loadPromptTemplateCatalog(SYSTEM_OWNER);

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
      SYSTEM_OWNER,
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
    const catalog = loadPromptTemplateCatalog(SYSTEM_OWNER);
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
    expect(saveCustomPromptTemplate(SYSTEM_OWNER, { title: ' ', body: 'Inspect lag' })).toEqual({
      ok: false,
      reason: 'empty_title',
    });
    expect(saveCustomPromptTemplate(SYSTEM_OWNER, { title: 'Inspect', body: ' ' })).toEqual({
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

    const catalog = loadPromptTemplateCatalog(SYSTEM_OWNER);

    expect(catalog.invalidCustomCount).toBe(2);
    expect(catalog.templates[0]).toMatchObject({
      id: 'custom-legacy',
      scope: 'custom',
      title: 'Legacy',
      mode: 'chat',
      tags: ['a'],
    });
  });

  it('does not report templates dropped by the storage cap as invalid', () => {
    const stored = Array.from({ length: MAX_CUSTOM_PROMPT_TEMPLATES + 3 }, (_, index) => ({
      id: `capped-${index}`,
      title: `Template ${index}`,
      body: 'Summarise the consumer lag for this group',
      mode: 'chat',
      tags: ['general'],
    }));
    localStorage.setItem(PROMPT_TEMPLATE_STORAGE_KEY, JSON.stringify(stored));

    const catalog = loadPromptTemplateCatalog(SYSTEM_OWNER);

    expect(catalog.invalidCustomCount).toBe(0);
    expect(catalog.customCount).toBe(MAX_CUSTOM_PROMPT_TEMPLATES);
  });

  it('repairs corrupt stored JSON instead of treating storage as unavailable', () => {
    localStorage.setItem(PROMPT_TEMPLATE_STORAGE_KEY, '{"broken":');

    expect(loadPromptTemplateCatalog(SYSTEM_OWNER)).toMatchObject({
      customCount: 0,
      storageAvailable: true,
    });
    expect(
      saveCustomPromptTemplate(SYSTEM_OWNER, { title: 'Recovery', body: 'Recovery body' }),
    ).toMatchObject({
      ok: true,
    });
    expect(loadPromptTemplateCatalog(SYSTEM_OWNER)).toMatchObject({
      customCount: 1,
      storageAvailable: true,
    });
    expect(localStorage.getItem(promptTemplateStorageKey(SYSTEM_OWNER))).toContain('Recovery');
  });

  it('bounds custom template count and body size', () => {
    for (let index = 0; index < MAX_CUSTOM_PROMPT_TEMPLATES + 2; index += 1) {
      saveCustomPromptTemplate(SYSTEM_OWNER, {
        title: `Template ${index}`,
        body: `${index}-${'x'.repeat(MAX_PROMPT_TEMPLATE_BODY_LENGTH + 20)}`,
      });
    }

    const custom = loadPromptTemplateCatalog(SYSTEM_OWNER).templates.filter(
      (template) => template.scope === 'custom',
    );
    expect(custom).toHaveLength(MAX_CUSTOM_PROMPT_TEMPLATES);
    expect(custom[0].title).toBe(`Template ${MAX_CUSTOM_PROMPT_TEMPLATES + 1}`);
    expect(custom[0].body).toHaveLength(MAX_PROMPT_TEMPLATE_BODY_LENGTH);
  });

  it('deletes only custom templates and leaves builtin templates available', () => {
    const saved = saveCustomPromptTemplate(SYSTEM_OWNER, {
      title: 'Temporary',
      body: 'Inspect temporary state',
    });
    expect(saved.template).toBeDefined();

    expect(deleteCustomPromptTemplate(SYSTEM_OWNER, saved.template!.id)).toBe(true);

    const catalog = loadPromptTemplateCatalog(SYSTEM_OWNER);
    expect(catalog.customCount).toBe(0);
    expect(catalog.templates).toHaveLength(builtinPromptTemplates.length);
    expect(localStorage.getItem(PROMPT_TEMPLATE_STORAGE_KEY)).toBeNull();
    expect(localStorage.getItem(promptTemplateStorageKey(SYSTEM_OWNER))).toBeNull();
  });

  it('keeps custom templates isolated between authenticated users', () => {
    expect(
      saveCustomPromptTemplate(ALICE_OWNER, {
        title: 'Orders incident',
        body: 'Inspect the private orders incident context',
      }).ok,
    ).toBe(true);

    const alice = loadPromptTemplateCatalog(ALICE_OWNER);
    const bob = loadPromptTemplateCatalog(BOB_OWNER);

    expect(alice.templates[0]).toMatchObject({ title: 'Orders incident', scope: 'custom' });
    expect(bob.customCount).toBe(0);
    expect(localStorage.getItem(promptTemplateStorageKey(ALICE_OWNER))).not.toBeNull();
    expect(localStorage.getItem(promptTemplateStorageKey(BOB_OWNER))).toBeNull();
  });

  it('does not assign ownerless legacy templates to the first authenticated user', () => {
    localStorage.setItem(
      PROMPT_TEMPLATE_STORAGE_KEY,
      JSON.stringify([{ title: 'Previous user', body: 'Private previous-user context' }]),
    );

    const catalog = loadPromptTemplateCatalog(ALICE_OWNER);

    expect(catalog.customCount).toBe(0);
    expect(localStorage.getItem(PROMPT_TEMPLATE_STORAGE_KEY)).toBeNull();
    expect(localStorage.getItem(promptTemplateStorageKey(ALICE_OWNER))).toBeNull();
  });

  it('migrates the ownerless legacy catalog in unauthenticated single-user mode', () => {
    localStorage.setItem(
      PROMPT_TEMPLATE_STORAGE_KEY,
      JSON.stringify([{ id: 'legacy', title: 'Local template', body: 'Inspect local broker' }]),
    );

    const catalog = loadPromptTemplateCatalog(SYSTEM_OWNER);

    expect(catalog.templates[0]).toMatchObject({ title: 'Local template', scope: 'custom' });
    expect(localStorage.getItem(PROMPT_TEMPLATE_STORAGE_KEY)).toBeNull();
    expect(localStorage.getItem(promptTemplateStorageKey(SYSTEM_OWNER))).not.toBeNull();
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

    expect(loadPromptTemplateCatalog(SYSTEM_OWNER)).toMatchObject({
      customCount: 0,
      storageAvailable: false,
    });
    expect(
      saveCustomPromptTemplate(SYSTEM_OWNER, { title: 'Blocked', body: 'Blocked body' }),
    ).toEqual({
      ok: false,
      reason: 'storage_unavailable',
    });
  });
});
