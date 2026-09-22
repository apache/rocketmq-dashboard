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

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LangProvider } from '../../../../i18n/LangContext';
import { LANGUAGE_STORAGE_KEY } from '../../../../i18n/languagePreference';
import SendStopButton, { type SendStopState } from '../SendStopButton';

/**
 * Two regressions this component exists to prevent, both of them visible in the page it replaces:
 *
 * 1. A SECOND antd `<Button danger>停止生成</Button>` was mounted next to the send button only while
 *    a run streamed, which pushed the toolbar sideways on every turn. Hence "exactly one button in
 *    every state" — the slot must be stable, not merely present.
 * 2. Those two literals were the only hard-coded Chinese on the page. Hence the CJK scan, which runs
 *    in English so every translated string is ASCII and anything left over is a literal in the source.
 */

const STATES: SendStopState[] = ['send', 'stop', 'stopping'];

/** CJK unified ideographs plus the compatibility block: what a hard-coded 停止生成 would match. */
const CJK = /[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]/;

function renderButton(state: SendStopState, canSend = true) {
  const onSend = vi.fn();
  const onStop = vi.fn();
  const view = render(
    <LangProvider>
      <SendStopButton state={state} canSend={canSend} onSend={onSend} onStop={onStop} />
    </LangProvider>,
  );
  return { ...view, onSend, onStop };
}

describe('SendStopButton', () => {
  beforeEach(() => {
    // The suite scans for hard-coded CJK, so it has to run in the locale where every translated
    // string is ASCII. `setup.ts` clears localStorage before each test, so this runs after that.
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
  });

  it('sendsWhenTheDraftIsSendableTest', async () => {
    const user = userEvent.setup();
    const { onSend, onStop } = renderButton('send');

    const button = screen.getByRole('button');
    expect(button).toBeEnabled();
    expect(button).toHaveAttribute('data-state', 'send');
    await user.click(button);

    expect(onSend).toHaveBeenCalledTimes(1);
    expect(onStop).not.toHaveBeenCalled();
  });

  it('staysDisabledWhenNothingCanBeSentTest', async () => {
    const user = userEvent.setup();
    const { onSend, onStop } = renderButton('send', false);

    const button = screen.getByRole('button');
    expect(button).toBeDisabled();
    await user.click(button);

    expect(onSend).not.toHaveBeenCalled();
    expect(onStop).not.toHaveBeenCalled();
  });

  it('stopsWhileARunIsGeneratingTest', async () => {
    const user = userEvent.setup();
    const { onSend, onStop } = renderButton('stop');

    const button = screen.getByRole('button');
    // Stop must be clickable even with an empty draft: `canSend` only describes the send state.
    expect(button).toBeEnabled();
    expect(button).toHaveAttribute('data-state', 'stop');
    await user.click(button);

    expect(onStop).toHaveBeenCalledTimes(1);
    expect(onSend).not.toHaveBeenCalled();
  });

  it('ignoresClicksWhileTheStopRequestIsInFlightTest', async () => {
    const user = userEvent.setup();
    const { onSend, onStop } = renderButton('stopping');

    const button = screen.getByRole('button');
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('data-state', 'stopping');
    await user.click(button);

    expect(onStop).not.toHaveBeenCalled();
    expect(onSend).not.toHaveBeenCalled();
  });

  it('carriesATitleAndAnAriaLabelInEveryStateTest', () => {
    for (const state of STATES) {
      const { unmount } = renderButton(state);
      const button = screen.getByRole('button');

      expect(button.getAttribute('title'), state).toBeTruthy();
      expect(button.getAttribute('aria-label'), state).toBeTruthy();
      // The spinner state has no distinct accessible name of its own; it reuses its title.
      expect(button.getAttribute('aria-label')).not.toBe(state);
      unmount();
    }
  });

  it('namesEachStateForScreenReadersTest', () => {
    renderButton('send');
    expect(screen.getByRole('button')).toHaveAccessibleName('Send');
  });

  it('keepsExactlyOneButtonSlotInEveryStateTest', () => {
    // The layout-shift regression: a sibling stop button appearing only while loading.
    for (const state of STATES) {
      const { container, unmount } = renderButton(state);
      expect(container.querySelectorAll('button'), state).toHaveLength(1);
      expect(screen.getAllByRole('button'), state).toHaveLength(1);
      // And the slot always shows an icon, so its width does not change between states either.
      expect(container.querySelector('svg'), state).not.toBeNull();
      unmount();
    }
  });

  it('containsNoHardCodedCjkLiteralTest', () => {
    for (const state of STATES) {
      const { container, unmount } = renderButton(state);
      // Every visible or assistive string has to come from translations.ts. Rendering in English
      // makes a surviving literal stand out, which is how the two 停止生成 strings are kept out.
      expect(CJK.test(container.innerHTML), state).toBe(false);
      unmount();
    }
  });
});
