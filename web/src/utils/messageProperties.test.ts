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
import { parseMessageProperties } from './messageProperties';

describe('parseMessageProperties', () => {
  it('preserves commas and additional equals signs in property values', () => {
    const result = parseMessageProperties(
      'location=Beijing,China\nsignature=part-a=part-b\ntags=blue,green',
    );

    expect(result).toEqual({
      properties: {
        location: 'Beijing,China',
        signature: 'part-a=part-b',
        tags: 'blue,green',
      },
      errors: [],
    });
  });

  it('supports Windows line endings and reports duplicate keys', () => {
    const result = parseMessageProperties('traceId=first\r\ntenant=demo\r\ntraceId=second');

    expect(result.properties).toEqual({ traceId: 'first', tenant: 'demo' });
    expect(result.errors).toEqual(['属性名“traceId”重复']);
  });

  it('preserves JavaScript object prototype property names', () => {
    const result = parseMessageProperties(
      '__proto__=trace-prototype\nconstructor=trace-constructor\ntoString=trace-string',
    );

    expect(result.errors).toEqual([]);
    expect(Object.keys(result.properties)).toEqual(['__proto__', 'constructor', 'toString']);
    expect(result.properties['__proto__']).toBe('trace-prototype');
    expect(result.properties['constructor']).toBe('trace-constructor');
    expect(result.properties['toString']).toBe('trace-string');
    const serialized = JSON.parse(JSON.stringify(result.properties)) as Record<string, string>;
    expect(serialized['__proto__']).toBe('trace-prototype');
    expect(serialized['constructor']).toBe('trace-constructor');
    expect(serialized['toString']).toBe('trace-string');
  });

  it('still reports duplicate special property names', () => {
    const result = parseMessageProperties('__proto__=first\n__proto__=second');

    expect(result.properties['__proto__']).toBe('first');
    expect(result.errors).toEqual([{ kind: 'duplicate', key: '__proto__' }]);
  });
});
