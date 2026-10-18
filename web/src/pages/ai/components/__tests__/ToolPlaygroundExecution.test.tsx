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
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { message } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { executeTool, listTools } from '../../../../api/ai';
import { listInstances } from '../../../../services/instanceService';
import ToolPlaygroundModal from '../ToolPlaygroundModal';

vi.mock('../../../../api/ai', () => ({ executeTool: vi.fn(), listTools: vi.fn() }));
vi.mock('../../../../services/instanceService', () => ({ listInstances: vi.fn() }));
vi.mock('../../../../i18n/LangContext', () => ({ useLang: () => ({ t: (key: string) => key }) }));

describe('ToolPlayground execution ownership', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    const completion = Promise.resolve(true);
    const dismiss = Object.assign(() => undefined, { then: completion.then.bind(completion) });
    vi.spyOn(message, 'success').mockReturnValue(dismiss);
    vi.spyOn(message, 'error').mockReturnValue(dismiss);
    vi.mocked(listInstances).mockResolvedValue(
      ['instance-x', 'instance-y'].map((name, id) => ({
        id,
        name,
        remark: null,
        type: 'DIRECT',
        endpoint: `${name}:9876`,
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '',
        gmtModified: '',
      })),
    );
    vi.mocked(listTools).mockResolvedValue([
      { name: 'tool-a', description: 'A', parameters: {} },
      { name: 'tool-b', description: 'B', parameters: {} },
    ]);
  });

  it.each(
    ['tool', 'scope', 'input', 'close', 'external-close', 'unmount'].flatMap((change) =>
      ['success', 'failure'].map((outcome) => [change, outcome]),
    ),
  )('discardsLateExecutionAfterContextChangesTest: %s %s', async (change, outcome) => {
    const user = userEvent.setup();
    let resolve!: (value: unknown) => void;
    let reject!: (error: Error) => void;
    vi.mocked(executeTool).mockReturnValue(
      new Promise((release, fail) => {
        resolve = release;
        reject = fail;
      }),
    );
    const onClose = vi.fn();
    const { rerender, unmount } = render(<ToolPlaygroundModal open onClose={onClose} />);
    await waitFor(() => expect(listTools).toHaveBeenCalledTimes(1));
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'ai.tools.execute' })).toBeEnabled(),
    );
    await user.click(screen.getByRole('button', { name: 'ai.tools.execute' }));
    expect(executeTool).toHaveBeenCalledTimes(1);

    if (change === 'tool') {
      await user.click(screen.getByRole('combobox', { name: 'ai.tools.selectTool' }));
      await user.click(
        await screen.findByText('tool-b', { selector: '.ant-select-item-option-content' }),
      );
    } else if (change === 'scope') {
      await user.click(screen.getByRole('combobox', { name: 'ai.tools.selectInstance' }));
      await user.click(
        await screen.findByText('instance-y', { selector: '.ant-select-item-option-content' }),
      );
    } else if (change === 'input') {
      fireEvent.change(screen.getByRole('textbox', { name: 'ai.tools.inputAria' }), {
        target: { value: '{"changed":true}' },
      });
    } else if (change === 'unmount') {
      unmount();
    } else {
      if (change === 'close')
        await user.click(screen.getByRole('button', { name: 'common.close' }));
      rerender(<ToolPlaygroundModal open={false} onClose={onClose} />);
      rerender(<ToolPlaygroundModal open onClose={onClose} />);
    }

    await act(async () => {
      if (outcome === 'success') resolve({ source: 'old execution' });
      else reject(new Error('old execution failed'));
    });
    expect(screen.queryByTestId('tool-result')).not.toBeInTheDocument();
    expect(message.success).not.toHaveBeenCalled();
    expect(message.error).not.toHaveBeenCalled();
  });

  it('reportsCurrentExecutionAndSuppressesDuplicateSubmissionTest', async () => {
    let resolve!: (value: unknown) => void;
    vi.mocked(executeTool).mockReturnValue(
      new Promise((release) => {
        resolve = release;
      }),
    );
    render(<ToolPlaygroundModal open onClose={vi.fn()} />);
    const button = screen.getByRole('button', { name: 'ai.tools.execute' });
    await waitFor(() => expect(button).toBeEnabled());
    act(() => {
      fireEvent.click(button);
      fireEvent.click(button);
    });
    expect(executeTool).toHaveBeenCalledTimes(1);
    await act(async () => resolve({ source: 'current execution' }));
    expect(screen.getByTestId('tool-result')).toHaveTextContent('current execution');
    expect(message.success).toHaveBeenCalledTimes(1);
  });
});
