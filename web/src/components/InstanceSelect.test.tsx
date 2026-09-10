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

import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { InstanceSelect, type InstanceOption } from './InstanceSelect';

const options: InstanceOption[] = [
  { value: 'inst-1', label: 'instance-a' },
  { value: 'inst-2', label: 'instance-b' },
];

describe('InstanceSelect', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders the placeholder and the current value label', () => {
    const onChange = vi.fn();
    const { container } = render(
      <InstanceSelect value="inst-1" onChange={onChange} options={options} />,
    );

    expect(screen.getByText('instance-a')).toBeTruthy();
    expect(container.querySelector('.ant-select-selector')).toBeTruthy();
  });

  it('notifies the page when an option is selected', async () => {
    const onChange = vi.fn();
    const { container } = render(
      <InstanceSelect onChange={onChange} options={options} />,
    );

    fireEvent.mouseDown(container.querySelector('.ant-select-selector')!);
    const option = await screen.findByText('instance-b');
    fireEvent.click(option);

    expect(onChange).toHaveBeenCalledWith('inst-2', expect.anything());
  });

  it('falls back to the first option when the selection is cleared', async () => {
    const onChange = vi.fn();
    const { container } = render(
      <InstanceSelect onChange={onChange} options={options} />,
    );

    fireEvent.mouseDown(container.querySelector('.ant-select-selector')!);
    fireEvent.click(await screen.findByText('instance-b'));

    fireEvent.mouseDown(container.querySelector('.ant-select-clear')!);
    expect(onChange).toHaveBeenLastCalledWith('inst-1');
  });

  it('shows the not-found message when there are no matches', async () => {
    const onChange = vi.fn();
    const { container } = render(
      <InstanceSelect onChange={onChange} options={[]} />,
    );

    fireEvent.mouseDown(container.querySelector('.ant-select-selector')!);
    expect(await screen.findByText('暂无匹配实例')).toBeTruthy();
  });
});
