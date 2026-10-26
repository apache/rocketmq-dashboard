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

import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { LangProvider } from '../../i18n/LangContext';
import { LANGUAGE_STORAGE_KEY } from '../../i18n/languagePreference';
import translations from '../../i18n/translations';
import ResourceRuleHelp from '../ResourceRuleHelp';

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

const MODAL_TITLE = translations['resourceModel.modalTitle'].zh;
const HELP_ARIA = translations['resourceModel.helpAria'].zh;

const renderHelp = (lang: 'zh' | 'en') => {
  localStorage.setItem(LANGUAGE_STORAGE_KEY, lang);
  return render(
    <App>
      <LangProvider>
        <ResourceRuleHelp />
      </LangProvider>
    </App>,
  );
};

const openHelp = async (user: ReturnType<typeof userEvent.setup>, lang: 'zh' | 'en' = 'zh') => {
  await user.click(
    screen.getByRole('button', { name: translations['resourceModel.helpAria'][lang] }),
  );
  return screen.findByRole('dialog', { name: translations['resourceModel.modalTitle'][lang] });
};

describe('ResourceRuleHelp', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('keeps the rule hidden until the question mark is used', async () => {
    const user = userEvent.setup();
    renderHelp('zh');
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(await openHelp(user)).toBeInTheDocument();
  });

  it('opens from the keyboard so the form stays reachable without a mouse', async () => {
    const user = userEvent.setup();
    renderHelp('zh');
    const trigger = screen.getByRole('button', { name: HELP_ARIA });
    trigger.focus();
    await user.keyboard('{Enter}');
    expect(await screen.findByText(MODAL_TITLE)).toBeInTheDocument();
  });

  // The standalone resource model page is gone (user decision 2026-10-26); the topology and the
  // four ownership rules now live only inside this modal.
  it('carries the ownership topology and the four rules into the modal', async () => {
    const user = userEvent.setup();
    renderHelp('zh');
    await openHelp(user);

    expect(screen.getByTestId('resource-ownership-topology').tagName).toBe('svg');
    expect(screen.getByText(translations['resourceModel.rulesTitle'].zh)).toBeInTheDocument();
    (['rule1', 'rule2', 'rule3', 'rule4'] as const).forEach((suffix) => {
      expect(screen.getByText(translations[`resourceModel.${suffix}`].zh)).toBeInTheDocument();
    });
    // The three-outcome list was dropped as redundant with rule1 (user decision 2026-10-26).
    expect(screen.queryByText('创建时的三种结果')).toBeNull();
  });

  it.each(['zh', 'en'] as const)(
    'resolves every %s label without leaking i18n keys',
    async (lang) => {
      const user = userEvent.setup();
      const { baseElement } = renderHelp(lang);
      expect(await openHelp(user, lang)).toBeInTheDocument();
      expect(baseElement.textContent).not.toContain('resourceModel.');
    },
  );

  it('hands the modal back to antd to close from the footer button', async () => {
    const user = userEvent.setup();
    renderHelp('zh');
    await openHelp(user);

    // antd pads a two-character CJK button label with a space, so match it loosely.
    await user.click(screen.getByRole('button', { name: /关\s*闭/ }));
    // jsdom never fires animationend, so a closed antd modal stays mounted mid-leave instead of
    // leaving the accessibility tree. The leave class on the mask is the observable proof that
    // `open` flipped back to false.
    await waitFor(() =>
      expect(document.querySelector('.ant-modal-mask')?.className).toContain('ant-fade-leave'),
    );
  });

  it('keeps the whole resourceModel catalogue filled in both languages', () => {
    const keys = Object.keys(translations).filter((key) => key.startsWith('resourceModel.'));
    // 27 keys since the outcomes list was dropped: 19 topology labels + 5 rule entries +
    // modalTitle / modalIntro / helpAria.
    expect(keys.length).toBeGreaterThan(25);
    keys.forEach((key) => {
      expect(translations[key].zh.trim(), key).not.toBe('');
      expect(translations[key].en.trim(), key).not.toBe('');
    });
  });

  // jsdom has no layout, so the SVG boxes cannot be measured here. The budgets come from a
  // browser measurement of the 14px UI font (~7px per Latin char, ~14px per CJK char) against
  // the usable width of each hand-drawn box; a label that grows past it would be clipped.
  const isWideChar = (ch: string) => {
    const code = ch.codePointAt(0) ?? 0;
    return (
      (code >= 0x2e80 && code <= 0x9fff) ||
      (code >= 0x3000 && code <= 0x303f) ||
      (code >= 0xf900 && code <= 0xfaff) ||
      (code >= 0xff00 && code <= 0xff60)
    );
  };
  const estimatedWidth = (text: string) =>
    [...text].reduce((sum, ch) => sum + (isWideChar(ch) ? 14 : 7), 0);

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
