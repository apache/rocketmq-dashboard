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
package org.apache.rocketmq.studio.ops.ai.conversation;

import org.apache.rocketmq.studio.persistence.entity.RmqAiEvent;

import java.util.List;

/** Persistence port for rmq_ai_event. */
public interface AiEventRepository {

    void insert(RmqAiEvent event);

    /**
     * The conversation's current maximum seq, or 0 when it has no events. This — not
     * rmq_ai_conversation.last_seq — is the authority a new run seeds its allocator from;
     * last_seq is only a cache for the reconnect fast path and can be stale after a crash.
     */
    int maxSeq(Long conversationId);

    /**
     * Events with seq strictly greater than afterSeq, capped at limit, ascending by seq.
     *
     * <p>The ascending order is part of the statement, not a courtesy of the storage engine.
     * uk_ai_event_conversation_seq leads with the equality column and orders by seq, so the range
     * scan that applies the seq predicate already visits the qualifying rows in seq order and MySQL
     * satisfies ORDER BY seq ASC from that index without materialising the MEDIUMTEXT payload into
     * sort_buffer_size. An implementation must order before it limits: which rows a LIMIT keeps
     * without an ORDER BY is unspecified, and the caller derives its next cursor from the last row
     * of the page it is handed.
     */
    List<RmqAiEvent> findByConversationIdAfterSeq(Long conversationId, int afterSeq, int limit);

    int deleteByConversationId(Long conversationId);

    int deleteByConversationIds(List<Long> conversationIds);
}
