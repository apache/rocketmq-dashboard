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
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { LangProvider } from '../../../i18n/LangContext';
import ProducerPage from '../Producer';
import { fetchTopicList, queryProducerConnection } from '../../../api/producer';

vi.mock('../../../api/producer', () => ({
  fetchTopicList: vi.fn(),
  queryProducerConnection: vi.fn(),
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

const renderWithProviders = (ui: React.ReactElement) => {
  return render(
    <App>
      <LangProvider>{ui}</LangProvider>
    </App>,
  );
};

describe('ProducerPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(fetchTopicList).mockResolvedValue(['order-events', 'payment-events']);
    vi.mocked(queryProducerConnection).mockResolvedValue([]);
  });

  it('loads topic options after mount', async () => {
    renderWithProviders(<ProducerPage />);

    await waitFor(() => {
      expect(fetchTopicList).toHaveBeenCalledTimes(1);
    });
  });

  it('renders topic options loaded from the API', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProducerPage />);

    await waitFor(() => {
      expect(fetchTopicList).toHaveBeenCalledTimes(1);
    });

    await user.click(screen.getByRole('combobox'));
    await screen.findByRole('option', { name: 'order-events' });
    expect(await screen.findByRole('option', { name: 'payment-events' })).toBeInTheDocument();
  });

  it('queries all producer connections for a topic without requiring a group', async () => {
    const user = userEvent.setup();
    vi.mocked(queryProducerConnection).mockResolvedValue([
      {
        clientId: 'producer-1',
        clientAddr: '192.168.1.10',
        language: 'JAVA',
        versionDesc: '5.1.0',
      },
    ]);
    renderWithProviders(<ProducerPage />);

    await waitFor(() => expect(fetchTopicList).toHaveBeenCalledTimes(1));
    const topicSelect = screen.getByRole('combobox');
    fireEvent.mouseDown(topicSelect.parentElement!);
    await user.click(
      await screen.findByText('order-events', { selector: '.ant-select-item-option-content' }),
    );
    await user.click(screen.getByRole('button', { name: /搜索/ }));

    await waitFor(() => {
      expect(queryProducerConnection).toHaveBeenCalledWith('order-events', undefined);
    });
    expect(await screen.findByText('producer-1')).toBeInTheDocument();
  });
});
