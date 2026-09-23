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
import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { LangProvider } from '../../i18n/LangContext';
import MessageProperties from '../MessageProperties';

describe('MessageProperties', () => {
  it('omits an empty section when no properties were truncated', () => {
    const { container } = render(
      <LangProvider>
        <MessageProperties properties={{}} propertiesTruncated={false} />
      </LangProvider>,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it('keeps the truncation warning when the server returned no properties', () => {
    render(
      <LangProvider>
        <MessageProperties properties={{}} propertiesTruncated />
      </LangProvider>,
    );

    const section = screen.getByRole('region', { name: '消息属性' });
    expect(within(section).getByText('属性过多或单值过长，服务端已截断展示')).toBeInTheDocument();
    expect(section.querySelector('dl')).not.toBeInTheDocument();
  });

  it('renders long property text and markup-looking values as literal text', () => {
    const longKey = `trace-${'k'.repeat(300)}`;
    const longValue = `route-${'v'.repeat(1000)}`;
    const markup = '<img src=x onerror=alert(1)>';
    render(
      <LangProvider>
        <MessageProperties properties={{ [longKey]: longValue, payload: markup }} />
      </LangProvider>,
    );

    const section = screen.getByRole('region', { name: '消息属性' });
    expect(within(section).getByText(longKey)).toBeInTheDocument();
    expect(within(section).getByText(longValue)).toBeInTheDocument();
    expect(within(section).getByText(markup)).toBeInTheDocument();
    expect(section.querySelector('img')).not.toBeInTheDocument();
  });
});
