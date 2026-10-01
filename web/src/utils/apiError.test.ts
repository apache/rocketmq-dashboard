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
import { describeApiError, describeThrownMessage } from './apiError';

type ResponseCarryingError = Error & { response?: { data?: { message?: unknown } } };

const httpError = (serverMessage: unknown, message = 'Request failed with status code 500') => {
  const error: ResponseCarryingError = new Error(message);
  error.response = { data: { message: serverMessage } };
  return error;
};

describe('describeApiError', () => {
  it('prefers the server-supplied rejection reason', () => {
    expect(describeApiError(httpError('broker not writable'), 'fallback')).toBe(
      'broker not writable',
    );
  });

  it('uses the fallback when the server sent no usable message', () => {
    expect(describeApiError(httpError('   '), 'fallback')).toBe('fallback');
    expect(describeApiError(httpError(42), 'fallback')).toBe('fallback');
    expect(describeApiError(new Error('Network Error'), 'fallback')).toBe('fallback');
  });

  it('never reads a property of a rejection that carries no value', () => {
    expect(describeApiError(undefined, 'fallback')).toBe('fallback');
    expect(describeApiError(null, 'fallback')).toBe('fallback');
  });
});

describe('describeThrownMessage', () => {
  it('prefers the server message over the transport message', () => {
    expect(describeThrownMessage(httpError('topic not found'))).toBe('topic not found');
  });

  it('falls back to the thrown message when the server sent none', () => {
    expect(describeThrownMessage(new Error('Network Error'))).toBe('Network Error');
    expect(describeThrownMessage(httpError(''))).toBe('Request failed with status code 500');
  });

  it('accepts a bare object thrown without an Error', () => {
    expect(describeThrownMessage({ message: 'rate limited' })).toBe('rate limited');
  });

  it('returns an empty string when nothing usable was thrown', () => {
    expect(describeThrownMessage(undefined)).toBe('');
    expect(describeThrownMessage(null)).toBe('');
    expect(describeThrownMessage({})).toBe('');
    expect(describeThrownMessage({ message: 42 })).toBe('');
    expect(describeThrownMessage(new Error('   '))).toBe('');
  });
});
