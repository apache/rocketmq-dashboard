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
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { LangProvider } from '../../../i18n/LangContext';
import SettingsPage from '../index';

vi.mock('../GeneralSettingsTab', () => ({
  GeneralSettingsTab: () => <div>general settings tab</div>,
}));
vi.mock('../AiAssistantTab', () => ({
  AiAssistantTab: () => <div>ai assistant tab</div>,
}));
vi.mock('../CloudCredentialTab', () => ({
  CloudCredentialTab: () => <div>cloud credential tab</div>,
}));
vi.mock('../DataSourceTab', () => ({
  DataSourceTab: () => <div>data source tab</div>,
}));
vi.mock('../AboutTab', () => ({
  AboutTab: () => <div>about tab</div>,
}));

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

const LocationProbe = () => {
  const { search } = useLocation();
  return <output aria-label="location-search">{search}</output>;
};

const renderPage = (initialEntry: string) =>
  render(
    <LangProvider>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route
            path="/settings"
            element={
              <>
                <SettingsPage />
                <LocationProbe />
              </>
            }
          />
        </Routes>
      </MemoryRouter>
    </LangProvider>,
  );

describe('SettingsPage', () => {
  it('preserves unrelated query parameters when switching tabs', async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    renderPage('/settings?source=nav&tab=general&focus=llm');

    await user.click(screen.getByRole('tab', { name: 'AI 助手' }));

    await waitFor(() => {
      expect(screen.getByLabelText('location-search')).toHaveTextContent(
        '?source=nav&tab=ai&focus=llm',
      );
    });
  });
});
