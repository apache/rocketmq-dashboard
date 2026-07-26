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

import { describe, expect, it, vi } from 'vitest';

vi.mock('../config', () => ({ API_BASE_URL: '/api', USE_MOCK: true }));

describe('topic service mock topic list', () => {
  it('trims search text before filtering topic names', async () => {
    const { listTopics } = await import('./topicService');

    const topics = await listTopics({ search: '  ORDER-CREATE  ' });

    expect(topics.map((topic) => topic.name)).toEqual(['order-create']);
  });

  it('ignores blank search text', async () => {
    const { listTopics } = await import('./topicService');

    const allTopics = await listTopics();
    const blankSearchTopics = await listTopics({ search: '   ' });

    expect(blankSearchTopics).toHaveLength(allTopics.length);
  });
});
