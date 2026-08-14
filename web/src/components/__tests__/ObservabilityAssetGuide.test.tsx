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

import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { LangProvider } from '../../i18n/LangContext';
import { LANGUAGE_STORAGE_KEY } from '../../i18n/languagePreference';
import ObservabilityAssetGuide from '../ObservabilityAssetGuide';

describe('ObservabilityAssetGuide', () => {
  it('explains the Grafana import workflow in English', () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
    render(
      <LangProvider>
        <ObservabilityAssetGuide kind="grafana" />
      </LangProvider>,
    );

    fireEvent.click(screen.getByText('How to import these dashboards'));

    expect(screen.getByText(/Dashboards > New > Import/)).toBeInTheDocument();
    expect(screen.getByText(/Choose your Prometheus data source/)).toBeInTheDocument();
  });

  it('explains Prometheus rule deployment in Chinese with copyable configuration', () => {
    render(
      <LangProvider>
        <ObservabilityAssetGuide kind="prometheus" />
      </LangProvider>,
    );

    fireEvent.click(screen.getByText('如何部署这些告警规则'));

    expect(screen.getByText(/promtool check rules/)).toBeInTheDocument();
    expect(screen.getByText(/rocketmq-\*\.yml/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Copy' })).toBeInTheDocument();
  });
});
