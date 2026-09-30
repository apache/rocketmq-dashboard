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

import { fireEvent, render, screen } from '@testing-library/react';
import { beforeAll, describe, expect, it, vi } from 'vitest';
import { InstanceSelect } from '../InstanceSelect';
import { LangProvider } from '../../i18n/LangContext';

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

const renderSelector = (ui: React.ReactElement) => render(<LangProvider>{ui}</LangProvider>);

describe('InstanceSelect localization', () => {
  // The selector is the shared entry point of every instance-scoped page; its default
  // placeholder and its empty list used to be hardcoded Chinese regardless of the
  // display language.
  it('resolves the default placeholder and the empty-list text in the display language', async () => {
    window.localStorage.setItem('rocketmq-studio-language', 'en');
    renderSelector(
      <InstanceSelect value={undefined} onChange={vi.fn()} options={[]} failed={false} />,
    );
    // The placeholder shows on the closed select; the empty-list text only exists in the
    // open dropdown.
    expect(screen.getByText('Select an instance')).toBeInTheDocument();
    fireEvent.mouseDown(screen.getByRole('combobox'));
    expect(await screen.findByText('No matching instances')).toBeInTheDocument();
    expect(screen.queryByText('选择实例')).not.toBeInTheDocument();
    expect(screen.queryByText('暂无匹配实例')).not.toBeInTheDocument();
  });

  it('keeps an explicitly provided placeholder', () => {
    window.localStorage.setItem('rocketmq-studio-language', 'zh');
    renderSelector(
      <InstanceSelect
        value={undefined}
        onChange={vi.fn()}
        options={[]}
        failed={false}
        placeholder="按名称过滤"
      />,
    );

    expect(screen.getByText('按名称过滤')).toBeInTheDocument();
  });
});
