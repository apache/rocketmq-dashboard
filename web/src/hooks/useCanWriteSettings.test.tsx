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

import { renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useCanWriteSettings } from './useCanWriteSettings';

type MockAuthState = { admin: boolean | null; userId: number | null };

// Read inside the selector, not when the factory runs, so the cases below can swap the session
// without re-registering the module mock.
let authState: MockAuthState = { admin: false, userId: 42 };

vi.mock('../stores/authStore', () => ({
  default: (selector: (state: MockAuthState) => unknown) => selector(authState),
}));

const rendered = () => renderHook(() => useCanWriteSettings()).result.current;

const sessions: Array<[string, MockAuthState, boolean]> = [
  ['a signed-in reader', { userId: 42, admin: false }, false],
  ['a signed-in admin', { userId: 42, admin: true }, true],
  // Auth disabled: nobody is signed in, the caller counts as system and the server lets the write
  // through. This row is why the guard reads `!userId ||` first — dropping it would lock every
  // deployment that runs without studio authentication.
  ['a deployment with studio auth disabled', { userId: null, admin: null }, true],
  ['a signed-in operator whose admin flag never arrived', { userId: 42, admin: null }, false],
];

describe('useCanWriteSettings', () => {
  it.each(sessions)('decides the settings writes for %sTest', (_label, state, expected) => {
    authState = state;
    expect(rendered()).toBe(expected);
  });
});
