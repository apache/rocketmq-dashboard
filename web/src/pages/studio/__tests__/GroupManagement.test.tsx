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

import { describe, it, expect, vi, beforeAll } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { LangProvider } from '../../../i18n/LangContext';
import GroupManagement from '../GroupManagement';

// Mock matchMedia for antd responsive components
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

// Mock react-router-dom
vi.mock('react-router-dom', () => ({
  useNavigate: () => vi.fn(),
  useParams: () => ({}),
}));

const renderWithProviders = (ui: React.ReactElement) => {
  return render(
    <App>
      <LangProvider>{ui}</LangProvider>
    </App>,
  );
};

describe('GroupManagement Page', () => {
  it('should render the page title', () => {
    renderWithProviders(<GroupManagement />);
    expect(screen.getByText('消费组管理')).toBeInTheDocument();
  });

  it('should render search input with placeholder', () => {
    renderWithProviders(<GroupManagement />);
    expect(screen.getByPlaceholderText('搜索消费组')).toBeInTheDocument();
  });

  it('should render create group button', () => {
    renderWithProviders(<GroupManagement />);
    expect(screen.getByText('创建消费组')).toBeInTheDocument();
  });

  it('should render reset button', () => {
    renderWithProviders(<GroupManagement />);
    expect(screen.getByText('重置')).toBeInTheDocument();
  });

  it('should show an explicit unavailable state instead of mock consumer groups', () => {
    renderWithProviders(<GroupManagement />);
    expect(screen.getByText('当前版本尚未接入真实消费组管理接口，已停止展示模拟消费组数据。')).toBeInTheDocument();
    expect(screen.queryByText('order-consumer-group')).not.toBeInTheDocument();
    expect(screen.queryByText('payment-consumer-group')).not.toBeInTheDocument();
  });

  it('should not render row actions without real consumer group data', () => {
    renderWithProviders(<GroupManagement />);
    expect(screen.queryByText('详情')).not.toBeInTheDocument();
  });

  it('should keep mock groups hidden when filtering by search text', async () => {
    const user = userEvent.setup();
    renderWithProviders(<GroupManagement />);
    const searchInput = screen.getByPlaceholderText('搜索消费组');
    await user.type(searchInput, 'ORDER');
    expect(screen.getByText('当前版本尚未接入真实消费组管理接口，已停止展示模拟消费组数据。')).toBeInTheDocument();
    expect(screen.queryByText('order-consumer-group')).not.toBeInTheDocument();
    expect(screen.queryByText('payment-consumer-group')).not.toBeInTheDocument();
  });
});
