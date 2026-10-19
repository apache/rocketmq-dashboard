/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { LangProvider } from '../../../i18n/LangContext';
import PromptTemplateModal from '../components/PromptTemplateModal';
import { loadPromptTemplateCatalog } from '../promptTemplates';

const before = () => {
  localStorage.setItem('rocketmq-studio-language', 'en');
  render(
    <App>
      <LangProvider>
        <PromptTemplateModal
          open
          inputValue="diagnose consumer lag on orders topic"
          mode="diagnose"
          enhance={false}
          onClose={vi.fn()}
          onApply={vi.fn()}
        />
      </LangProvider>
    </App>,
  );
};

describe('PromptTemplateModal', () => {
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

  beforeEach(() => {
    localStorage.clear();
  });

  it('a second click on Save after a successful save neither saves nor errors', async () => {
    const user = userEvent.setup();
    before();

    await user.type(screen.getByLabelText('Template title'), 'lag runbook');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    const saved = loadPromptTemplateCatalog().templates.filter(
      (template) => template.scope === 'custom',
    );
    expect(saved).toHaveLength(1);
    expect(saved[0]?.title).toBe('lag runbook');

    // Double-click / repeated Enter lands on the Save button again right after the
    // first save cleared the title: that must be a no-op, not a "title required"
    // error toast next to a success toast.
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(screen.queryByText('Enter a template title')).not.toBeInTheDocument();
    expect(
      loadPromptTemplateCatalog().templates.filter((template) => template.scope === 'custom'),
    ).toHaveLength(1);
  });
});
