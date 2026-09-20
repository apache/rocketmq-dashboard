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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.persistence.entity.RmqAiEvent;
import org.apache.rocketmq.studio.persistence.mapper.RmqAiEventMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** MySQL-backed AI event repository (rmq_ai_event). */
@RequiredArgsConstructor
@Repository
public class MybatisPlusAiEventRepository implements AiEventRepository {

    /** Hard ceiling so a caller cannot ask for an unbounded timeline slice. */
    private static final int MAX_LIMIT = 500;

    private final RmqAiEventMapper eventMapper;

    @Override
    public void insert(RmqAiEvent event) {
        eventMapper.insert(event);
    }

    @Override
    public int maxSeq(Long conversationId) {
        if (conversationId == null) {
            return 0;
        }
        List<Object> values = eventMapper.selectObjs(new QueryWrapper<RmqAiEvent>()
                .select("COALESCE(MAX(seq), 0)")
                .eq("conversation_id", conversationId));
        if (values == null || values.isEmpty() || values.get(0) == null) {
            return 0;
        }
        return ((Number) values.get(0)).intValue();
    }

    @Override
    public List<RmqAiEvent> findByConversationIdAfterSeq(Long conversationId, int afterSeq, int limit) {
        if (conversationId == null || limit <= 0) {
            return List.of();
        }
        int bounded = Math.min(limit, MAX_LIMIT);
        // No SQL ORDER BY on purpose: payload is MEDIUMTEXT and any sort MySQL cannot serve
        // from uk_ai_event_conversation_seq materialises the whole value into
        // sort_buffer_size. Filter on the index, then sort this bounded slice in memory.
        List<RmqAiEvent> rows = eventMapper.selectList(new QueryWrapper<RmqAiEvent>()
                .eq("conversation_id", conversationId)
                .gt("seq", afterSeq)
                .last("LIMIT " + bounded));
        List<RmqAiEvent> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(RmqAiEvent::getSeq).thenComparing(RmqAiEvent::getId));
        return sorted;
    }

    @Override
    public int deleteByConversationId(Long conversationId) {
        if (conversationId == null) {
            return 0;
        }
        return eventMapper.delete(new QueryWrapper<RmqAiEvent>().eq("conversation_id", conversationId));
    }

    @Override
    public int deleteByConversationIds(List<Long> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return 0;
        }
        return eventMapper.delete(new QueryWrapper<RmqAiEvent>().in("conversation_id", conversationIds));
    }
}
