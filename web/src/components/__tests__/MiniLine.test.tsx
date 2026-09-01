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

import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import MiniLine from '../MiniLine';

describe('MiniLine', () => {
  it('renders nothing when data is not an array', () => {
    const { container } = render(<MiniLine data={undefined as unknown as number[]} />);

    expect(container.querySelector('svg')).toBeNull();
  });

  it('renders nothing when fewer than two finite values remain', () => {
    const { container } = render(<MiniLine data={[Number.NaN, 5]} />);

    expect(container.querySelector('svg')).toBeNull();
  });

  it('excludes non-finite values from the rendered path', () => {
    const { container } = render(<MiniLine data={[0, Number.NaN, 5, 1]} />);

    const path = container.querySelector('path');
    expect(path).not.toBeNull();
    expect(path?.getAttribute('d')).not.toContain('NaN');
  });

  it('renders a path for finite data', () => {
    const { container } = render(<MiniLine data={[0, 5, 1, 8]} />);

    const path = container.querySelector('path');
    expect(path).not.toBeNull();
    expect(path?.getAttribute('d')).toContain('C');
  });
});
