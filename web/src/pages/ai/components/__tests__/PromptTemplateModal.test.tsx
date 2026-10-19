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

import { ConfigProvider, theme } from 'antd';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import PromptTemplateModal from '../PromptTemplateModal';
import {
  builtinPromptTemplates,
  loadPromptTemplateCatalog,
  saveCustomPromptTemplate,
  deleteCustomPromptTemplate,
} from '../../promptTemplates';
import useAuthStore from '../../../../stores/authStore';

// Comparing raw CSS text would depend on how each side spells a colour, so both the element's
// value and the expected token go through the same parser before they are compared.
const normalizeBackground = (value: string): string => {
  const probe = document.createElement('span');
  probe.style.background = value;
  return probe.style.background;
};

describe('prompt templates with denied storage access', () => {
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
  afterEach(() => vi.restoreAllMocks());

  function denyStorage() {
    vi.spyOn(window, 'localStorage', 'get').mockImplementation(() => {
      throw new DOMException('Storage access denied', 'SecurityError');
    });
  }

  it('reportsUnavailableStorageAcrossPublicOperationsTest', () => {
    denyStorage();
    expect(loadPromptTemplateCatalog()).toMatchObject({
      storageAvailable: false,
      customCount: 0,
      templates: builtinPromptTemplates,
    });
    expect(saveCustomPromptTemplate({ title: 'check', body: 'inspect brokers' })).toEqual({
      ok: false,
      reason: 'storage_unavailable',
    });
    expect(deleteCustomPromptTemplate('custom-existing')).toBe(false);
  });

  it('keepsBuiltinPromptsUsableInTheModalTest', async () => {
    denyStorage();
    const apply = vi.fn();
    render(
      <PromptTemplateModal
        open
        onClose={vi.fn()}
        inputValue=""
        mode="chat"
        enhance={false}
        onApply={apply}
      />,
    );
    expect(
      await screen.findByText('浏览器存储不可用，自定义 Prompt 模板暂时无法保存。'),
    ).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: /使\s*用/ })[0]);
    expect(apply).toHaveBeenCalledWith(
      expect.objectContaining({
        id: builtinPromptTemplates[0].id,
        body: builtinPromptTemplates[0].body,
        mode: builtinPromptTemplates[0].mode,
      }),
      'replace',
    );
  });

  it('canSaveLoadAndDeleteAfterStorageAccessRecoversTest', () => {
    denyStorage();
    expect(loadPromptTemplateCatalog().storageAvailable).toBe(false);
    vi.restoreAllMocks();
    const storage = localStorage;
    const original = storage.getItem('rocketmq-studio-ai-prompt-templates');
    try {
      storage.removeItem('rocketmq-studio-ai-prompt-templates');
      const result = saveCustomPromptTemplate({ title: 'Recovered', body: '完整检查内容' });
      expect(result.ok).toBe(true);
      expect(loadPromptTemplateCatalog().templates[0]).toMatchObject({
        title: 'Recovered',
        body: '完整检查内容',
      });
      expect(deleteCustomPromptTemplate(result.template!.id)).toBe(true);
      expect(loadPromptTemplateCatalog().customCount).toBe(0);
    } finally {
      if (original === null) storage.removeItem('rocketmq-studio-ai-prompt-templates');
      else storage.setItem('rocketmq-studio-ai-prompt-templates', original);
    }
  });

  it('rendersTheSaveBlockOnThemeTokensInDarkModeTest', async () => {
    render(
      <ConfigProvider theme={{ algorithm: theme.darkAlgorithm }}>
        <PromptTemplateModal
          open
          onClose={vi.fn()}
          inputValue=""
          mode="chat"
          enhance={false}
          onApply={vi.fn()}
        />
      </ConfigProvider>,
    );

    const saveBlock = (await screen.findByText('保存当前输入')).closest('div') as HTMLElement;
    // Fixed light values leave the dark theme's light text unreadable on them, so the block has
    // to follow the theme's fill and border tokens instead.
    const dark = theme.getDesignToken({ algorithm: theme.darkAlgorithm });
    expect(normalizeBackground(saveBlock.style.background)).toBe(
      normalizeBackground(dark.colorFillQuaternary),
    );
    expect(normalizeBackground(saveBlock.style.background)).not.toBe(
      normalizeBackground('#fafafa'),
    );
    // Same parser trick for the border: jsdom spells the token's hex as rgb(), so both sides of
    // the comparison go through a probe before they are matched.
    const borderProbe = document.createElement('span');
    borderProbe.style.border = `1px solid ${dark.colorBorderSecondary}`;
    expect(saveBlock.style.border).toBe(borderProbe.style.border);
  });
});

describe('prompt templates scoped to the signed-in operator', () => {
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
  afterEach(() => vi.restoreAllMocks());

  it("does not show another operator's custom template after an account switch", async () => {
    localStorage.clear();
    useAuthStore.getState().login('alice', 1, false);
    saveCustomPromptTemplate(
      { title: 'Alice private template', body: 'Inspect a private topic' },
      undefined,
      100,
      'id:1',
    );
    const view = render(
      <PromptTemplateModal
        open
        onClose={vi.fn()}
        inputValue=""
        mode="chat"
        enhance={false}
        onApply={vi.fn()}
      />,
    );
    try {
      expect(await screen.findByText('Alice private template')).toBeInTheDocument();
      act(() => useAuthStore.getState().login('bob', 2, false));
      await waitFor(() =>
        expect(screen.queryByText('Alice private template')).not.toBeInTheDocument(),
      );
    } finally {
      view.unmount();
      useAuthStore.getState().logout();
      localStorage.clear();
    }
  });
});
