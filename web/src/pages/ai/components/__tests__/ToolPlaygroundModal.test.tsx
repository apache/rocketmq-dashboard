/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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
import { render, screen } from '@testing-library/react';
import { LangProvider } from '../../../../i18n/LangContext';
import type { McpTool } from '../../../../api/ai';
import ToolPlaygroundModal from '../ToolPlaygroundModal';

vi.mock('../../../../api/ai', () => ({
  listTools: vi.fn(),
  executeTool: vi.fn(),
}));
vi.mock('../../../../services/instanceService', () => ({
  listInstances: vi.fn(),
}));

import { listTools } from '../../../../api/ai';
import { listInstances } from '../../../../services/instanceService';

/**
 * The playground is the surface an operator uses to judge a tool BEFORE running it, so its risk
 * tag must carry the same colour vocabulary as the transcript: a destructive L3 entry is a red
 * tag, not the orange of a previewable L2 mutation. The catalog arrives from `listTools` on
 * open and the first non-deprecated entry auto-selects, which is what puts the tag on screen.
 */

const listToolsMock = vi.mocked(listTools);
const listInstancesMock = vi.mocked(listInstances);

const DESTRUCTIVE_TOOL: McpTool = {
  name: 'rmq.topic.delete',
  description: 'Delete a topic and all of its messages',
  parameters: { type: 'object', properties: {}, required: [] },
  riskLevel: 'L3',
};

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

describe('ToolPlaygroundModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    listInstancesMock.mockResolvedValue([]);
  });

  it('rendersTheDestructiveRiskLevelInRedLikeTheTranscriptTest', async () => {
    listToolsMock.mockResolvedValue([DESTRUCTIVE_TOOL]);

    render(
      <LangProvider>
        <ToolPlaygroundModal open onClose={() => undefined} />
      </LangProvider>,
    );

    // The catalog loads on open and auto-selects the first non-deprecated entry.
    const riskTag = await screen.findByText('L3');
    expect(riskTag).toHaveClass('ant-tag-red');
    expect(riskTag).not.toHaveClass('ant-tag-orange');
  });
});
