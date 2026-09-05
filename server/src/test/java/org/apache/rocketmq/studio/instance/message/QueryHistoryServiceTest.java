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
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
        assertThat(captor.getValue().getTraceTopic()).isNull();
    }

    @Test
    void recordsNormalizedCustomTraceTopic() {
        AuthenticatedUserContext.setUsername("alice");

        service.recordTraceQuery("cluster-a", "msg-1", "orders", "  CUSTOM_TRACE  ", 2, 1);

        ArgumentCaptor<RmqTraceQuery> captor = ArgumentCaptor.forClass(RmqTraceQuery.class);
        verify(traceQueryMapper).insert(captor.capture());
        assertThat(captor.getValue().getTraceTopic()).isEqualTo("CUSTOM_TRACE");
        assertThat(captor.getValue().getQueriedBy()).isEqualTo("alice");
    }

    @Test
    void mapsCustomTraceTopicIntoHistoryView() {
        AuthenticatedUserContext.setUsername("alice");
        RmqTraceQuery entity = new RmqTraceQuery();
        entity.setId(7L);
        entity.setMsgId("msg-7");
        entity.setTopic("orders");
        entity.setTraceTopic("CUSTOM_TRACE");
        entity.setNodeCount(3);
        entity.setConsumerCount(2);
        entity.setClusterId("cluster-a");
        entity.setQueriedBy("alice");
        entity.setGmtCreate(LocalDateTime.of(2026, 8, 5, 12, 0));
        when(traceQueryMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(invocation -> {
                    Page<RmqTraceQuery> result = invocation.getArgument(0);
                    result.setRecords(java.util.List.of(entity));
                    result.setTotal(1);
                    return result;
                });

        PageResult<TraceQueryHistoryVO> result = service.listTraceQueries("cluster-a", "CUSTOM", 1, 20);

        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getTraceTopic()).isEqualTo("CUSTOM_TRACE");
            assertThat(item.getMsgId()).isEqualTo("msg-7");
        });

        ArgumentCaptor<Wrapper<RmqTraceQuery>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(traceQueryMapper).selectPage(any(Page.class), queryCaptor.capture());
        assertThat(queryCaptor.getValue().getCustomSqlSegment()).contains("trace_topic");
    }

    @Test
    void purgesBothQueryHistoriesUsingConfiguredRetention() {
        properties.setRetentionDays(7);
        when(messageQueryMapper.selectList(any())).thenReturn(List.of());
        when(traceQueryMapper.selectList(any())).thenReturn(List.of());

        service.purgeExpiredQueries();

        ArgumentCaptor<Wrapper<RmqMessageQuery>> messageCaptor = ArgumentCaptor.forClass(Wrapper.class);
        ArgumentCaptor<Wrapper<RmqTraceQuery>> traceCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(messageQueryMapper).selectList(messageCaptor.capture());
        verify(traceQueryMapper).selectList(traceCaptor.capture());
        assertThat(messageCaptor.getValue().getCustomSqlSegment())
                .contains("gmt_create", "ORDER BY gmt_create ASC,id ASC", "LIMIT 500");
        assertThat(traceCaptor.getValue().getCustomSqlSegment())
                .contains("gmt_create", "ORDER BY gmt_create ASC,id ASC", "LIMIT 500");
    }

    @Test
    void purgeExpiredQueriesDeletesMessageAndTraceHistoryInBoundedBatchesTest() {
        properties.setRetentionDays(7);
        properties.setCleanupBatchSize(2);
        properties.setCleanupMaxBatches(3);
        RmqMessageQuery message1 = messageQuery(1L);
        RmqMessageQuery message2 = messageQuery(2L);
        RmqMessageQuery message3 = messageQuery(3L);
        RmqTraceQuery trace1 = traceQuery(11L);
        RmqTraceQuery trace2 = traceQuery(12L);
        when(messageQueryMapper.selectList(any()))
                .thenReturn(List.of(message1, message2), List.of(message3));
        when(traceQueryMapper.selectList(any()))
                .thenReturn(List.of(trace1, trace2), List.of());
        when(messageQueryMapper.deleteByIds(List.of(1L, 2L))).thenReturn(2);
        when(messageQueryMapper.deleteByIds(List.of(3L))).thenReturn(1);
        when(traceQueryMapper.deleteByIds(List.of(11L, 12L))).thenReturn(2);

        service.purgeExpiredQueries();

        ArgumentCaptor<Wrapper<RmqMessageQuery>> messageQueryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        ArgumentCaptor<Wrapper<RmqTraceQuery>> traceQueryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(messageQueryMapper, times(2)).selectList(messageQueryCaptor.capture());
        verify(traceQueryMapper, times(2)).selectList(traceQueryCaptor.capture());
        assertThat(messageQueryCaptor.getAllValues().get(0).getCustomSqlSegment())
                .contains("gmt_create", "ORDER BY gmt_create ASC,id ASC", "LIMIT 2");
        assertThat(traceQueryCaptor.getAllValues().get(0).getCustomSqlSegment())
                .contains("gmt_create", "ORDER BY gmt_create ASC,id ASC", "LIMIT 2");
        verify(messageQueryMapper).deleteByIds(List.of(1L, 2L));
        verify(messageQueryMapper).deleteByIds(List.of(3L));
        verify(traceQueryMapper).deleteByIds(List.of(11L, 12L));
        verify(messageQueryMapper, never()).delete(any());
        verify(traceQueryMapper, never()).delete(any());
    }

    @Test
    void purgeExpiredQueriesContinuesTraceCleanupWhenMessageCleanupFailsTest() {
        properties.setRetentionDays(7);
        properties.setCleanupBatchSize(2);
        properties.setCleanupMaxBatches(3);
        when(messageQueryMapper.selectList(any())).thenThrow(new IllegalStateException("message db down"));
        RmqTraceQuery trace = traceQuery(21L);
        when(traceQueryMapper.selectList(any())).thenReturn(List.of(trace));
        when(traceQueryMapper.deleteByIds(List.of(21L))).thenReturn(1);

        service.purgeExpiredQueries();

        verify(traceQueryMapper).selectList(any());
        verify(traceQueryMapper).deleteByIds(List.of(21L));
    }

    @Test
    void disablesCleanupWhenRetentionIsNonPositive() {
        properties.setRetentionDays(0);

        service.purgeExpiredQueries();

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
    void loadsResultSnapshotOnlyForTheAuthenticatedOperator() {
        AuthenticatedUserContext.setUsername("alice");
        RmqMessageQuery entity = new RmqMessageQuery();
        entity.setId(9L);
        entity.setQueriedBy("alice");
        entity.setResultSnapshot("[{\"msgId\":\"msg-9\",\"topic\":\"orders\"}]");
        when(messageQueryMapper.selectOne(any())).thenReturn(entity);

        List<MessageRecordVO> results = service.getMessageQueryResults(9L);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.getMsgId()).isEqualTo("msg-9");
            assertThat(result.getTopic()).isEqualTo("orders");
        });
        ArgumentCaptor<QueryWrapper<RmqMessageQuery>> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(messageQueryMapper).selectOne(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getCustomSqlSegment())
                .contains("id", "queried_by");
        assertThat(queryCaptor.getValue().getParamNameValuePairs().values())
                .contains(9L, "alice");
    }

    @Test
    void hidesResultSnapshotOwnedByAnotherOperator() {
        AuthenticatedUserContext.setUsername("bob");
        when(messageQueryMapper.selectOne(any())).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getMessageQueryResults(9L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Query history record not found");

        ArgumentCaptor<QueryWrapper<RmqMessageQuery>> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(messageQueryMapper).selectOne(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getCustomSqlSegment())
                .contains("id", "queried_by");
        assertThat(queryCaptor.getValue().getParamNameValuePairs().values())
                .contains(9L, "bob");
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

    private static RmqMessageQuery messageQuery(Long id) {
        RmqMessageQuery query = new RmqMessageQuery();
        query.setId(id);
        return query;
    }

    private static RmqTraceQuery traceQuery(Long id) {
        RmqTraceQuery query = new RmqTraceQuery();
        query.setId(id);
        return query;
    }
}
