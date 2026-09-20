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
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.persistence.entity.RmqAiConversation;
import org.apache.rocketmq.studio.persistence.mapper.RmqAiConversationMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** MySQL-backed AI conversation repository (rmq_ai_conversation). */
@RequiredArgsConstructor
@Repository
public class MybatisPlusAiConversationRepository implements AiConversationRepository {

    private final RmqAiConversationMapper conversationMapper;

    @Override
    public RmqAiConversation insert(RmqAiConversation conversation) {
        conversationMapper.insert(conversation);
        return conversation;
    }

    @Override
    public Optional<RmqAiConversation> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(conversationMapper.selectById(id));
    }

    @Override
    public Optional<RmqAiConversation> findByIdAndOwner(Long id, String owner) {
        if (id == null || !StringUtils.hasText(owner)) {
            return Optional.empty();
        }
        return Optional.ofNullable(conversationMapper.selectOne(new QueryWrapper<RmqAiConversation>()
                .eq("id", id)
                .eq("owner", owner)));
    }

    @Override
    public PageResult<RmqAiConversation> findPage(String owner, String search, Boolean archived,
                                                  int page, int pageSize) {
        QueryWrapper<RmqAiConversation> query = new QueryWrapper<RmqAiConversation>()
                .eq("owner", owner)
                .eq(archived != null, "archived", archived)
                .like(StringUtils.hasText(search), "title", escapeLike(search))
                // gmt_modified, not the project-wide gmt_create: a conversation resumed today
                // must float to the top. idx_ai_conversation_owner is built for this ordering.
                .orderByDesc("gmt_modified", "id");
        Page<RmqAiConversation> result = conversationMapper.selectPage(new Page<>(page, pageSize), query);
        return PageResult.of(result.getRecords(), result.getTotal(), page, pageSize);
    }

    @Override
    public void update(RmqAiConversation conversation) {
        conversationMapper.updateById(conversation);
    }

    @Override
    public int deleteById(Long id) {
        return id == null ? 0 : conversationMapper.deleteById(id);
    }

    @Override
    public List<Long> findIdsCreatedBefore(LocalDateTime cutoff, int limit) {
        if (cutoff == null || limit <= 0) {
            return List.of();
        }
        return conversationMapper.selectObjs(new QueryWrapper<RmqAiConversation>()
                        .select("id")
                        .lt("gmt_create", cutoff)
                        .orderByAsc("gmt_create", "id")
                        .last("LIMIT " + limit))
                .stream()
                .map(value -> ((Number) value).longValue())
                .toList();
    }

    @Override
    public int deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return conversationMapper.deleteByIds(ids);
    }

    /**
     * Escapes LIKE wildcards so user-supplied search terms match literally. Mirrors
     * QueryHistoryService.escapeLike; a shared util is tracked separately upstream.
     */
    private static String escapeLike(String search) {
        if (!StringUtils.hasText(search)) {
            return search;
        }
        return search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
