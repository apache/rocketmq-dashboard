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
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import useAuthStore from '../../../stores/authStore';
import { COMPOSER_DRAFT_STORAGE_KEY, useComposerDraft } from './useComposerDraft';

beforeEach(() => {
  useAuthStore.getState().logout();
  sessionStorage.clear();
});

afterEach(() => {
  useAuthStore.getState().logout();
  sessionStorage.clear();
});

describe('useComposerDraft', () => {
  it('keeps unsent text with its account across login changes in one tab', () => {
    useAuthStore.getState().login('alice', 1, false);
    const draft = renderHook(() => useComposerDraft());
    act(() => draft.result.current[1]('private draft'));

    act(() => {
      useAuthStore.getState().logout();
      useAuthStore.getState().login('bob', 2, false);
    });
    expect(draft.result.current[0]).toBe('');
    act(() => draft.result.current[1]('bob draft'));

    act(() => {
      useAuthStore.getState().logout();
      useAuthStore.getState().login('alice', 1, false);
    });
    expect(draft.result.current[0]).toBe('private draft');
    draft.unmount();
  });

  it('does not assign an unowned legacy draft to the next account', () => {
    sessionStorage.setItem(COMPOSER_DRAFT_STORAGE_KEY, 'previous account draft');
    useAuthStore.getState().login('bob', 2, false);

    const bob = renderHook(() => useComposerDraft());
    expect(bob.result.current[0]).toBe('');
    expect(sessionStorage.getItem(COMPOSER_DRAFT_STORAGE_KEY)).toBeNull();
  });

  it('uses the username when accounts do not have numeric IDs', () => {
    useAuthStore.getState().login('alice', null, false);
    const alice = renderHook(() => useComposerDraft());
    act(() => alice.result.current[1]('alice draft'));
    alice.unmount();

    useAuthStore.getState().logout();
    useAuthStore.getState().login('bob', null, false);
    const bob = renderHook(() => useComposerDraft());
    expect(bob.result.current[0]).toBe('');
  });
});
