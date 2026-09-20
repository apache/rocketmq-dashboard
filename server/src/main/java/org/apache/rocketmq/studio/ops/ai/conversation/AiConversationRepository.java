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

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.persistence.entity.RmqAiConversation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Persistence port for rmq_ai_conversation. */
public interface AiConversationRepository {

    /** Inserts and returns the row with its generated id populated. */
    RmqAiConversation insert(RmqAiConversation conversation);

    Optional<RmqAiConversation> findById(Long id);

    /**
     * Owner-scoped lookup. Returns empty rather than throwing so callers can answer 404 for
     * somebody else's id, which avoids leaking which ids exist.
     */
    Optional<RmqAiConversation> findByIdAndOwner(Long id, String owner);

    /**
     * Paged list for one owner, ordered by gmt_modified desc then id desc so a conversation
     * resumed today floats to the top. Deliberately NOT the project-wide gmt_create ordering.
     */
    PageResult<RmqAiConversation> findPage(String owner, String search, Boolean archived, int page, int pageSize);

    void update(RmqAiConversation conversation);

    int deleteById(Long id);

    /** Ids created strictly before the cutoff, oldest first, capped at limit. */
    List<Long> findIdsCreatedBefore(LocalDateTime cutoff, int limit);

    int deleteByIds(List<Long> ids);
}
