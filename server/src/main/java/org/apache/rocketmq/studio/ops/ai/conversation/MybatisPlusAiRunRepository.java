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
import org.apache.rocketmq.studio.ops.ai.conversation.event.RunStatus;
import org.apache.rocketmq.studio.persistence.entity.RmqAiRun;
import org.apache.rocketmq.studio.persistence.mapper.RmqAiRunMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * MySQL-backed AI run repository (rmq_ai_run).
 *
 * <p>The status vocabulary lives on {@link RunStatus} and is never re-listed here:
 * {@link RunStatus#ACTIVE_STATUSES} is the single definition of "still running" used by the active
 * run queries, and {@link RunStatus#isTerminal()} is the single terminal check.
 */
@RequiredArgsConstructor
@Repository
public class MybatisPlusAiRunRepository implements AiRunRepository {

    private final RmqAiRunMapper runMapper;

    @Override
    public RmqAiRun insert(RmqAiRun run) {
        runMapper.insert(run);
        return run;
    }

    @Override
    public Optional<RmqAiRun> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(runMapper.selectById(id));
    }

    @Override
    public Optional<RmqAiRun> findActiveByConversationId(Long conversationId) {
        if (conversationId == null) {
            return Optional.empty();
        }
        List<RmqAiRun> active = runMapper.selectList(new QueryWrapper<RmqAiRun>()
                .eq("conversation_id", conversationId)
                .in("status", RunStatus.ACTIVE_STATUSES)
                .orderByDesc("id")
                .last("LIMIT 1"));
        return active.stream().findFirst();
    }

    @Override
    public List<RmqAiRun> findByConversationId(Long conversationId) {
        if (conversationId == null) {
            return List.of();
        }
        return runMapper.selectList(new QueryWrapper<RmqAiRun>()
                .eq("conversation_id", conversationId)
                .orderByAsc("turn", "id"));
    }

    @Override
    public Map<Long, RmqAiRun> findLatestByConversationIds(Collection<Long> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = conversationIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        // Two statements for a whole page, not one per conversation. The grouping is served by
        // idx_ai_run_conversation (conversation_id, id), so MAX(id) is an index-only lookup.
        List<Long> latestIds = runMapper.selectObjs(new QueryWrapper<RmqAiRun>()
                        .select("MAX(id)")
                        .in("conversation_id", ids)
                        .groupBy("conversation_id"))
                .stream()
                .filter(Objects::nonNull)
                .map(value -> ((Number) value).longValue())
                .toList();
        if (latestIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, RmqAiRun> latest = new HashMap<>();
        for (RmqAiRun run : runMapper.selectList(new QueryWrapper<RmqAiRun>().in("id", latestIds))) {
            if (run.getConversationId() == null) {
                continue;
            }
            // MAX(id) already selects one row per group; the merge is a tie-break so an unexpected
            // duplicate cannot silently report the older run as the conversation's last one.
            latest.merge(run.getConversationId(), run, MybatisPlusAiRunRepository::newerOf);
        }
        return latest;
    }

    @Override
    public int maxTurn(Long conversationId) {
        if (conversationId == null) {
            return 0;
        }
        return firstInt(runMapper.selectObjs(new QueryWrapper<RmqAiRun>()
                .select("COALESCE(MAX(turn), 0)")
                .eq("conversation_id", conversationId)));
    }

    @Override
    public void update(RmqAiRun run) {
        runMapper.updateById(run);
    }

    @Override
    public List<RmqAiRun> findByStatusIn(Collection<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        return runMapper.selectList(new QueryWrapper<RmqAiRun>().in("status", statuses));
    }

    @Override
    public List<RmqAiRun> findStaleActive(LocalDateTime modifiedBefore, Collection<String> statuses) {
        if (modifiedBefore == null || statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        return runMapper.selectList(new QueryWrapper<RmqAiRun>()
                .in("status", statuses)
                .lt("gmt_modified", modifiedBefore));
    }

    @Override
    public int deleteByConversationId(Long conversationId) {
        if (conversationId == null) {
            return 0;
        }
        return runMapper.delete(new QueryWrapper<RmqAiRun>().eq("conversation_id", conversationId));
    }

    @Override
    public int deleteByConversationIds(List<Long> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return 0;
        }
        return runMapper.delete(new QueryWrapper<RmqAiRun>().in("conversation_id", conversationIds));
    }

    private static int firstInt(List<Object> values) {
        if (values == null || values.isEmpty() || values.get(0) == null) {
            return 0;
        }
        return ((Number) values.get(0)).intValue();
    }

    /** The run with the higher id, i.e. the one admitted later. Null ids lose to everything. */
    private static RmqAiRun newerOf(RmqAiRun first, RmqAiRun second) {
        if (first.getId() == null) {
            return second;
        }
        if (second.getId() == null) {
            return first;
        }
        return second.getId() > first.getId() ? second : first;
    }
}
