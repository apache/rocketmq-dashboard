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

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.rocketmq.studio.persistence.entity.RmqAiConversation;
import org.apache.rocketmq.studio.persistence.mapper.RmqAiConversationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The conversation search is the one LIKE that started life with a private copy of the escaping
 * rule. It escaped the term but named no escape character, so on an engine whose default differs the
 * pattern was only half effective; the wiring assertion below is what keeps the shared predicate and
 * its {@code ESCAPE} clause in place.
 */
@ExtendWith(MockitoExtension.class)
class MybatisPlusAiConversationRepositoryTest {

    @Mock
    private RmqAiConversationMapper conversationMapper;

    @InjectMocks
    private MybatisPlusAiConversationRepository repository;

    @Test
    void findPageShouldEscapeLikeWildcardsInTheTitleSearchTest() {
        when(conversationMapper.selectPage(any(IPage.class), any(Wrapper.class)))
                .thenReturn(new Page<RmqAiConversation>(1, 20).setRecords(List.of()).setTotal(0));

        repository.findPage("ai-persistence-it", "100%_done", null, 1, 20);

        ArgumentCaptor<QueryWrapper<RmqAiConversation>> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(conversationMapper).selectPage(any(IPage.class), queryCaptor.capture());
        // MyBatis-Plus binds the values lazily, while it renders the SQL segment.
        String sqlSegment = queryCaptor.getValue().getSqlSegment();
        assertThat(sqlSegment).contains("title LIKE", "ESCAPE CHAR(92)");
        assertThat(queryCaptor.getValue().getParamNameValuePairs().values())
                .contains("ai-persistence-it", "%100\\%\\_done%");
    }
}
