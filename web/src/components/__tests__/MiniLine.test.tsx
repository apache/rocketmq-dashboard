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

const getStrokePath = (container: HTMLElement) =>
  container.querySelector('path[fill="none"]')?.getAttribute('d') ?? '';

const getAreaPath = (container: HTMLElement) =>
  container.querySelector('path[fill^="url"]')?.getAttribute('d') ?? '';

describe('MiniLine', () => {
  it('renders a smooth path for a plain series', () => {
    const { container } = render(<MiniLine data={[1, 2, 3]} />);

    expect(container.querySelector('svg')).not.toBeNull();
    expect(getStrokePath(container)).toMatch(/^M/);
  });

  it('returns nothing for fewer than two samples', () => {
    const { container } = render(<MiniLine data={[1]} />);

    expect(container.querySelector('svg')).toBeNull();
  });

  it('treats negative and non-finite samples as zero', () => {
    // The invalid series must render exactly the shape of its zero-clamped
    // reference: an unsanitized sample would poison Math.min/Math.max, and every
    // path coordinate would collapse to NaN.
    const invalid = render(<MiniLine data={[-5, Number.NaN, Number.POSITIVE_INFINITY, 10]} />);
    const reference = render(<MiniLine data={[0, 0, 0, 10]} />);

    expect(getStrokePath(invalid.container)).not.toContain('NaN');
    expect(getAreaPath(invalid.container)).not.toContain('NaN');
    expect(getStrokePath(invalid.container)).toBe(getStrokePath(reference.container));
    expect(getAreaPath(invalid.container)).toBe(getAreaPath(reference.container));
  });
});
