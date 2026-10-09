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

import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import useAuthStore from '../../../stores/authStore';
import { COMPOSER_DRAFT_STORAGE_KEY, useComposerDraft } from './useComposerDraft';

const setAccount = (user: string | null, userId: number | null) => {
  useAuthStore.setState({ user, userId, admin: null });
};

describe('useComposerDraft', () => {
  afterEach(() => {
    sessionStorage.clear();
    setAccount(null, null);
  });

  it('does not hand an unsent draft to the next account signed in on the same tab', () => {
    setAccount('operator-a', 12);
    const first = renderHook(() => useComposerDraft());
    act(() => first.result.current[1]('check the backlog on rmq-prod'));
    first.unmount();

    // operator A logs out and operator B signs in; sessionStorage survives both, and logout only
    // clears localStorage, so the draft must not follow the tab.
    setAccount('operator-b', 13);
    const second = renderHook(() => useComposerDraft());

    expect(second.result.current[0]).toBe('');
    expect(
      Object.keys(sessionStorage).filter((key) => key.startsWith(COMPOSER_DRAFT_STORAGE_KEY)),
    ).toHaveLength(1);
  });

  it('restores the draft of the account that typed it', () => {
    setAccount('operator-a', 12);
    const first = renderHook(() => useComposerDraft());
    act(() => first.result.current[1]('unfinished question'));
    first.unmount();

    setAccount('operator-a', 12);
    const second = renderHook(() => useComposerDraft());

    expect(second.result.current[0]).toBe('unfinished question');
  });
});
