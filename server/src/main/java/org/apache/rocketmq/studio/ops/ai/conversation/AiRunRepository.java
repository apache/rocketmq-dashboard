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

import org.apache.rocketmq.studio.persistence.entity.RmqAiRun;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Persistence port for rmq_ai_run. */
public interface AiRunRepository {

    /** Inserts and returns the row with its generated id populated. */
    RmqAiRun insert(RmqAiRun run);

    Optional<RmqAiRun> findById(Long id);

    /** The conversation's run in QUEUED or RUNNING, if any. At most one by admission rule. */
    Optional<RmqAiRun> findActiveByConversationId(Long conversationId);

    List<RmqAiRun> findByConversationId(Long conversationId);

    /**
     * The newest run of each given conversation, keyed by conversation id; conversations that never
     * ran are simply absent.
     *
     * <p>A batch on purpose: {@code GET /api/ai/conversations} shows every row's
     * {@code lastRunId}/{@code lastRunStatus}, and resolving that per row would be one query per
     * conversation on a page of up to a hundred. This is two queries for the whole page — a
     * {@code MAX(id)} grouped by conversation, served by {@code idx_ai_run_conversation}, then the
     * rows for those ids.
     */
    Map<Long, RmqAiRun> findLatestByConversationIds(Collection<Long> conversationIds);

    /** Highest existing turn for the conversation, or 0 when it has none. */
    int maxTurn(Long conversationId);

    void update(RmqAiRun run);

    /** Used by the startup reaper: any run left non-terminal across a restart is orphaned. */
    List<RmqAiRun> findByStatusIn(Collection<String> statuses);

    /** Runs still non-terminal whose gmt_modified is older than the cutoff. */
    List<RmqAiRun> findStaleActive(LocalDateTime modifiedBefore, Collection<String> statuses);

    int deleteByConversationId(Long conversationId);

    int deleteByConversationIds(List<Long> conversationIds);
}
