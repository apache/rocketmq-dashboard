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

import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import PageHeader from './PageHeader';

describe('PageHeader', () => {
  it('renders the title as a level-one heading by default', () => {
    render(<PageHeader title="Clusters" />);

    const heading = screen.getByRole('heading', { name: 'Clusters' });
    expect(heading.tagName).toBe('H1');
  });

  it('honours the heading level and renders the subtitle', () => {
    render(<PageHeader title="Brokers" subtitle="Per-broker runtime state" headingLevel={2} />);

    expect(screen.getByRole('heading', { name: 'Brokers' }).tagName).toBe('H2');
    expect(screen.getByText('Per-broker runtime state')).toBeTruthy();
  });

  it('renders the extra slot', () => {
    render(
      <PageHeader title="Topics" extra={<button type="button">Create topic</button>} />,
    );

    expect(screen.getByRole('button', { name: 'Create topic' })).toBeTruthy();
  });
});
