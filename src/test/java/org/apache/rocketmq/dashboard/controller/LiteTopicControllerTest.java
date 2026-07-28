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
package org.apache.rocketmq.dashboard.controller;

import org.apache.rocketmq.dashboard.model.LiteTopicQuota;
import org.apache.rocketmq.dashboard.model.LiteTopicSession;
import org.apache.rocketmq.dashboard.model.LiteTopicSummary;
import org.apache.rocketmq.dashboard.service.LiteTopicService;
import org.apache.rocketmq.dashboard.support.JsonResult;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class LiteTopicControllerTest {

    @InjectMocks
    private LiteTopicController liteTopicController;

    @Mock
    private LiteTopicService liteTopicService;

    @SuppressWarnings("unchecked")
    private <T> JsonResult<T> asResult(Object obj) {
        return (JsonResult<T>) obj;
    }

    // ==================== listLiteTopics ====================

    @Test
    public void testListLiteTopics() throws Exception {
        when(liteTopicService.listLiteTopics("order-*", Optional.of("ns1")))
            .thenReturn(Arrays.asList(new LiteTopicSummary()));

        JsonResult<Object> result = asResult(liteTopicController.listLiteTopics("order-*", " ns1 "));
        assertEquals(0, result.getStatus());
        assertNotNull(result.getData());
    }

    @Test
    public void testListLiteTopicsWithoutNamespace() throws Exception {
        when(liteTopicService.listLiteTopics(null, Optional.empty()))
            .thenReturn(Arrays.asList(new LiteTopicSummary()));

        JsonResult<Object> result = asResult(liteTopicController.listLiteTopics(null, " "));
        assertEquals(0, result.getStatus());
    }

    @Test
    public void testListLiteTopicsError() throws Exception {
        when(liteTopicService.listLiteTopics(any(), any())).thenThrow(new RuntimeException("boom"));

        JsonResult<Object> result = asResult(liteTopicController.listLiteTopics(null, null));
        assertEquals(1, result.getStatus());
        assertTrue(result.getErrMsg().contains("boom"));
    }

    // ==================== getLiteTopicSession ====================

    @Test
    public void testGetLiteTopicSessionBlankId() {
        JsonResult<Object> result = asResult(liteTopicController.getLiteTopicSession(" "));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testGetLiteTopicSession() throws Exception {
        when(liteTopicService.getLiteTopicSession("s1")).thenReturn(new LiteTopicSession());

        JsonResult<Object> result = asResult(liteTopicController.getLiteTopicSession("s1"));
        assertEquals(0, result.getStatus());
    }

    @Test
    public void testGetLiteTopicSessionUnsupported() throws Exception {
        when(liteTopicService.getLiteTopicSession("s1"))
            .thenThrow(new UnsupportedOperationException("not supported"));

        JsonResult<Map<String, Object>> result = asResult(liteTopicController.getLiteTopicSession("s1"));
        assertEquals(2, result.getStatus());
        assertEquals(Boolean.FALSE, result.getData().get("supported"));
    }

    @Test
    public void testGetLiteTopicSessionError() throws Exception {
        when(liteTopicService.getLiteTopicSession("s1")).thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(liteTopicController.getLiteTopicSession("s1"));
        assertEquals(1, result.getStatus());
    }

    // ==================== extendLiteTopicTTL ====================

    @Test
    public void testExtendTTLNullBody() {
        JsonResult<Object> result = asResult(liteTopicController.extendLiteTopicTTL(null));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testExtendTTLMissingPattern() {
        Map<String, Object> request = new HashMap<>();
        request.put("newTTL", 1000L);
        JsonResult<Object> result = asResult(liteTopicController.extendLiteTopicTTL(request));
        assertEquals(1, result.getStatus());
        assertTrue(result.getErrMsg().contains("topicPattern"));
    }

    @Test
    public void testExtendTTLMissingTTL() {
        Map<String, Object> request = new HashMap<>();
        request.put("topicPattern", "order-*");
        JsonResult<Object> result = asResult(liteTopicController.extendLiteTopicTTL(request));
        assertEquals(1, result.getStatus());
        assertTrue(result.getErrMsg().contains("newTTL"));
    }

    @Test
    public void testExtendTTLNumberValue() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("topicPattern", "order-*");
        request.put("newTTL", 3600000);

        JsonResult<Map<String, Object>> result = asResult(liteTopicController.extendLiteTopicTTL(request));
        assertEquals(0, result.getStatus());
        assertEquals(Boolean.TRUE, result.getData().get("success"));
        verify(liteTopicService).extendLiteTopicTTL("order-*", 3600000L);
    }

    @Test
    public void testExtendTTLStringValue() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("topicPattern", "order-*");
        request.put("newTTL", "7200000");

        JsonResult<Map<String, Object>> result = asResult(liteTopicController.extendLiteTopicTTL(request));
        assertEquals(0, result.getStatus());
        verify(liteTopicService).extendLiteTopicTTL("order-*", 7200000L);
    }

    @Test
    public void testExtendTTLInvalidStringValue() {
        Map<String, Object> request = new HashMap<>();
        request.put("topicPattern", "order-*");
        request.put("newTTL", "not-a-number");

        JsonResult<Object> result = asResult(liteTopicController.extendLiteTopicTTL(request));
        assertEquals(1, result.getStatus());
        assertTrue(result.getErrMsg().contains("valid number"));
    }

    @Test
    public void testExtendTTLIllegalArgument() throws Exception {
        doThrow(new IllegalArgumentException("bad pattern"))
            .when(liteTopicService).extendLiteTopicTTL(anyString(), anyLong());

        Map<String, Object> request = new HashMap<>();
        request.put("topicPattern", "bad");
        request.put("newTTL", 1L);
        JsonResult<Object> result = asResult(liteTopicController.extendLiteTopicTTL(request));
        assertEquals(1, result.getStatus());
        assertEquals("bad pattern", result.getErrMsg());
    }

    @Test
    public void testExtendTTLUnsupported() throws Exception {
        doThrow(new UnsupportedOperationException("v4"))
            .when(liteTopicService).extendLiteTopicTTL(anyString(), anyLong());

        Map<String, Object> request = new HashMap<>();
        request.put("topicPattern", "p");
        request.put("newTTL", 1L);
        JsonResult<Map<String, Object>> result = asResult(liteTopicController.extendLiteTopicTTL(request));
        assertEquals(2, result.getStatus());
        assertEquals(Boolean.FALSE, result.getData().get("supported"));
    }

    @Test
    public void testExtendTTLError() throws Exception {
        doThrow(new RuntimeException("err"))
            .when(liteTopicService).extendLiteTopicTTL(anyString(), anyLong());

        Map<String, Object> request = new HashMap<>();
        request.put("topicPattern", "p");
        request.put("newTTL", 1L);
        JsonResult<Object> result = asResult(liteTopicController.extendLiteTopicTTL(request));
        assertEquals(1, result.getStatus());
    }

    // ==================== getLiteTopicQuota ====================

    @Test
    public void testGetLiteTopicQuota() throws Exception {
        when(liteTopicService.getLiteTopicQuota(Optional.of("ns1"))).thenReturn(new LiteTopicQuota());

        JsonResult<Object> result = asResult(liteTopicController.getLiteTopicQuota("ns1"));
        assertEquals(0, result.getStatus());
    }

    @Test
    public void testGetLiteTopicQuotaNoNamespace() throws Exception {
        when(liteTopicService.getLiteTopicQuota(Optional.empty())).thenReturn(new LiteTopicQuota());

        JsonResult<Object> result = asResult(liteTopicController.getLiteTopicQuota(null));
        assertEquals(0, result.getStatus());
    }

    @Test
    public void testGetLiteTopicQuotaUnsupported() throws Exception {
        when(liteTopicService.getLiteTopicQuota(any()))
            .thenThrow(new UnsupportedOperationException("v4"));

        JsonResult<Map<String, Object>> result = asResult(liteTopicController.getLiteTopicQuota(null));
        assertEquals(2, result.getStatus());
        assertEquals(Boolean.FALSE, result.getData().get("supported"));
    }

    @Test
    public void testGetLiteTopicQuotaError() throws Exception {
        when(liteTopicService.getLiteTopicQuota(any())).thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(liteTopicController.getLiteTopicQuota(null));
        assertEquals(1, result.getStatus());
    }

    // ==================== getLiteTopicCapability ====================

    @Test
    public void testGetLiteTopicCapabilitySupported() {
        when(liteTopicService.isLiteTopicSupported()).thenReturn(true);

        JsonResult<Map<String, Object>> result = asResult(liteTopicController.getLiteTopicCapability());
        assertEquals(0, result.getStatus());
        assertEquals(Boolean.TRUE, result.getData().get("liteTopicSupported"));
    }

    @Test
    public void testGetLiteTopicCapabilityUnsupported() {
        when(liteTopicService.isLiteTopicSupported()).thenReturn(false);

        JsonResult<Map<String, Object>> result = asResult(liteTopicController.getLiteTopicCapability());
        assertEquals(0, result.getStatus());
        assertEquals(Boolean.FALSE, result.getData().get("liteTopicSupported"));
    }
}
