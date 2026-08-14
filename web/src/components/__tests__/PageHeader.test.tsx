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
import { afterEach, describe, expect, it } from 'vitest';
import PageHeader from '../PageHeader';

describe('PageHeader document title', () => {
  const initialTitle = 'RocketMQ Studio';

  afterEach(() => {
    document.title = initialTitle;
  });

  it('synchronizes the browser tab and restores the previous title', () => {
    document.title = initialTitle;
    const view = render(<PageHeader title="Topics" />);

    expect(screen.getByRole('heading', { name: 'Topics' })).toBeInTheDocument();
    expect(document.title).toBe('Topics | RocketMQ Studio');

    view.unmount();
    expect(document.title).toBe(initialTitle);
  });

  it('supports a title override', () => {
    render(<PageHeader title="主题" documentTitle="Topics" />);

    expect(document.title).toBe('Topics | RocketMQ Studio');
  });

  it('can leave the existing document title unchanged', () => {
    document.title = 'Managed by parent';
    render(<PageHeader title="Topics" documentTitle={false} />);

    expect(document.title).toBe('Managed by parent');
  });
});
