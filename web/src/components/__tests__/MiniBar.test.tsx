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

import { beforeEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import MiniBar from '../MiniBar';
import { LangProvider } from '../../i18n/LangContext';
import { LANGUAGE_STORAGE_KEY } from '../../i18n/languagePreference';

const getBarHeights = () =>
  Array.from(screen.getByRole('img').children, (bar) => (bar as HTMLElement).style.height);

const getTrendLabel = () => screen.getByRole('img').getAttribute('aria-label');

const renderTrend = (data: number[], label?: string) =>
  render(
    <LangProvider>
      <MiniBar data={data} height={20} {...(label === undefined ? {} : { label })} />
    </LangProvider>,
  );

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

describe('MiniBar accessible trend labels', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('describes the trend in the active language', () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');

    renderTrend([1, 2, 3]);

    expect(getTrendLabel()).toBe('Trend: 1, 2, 3');
  });

  it('keeps the Chinese label and separator for the Chinese UI', () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'zh');

    renderTrend([1, 2, 3]);

    expect(getTrendLabel()).toBe('趋势数据：1、2、3');
  });

  it('describes an empty trend in the active language', () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');

    renderTrend([]);

    expect(getTrendLabel()).toBe('No trend data yet');
  });

  it('prefers an explicit label over the localized default', () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');

    renderTrend([4], 'Throughput');

    expect(getTrendLabel()).toBe('Throughput');
  });
});
