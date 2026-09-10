/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.message;

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;


import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QueryHistoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class QueryHistoryControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QueryHistoryService queryHistoryService;

    @Test
    void listsFilteredMessageHistory() throws Exception {
        MessageQueryHistoryVO item = MessageQueryHistoryVO.builder()
                .id(1L).queryType("TOPIC").topic("orders").resultCount(2)
                .clusterId("instance-a").queriedBy("alice")
                .queriedAt(LocalDateTime.of(2026, 8, 5, 12, 0)).build();
        when(queryHistoryService.listMessageQueries("instance-a", "TOPIC", "orders", 2, 10))
                .thenReturn(PageResult.of(List.of(item), 11, 2, 10));

        mockMvc.perform(get("/api/query-history/messages")
                        .param("clusterId", "instance-a")
                        .param("queryType", "TOPIC")
                        .param("search", "orders")
                        .param("page", "2")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(11))
                .andExpect(jsonPath("$.data.items[0].topic").value("orders"));

        verify(queryHistoryService).listMessageQueries("instance-a", "TOPIC", "orders", 2, 10);
    }

    @Test
    void rejectsOversizedHistoryPages() throws Exception {
        mockMvc.perform(get("/api/query-history/traces").param("pageSize", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("pageSize must be between 1 and 100"));
    }

    @Test
    void returnsHistorySummary() throws Exception {
        when(queryHistoryService.summarize("instance-a"))
                .thenReturn(QueryHistorySummaryVO.builder()
                        .messageQueries(7).traceQueries(3)
                        .latestQueryAt(LocalDateTime.of(2026, 8, 5, 12, 0)).build());

        mockMvc.perform(get("/api/query-history/summary").param("clusterId", "instance-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messageQueries").value(7))
                .andExpect(jsonPath("$.data.traceQueries").value(3));
    }

    @Test
    void normalizesOptionalHistoryFilters() throws Exception {
        when(queryHistoryService.listMessageQueries("instance-a", "TOPIC", null, 1, 20))
                .thenReturn(PageResult.of(List.of(), 0, 1, 20));
        when(queryHistoryService.listTraceQueries("instance-a", null, 1, 20))
                .thenReturn(PageResult.of(List.of(), 0, 1, 20));

        mockMvc.perform(get("/api/query-history/messages")
                        .param("clusterId", "  instance-a  ")
                        .param("queryType", " TOPIC ")
                        .param("search", "   "))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/query-history/traces")
                        .param("clusterId", " instance-a ")
                        .param("search", "\t"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/query-history/summary").param("clusterId", "   "))
                .andExpect(status().isOk());

        verify(queryHistoryService).listMessageQueries("instance-a", "TOPIC", null, 1, 20);
        verify(queryHistoryService).listTraceQueries("instance-a", null, 1, 20);
        verify(queryHistoryService).summarize(null);
    }

    @Test
    void returnsStoredMessageQueryResults() throws Exception {
        MessageRecordVO message = MessageRecordVO.builder()
                .msgId("msg-1").topic("orders").build();
        when(queryHistoryService.getMessageQueryResults(12L)).thenReturn(List.of(message));

        mockMvc.perform(get("/api/query-history/messages/12/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].msgId").value("msg-1"))
                .andExpect(jsonPath("$.data[0].topic").value("orders"));

        verify(queryHistoryService).getMessageQueryResults(12L);
    }
}
