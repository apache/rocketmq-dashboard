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
import { App } from 'antd';
import { LangProvider } from '../../../i18n/LangContext';
import SslSettings from '../SslSettings';

const renderWithProviders = () =>
  render(
    <App>
      <LangProvider>
        <SslSettings />
      </LangProvider>
    </App>,
  );

describe('SslSettings Page', () => {
  it('explains that SSL settings cannot be saved before backend support exists', () => {
    renderWithProviders();

    expect(screen.getByTestId('ssl-settings-unavailable')).toHaveTextContent(
      'SSL/TLS 配置暂不可用',
    );
    expect(screen.getByText(/暂不支持保存或上传/)).toBeInTheDocument();
  });

  it('does not expose local-only controls that imply TLS is configured', () => {
    renderWithProviders();

    expect(screen.queryByRole('switch')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /保\s*存/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /上\s*传/ })).not.toBeInTheDocument();
    expect(screen.queryByText('证书信息')).not.toBeInTheDocument();
  });
});
