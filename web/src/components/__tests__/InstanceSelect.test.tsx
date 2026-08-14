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

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../../i18n/LangContext';
import { LANGUAGE_STORAGE_KEY } from '../../i18n/languagePreference';
import InstanceSelect, { INSTANCE_RECENTS_STORAGE_KEY } from '../InstanceSelect';

const options = [
  { value: 'cluster-a', label: 'Cluster A' },
  { value: 'cluster-b', label: 'Cluster B' },
  { value: 'cluster-c', label: 'Cluster C' },
];

function openSelect() {
  fireEvent.mouseDown(screen.getByRole('combobox'));
}

describe('InstanceSelect recent instances', () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('persists a selection and groups it first on the next render', async () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
    const onChange = vi.fn();
    const firstRender = render(
      <LangProvider>
        <InstanceSelect options={options} onChange={onChange} />
      </LangProvider>,
    );

    openSelect();
    fireEvent.click(screen.getByText('Cluster B'));

    expect(onChange).toHaveBeenCalledWith('cluster-b', expect.anything());
    expect(JSON.parse(localStorage.getItem(INSTANCE_RECENTS_STORAGE_KEY) ?? '[]')).toEqual([
      'cluster-b',
    ]);

    firstRender.unmount();
    render(
      <LangProvider>
        <InstanceSelect options={options} onChange={vi.fn()} />
      </LangProvider>,
    );
    openSelect();

    expect(await screen.findByText('Recent')).toBeInTheDocument();
    expect(screen.getByText('All instances')).toBeInTheDocument();
  });

  it('prunes instances that are no longer available', async () => {
    localStorage.setItem(
      INSTANCE_RECENTS_STORAGE_KEY,
      JSON.stringify(['deleted-cluster', 'cluster-a']),
    );

    render(<InstanceSelect options={options} onChange={vi.fn()} />);

    await waitFor(() =>
      expect(JSON.parse(localStorage.getItem(INSTANCE_RECENTS_STORAGE_KEY) ?? '[]')).toEqual([
        'cluster-a',
      ]),
    );
    openSelect();
    expect(screen.queryByText('deleted-cluster')).not.toBeInTheDocument();
  });

  it('keeps selection usable when browser storage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('storage blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('storage blocked');
    });
    const onChange = vi.fn();

    render(<InstanceSelect options={options} onChange={onChange} />);
    openSelect();
    fireEvent.click(screen.getByText('Cluster C'));

    expect(onChange).toHaveBeenCalledWith('cluster-c', expect.anything());
  });
});
