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

const metadataApiMocks = vi.hoisted(() => ({
  deleteTopic: vi.fn(),
}));

vi.mock('../config', () => ({
  API_BASE_URL: '/api',
  USE_MOCK: false,
}));

vi.mock('../api/metadata', () => metadataApiMocks);

import { batchDeleteTopics } from './topicService';

describe('topic service batch deletion', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('continues deleting after a failure and reports each outcome', async () => {
    metadataApiMocks.deleteTopic.mockImplementation((name: string) =>
      name === 'topic-02' ? Promise.reject(new Error('delete failed')) : Promise.resolve(),
    );

    await expect(batchDeleteTopics(['topic-01', 'topic-02', 'topic-03'])).resolves.toEqual({
      deleted: ['topic-01', 'topic-03'],
      failed: ['topic-02'],
    });
    expect(metadataApiMocks.deleteTopic.mock.calls.map(([name]) => name)).toEqual([
      'topic-01',
      'topic-02',
      'topic-03',
    ]);
  });

  it('resolves with empty buckets for an empty selection without touching the api', async () => {
    await expect(batchDeleteTopics([])).resolves.toEqual({ deleted: [], failed: [] });
    expect(metadataApiMocks.deleteTopic).not.toHaveBeenCalled();
  });

  it('forwards the instance id to every deletion attempt', async () => {
    metadataApiMocks.deleteTopic.mockResolvedValue(undefined);

    await expect(batchDeleteTopics(['topic-01', 'topic-02'], 'cluster-pre')).resolves.toEqual({
      deleted: ['topic-01', 'topic-02'],
      failed: [],
    });
    expect(metadataApiMocks.deleteTopic).toHaveBeenCalledWith('topic-01', 'cluster-pre');
    expect(metadataApiMocks.deleteTopic).toHaveBeenCalledWith('topic-02', 'cluster-pre');
  });

  it('reports an all-successful selection in the original order', async () => {
    metadataApiMocks.deleteTopic.mockResolvedValue(undefined);

    await expect(batchDeleteTopics(['alpha', 'beta', 'gamma'])).resolves.toEqual({
      deleted: ['alpha', 'beta', 'gamma'],
      failed: [],
    });
    expect(metadataApiMocks.deleteTopic.mock.calls.map(([name]) => name)).toEqual([
      'alpha',
      'beta',
      'gamma',
    ]);
  });

  it('keeps attempting later topics when the api rejects with a non-Error value', async () => {
    metadataApiMocks.deleteTopic.mockImplementation((name: string) =>
      name === 'topic-02' ? Promise.reject('topic gone') : Promise.resolve(),
    );

    await expect(batchDeleteTopics(['topic-01', 'topic-02', 'topic-03'])).resolves.toEqual({
      deleted: ['topic-01', 'topic-03'],
      failed: ['topic-02'],
    });
  });

  it('reports every topic as failed when the api rejects for each one', async () => {
    metadataApiMocks.deleteTopic.mockRejectedValue(new Error('cluster offline'));

    await expect(
      batchDeleteTopics(['topic-01', 'topic-02']),
    ).resolves.toEqual({ deleted: [], failed: ['topic-01', 'topic-02'] });
    expect(metadataApiMocks.deleteTopic).toHaveBeenCalledTimes(2);
  });
});
