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
import MiniLine from '../MiniLine';

describe('MiniLine accessibility', () => {
  it('exposes samples in order to assistive technology and native hover', () => {
    const { container } = render(<MiniLine data={[12, 8, 15]} animated={false} />);

    expect(screen.getByRole('img', { name: '12 → 8 → 15' })).toBeInTheDocument();
    expect(container.querySelector('title')).toHaveTextContent('12 → 8 → 15');
  });

  it('prefixes the summary with caller-provided context', () => {
    render(<MiniLine data={[1.5, 2.5]} ariaLabel="Send TPS" />);

    expect(screen.getByRole('img', { name: 'Send TPS: 1.5 → 2.5' })).toBeInTheDocument();
  });

  it('does not expose misleading metadata when there is no chart', () => {
    const { container } = render(<MiniLine data={[1]} ariaLabel="Send TPS" />);

    expect(container).toBeEmptyDOMElement();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });
});
