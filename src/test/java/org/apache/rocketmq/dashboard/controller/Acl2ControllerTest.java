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

import org.apache.rocketmq.dashboard.model.Acl2PolicyContext;
import org.apache.rocketmq.dashboard.service.Acl2Service;
import org.apache.rocketmq.dashboard.support.JsonResult;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class Acl2ControllerTest {

    @InjectMocks
    private Acl2Controller acl2Controller;

    @Mock
    private Acl2Service acl2Service;

    @SuppressWarnings("unchecked")
    private <T> JsonResult<T> asResult(Object obj) {
        return (JsonResult<T>) obj;
    }

    // ==================== getAclStatus ====================

    @Test
    public void testGetAclStatusV2() {
        when(acl2Service.detectAclVersion()).thenReturn("V2");

        JsonResult<Map<String, Object>> result = asResult(acl2Controller.getAclStatus());
        assertEquals(0, result.getStatus());
        assertEquals("V2", result.getData().get("aclVersion"));
        assertEquals(Boolean.TRUE, result.getData().get("supported"));
    }

    @Test
    public void testGetAclStatusV1() {
        when(acl2Service.detectAclVersion()).thenReturn("V1");

        JsonResult<Map<String, Object>> result = asResult(acl2Controller.getAclStatus());
        assertEquals(0, result.getStatus());
        assertEquals(Boolean.FALSE, result.getData().get("supported"));
        assertTrue(((String) result.getData().get("message")).contains("ACL 1.0"));
    }

    @Test
    public void testGetAclStatusUnknownVersion() {
        when(acl2Service.detectAclVersion()).thenReturn("UNKNOWN");

        JsonResult<Map<String, Object>> result = asResult(acl2Controller.getAclStatus());
        assertEquals(0, result.getStatus());
        assertEquals("Unable to determine ACL version", result.getData().get("message"));
    }

    @Test
    public void testGetAclStatusError() {
        when(acl2Service.detectAclVersion()).thenThrow(new RuntimeException("boom"));

        JsonResult<Object> result = asResult(acl2Controller.getAclStatus());
        assertEquals(1, result.getStatus());
        assertTrue(result.getErrMsg().contains("boom"));
    }

    // ==================== listPolicies ====================

    @Test
    public void testListPolicies() {
        when(acl2Service.listPolicies(null)).thenReturn(Collections.emptyList());

        JsonResult<Object> result = asResult(acl2Controller.listPolicies(null));
        assertEquals(0, result.getStatus());
        assertNotNull(result.getData());
    }

    @Test
    public void testListPoliciesIllegalArgument() {
        when(acl2Service.listPolicies("ns1")).thenThrow(new IllegalArgumentException("bad ns"));

        JsonResult<Object> result = asResult(acl2Controller.listPolicies("ns1"));
        assertEquals(1, result.getStatus());
        assertEquals("bad ns", result.getErrMsg());
    }

    @Test
    public void testListPoliciesError() {
        when(acl2Service.listPolicies(anyString())).thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(acl2Controller.listPolicies("ns1"));
        assertEquals(1, result.getStatus());
    }

    // ==================== createPolicy ====================

    @Test
    public void testCreatePolicyNullBody() {
        JsonResult<Object> result = asResult(acl2Controller.createPolicy(null));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testCreatePolicy() {
        Acl2PolicyContext context = new Acl2PolicyContext();
        when(acl2Service.createPolicy(context)).thenReturn(new HashMap<>());

        JsonResult<Object> result = asResult(acl2Controller.createPolicy(context));
        assertEquals(0, result.getStatus());
    }

    @Test
    public void testCreatePolicyIllegalArgument() {
        when(acl2Service.createPolicy(any())).thenThrow(new IllegalArgumentException("accessKey exists"));

        JsonResult<Object> result = asResult(acl2Controller.createPolicy(new Acl2PolicyContext()));
        assertEquals(1, result.getStatus());
        assertEquals("accessKey exists", result.getErrMsg());
    }

    @Test
    public void testCreatePolicyError() {
        when(acl2Service.createPolicy(any())).thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(acl2Controller.createPolicy(new Acl2PolicyContext()));
        assertEquals(1, result.getStatus());
    }

    // ==================== updatePolicy ====================

    @Test
    public void testUpdatePolicyNullBody() {
        JsonResult<Object> result = asResult(acl2Controller.updatePolicy("ak1", null));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testUpdatePolicy() {
        Acl2PolicyContext context = new Acl2PolicyContext();
        when(acl2Service.updatePolicy("ak1", context)).thenReturn(new HashMap<>());

        JsonResult<Object> result = asResult(acl2Controller.updatePolicy("ak1", context));
        assertEquals(0, result.getStatus());
    }

    @Test
    public void testUpdatePolicyIllegalArgument() {
        when(acl2Service.updatePolicy(eq("ak1"), any()))
            .thenThrow(new IllegalArgumentException("not found"));

        JsonResult<Object> result = asResult(acl2Controller.updatePolicy("ak1", new Acl2PolicyContext()));
        assertEquals(1, result.getStatus());
        assertEquals("not found", result.getErrMsg());
    }

    @Test
    public void testUpdatePolicyError() {
        when(acl2Service.updatePolicy(eq("ak1"), any())).thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(acl2Controller.updatePolicy("ak1", new Acl2PolicyContext()));
        assertEquals(1, result.getStatus());
    }

    // ==================== deletePolicy ====================

    @Test
    public void testDeletePolicyBlankKey() {
        JsonResult<Object> result = asResult(acl2Controller.deletePolicy(" "));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testDeletePolicy() {
        when(acl2Service.deletePolicy("ak1")).thenReturn(Boolean.TRUE);

        JsonResult<Object> result = asResult(acl2Controller.deletePolicy("ak1"));
        assertEquals(0, result.getStatus());
    }

    @Test
    public void testDeletePolicyIllegalArgument() {
        when(acl2Service.deletePolicy("ak1")).thenThrow(new IllegalArgumentException("no policy"));

        JsonResult<Object> result = asResult(acl2Controller.deletePolicy("ak1"));
        assertEquals(1, result.getStatus());
        assertEquals("no policy", result.getErrMsg());
    }

    @Test
    public void testDeletePolicyError() {
        when(acl2Service.deletePolicy("ak1")).thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(acl2Controller.deletePolicy("ak1"));
        assertEquals(1, result.getStatus());
    }

    // ==================== detectAclVersion ====================

    @Test
    public void testDetectAclVersion() {
        Map<String, Object> report = new HashMap<>();
        report.put("version", "V2");
        when(acl2Service.detectAndReport()).thenReturn(report);

        JsonResult<Map<String, Object>> result = asResult(acl2Controller.detectAclVersion());
        assertEquals(0, result.getStatus());
        assertEquals("V2", result.getData().get("version"));
    }

    @Test
    public void testDetectAclVersionError() {
        when(acl2Service.detectAndReport()).thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(acl2Controller.detectAclVersion());
        assertEquals(1, result.getStatus());
    }

    // ==================== listNamespaces ====================

    @Test
    public void testListNamespaces() {
        when(acl2Service.listNamespaces()).thenReturn(Collections.singletonList("ns1"));

        JsonResult<Object> result = asResult(acl2Controller.listNamespaces());
        assertEquals(0, result.getStatus());
    }

    @Test
    public void testListNamespacesError() {
        when(acl2Service.listNamespaces()).thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(acl2Controller.listNamespaces());
        assertEquals(1, result.getStatus());
    }

    // ==================== rotationStatus ====================

    @Test
    public void testRotationStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("rotationCount", 5);
        when(acl2Service.getRotationStatus()).thenReturn(status);

        JsonResult<Map<String, Object>> result = asResult(acl2Controller.rotationStatus());
        assertEquals(0, result.getStatus());
        assertEquals(5, result.getData().get("rotationCount"));
    }

    @Test
    public void testRotationStatusError() {
        when(acl2Service.getRotationStatus()).thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(acl2Controller.rotationStatus());
        assertEquals(1, result.getStatus());
    }
}
