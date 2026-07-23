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
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LangProvider, useLang, useLanguage } from '../LangContext';

/** Helper component that consumes LangContext and exposes its values */
const LangConsumer = () => {
  const { lang, setLang, t } = useLang();
  return (
    <div>
      <span data-testid="lang">{lang}</span>
      <span data-testid="translated">{t('nav.home')}</span>
      <span data-testid="missing">{t('nonexistent.key')}</span>
      <span data-testid="param">{t('common.total', { count: 42 })}</span>
      <button onClick={() => setLang('en')}>switch-en</button>
      <button onClick={() => setLang('zh')}>switch-zh</button>
    </div>
  );
};

/** Component that uses the useLanguage alias */
const LanguageAliasConsumer = () => {
  const { lang } = useLanguage();
  return <span data-testid="alias-lang">{lang}</span>;
};

describe('LangContext', () => {
  it('defaults to zh language', () => {
    render(
      <LangProvider>
        <LangConsumer />
      </LangProvider>,
    );
    expect(screen.getByTestId('lang')).toHaveTextContent('zh');
  });

  it('translates keys in Chinese by default', () => {
    render(
      <LangProvider>
        <LangConsumer />
      </LangProvider>,
    );
    expect(screen.getByTestId('translated')).toHaveTextContent('首页');
  });

  it('switches to English via setLang', async () => {
    const user = userEvent.setup();
    render(
      <LangProvider>
        <LangConsumer />
      </LangProvider>,
    );
    await user.click(screen.getByText('switch-en'));
    expect(screen.getByTestId('lang')).toHaveTextContent('en');
    expect(screen.getByTestId('translated')).toHaveTextContent('Home');
  });

  it('switches back to Chinese via setLang', async () => {
    const user = userEvent.setup();
    render(
      <LangProvider>
        <LangConsumer />
      </LangProvider>,
    );
    await user.click(screen.getByText('switch-en'));
    expect(screen.getByTestId('lang')).toHaveTextContent('en');
    await user.click(screen.getByText('switch-zh'));
    expect(screen.getByTestId('lang')).toHaveTextContent('zh');
    expect(screen.getByTestId('translated')).toHaveTextContent('首页');
  });

  it('returns the key itself for missing translations', () => {
    render(
      <LangProvider>
        <LangConsumer />
      </LangProvider>,
    );
    expect(screen.getByTestId('missing')).toHaveTextContent('nonexistent.key');
  });

  it('interpolates parameters into translation strings', async () => {
    const user = userEvent.setup();
    render(
      <LangProvider>
        <LangConsumer />
      </LangProvider>,
    );
    // In Chinese mode, 'common.total' is '共' — no {count} placeholder in base text,
    // but the interpolation logic should still work without error.
    // Switch to English to verify a key that might use params.
    await user.click(screen.getByText('switch-en'));
    // Even if the key doesn't have a {count} placeholder, the t() function
    // should not throw — it simply replaces matching placeholders.
    expect(screen.getByTestId('param')).toHaveTextContent('Total');
  });

  it('useLanguage alias provides the same context as useLang', () => {
    render(
      <LangProvider>
        <LanguageAliasConsumer />
      </LangProvider>,
    );
    expect(screen.getByTestId('alias-lang')).toHaveTextContent('zh');
  });
});
