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

import { afterEach, describe, it, expect } from 'vitest';
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
});
