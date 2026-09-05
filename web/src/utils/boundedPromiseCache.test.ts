/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import { describe, expect, it } from 'vitest';
import { BoundedPromiseCache } from './boundedPromiseCache';

describe('BoundedPromiseCache', () => {
  it('rejects an invalid capacity', () => {
    expect(() => new BoundedPromiseCache(0)).toThrow('maxEntries must be a positive integer');
    expect(() => new BoundedPromiseCache(1.5)).toThrow('maxEntries must be a positive integer');
  });

  it('evicts the least recently used entry when capacity is exceeded', async () => {
    const cache = new BoundedPromiseCache<string, string>(2);
    const first = Promise.resolve('first');
    const second = Promise.resolve('second');
    const third = Promise.resolve('third');
    cache.set('a', first);
    cache.set('b', second);
    expect(cache.get('a')).toBe(first);
    cache.set('c', third);

    expect(cache.size).toBe(2);
    expect(cache.get('a')).toBe(first);
    expect(cache.get('b')).toBeUndefined();
    expect(await cache.get('c')).toBe('third');
  });

  it('refreshes recency on a cache hit and replaces duplicate keys', () => {
    const cache = new BoundedPromiseCache<string, number>(2);
    const first = Promise.resolve(1);
    const replacement = Promise.resolve(2);
    cache.set('a', first);
    cache.set('b', Promise.resolve(3));
    expect(cache.get('a')).toBe(first);
    cache.set('a', replacement);
    expect(cache.get('a')).toBe(replacement);
    expect(cache.size).toBe(2);
  });

  it('supports explicit deletion for rejected requests and cleanup', () => {
    const cache = new BoundedPromiseCache<string, string>(2);
    cache.set('a', Promise.resolve('a'));
    cache.set('b', Promise.resolve('b'));
    expect(cache.delete('a')).toBe(true);
    expect(cache.delete('a')).toBe(false);
    cache.clear();
    expect(cache.size).toBe(0);
  });
});
