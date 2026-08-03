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

import { getTopicConsumers, getTopicRoutes, listTopics } from './topicService';

vi.mock('./dataMode', () => ({ isMockMode: () => true }));
vi.mock('../config', () => ({
  API_BASE_URL: '/api',
}));

describe('topic service mock data', () => {
  it('returns copied topic rows', async () => {
    const first = await listTopics({ search: 'order-create' });
    expect(first[0].name).toBe('order-create');

    first[0].name = 'mutated-topic';

    const second = await listTopics({ search: 'order-create' });
    expect(second[0].name).toBe('order-create');
    expect(second[0]).not.toBe(first[0]);
  });

  it('returns copied topic route rows', async () => {
    const first = await getTopicRoutes('order-create');
    expect(first[0].brokerName).toBe('broker-a-0');

    first[0].brokerName = 'mutated-broker';

    const second = await getTopicRoutes('order-create');
    expect(second[0].brokerName).toBe('broker-a-0');
    expect(second[0]).not.toBe(first[0]);
  });

  it('returns copied topic consumer rows', async () => {
    const first = await getTopicConsumers('order-create');
    expect(first[0].group).toBe('GID_order_service');

    first[0].group = 'mutated-group';

    const second = await getTopicConsumers('order-create');
    expect(second[0].group).toBe('GID_order_service');
    expect(second[0]).not.toBe(first[0]);
  });

  it('trims search text before filtering topic names', async () => {
    const topics = await listTopics({ search: '  ORDER-CREATE  ' });

    expect(topics.map((topic) => topic.name)).toEqual(['order-create']);
  });

  it('ignores blank search text', async () => {
    const allTopics = await listTopics();
    const blankSearchTopics = await listTopics({ search: '   ' });

    expect(blankSearchTopics).toHaveLength(allTopics.length);
  });
});
