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
package org.apache.rocketmq.studio.instance.message;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.persistence.entity.RmqMessageQuery;
import org.apache.rocketmq.studio.persistence.entity.RmqTraceQuery;
import org.apache.rocketmq.studio.persistence.mapper.RmqMessageQueryMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTraceQueryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryHistoryServiceTest {

    private final RmqMessageQueryMapper messageQueryMapper = mock(RmqMessageQueryMapper.class);
    private final RmqTraceQueryMapper traceQueryMapper = mock(RmqTraceQueryMapper.class);
    private final QueryHistoryProperties properties = new QueryHistoryProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-05T12:00:00Z"), ZoneOffset.UTC);
    private final QueryHistoryService service = new QueryHistoryService(
            messageQueryMapper, traceQueryMapper, properties, clock, new ObjectMapper());

    @AfterEach
    void clearUserContext() {
        AuthenticatedUserContext.clear();
    }

    @Test
    void recordsMessageQueryWithClusterAndAuthenticatedOperator() {
        AuthenticatedUserContext.setUsername("alice");

        service.recordMessageQuery("cluster-a", "TOPIC", "orders", null, "tag-a", "key-a",
                1L, 2L, 3, null);

        ArgumentCaptor<RmqMessageQuery> captor = ArgumentCaptor.forClass(RmqMessageQuery.class);
        verify(messageQueryMapper).insert(captor.capture());
        RmqMessageQuery query = captor.getValue();
        assertThat(query.getClusterId()).isEqualTo("cluster-a");
        assertThat(query.getQueriedBy()).isEqualTo("alice");
        assertThat(query.getGmtCreate()).isEqualTo(LocalDateTime.of(2026, 8, 5, 12, 0));
    }

    @Test
    void recordsTraceQueryWithSystemOperatorWhenNoUserIsAuthenticated() {
        service.recordTraceQuery("cluster-a", "msg-1", "orders", 2, 1);

        ArgumentCaptor<RmqTraceQuery> captor = ArgumentCaptor.forClass(RmqTraceQuery.class);
        verify(traceQueryMapper).insert(captor.capture());
        assertThat(captor.getValue().getClusterId()).isEqualTo("cluster-a");
        assertThat(captor.getValue().getQueriedBy()).isEqualTo(AuthenticatedUserContext.SYSTEM_ACTOR);
    }

    @Test
    void purgesBothQueryHistoriesUsingConfiguredRetention() {
        properties.setRetentionDays(7);
        when(messageQueryMapper.delete(any())).thenReturn(2);
        when(traceQueryMapper.delete(any())).thenReturn(3);

        service.purgeExpiredQueries();

        ArgumentCaptor<Wrapper<RmqMessageQuery>> messageCaptor = ArgumentCaptor.forClass(Wrapper.class);
        ArgumentCaptor<Wrapper<RmqTraceQuery>> traceCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(messageQueryMapper).delete(messageCaptor.capture());
        verify(traceQueryMapper).delete(traceCaptor.capture());
        assertThat(messageCaptor.getValue().getCustomSqlSegment()).contains("gmt_create");
        assertThat(traceCaptor.getValue().getCustomSqlSegment()).contains("gmt_create");
    }

    @Test
    void disablesCleanupWhenRetentionIsNonPositive() {
        properties.setRetentionDays(0);

        service.purgeExpiredQueries();

        verify(messageQueryMapper, never()).delete(any());
        verify(traceQueryMapper, never()).delete(any());
    }

    @Test
    void skipsPurgeWhenRetentionExceedsTheDateRange() {
        // The service clock is injectable: a clock near the low end of the date range plus a
        // multi-millennium retention pushes the cutoff past LocalDateTime.MIN.
        QueryHistoryService ranged = new QueryHistoryService(
                messageQueryMapper, traceQueryMapper, properties,
                Clock.fixed(LocalDateTime.of(-999_990_000, 1, 1, 0, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
                new ObjectMapper());
        properties.setRetentionDays(10_000_000);

        ranged.purgeExpiredQueries();

        verify(messageQueryMapper, never()).delete(any());
        verify(traceQueryMapper, never()).delete(any());
    }

    @Test
    void listsMessageHistoryAsNewestFirstPage() {
        AuthenticatedUserContext.setUsername("alice");
        RmqMessageQuery entity = new RmqMessageQuery();
        entity.setId(9L);
        entity.setQueryType("KEY");
        entity.setTopic("orders");
        entity.setMessageKey("order-1");
        entity.setResultCount(3);
        entity.setClusterId("cluster-a");
        entity.setQueriedBy("alice");
        entity.setGmtCreate(LocalDateTime.of(2026, 8, 5, 12, 0));
        when(messageQueryMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(invocation -> {
                    Page<RmqMessageQuery> result = invocation.getArgument(0);
                    result.setRecords(java.util.List.of(entity));
                    result.setTotal(1);
                    return result;
                });

        PageResult<MessageQueryHistoryVO> result = service.listMessageQueries(
                "cluster-a", "KEY", "order", 1, 20);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getMessageKey()).isEqualTo("order-1");
            assertThat(item.getQueriedBy()).isEqualTo("alice");
        });

        ArgumentCaptor<Wrapper<RmqMessageQuery>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(messageQueryMapper).selectPage(any(Page.class), queryCaptor.capture());
        assertThat(queryCaptor.getValue().getCustomSqlSegment()).contains("queried_by");
    }

    @Test
    void summarizesBothHistoryStreams() {
        AuthenticatedUserContext.setUsername("alice");
        RmqMessageQuery message = new RmqMessageQuery();
        message.setGmtCreate(LocalDateTime.of(2026, 8, 5, 10, 0));
        RmqTraceQuery trace = new RmqTraceQuery();
        trace.setGmtCreate(LocalDateTime.of(2026, 8, 5, 12, 0));
        when(messageQueryMapper.selectCount(any())).thenReturn(7L);
        when(traceQueryMapper.selectCount(any())).thenReturn(4L);
        when(messageQueryMapper.selectOne(any())).thenReturn(message);
        when(traceQueryMapper.selectOne(any())).thenReturn(trace);

        QueryHistorySummaryVO summary = service.summarize("cluster-a");

        assertThat(summary.getMessageQueries()).isEqualTo(7);
        assertThat(summary.getTraceQueries()).isEqualTo(4);
        assertThat(summary.getLatestQueryAt()).isEqualTo(LocalDateTime.of(2026, 8, 5, 12, 0));

        ArgumentCaptor<Wrapper<RmqMessageQuery>> messageCountCaptor = ArgumentCaptor.forClass(Wrapper.class);
        ArgumentCaptor<Wrapper<RmqTraceQuery>> traceCountCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(messageQueryMapper).selectCount(messageCountCaptor.capture());
        verify(traceQueryMapper).selectCount(traceCountCaptor.capture());
        assertThat(messageCountCaptor.getValue().getCustomSqlSegment()).contains("queried_by");
        assertThat(traceCountCaptor.getValue().getCustomSqlSegment()).contains("queried_by");
    }
}
