/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import { getConversation } from '../../../../api/aiConversations';
import { listInstances } from '../../../../services/instanceService';
import ConversationTarget from '../ConversationTarget';

vi.mock('../../../../api/aiConversations', () => ({ getConversation: vi.fn() }));
vi.mock('../../../../services/instanceService', () => ({ listInstances: vi.fn() }));
vi.mock('../../../../i18n/LangContext', () => ({ useLang: () => ({ t: (key: string) => key }) }));
beforeEach(() => vi.resetAllMocks());

it('showsExplicitPlainChatAndRetriesADirectoryFailureTest', async () => {
  const user = userEvent.setup();
  const onChange = vi.fn();
  vi.mocked(listInstances)
    .mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValueOnce([
      {
        id: 17,
        name: 'staging',
        type: 'DIRECT',
        endpoint: 'ns:9876',
        remark: null,
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '',
        gmtModified: '',
      },
    ]);
  render(<ConversationTarget value="" onChange={onChange} />);
  expect(await screen.findByRole('alert')).toHaveTextContent('ai.target.loadFailed');
  expect(screen.getByText('ai.target.plainChat')).toBeInTheDocument();
  expect(onChange).not.toHaveBeenCalled();
  await user.click(screen.getByRole('button', { name: 'common.retry' }));
  await user.click(screen.getByRole('combobox', { name: 'ai.target.label' }));
  await user.click(
    await screen.findByText('staging', { selector: '.ant-select-item-option-content' }),
  );
  expect(onChange).toHaveBeenCalledWith('staging', expect.anything());
});

it('showsThePersistedBindingWithoutAllowingHistoryToBeReboundTest', async () => {
  vi.mocked(getConversation).mockResolvedValue({
    id: 7,
    title: '',
    owner: 'operator',
    engine: 'http',
    model: '',
    mode: 'chat',
    instanceId: 'fixed-production',
    lastSeq: 0,
    archived: false,
    createdAt: '',
    updatedAt: '',
    activeRun: null,
  });
  render(<ConversationTarget conversationId={7} value="staging" onChange={vi.fn()} />);
  expect(await screen.findByText('fixed-production')).toBeInTheDocument();
  expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
  expect(listInstances).not.toHaveBeenCalled();
});

it('doesNotShowThePreviousConversationsBindingAfterNavigationTest', async () => {
  let resolve!: (value: Awaited<ReturnType<typeof getConversation>>) => void;
  vi.mocked(getConversation).mockReturnValueOnce(
    new Promise((release) => {
      resolve = release;
    }),
  );
  const { rerender } = render(
    <ConversationTarget conversationId={7} value="" onChange={vi.fn()} />,
  );
  vi.mocked(listInstances).mockResolvedValue([]);
  rerender(<ConversationTarget value="" onChange={vi.fn()} />);
  await act(async () =>
    resolve({
      id: 7,
      title: '',
      owner: 'operator',
      engine: 'http',
      model: '',
      mode: 'chat',
      instanceId: 'obsolete',
      lastSeq: 0,
      archived: false,
      createdAt: '',
      updatedAt: '',
      activeRun: null,
    }),
  );
  expect(screen.queryByText('obsolete')).not.toBeInTheDocument();
});
