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
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import type { Instance } from '../../api/instance';
import { LangProvider } from '../../i18n/LangContext';
import InstanceCapabilityMatrixDrawer from '../InstanceCapabilityMatrixDrawer';
import { downloadCsv } from '../../utils/download';

const instanceServiceMocks = vi.hoisted(() => ({ getInstanceCapabilities: vi.fn() }));
vi.mock('../../services/instanceService', () => instanceServiceMocks);
vi.mock('../../utils/download', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../utils/download')>();
  return { ...actual, downloadCsv: vi.fn() };
});

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

const instances: Instance[] = [
  {
    id: 1,
    name: 'apache-prod',
    type: 'DIRECT',
    endpoint: 'nameserver:9876',
    vendor: 'APACHE',
    remark: null,
    topicCount: 10,
    consumerGroupCount: 5,
    gmtCreate: '',
    gmtModified: '',
  },
  {
    id: 2,
    name: 'cloud-prod',
    type: 'CLOUD',
    endpoint: 'cloud.example:8080',
    vendor: 'ALIYUN',
    remark: null,
    topicCount: 20,
    consumerGroupCount: 8,
    gmtCreate: '',
    gmtModified: '',
  },
];

const renderDrawer = (
  overrides: Partial<React.ComponentProps<typeof InstanceCapabilityMatrixDrawer>> = {},
) =>
  render(
    <App>
      <LangProvider>
        <InstanceCapabilityMatrixDrawer
          open
          instances={instances}
          onClose={vi.fn()}
          {...overrides}
        />
      </LangProvider>
    </App>,
  );

describe('InstanceCapabilityMatrixDrawer', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    instanceServiceMocks.getInstanceCapabilities.mockImplementation((instanceId: string) =>
      Promise.resolve({
        instanceId,
        vendor: instanceId === 'apache-prod' ? 'APACHE' : 'ALIYUN',
        accessType: instanceId === 'apache-prod' ? 'DIRECT' : 'CLOUD',
        capabilities:
          instanceId === 'apache-prod'
            ? [
                'TOPIC_MANAGEMENT',
                'CONSUMER_GROUP_MANAGEMENT',
                'MESSAGE_QUERY',
                'MESSAGE_TRACE',
                'ACL_MANAGEMENT',
                'DLQ_MANAGEMENT',
              ]
            : ['TOPIC_MANAGEMENT', 'MESSAGE_QUERY'],
      }),
    );
  });

  it('loads capabilities for all selected instances and presents coverage', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '加载能力' }));

    await waitFor(() =>
      expect(instanceServiceMocks.getInstanceCapabilities).toHaveBeenCalledTimes(2),
    );
    expect(instanceServiceMocks.getInstanceCapabilities).toHaveBeenCalledWith('apache-prod');
    expect(instanceServiceMocks.getInstanceCapabilities).toHaveBeenCalledWith('cloud-prod');
    expect(await screen.findByText('能力覆盖概览')).toBeInTheDocument();
    expect(screen.getByText(/当前显示 2 个实例/)).toBeInTheDocument();
  });

  it('preserves successful rows and explains partial discovery failures', async () => {
    instanceServiceMocks.getInstanceCapabilities.mockRejectedValueOnce(
      new Error('gateway timeout'),
    );
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '加载能力' }));

    expect(
      await screen.findByText('1 个实例的能力发现失败，其他实例结果仍然可用。'),
    ).toBeInTheDocument();
    expect(screen.getByText(/gateway timeout/)).toBeInTheDocument();
    expect(screen.getByText(/当前显示 2 个实例/)).toBeInTheDocument();
  });

  it('exports only visible rows after a text filter', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '加载能力' }));
    await screen.findByText('能力覆盖概览');
    await user.type(screen.getByLabelText('搜索实例、地址或错误'), 'cloud-prod');
    await user.click(screen.getByRole('button', { name: '导出矩阵' }));

    const [filename, csv] = vi.mocked(downloadCsv).mock.calls[0];
    expect(filename).toBe('rocketmq-instance-capability-matrix.csv');
    expect(csv).toContain('cloud-prod');
    expect(csv).not.toContain('apache-prod');
    expect(csv).toContain('DLQ_MANAGEMENT');
  });

  it('marks a confirmed missing capability without mixing in failed discovery', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '加载能力' }));
    await screen.findByText('能力覆盖概览');

    await user.click(screen.getByRole('combobox', { name: '能力筛选' }));
    await user.click(
      await screen.findByText('死信管理', { selector: '.ant-select-item-option-content' }),
    );
    await user.click(screen.getByRole('combobox', { name: '支持状态筛选' }));
    await user.click(
      await screen.findByText('缺失', { selector: '.ant-select-item-option-content' }),
    );
    expect(screen.getByText(/当前显示 1 个实例/)).toBeInTheDocument();
  });

  it('renders an actionable empty state', () => {
    renderDrawer({ instances: [] });
    expect(screen.getByText('暂无可检查的实例')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '加载能力' })).toBeDisabled();
  });
});
