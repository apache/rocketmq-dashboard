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

const OUTCOME_KEYS = [
  'resourceModel.outcomeFreeTitle',
  'resourceModel.outcomeOwnTitle',
  'resourceModel.outcomeOtherTitle',
] as const;

describe('ResourceRuleHelp', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('keeps the rule hidden until the question mark is used', async () => {
    const user = userEvent.setup();
    renderHelp('zh');
    expect(screen.queryByText('创建时的归属规则')).toBeNull();

    await user.click(screen.getByRole('button', { name: '查看资源命名与归属规则' }));
    expect(await screen.findByText('创建时的归属规则')).toBeInTheDocument();
  });

  it('opens from the keyboard so the form stays reachable without a mouse', async () => {
    const user = userEvent.setup();
    renderHelp('zh');
    const trigger = screen.getByRole('button', { name: '查看资源命名与归属规则' });
    trigger.focus();
    await user.keyboard('{Enter}');
    expect(await screen.findByText('创建时的归属规则')).toBeInTheDocument();
  });

  it('spells out the three creation outcomes', async () => {
    const user = userEvent.setup();
    renderHelp('zh');
    await user.click(screen.getByRole('button', { name: '查看资源命名与归属规则' }));

    OUTCOME_KEYS.forEach((key) => {
      expect(screen.getByText(translations[key].zh)).toBeInTheDocument();
    });
    expect(screen.getByText(translations['resourceModel.outcomeOtherDesc'].zh)).toBeInTheDocument();
  });

  it.each(['zh', 'en'] as const)(
    'resolves every %s label without leaking i18n keys',
    async (lang) => {
      const user = userEvent.setup();
      const { baseElement } = renderHelp(lang);
      await user.click(
        screen.getByRole('button', { name: translations['resourceModel.helpAria'][lang] }),
      );

      expect(
        await screen.findByText(translations['resourceModel.modalTitle'][lang]),
      ).toBeInTheDocument();
      expect(baseElement.textContent).not.toContain('resourceModel.');
    },
  );

  it('hands the modal back to antd to close from the footer button', async () => {
    const user = userEvent.setup();
    renderHelp('zh');
    await user.click(screen.getByRole('button', { name: '查看资源命名与归属规则' }));
    expect(await screen.findByText('创建时的归属规则')).toBeInTheDocument();

    // antd pads a two-character CJK button label with a space, so match it loosely.
    await user.click(screen.getByRole('button', { name: /关\s*闭/ }));
    // jsdom never fires animationend, so a closed antd modal stays mounted mid-leave instead of
    // leaving the accessibility tree. The leave class on the mask is the observable proof that
    // `open` flipped back to false.
    await waitFor(() =>
      expect(document.querySelector('.ant-modal-mask')?.className).toContain('ant-fade-leave'),
    );
  });
});
