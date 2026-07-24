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

import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import MiniLine from '../MiniLine';

describe('MiniLine', () => {
  const sampleData = [10, 20, 15, 30, 25, 40, 35];

  it('renders an SVG element', () => {
    const { container } = render(<MiniLine data={sampleData} />);
    const svg = container.querySelector('svg');
    expect(svg).toBeInTheDocument();
  });

  it('renders with default width and height', () => {
    const { container } = render(<MiniLine data={sampleData} />);
    const svg = container.querySelector('svg');
    expect(svg?.getAttribute('width')).toBe('120');
    expect(svg?.getAttribute('height')).toBe('32');
  });

  it('renders with custom width and height', () => {
    const { container } = render(<MiniLine data={sampleData} width={200} height={50} />);
    const svg = container.querySelector('svg');
    expect(svg?.getAttribute('width')).toBe('200');
    expect(svg?.getAttribute('height')).toBe('50');
  });

  it('renders responsive SVG when responsive is true', () => {
    const { container } = render(<MiniLine data={sampleData} responsive />);
    const svg = container.querySelector('svg');
    expect(svg?.getAttribute('width')).toBe('100%');
    expect(svg?.getAttribute('viewBox')).toBe('0 0 120 32');
    expect(svg?.getAttribute('preserveAspectRatio')).toBe('none');
  });

  it('renders a stroke path for the line', () => {
    const { container } = render(<MiniLine data={sampleData} />);
    const paths = container.querySelectorAll('path');
    // At least one path for the line stroke (plus optional fill area)
    expect(paths.length).toBeGreaterThanOrEqual(1);
  });

  it('renders fill area when fill is true (default)', () => {
    const { container } = render(<MiniLine data={sampleData} fill />);
    const paths = container.querySelectorAll('path');
    // Should have both fill path and stroke path
    expect(paths.length).toBeGreaterThanOrEqual(2);
  });

  it('does not render fill area when fill is false', () => {
    const { container } = render(<MiniLine data={sampleData} fill={false} />);
    const paths = container.querySelectorAll('path');
    // Only the stroke path, no fill path
    expect(paths.length).toBe(1);
  });

  it('renders a dot on the last data point when showDot is true (default)', () => {
    const { container } = render(<MiniLine data={sampleData} showDot />);
    const circles = container.querySelectorAll('circle');
    // Two circles: outer glow + inner dot
    expect(circles.length).toBe(2);
  });

  it('does not render dot when showDot is false', () => {
    const { container } = render(<MiniLine data={sampleData} showDot={false} />);
    const circles = container.querySelectorAll('circle');
    expect(circles.length).toBe(0);
  });

  it('returns null for data with less than 2 points', () => {
    const { container } = render(<MiniLine data={[42]} />);
    expect(container.innerHTML).toBe('');
  });

  it('uses custom color', () => {
    const { container } = render(<MiniLine data={sampleData} color="#ff0000" />);
    // The stroke path is the one with a stroke attribute (second path when fill is enabled)
    const paths = container.querySelectorAll('path');
    const strokePath = Array.from(paths).find((p) => p.getAttribute('stroke'));
    expect(strokePath?.getAttribute('stroke')).toBe('#ff0000');
  });

  it('renders gradient and glow filter definitions', () => {
    const { container } = render(<MiniLine data={sampleData} />);
    const defs = container.querySelector('defs');
    expect(defs).toBeInTheDocument();
    const linearGradient = defs?.querySelector('linearGradient');
    const filter = defs?.querySelector('filter');
    expect(linearGradient).toBeInTheDocument();
    expect(filter).toBeInTheDocument();
  });
});
