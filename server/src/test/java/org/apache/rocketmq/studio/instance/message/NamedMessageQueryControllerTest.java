/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.message;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NamedMessageQueryController.class)
@AutoConfigureMockMvc(addFilters = false)
class NamedMessageQueryControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private NamedMessageQueryService service;

    @Test
    void listsSharedNamedQueriesTest() throws Exception {
        when(service.list("alpha")).thenReturn(List.of(new NamedMessageQueryService.NamedQuery(
                "one", "alpha", "Orders", "key", "orders", "key-1", null, null, null, 10, 20, "alice")));
        mvc.perform(get("/api/query-history/named-messages").param("instanceId", "alpha"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].name").value("Orders"))
                .andExpect(jsonPath("$.data[0].createdBy").value("alice"));
    }

    @Test
    void routesSaveRenameAndDeleteTest() throws Exception {
        mvc.perform(post("/api/query-history/named-messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instanceId\":\"alpha\",\"name\":\"Orders\",\"mode\":\"key\",\"topic\":\"orders\",\"key\":\"one\"}"))
                .andExpect(status().isOk());
        verify(service).save(new NamedMessageQueryService.Draft("alpha", "Orders", "key", "orders", "one", null, null, null));
        mvc.perform(put("/api/query-history/named-messages/one").param("instanceId", "alpha")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Updated\"}"))
                .andExpect(status().isOk());
        verify(service).rename("alpha", "one", "Updated");
        mvc.perform(delete("/api/query-history/named-messages/one").param("instanceId", "alpha"))
                .andExpect(status().isOk());
        verify(service).delete("alpha", "one");
    }

    @Test
    void exposesConflictAndRequiresInstanceTest() throws Exception {
        doThrow(new BusinessException(409, "duplicate")).when(service).save(any());
        mvc.perform(post("/api/query-history/named-messages").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/query-history/named-messages")).andExpect(status().isBadRequest());
    }
}
