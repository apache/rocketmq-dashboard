/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { LangProvider } from '../../../i18n/LangContext';
import ToolPlaygroundModal from '../components/ToolPlaygroundModal';
import { listTools } from '../../../api/ai';
import { listInstances } from '../../../services/instanceService';

vi.mock('../../../api/ai', () => ({
  listTools: vi.fn(),
  executeTool: vi.fn(),
}));

vi.mock('../../../services/instanceService', () => ({
  listInstances: vi.fn(),
}));

const renderModal = (open: boolean) =>
  render(
    <App>
      <LangProvider>
        <ToolPlaygroundModal open={open} onClose={vi.fn()} />
      </LangProvider>
    </App>,
  );

describe('ToolPlaygroundModal', () => {
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

  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    vi.mocked(listInstances).mockResolvedValue([]);
    vi.mocked(listTools).mockRejectedValue(new Error('catalog unavailable'));
  });

  it('retries the catalog bootstrap when the previous open failed to load it', async () => {
    const first = renderModal(true);
    await waitFor(() => expect(listTools).toHaveBeenCalledTimes(1));

    // Close and reopen: the once-per-mount flag must not pin a failed bootstrap,
    // or the playground stays empty until a full page reload.
    first.rerender(
      <App>
        <LangProvider>
          <ToolPlaygroundModal open={false} onClose={vi.fn()} />
        </LangProvider>
      </App>,
    );
    first.rerender(
      <App>
        <LangProvider>
          <ToolPlaygroundModal open={true} onClose={vi.fn()} />
        </LangProvider>
      </App>,
    );

    await waitFor(() => expect(listTools).toHaveBeenCalledTimes(2));
  });
});
