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

import { beforeAll, describe, expect, it, vi } from 'vitest';
import { render } from '@testing-library/react';
import { App } from 'antd';
import { LangProvider } from '../../../i18n/LangContext';
import { LANGUAGE_STORAGE_KEY } from '../../../i18n/languagePreference';
import translations from '../../../i18n/translations';
import ResourceModelPage from '../ResourceModel';

beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
});

const renderPage = (lang: 'zh' | 'en') => {
  localStorage.setItem(LANGUAGE_STORAGE_KEY, lang);
  return render(
    <App>
      <LangProvider>
        <ResourceModelPage />
      </LangProvider>
    </App>,
  );
};

describe('ResourceModel page', () => {
  it('renders the topology diagram and the summary banner', () => {
    const { getByTestId } = renderPage('zh');
    expect(getByTestId('resource-model-topology').tagName).toBe('svg');
    expect(getByTestId('resource-model-banner')).toBeInTheDocument();
  });

  // The creation-time decision flow was a backend implementation detail; the rule now lives in
  // the ResourceRuleHelp modal on the create forms, so the page must not carry the diagram back.
  it('leaves the creation-decision flow to the create forms', () => {
    const { queryByTestId, container } = renderPage('zh');
    expect(queryByTestId('resource-model-flow')).toBeNull();
    expect(container.textContent).not.toContain('创建时的判定');
  });

  // An untranslated label falls back to the raw key, so a missing entry shows up as
  // "resourceModel.xxx" in the rendered text.
  it.each(['zh', 'en'] as const)('resolves every %s label without leaking i18n keys', (lang) => {
    const { container } = renderPage(lang);
    expect(container.textContent).not.toContain('resourceModel.');
    expect(container.textContent?.length).toBeGreaterThan(500);
  });

  it('keeps the whole resourceModel catalogue filled in both languages', () => {
    const keys = Object.keys(translations).filter((key) => key.startsWith('resourceModel.'));
    expect(keys.length).toBeGreaterThan(30);
    keys.forEach((key) => {
      expect(translations[key].zh.trim(), key).not.toBe('');
      expect(translations[key].en.trim(), key).not.toBe('');
    });
  });

  // jsdom has no layout, so the SVG boxes cannot be measured here. The budgets come from a
  // browser measurement of the 14px UI font (~7px per Latin char, ~14px per CJK char) against
  // the usable width of each hand-drawn box; a label that grows past it would be clipped.
  const WIDE_CHAR = /[\u2E80-\u9FFF\u3000-\u303F\uF900-\uFAFF\uFF00-\uFF60]/;
  const estimatedWidth = (text: string) =>
    [...text].reduce((sum, ch) => sum + (WIDE_CHAR.test(ch) ? 14 : 7), 0);

  const SVG_LABEL_BUDGETS: Array<[string, number]> = [
    // topology: resource chips (169 / 242 wide, text starts 10 inside)
    ['resourceModel.svgChipOrdersTrade', 150],
    ['resourceModel.svgChipPaymentsTrade', 150],
    ['resourceModel.svgChipOrdersLogistics', 150],
    ['resourceModel.svgChipOrdersStaging', 224],
    // domain titles and body notes
    ['resourceModel.svgDomainProd', 398],
    ['resourceModel.svgDomainStaging', 242],
    ['resourceModel.svgMergeNote1', 398],
    ['resourceModel.svgMergeNote2', 398],
    ['resourceModel.svgCrossDomainNote', 242],
    ['resourceModel.svgCrossDomainNote2', 242],
    ['resourceModel.svgCrossLine1', 242],
    ['resourceModel.svgCrossLine2', 242],
    ['resourceModel.svgConflictLine1', 374],
    ['resourceModel.svgConflictLine2', 374],
    // instance boxes (193 / 266 wide, text starts 26 inside)
    ['resourceModel.svgInstTrade', 155],
    ['resourceModel.svgInstLogistics', 155],
    ['resourceModel.svgInstStaging', 228],
    ['resourceModel.svgBoundNote', 155],
  ];

  it.each(SVG_LABEL_BUDGETS)('keeps %s inside its drawn box', (key, budget) => {
    (['zh', 'en'] as const).forEach((lang) => {
      const width = estimatedWidth(translations[key][lang]);
      expect(width, `${key}.${lang} = "${translations[key][lang]}"`).toBeLessThanOrEqual(budget);
    });
  });
});
