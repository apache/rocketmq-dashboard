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

import InfoBanner from './InfoBanner';

describe('InfoBanner', () => {
  it('renders title and description', () => {
    render(<InfoBanner title="Cluster overview" description="Runtime health of every cluster" />);

    expect(screen.getByText('Cluster overview')).toBeTruthy();
    expect(screen.getByText('Runtime health of every cluster')).toBeTruthy();
  });

  it('omits the optional sections when absent', () => {
    const { container } = render(<InfoBanner />);

    expect(container.textContent ?? '').toBe('');
  });

  it('renders children and forwards the test id', () => {
    render(
      <InfoBanner data-testid="info-banner">
        <span>extra action</span>
      </InfoBanner>,
    );

    expect(screen.getByTestId('info-banner')).toBeTruthy();
    expect(screen.getByText('extra action')).toBeTruthy();
  });
});
