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
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { LangProvider } from '../../../i18n/LangContext';
import LiteTopic from '../LiteTopic';

const apiMocks = vi.hoisted(() => ({
  queryLiteTopicCapability: vi.fn(),
  queryLiteTopicQuota: vi.fn(),
  queryLiteTopicList: vi.fn(),
  queryLiteTopicSession: vi.fn(),
  extendLiteTopicTTL: vi.fn(),
}));

vi.mock('../../../api/liteTopic', () => apiMocks);

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

const renderPage = () =>
  render(
    <App>
      <LangProvider>
        <LiteTopic />
      </LangProvider>
    </App>,
  );

describe('LiteTopic Page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    apiMocks.queryLiteTopicCapability.mockResolvedValue({ supported: true });
    apiMocks.queryLiteTopicQuota.mockResolvedValue(null);
    apiMocks.queryLiteTopicList.mockResolvedValue([
      {
        topicPattern: 'order-*',
        sessionIds: ['session-1'],
      },
    ]);
    apiMocks.queryLiteTopicSession.mockResolvedValue({
      sessionId: 'session-1',
      totalMessages: 100,
      consumedMessages: 0,
      popProgress: 96,
    });
  });

  it('displays the session POP progress returned by the API as a percentage', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByText('查看会话'));

    expect(apiMocks.queryLiteTopicSession).toHaveBeenCalledWith('session-1');
    const popProgressLabel = await screen.findByText('Pop 进度');
    expect(within(popProgressLabel.parentElement!).getByText('96%')).toBeInTheDocument();
  });
});
