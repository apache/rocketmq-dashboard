// SPDX-License-Identifier: Apache-2.0
import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import MiniLine from '../MiniLine';

describe('MiniLine', () => {
  it('keeps malformed metric samples out of SVG coordinates', () => {
    const { container } = render(
      <MiniLine data={[1, Number.NaN, Number.POSITIVE_INFINITY, 4]} animated={false} />,
    );

    expect(container.querySelector('svg')).not.toBeNull();
    expect(container.innerHTML).not.toMatch(/NaN|Infinity/);
  });
});
