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
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { LangProvider } from '../../../i18n/LangContext';
import type { LiteTopicItem, LiteTopicQuota } from '../../../api/liteTopic';
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

const createDeferred = <T,>() => {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
};

const createQuota = (currentTopicCount: number): LiteTopicQuota => ({
  currentTopicCount,
  maxTopicCount: 100,
  currentSessionCount: 0,
  maxSessionCount: 100,
  currentCreationRate: 0,
  maxCreationRate: 100,
});

const selectNamespace = async (name: string) => {
  const user = userEvent.setup();
  await user.click(screen.getAllByRole('combobox')[0]);
  await user.click(
    await screen.findByText(name, { selector: '.ant-select-item-option-content' }),
  );
};

describe('LiteTopic Page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    apiMocks.queryLiteTopicCapability.mockResolvedValue({ supported: true });
    apiMocks.queryLiteTopicQuota.mockResolvedValue(null);
    apiMocks.queryLiteTopicList.mockResolvedValue([
      {
        namespace: 'default',
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

  it('keeps namespace identity in the table and builds stable options from the initial list', async () => {
    const initialItems: LiteTopicItem[] = [
      { namespace: 'zeta', topicPattern: 'shared-*' },
      { namespace: 'Alpha', topicPattern: 'shared-*' },
      { namespace: ' alpha ', topicPattern: 'another-*' },
      { namespace: '   ', topicPattern: 'blank-*' },
    ];
    apiMocks.queryLiteTopicList
      .mockResolvedValueOnce(initialItems)
      .mockResolvedValueOnce([{ namespace: 'Alpha', topicPattern: 'filtered-*' }]);

    const user = userEvent.setup();
    const { container } = renderPage();

    expect(await screen.findAllByText('shared-*')).toHaveLength(2);
    expect(screen.getByRole('columnheader', { name: '命名空间' })).toBeInTheDocument();

    const rowKeys = Array.from(container.querySelectorAll('tr[data-row-key]')).map((row) =>
      row.getAttribute('data-row-key'),
    );
    expect(rowKeys).toContain(JSON.stringify(['zeta', 'shared-*']));
    expect(rowKeys).toContain(JSON.stringify(['Alpha', 'shared-*']));

    await user.type(screen.getByPlaceholderText('按 Topic 模式筛选...'), 'filtered');
    await user.click(screen.getByRole('button', { name: /搜索/ }));
    expect(await screen.findByText('filtered-*')).toBeInTheDocument();

    await user.click(screen.getAllByRole('combobox')[0]);
    const dropdown = document.querySelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)');
    expect(dropdown).not.toBeNull();
    expect(
      Array.from(dropdown!.querySelectorAll('.ant-select-item-option-content')).map(
        (option) => option.textContent,
      ),
    ).toEqual(['全部命名空间', 'Alpha', 'zeta']);
  });

  it('keeps an early filtered display while a delayed bootstrap supplies namespace options', async () => {
    const capability = createDeferred<{ supported: boolean }>();
    const bootstrapList = createDeferred<LiteTopicItem[]>();

    apiMocks.queryLiteTopicCapability.mockReturnValue(capability.promise);
    apiMocks.queryLiteTopicList.mockImplementation((pattern: string | undefined) =>
      pattern === 'needle'
        ? Promise.resolve([{ namespace: 'Filtered', topicPattern: 'filtered-result-*' }])
        : bootstrapList.promise,
    );
    apiMocks.queryLiteTopicQuota.mockResolvedValue(createQuota(20));

    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByPlaceholderText('按 Topic 模式筛选...'), 'needle');
    await user.click(screen.getByRole('button', { name: /搜索/ }));
    expect(await screen.findByText('filtered-result-*')).toBeInTheDocument();
    expect(screen.getByText('20 / 100')).toBeInTheDocument();

    await act(async () => {
      capability.resolve({ supported: true });
    });
    await waitFor(() => {
      expect(apiMocks.queryLiteTopicList).toHaveBeenCalledTimes(2);
      expect(apiMocks.queryLiteTopicList).toHaveBeenLastCalledWith();
    });

    await act(async () => {
      bootstrapList.resolve([
        { namespace: 'Beta', topicPattern: 'bootstrap-beta-*' },
        { namespace: 'Alpha', topicPattern: 'bootstrap-alpha-*' },
      ]);
    });

    expect(screen.getByText('filtered-result-*')).toBeInTheDocument();
    expect(screen.getByText('20 / 100')).toBeInTheDocument();
    expect(screen.queryByText('bootstrap-alpha-*')).not.toBeInTheDocument();
    expect(apiMocks.queryLiteTopicQuota).toHaveBeenCalledTimes(1);

    await user.click(screen.getAllByRole('combobox')[0]);
    const dropdown = document.querySelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)');
    expect(dropdown).not.toBeNull();
    expect(
      Array.from(dropdown!.querySelectorAll('.ant-select-item-option-content')).map(
        (option) => option.textContent,
      ),
    ).toEqual(['全部命名空间', 'Alpha', 'Beta']);
  });

  it('collects namespace options from a stale in-flight bootstrap display request', async () => {
    const bootstrapList = createDeferred<LiteTopicItem[]>();
    const bootstrapQuota = createDeferred<LiteTopicQuota>();

    apiMocks.queryLiteTopicList.mockImplementation((pattern: string | undefined) =>
      pattern === 'needle'
        ? Promise.resolve([{ namespace: 'Filtered', topicPattern: 'filtered-result-*' }])
        : bootstrapList.promise,
    );
    apiMocks.queryLiteTopicQuota
      .mockReturnValueOnce(bootstrapQuota.promise)
      .mockResolvedValueOnce(createQuota(20));

    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(apiMocks.queryLiteTopicList).toHaveBeenCalledWith(undefined, undefined);
    });
    await user.type(screen.getByPlaceholderText('按 Topic 模式筛选...'), 'needle');
    await user.click(screen.getByRole('button', { name: /搜索/ }));
    expect(await screen.findByText('filtered-result-*')).toBeInTheDocument();
    expect(screen.getByText('20 / 100')).toBeInTheDocument();

    await act(async () => {
      bootstrapQuota.resolve(createQuota(90));
      bootstrapList.resolve([
        { namespace: 'Beta', topicPattern: 'bootstrap-beta-*' },
        { namespace: 'Alpha', topicPattern: 'bootstrap-alpha-*' },
      ]);
    });

    expect(screen.getByText('filtered-result-*')).toBeInTheDocument();
    expect(screen.getByText('20 / 100')).toBeInTheDocument();
    expect(screen.queryByText('bootstrap-alpha-*')).not.toBeInTheDocument();
    expect(screen.queryByText('90 / 100')).not.toBeInTheDocument();

    await user.click(screen.getAllByRole('combobox')[0]);
    const dropdown = document.querySelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)');
    expect(dropdown).not.toBeNull();
    expect(
      Array.from(dropdown!.querySelectorAll('.ant-select-item-option-content')).map(
        (option) => option.textContent,
      ),
    ).toEqual(['全部命名空间', 'Alpha', 'Beta']);
  });

  it('honors a delayed unsupported capability after an early search', async () => {
    const capability = createDeferred<{ supported: boolean }>();
    const earlyList = createDeferred<LiteTopicItem[]>();
    const earlyQuota = createDeferred<LiteTopicQuota>();

    apiMocks.queryLiteTopicCapability.mockReturnValue(capability.promise);
    apiMocks.queryLiteTopicList.mockReturnValue(earlyList.promise);
    apiMocks.queryLiteTopicQuota.mockReturnValue(earlyQuota.promise);

    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByPlaceholderText('按 Topic 模式筛选...'), 'needle');
    await user.click(screen.getByRole('button', { name: /搜索/ }));
    expect(apiMocks.queryLiteTopicList).toHaveBeenCalledWith('needle', undefined);

    await act(async () => {
      capability.resolve({ supported: false });
    });
    expect(await screen.findByText('当前集群不支持 LiteTopic，请升级到 RocketMQ 5.x')).toBeInTheDocument();

    await act(async () => {
      earlyQuota.resolve(createQuota(20));
      earlyList.resolve([{ namespace: 'default', topicPattern: 'late-result-*' }]);
    });
    expect(screen.getByText('当前集群不支持 LiteTopic，请升级到 RocketMQ 5.x')).toBeInTheDocument();
    expect(screen.queryByText('late-result-*')).not.toBeInTheDocument();
  });

  it('ignores stale list and quota responses when namespaces change quickly', async () => {
    const alphaList = createDeferred<LiteTopicItem[]>();
    const betaList = createDeferred<LiteTopicItem[]>();
    const alphaQuota = createDeferred<LiteTopicQuota>();
    const betaQuota = createDeferred<LiteTopicQuota>();

    apiMocks.queryLiteTopicList.mockImplementation(
      (_pattern: string | undefined, namespace: string | undefined) => {
        if (namespace === 'Alpha') return alphaList.promise;
        if (namespace === 'Beta') return betaList.promise;
        return Promise.resolve([
          { namespace: 'Alpha', topicPattern: 'initial-alpha-*' },
          { namespace: 'Beta', topicPattern: 'initial-beta-*' },
        ]);
      },
    );
    apiMocks.queryLiteTopicQuota.mockImplementation((namespace: string | undefined) => {
      if (namespace === 'Alpha') return alphaQuota.promise;
      if (namespace === 'Beta') return betaQuota.promise;
      return Promise.resolve(createQuota(90));
    });

    renderPage();

    expect(await screen.findByText('initial-alpha-*')).toBeInTheDocument();
    expect(screen.getByText('90 / 100')).toBeInTheDocument();

    await selectNamespace('Alpha');
    expect(screen.queryByText('initial-alpha-*')).not.toBeInTheDocument();
    expect(screen.queryByText('90 / 100')).not.toBeInTheDocument();

    await selectNamespace('Beta');
    expect(apiMocks.queryLiteTopicList).toHaveBeenCalledWith(undefined, 'Beta');
    expect(apiMocks.queryLiteTopicQuota).toHaveBeenCalledWith('Beta');

    await act(async () => {
      betaQuota.resolve(createQuota(20));
      betaList.resolve([{ namespace: 'Beta', topicPattern: 'beta-result-*' }]);
    });
    expect(await screen.findByText('beta-result-*')).toBeInTheDocument();
    expect(screen.getByText('20 / 100')).toBeInTheDocument();

    await act(async () => {
      alphaQuota.resolve(createQuota(10));
      alphaList.resolve([{ namespace: 'Alpha', topicPattern: 'stale-alpha-*' }]);
    });
    expect(screen.getByText('beta-result-*')).toBeInTheDocument();
    expect(screen.getByText('20 / 100')).toBeInTheDocument();
    expect(screen.queryByText('stale-alpha-*')).not.toBeInTheDocument();
    expect(screen.queryByText('10 / 100')).not.toBeInTheDocument();
  });

  it('keeps a failed namespace request cleared', async () => {
    const failedList = createDeferred<LiteTopicItem[]>();
    const failedQuota = createDeferred<LiteTopicQuota>();

    apiMocks.queryLiteTopicList.mockImplementation(
      (_pattern: string | undefined, namespace: string | undefined) =>
        namespace === 'Alpha'
          ? failedList.promise
          : Promise.resolve([{ namespace: 'Alpha', topicPattern: 'old-*' }]),
    );
    apiMocks.queryLiteTopicQuota.mockImplementation((namespace: string | undefined) =>
      namespace === 'Alpha' ? failedQuota.promise : Promise.resolve(createQuota(90)),
    );

    const { container } = renderPage();
    expect(await screen.findByText('old-*')).toBeInTheDocument();

    await selectNamespace('Alpha');
    expect(screen.queryByText('old-*')).not.toBeInTheDocument();
    expect(screen.queryByText('90 / 100')).not.toBeInTheDocument();

    await act(async () => {
      failedQuota.reject(new Error('quota failed'));
      failedList.reject(new Error('list failed'));
    });

    expect(await screen.findByText('获取 LiteTopic 列表失败')).toBeInTheDocument();
    await waitFor(() => {
      expect(container.querySelector('.ant-spin-spinning')).not.toBeInTheDocument();
    });
    expect(screen.queryByText('old-*')).not.toBeInTheDocument();
    expect(screen.queryByText('90 / 100')).not.toBeInTheDocument();
  });
});
