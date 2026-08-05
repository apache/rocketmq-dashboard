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
import {
  fetchProducerGroups,
  fetchTopicList,
  queryProducerConnection,
} from '../../../api/producer';

vi.mock('../../../api/producer', () => ({
  fetchProducerGroups: vi.fn(),
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
    vi.mocked(fetchProducerGroups).mockResolvedValue(['pg-order', 'pg-payment']);
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

    await user.click(screen.getAllByRole('combobox')[0]);
    await screen.findByRole('option', { name: 'order-events' });
    expect(await screen.findByRole('option', { name: 'payment-events' })).toBeInTheDocument();
  });

  it('suggests active producer groups while keeping free-form input', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProducerPage />);

    await waitFor(() => expect(fetchProducerGroups).toHaveBeenCalledTimes(1));
    const groupInput = screen.getAllByRole('combobox')[1];
    await user.type(groupInput, 'payment');

    expect(await screen.findByRole('option', { name: 'pg-payment' })).toBeInTheDocument();
  });

  it('queries producer connections with the required topic and group', async () => {
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
    const [topicSelect, groupInput] = screen.getAllByRole('combobox');
    fireEvent.mouseDown(topicSelect.parentElement!);
    await user.click(
      await screen.findByText('order-events', { selector: '.ant-select-item-option-content' }),
    );
    await user.type(groupInput, 'order-producer');
    await user.click(screen.getByRole('button', { name: /搜索/ }));

    await waitFor(() => {
      expect(queryProducerConnection).toHaveBeenCalledWith('order-events', 'order-producer');
    });
    expect(await screen.findByText('producer-1')).toBeInTheDocument();
  });

  it('does not query without a producer group', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProducerPage />);

    await waitFor(() => expect(fetchTopicList).toHaveBeenCalledTimes(1));
    const [topicSelect] = screen.getAllByRole('combobox');
    fireEvent.mouseDown(topicSelect.parentElement!);
    await user.click(
      await screen.findByText('order-events', { selector: '.ant-select-item-option-content' }),
    );
    await user.click(screen.getByRole('button', { name: /搜索/ }));

    expect(
      await screen.findByText('请输入生产者组', { selector: '.ant-form-item-explain-error' }),
    ).toBeInTheDocument();
    expect(queryProducerConnection).not.toHaveBeenCalled();
  });

  it('keeps manual producer group queries available when suggestions fail', async () => {
    vi.mocked(fetchProducerGroups).mockRejectedValue(new Error('broker unavailable'));
    const user = userEvent.setup();
    renderWithProviders(<ProducerPage />);

    await waitFor(() => expect(fetchTopicList).toHaveBeenCalledTimes(1));
    const [topicSelect, groupInput] = screen.getAllByRole('combobox');
    fireEvent.mouseDown(topicSelect.parentElement!);
    await user.click(
      await screen.findByText('order-events', { selector: '.ant-select-item-option-content' }),
    );
    await user.type(groupInput, 'manual-producer');
    await user.click(screen.getByRole('button', { name: /搜索/ }));

    await waitFor(() => {
      expect(queryProducerConnection).toHaveBeenCalledWith('order-events', 'manual-producer');
    });
  });
});
