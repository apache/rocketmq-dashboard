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
import { render, screen } from '@testing-library/react';
import MiniBar from '../MiniBar';

const getBarHeights = () =>
  Array.from(screen.getByRole('img').children, (bar) => (bar as HTMLElement).style.height);

describe('MiniBar', () => {
  it('renders zero values without a visible bar', () => {
    render(<MiniBar data={[0, 0, 0]} height={20} label="Throughput trend" />);

    expect(getBarHeights()).toEqual(['0px', '0px', '0px']);
  });

  it('keeps positive values visible without turning zero into traffic', () => {
    render(<MiniBar data={[0, 1, 10]} height={20} label="Throughput trend" />);

    expect(getBarHeights()).toEqual(['0px', '4px', '20px']);
  });
});
