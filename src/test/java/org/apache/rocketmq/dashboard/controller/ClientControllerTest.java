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

import org.apache.rocketmq.dashboard.model.ClientInstance;
import org.apache.rocketmq.dashboard.model.SubscriptionInfo;
import org.apache.rocketmq.dashboard.service.ClientService;
import org.apache.rocketmq.dashboard.service.UnifiedClientService;
import org.apache.rocketmq.dashboard.support.JsonResult;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ClientControllerTest {

    @InjectMocks
    private ClientController clientController;

    @Mock
    private UnifiedClientService unifiedClientService;

    @Mock
    private ClientService clientService;

    @SuppressWarnings("unchecked")
    private <T> JsonResult<T> asResult(Object obj) {
        return (JsonResult<T>) obj;
    }

    // ==================== listClients ====================

    @Test
    public void testListClientsWithoutFilters() {
        List<ClientInstance> clients = Arrays.asList(new ClientInstance());
        when(unifiedClientService.listAllClients(Optional.empty(), Optional.empty())).thenReturn(clients);

        JsonResult<List<ClientInstance>> result = asResult(clientController.listClients(null, null));
        assertEquals(0, result.getStatus());
        assertEquals(1, result.getData().size());
    }

    @Test
    public void testListClientsWithFilters() {
        when(unifiedClientService.listAllClients(Optional.of("topicA"), Optional.of("groupA")))
            .thenReturn(Collections.emptyList());

        JsonResult<List<ClientInstance>> result = asResult(clientController.listClients(" topicA ", " groupA "));
        assertEquals(0, result.getStatus());
        assertTrue(result.getData().isEmpty());
    }

    @Test
    public void testListClientsError() {
        when(unifiedClientService.listAllClients(any(), any()))
            .thenThrow(new RuntimeException("boom"));

        JsonResult<Object> result = asResult(clientController.listClients("", ""));
        assertEquals(1, result.getStatus());
        assertTrue(result.getErrMsg().contains("boom"));
    }

    // ==================== getClient ====================

    @Test
    public void testGetClientBlankId() {
        JsonResult<Object> result = asResult(clientController.getClient(" "));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testGetClientFound() {
        ClientInstance instance = new ClientInstance();
        when(unifiedClientService.describeClient("c1")).thenReturn(Optional.of(instance));

        JsonResult<ClientInstance> result = asResult(clientController.getClient("c1"));
        assertEquals(0, result.getStatus());
        assertNotNull(result.getData());
    }

    @Test
    public void testGetClientNotFound() {
        when(unifiedClientService.describeClient("c404")).thenReturn(Optional.empty());

        JsonResult<Object> result = asResult(clientController.getClient("c404"));
        assertEquals(1, result.getStatus());
        assertTrue(result.getErrMsg().contains("not found"));
    }

    @Test
    public void testGetClientError() {
        when(unifiedClientService.describeClient("cerr")).thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(clientController.getClient("cerr"));
        assertEquals(1, result.getStatus());
    }

    // ==================== getClientSubscriptions ====================

    @Test
    public void testGetClientSubscriptionsBlankId() {
        JsonResult<Object> result = asResult(clientController.getClientSubscriptions(""));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testGetClientSubscriptions() {
        when(unifiedClientService.getClientSubscriptions("c1"))
            .thenReturn(Arrays.asList(new SubscriptionInfo()));

        JsonResult<List<SubscriptionInfo>> result = asResult(clientController.getClientSubscriptions("c1"));
        assertEquals(0, result.getStatus());
        assertEquals(1, result.getData().size());
    }

    @Test
    public void testGetClientSubscriptionsError() {
        when(unifiedClientService.getClientSubscriptions("c1")).thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(clientController.getClientSubscriptions("c1"));
        assertEquals(1, result.getStatus());
    }

    // ==================== listClientsByProtocol / byType / byCluster ====================

    @Test
    public void testListClientsByProtocolBlank() {
        JsonResult<Object> result = asResult(clientController.listClientsByProtocol(" "));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testListClientsByProtocol() {
        when(clientService.listClientsByProtocol("REMOTING")).thenReturn(Collections.emptyList());

        JsonResult<Object> result = asResult(clientController.listClientsByProtocol("REMOTING"));
        assertEquals(0, result.getStatus());
    }

    @Test
    public void testListClientsByProtocolError() {
        when(clientService.listClientsByProtocol("GRPC")).thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(clientController.listClientsByProtocol("GRPC"));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testListClientsByTypeBlank() {
        JsonResult<Object> result = asResult(clientController.listClientsByType(""));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testListClientsByType() {
        when(clientService.listClientsByType("PRODUCER")).thenReturn(Collections.emptyList());

        JsonResult<Object> result = asResult(clientController.listClientsByType("PRODUCER"));
        assertEquals(0, result.getStatus());
    }

    @Test
    public void testListClientsByTypeError() {
        when(clientService.listClientsByType("CONSUMER")).thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(clientController.listClientsByType("CONSUMER"));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testListClientsByClusterBlank() {
        JsonResult<Object> result = asResult(clientController.listClientsByCluster(null));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testListClientsByCluster() {
        when(clientService.listClientsByCluster("DefaultCluster")).thenReturn(Collections.emptyList());

        JsonResult<Object> result = asResult(clientController.listClientsByCluster("DefaultCluster"));
        assertEquals(0, result.getStatus());
    }

    @Test
    public void testListClientsByClusterError() {
        when(clientService.listClientsByCluster("c")).thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(clientController.listClientsByCluster("c"));
        assertEquals(1, result.getStatus());
    }

    // ==================== connected / idle / issues ====================

    @Test
    public void testGetConnectedClientsBlank() {
        JsonResult<Object> result = asResult(clientController.getConnectedClients(" "));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testGetConnectedClients() {
        when(clientService.getConnectedClients("127.0.0.1:10911")).thenReturn(Collections.emptyList());

        JsonResult<Object> result = asResult(clientController.getConnectedClients("127.0.0.1:10911"));
        assertEquals(0, result.getStatus());
    }

    @Test
    public void testGetConnectedClientsError() {
        when(clientService.getConnectedClients(anyString())).thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(clientController.getConnectedClients("addr"));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testGetIdleClients() {
        when(clientService.getIdleClients(300000L)).thenReturn(Collections.emptyList());

        JsonResult<Object> result = asResult(clientController.getIdleClients(300000L));
        assertEquals(0, result.getStatus());
    }

    @Test
    public void testGetIdleClientsError() {
        when(clientService.getIdleClients(anyLong())).thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(clientController.getIdleClients(1000L));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testGetClientsWithIssueBlank() {
        JsonResult<Object> result = asResult(clientController.getClientsWithIssue(""));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testGetClientsWithIssue() {
        when(clientService.getClientsWithIssue("LAG")).thenReturn(Collections.emptyList());

        JsonResult<Object> result = asResult(clientController.getClientsWithIssue("LAG"));
        assertEquals(0, result.getStatus());
    }

    @Test
    public void testGetClientsWithIssueError() {
        when(clientService.getClientsWithIssue("X")).thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(clientController.getClientsWithIssue("X"));
        assertEquals(1, result.getStatus());
    }

    // ==================== killClient ====================

    @Test
    public void testKillClientBlankId() {
        JsonResult<Object> result = asResult(clientController.killClient("", null));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testKillClientSuccessWithReason() {
        when(clientService.killClient("c1", "manual")).thenReturn(true);

        Map<String, Object> request = new HashMap<>();
        request.put("reason", "manual");
        JsonResult<Map<String, Object>> result = asResult(clientController.killClient("c1", request));
        assertEquals(0, result.getStatus());
        assertEquals(Boolean.TRUE, result.getData().get("success"));
    }

    @Test
    public void testKillClientFailedNoReason() {
        when(clientService.killClient("c1", null)).thenReturn(false);

        JsonResult<Map<String, Object>> result = asResult(clientController.killClient("c1", null));
        assertEquals(0, result.getStatus());
        assertEquals(Boolean.FALSE, result.getData().get("success"));
    }

    @Test
    public void testKillClientError() {
        when(clientService.killClient(eq("c1"), any())).thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(clientController.killClient("c1", null));
        assertEquals(1, result.getStatus());
    }

    // ==================== updateClientConfig ====================

    @Test
    public void testUpdateClientConfigBlankId() {
        JsonResult<Object> result = asResult(clientController.updateClientConfig(" ", new HashMap<>()));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testUpdateClientConfigNullBody() {
        JsonResult<Object> result = asResult(clientController.updateClientConfig("c1", null));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testUpdateClientConfigMissingKey() {
        JsonResult<Object> result = asResult(clientController.updateClientConfig("c1", new HashMap<>()));
        assertEquals(1, result.getStatus());
        assertTrue(result.getErrMsg().contains("configKey"));
    }

    @Test
    public void testUpdateClientConfigSuccess() {
        when(clientService.updateClientConfig("c1", "threadNums", "8")).thenReturn(true);

        Map<String, Object> request = new HashMap<>();
        request.put("configKey", "threadNums");
        request.put("configValue", 8);
        JsonResult<Map<String, Object>> result = asResult(clientController.updateClientConfig("c1", request));
        assertEquals(0, result.getStatus());
        assertEquals(Boolean.TRUE, result.getData().get("success"));
    }

    @Test
    public void testUpdateClientConfigFailedNullValue() {
        when(clientService.updateClientConfig("c1", "k", null)).thenReturn(false);

        Map<String, Object> request = new HashMap<>();
        request.put("configKey", "k");
        JsonResult<Map<String, Object>> result = asResult(clientController.updateClientConfig("c1", request));
        assertEquals(0, result.getStatus());
        assertEquals(Boolean.FALSE, result.getData().get("success"));
    }

    @Test
    public void testUpdateClientConfigError() {
        when(clientService.updateClientConfig(anyString(), anyString(), any()))
            .thenThrow(new RuntimeException("err"));

        Map<String, Object> request = new HashMap<>();
        request.put("configKey", "k");
        JsonResult<Object> result = asResult(clientController.updateClientConfig("c1", request));
        assertEquals(1, result.getStatus());
    }

    // ==================== getAvailableChannels ====================

    @Test
    public void testGetAvailableChannels() {
        when(unifiedClientService.getAvailableChannels()).thenReturn(Arrays.asList("REMOTING", "GRPC"));

        JsonResult<Map<String, Object>> result = asResult(clientController.getAvailableChannels());
        assertEquals(0, result.getStatus());
        assertEquals(Boolean.TRUE, result.getData().get("remotingAvailable"));
        assertEquals(Boolean.TRUE, result.getData().get("grpcAvailable"));
    }

    @Test
    public void testGetAvailableChannelsEmpty() {
        when(unifiedClientService.getAvailableChannels()).thenReturn(Collections.emptyList());

        JsonResult<Map<String, Object>> result = asResult(clientController.getAvailableChannels());
        assertEquals(0, result.getStatus());
        assertEquals(Boolean.FALSE, result.getData().get("remotingAvailable"));
        assertEquals("No client collection channels available", result.getData().get("message"));
    }

    @Test
    public void testGetAvailableChannelsError() {
        when(unifiedClientService.getAvailableChannels()).thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(clientController.getAvailableChannels());
        assertEquals(1, result.getStatus());
    }
}
