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
import { mockDLQGroups } from './dlq';

/**
 * RocketMQDLQProvider.listDLQGroups (server/src/main/java/org/apache/rocketmq/studio/provider/
 * apache/RocketMQDLQProvider.java:172) is the only producer of DLQGroupVO.status:
 *
 *   .status(statsAvailable ? (messageCount > 0 ? "ACTIVE" : "EMPTY") : "UNAVAILABLE")
 *
 * The DLQ page renders the field verbatim, so mock rows must carry those codes and must stay
 * consistent with the message count the same expression uses.
 */
const backendStatuses = ['ACTIVE', 'EMPTY', 'UNAVAILABLE'];

describe('DLQ mock contract', () => {
  it('pins the group status to the values the backend emits', () => {
    const statuses = [...new Set(mockDLQGroups.map((group) => group.status))];

    expect(statuses.filter((status) => !backendStatuses.includes(status))).toEqual([]);
  });

  it('keeps the status consistent with the backlog the backend counts', () => {
    const inconsistent = mockDLQGroups
      .filter((group) => group.status !== (group.messageCount > 0 ? 'ACTIVE' : 'EMPTY'))
      .map((group) => `${group.groupName}:${group.status}:${group.messageCount}`);

    expect(inconsistent).toEqual([]);
  });

  it('derives the dead-letter topic from the group name', () => {
    const derived = mockDLQGroups
      .filter((group) => group.dlqTopic !== `%DLQ%${group.groupName}`)
      .map((group) => group.dlqTopic);

    expect(derived).toEqual([]);
  });
});
