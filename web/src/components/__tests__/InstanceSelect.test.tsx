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

import { afterEach, describe, it, expect, vi } from 'vitest';
import { useState } from 'react';
import userEvent from '@testing-library/user-event';
import { render, screen, fireEvent } from '@testing-library/react';
import InstanceSelect from '../InstanceSelect';
import { LangProvider } from '../../i18n/LangContext';
import { LANGUAGE_STORAGE_KEY } from '../../i18n/languagePreference';

const renderWithLang = (ui: React.ReactElement) => render(<LangProvider>{ui}</LangProvider>);

describe('InstanceSelect', () => {
  afterEach(() => {
    localStorage.clear();
  });

  it('renders the placeholder in Chinese by default', () => {
    renderWithLang(<InstanceSelect onChange={() => {}} options={[]} />);
    expect(screen.getByText('选择实例')).toBeInTheDocument();
  });

  it('renders the placeholder in English when the language is en', () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
    renderWithLang(<InstanceSelect onChange={() => {}} options={[]} />);
    expect(screen.getByText('Select Instance')).toBeInTheDocument();
  });

  it('renders the empty-list hint in English when the language is en', async () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
    renderWithLang(<InstanceSelect onChange={() => {}} options={[]} />);
    const select = screen.getByRole('combobox');
    fireEvent.mouseDown(select.parentElement!);
    expect(await screen.findByText('No matching instances')).toBeInTheDocument();
  });

  it('keeps an explicit placeholder when one is provided', () => {
    renderWithLang(<InstanceSelect onChange={() => {}} options={[]} placeholder="My instance" />);
    expect(screen.getByText('My instance')).toBeInTheDocument();
  });

  it.each([
    ['zh', '选择实例'],
    ['en', 'Select Instance'],
  ] as const)('namesTheSearchableInstanceSelectorIn%sTest', (lang, label) => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, lang);
    renderWithLang(
      <InstanceSelect
        value="cluster-a"
        onChange={() => {}}
        options={[{ value: 'cluster-a', label: 'Cluster A' }]}
      />,
    );
    expect(screen.getByRole('combobox', { name: label })).toBeEnabled();
  });

  it.each([
    ['zh', '选择实例'],
    ['en', 'Select Instance'],
  ] as const)('keepsNameDuringSearchSelectionAndClearTest (%s)', async (lang, label) => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, lang);
    const onChange = vi.fn();
    const Probe = () => {
      const [value, setValue] = useState('cluster-a');
      return (
        <InstanceSelect
          value={value}
          onChange={(next) => {
            onChange(next);
            setValue(next);
          }}
          options={[
            { value: 'cluster-a', label: 'Cluster A' },
            { value: 'cluster-b', label: 'Cluster B' },
          ]}
        />
      );
    };
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    const { container } = renderWithLang(<Probe />);
    const select = screen.getByRole('combobox', { name: label });
    await user.type(select, 'cluster b');
    expect(select).toHaveAccessibleName(label);
    fireEvent.keyDown(select, { key: 'ArrowDown', keyCode: 40 });
    fireEvent.keyDown(select, { key: 'Enter', keyCode: 13 });
    expect(onChange).toHaveBeenLastCalledWith('cluster-b');
    expect(select).toHaveAccessibleName(label);
    await user.click(container.querySelector('.ant-select-clear')!);
    // Clearing intentionally falls back to the first instance rather than an empty route.
    expect(onChange).toHaveBeenLastCalledWith('cluster-a');
    expect(select).toHaveAccessibleName(label);
  });

  it('preservesTheExplicitInstancePromptAsAccessibleNameTest', () => {
    renderWithLang(
      <InstanceSelect onChange={() => {}} options={[]} placeholder="Source instance" />,
    );
    expect(screen.getByRole('combobox', { name: 'Source instance' })).toBeInTheDocument();
  });

  it('retainsTheInstanceSelectorNameWhenTheInstanceListFailsTest', () => {
    renderWithLang(<InstanceSelect onChange={() => {}} options={[]} failed />);
    expect(screen.getByRole('combobox', { name: '选择实例' })).toBeInTheDocument();
  });
});
