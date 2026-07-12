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
package org.apache.rocketmq.dashboard.llm;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for DashboardApiClient using a local HTTP server.
 * Covers all 13 tool endpoint methods + error handling.
 */
public class DashboardApiClientTest {

    private DashboardApiClient client;
    private HttpServer server;
    private int port;

    @Before
    public void setUp() throws Exception {
        // Start a local HTTP server on a random available port
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.setExecutor(null);
        server.start();

        client = new DashboardApiClient();
        client.setDashboardBaseUrl("http://127.0.0.1:" + port);
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * Register a handler that returns the given JSON with status 200.
     */
    private void registerOkHandler(String path, String json) {
        server.createContext(path, exchange -> {
            byte[] resp = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
    }

    /**
     * Register a handler that returns an error status code.
     */
    private void registerErrorHandler(String path, int statusCode, String body) {
        server.createContext(path, exchange -> {
            byte[] resp = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
    }

    // ---- clusterList tests ----------------------------------------------------

    @Test
    public void testClusterListSuccess() throws Exception {
        String json = "{\"status\":0,\"data\":{\"clusterInfo\":{"
                + "\"clusterAddrTable\":{\"default-cluster\":[\"broker-a\"]},"
                + "\"brokerAddrTable\":{\"broker-a\":{\"cluster\":\"default-cluster\","
                + "\"brokerAddrs\":{\"0\":\"192.168.1.1:10911\"}}}"
                + "}}}";
        registerOkHandler("/cluster/list.query", json);

        Map<String, Object> result = client.clusterList(null);

        assertEquals(0, result.get("status"));
        assertNotNull(result.get("data"));
        assertTrue(result.get("data").toString().contains("default-cluster"));
    }

    @Test
    public void testClusterListNoData() throws Exception {
        String json = "{\"status\":0}";
        registerOkHandler("/cluster/list.query", json);

        Map<String, Object> result = client.clusterList(null);

        assertEquals(0, result.get("status"));
    }

    @Test
    public void testClusterListHttpError() throws Exception {
        registerErrorHandler("/cluster/list.query", 404, "Not Found");

        Map<String, Object> result = client.clusterList(null);

        assertEquals(-1, result.get("status"));
        assertTrue(result.get("errMsg").toString().contains("HTTP 404"));
    }

    @Test
    public void testClusterListNullClusterAddrTable() throws Exception {
        String json = "{\"status\":0,\"data\":{\"clusterInfo\":{"
                + "\"clusterAddrTable\":null,"
                + "\"brokerAddrTable\":{}"
                + "}}}";
        registerOkHandler("/cluster/list.query", json);

        Map<String, Object> result = client.clusterList(null);

        assertEquals(0, result.get("status"));
    }

    @Test
    public void testClusterListNullBrokerNames() throws Exception {
        String json = "{\"status\":0,\"data\":{\"clusterInfo\":{"
                + "\"clusterAddrTable\":{\"default-cluster\":null},"
                + "\"brokerAddrTable\":{}"
                + "}}}";
        registerOkHandler("/cluster/list.query", json);

        Map<String, Object> result = client.clusterList(null);

        assertEquals(0, result.get("status"));
    }

    // ---- clusterDescribe tests ------------------------------------------------

    @Test
    public void testClusterDescribeSuccess() throws Exception {
        String json = "{\"status\":0,\"data\":{\"clusterInfo\":{"
                + "\"clusterAddrTable\":{\"default-cluster\":[\"broker-a\"]},"
                + "\"brokerAddrTable\":{\"broker-a\":{\"cluster\":\"default-cluster\","
                + "\"brokerAddrs\":{\"0\":\"192.168.1.1:10911\"}}}"
                + "},\"brokerServer\":{\"broker-a\":{}}}}";
        registerOkHandler("/cluster/list.query", json);

        Map<String, Object> result = client.clusterDescribe(null);

        assertEquals(0, result.get("status"));
        assertNotNull(result.get("data"));
    }

    @Test
    public void testClusterDescribeNoClusterInfo() throws Exception {
        String json = "{\"status\":0,\"data\":{}}";
        registerOkHandler("/cluster/list.query", json);

        Map<String, Object> result = client.clusterDescribe(null);

        assertEquals(0, result.get("status"));
    }

    // ---- topicList tests ------------------------------------------------------

    @Test
    public void testTopicListSuccess() throws Exception {
        String json = "{\"status\":0,\"data\":[\"topic1\",\"topic2\"]}";
        registerOkHandler("/topic/list.query", json);

        Map<String, Object> result = client.topicList(null);

        assertEquals(0, result.get("status"));
    }

    // ---- topicDescribe tests --------------------------------------------------

    @Test
    public void testTopicDescribeSuccess() throws Exception {
        String json = "{\"status\":0,\"data\":{\"topic\":\"test-topic\"}}";
        registerOkHandler("/topic/route.query", json);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("topic", "test-topic");
        Map<String, Object> result = client.topicDescribe(args);

        assertEquals(0, result.get("status"));
    }

    @Test
    public void testTopicDescribeMissingTopic() {
        Map<String, Object> args = new LinkedHashMap<>();
        Map<String, Object> result = client.topicDescribe(args);

        assertEquals(-1, result.get("status"));
        assertTrue(result.get("errMsg").toString().contains("topic"));
    }

    // ---- groupList tests ------------------------------------------------------

    @Test
    public void testGroupListSuccess() throws Exception {
        String json = "{\"status\":0,\"data\":[\"group1\",\"group2\"]}";
        registerOkHandler("/consumer/groupList.query", json);

        Map<String, Object> result = client.groupList(null);

        assertEquals(0, result.get("status"));
    }

    // ---- groupDescribe tests --------------------------------------------------

    @Test
    public void testGroupDescribeMissingGroup() {
        Map<String, Object> args = new LinkedHashMap<>();
        Map<String, Object> result = client.groupDescribe(args);

        assertEquals(-1, result.get("status"));
        assertTrue(result.get("errMsg").toString().contains("group"));
    }

    @Test
    public void testGroupDescribeSuccess() throws Exception {
        String json = "{\"status\":0,\"data\":{\"group\":\"test-group\"}}";
        registerOkHandler("/consumer/group.query", json);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("group", "test-group");
        Map<String, Object> result = client.groupDescribe(args);

        assertEquals(0, result.get("status"));
    }

    // ---- brokerList tests -----------------------------------------------------

    @Test
    public void testBrokerListSuccess() throws Exception {
        String json = "{\"status\":0,\"data\":{\"clusterInfo\":{"
                + "\"clusterAddrTable\":{\"default-cluster\":[\"broker-a\"]},"
                + "\"brokerAddrTable\":{\"broker-a\":{\"cluster\":\"default-cluster\","
                + "\"brokerAddrs\":{\"0\":\"192.168.1.1:10911\"}}}"
                + "}}}";
        registerOkHandler("/cluster/list.query", json);

        Map<String, Object> result = client.brokerList(null);

        assertEquals(0, result.get("status"));
        assertNotNull(result.get("data"));
    }

    @Test
    public void testBrokerListNoData() throws Exception {
        String json = "{\"status\":0}";
        registerOkHandler("/cluster/list.query", json);

        Map<String, Object> result = client.brokerList(null);

        assertEquals(0, result.get("status"));
        assertNotNull(result.get("data"));
    }

    @Test
    public void testBrokerListNullClusterInfo() throws Exception {
        String json = "{\"status\":0,\"data\":{\"clusterInfo\":null}}";
        registerOkHandler("/cluster/list.query", json);

        Map<String, Object> result = client.brokerList(null);

        assertEquals(0, result.get("status"));
    }

    // ---- brokerDescribe tests -------------------------------------------------

    @Test
    public void testBrokerDescribeSuccess() throws Exception {
        String json = "{\"status\":0,\"data\":{\"clusterInfo\":{"
                + "\"brokerAddrTable\":{\"broker-a\":{\"cluster\":\"default-cluster\","
                + "\"brokerAddrs\":{\"0\":\"192.168.1.1:10911\"}}}"
                + "}}}";
        registerOkHandler("/cluster/list.query", json);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("brokerName", "broker-a");
        Map<String, Object> result = client.brokerDescribe(args);

        assertEquals(0, result.get("status"));
        assertNotNull(result.get("data"));
    }

    @Test
    public void testBrokerDescribeNotFound() throws Exception {
        String json = "{\"status\":0,\"data\":{\"clusterInfo\":{"
                + "\"brokerAddrTable\":{}"
                + "}}}";
        registerOkHandler("/cluster/list.query", json);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("brokerName", "nonexistent");
        Map<String, Object> result = client.brokerDescribe(args);

        assertEquals(-1, result.get("status"));
        assertTrue(result.get("errMsg").toString().contains("not found"));
    }

    @Test
    public void testBrokerDescribeNullData() throws Exception {
        String json = "{\"status\":0}";
        registerOkHandler("/cluster/list.query", json);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("brokerName", "broker-a");
        Map<String, Object> result = client.brokerDescribe(args);

        assertEquals(-1, result.get("status"));
    }

    // ---- messageQueryById tests -----------------------------------------------

    @Test
    public void testMessageQueryByIdMissingParams() {
        Map<String, Object> args = new LinkedHashMap<>();
        Map<String, Object> result = client.messageQueryById(args);

        assertEquals(-1, result.get("status"));
        assertTrue(result.get("errMsg").toString().contains("msgId"));
    }

    @Test
    public void testMessageQueryByIdSuccess() throws Exception {
        String json = "{\"status\":0,\"data\":{\"msgId\":\"123\",\"topic\":\"test\"}}";
        registerOkHandler("/message/viewMessage.query", json);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("msgId", "123");
        args.put("topic", "test");
        Map<String, Object> result = client.messageQueryById(args);

        assertEquals(0, result.get("status"));
    }

    // ---- messageQueryByTime tests ---------------------------------------------

    @Test
    public void testMessageQueryByTimeMissingParams() {
        Map<String, Object> args = new LinkedHashMap<>();
        Map<String, Object> result = client.messageQueryByTime(args);

        assertEquals(-1, result.get("status"));
        assertTrue(result.get("errMsg").toString().contains("topic"));
    }

    @Test
    public void testMessageQueryByTimeSuccess() throws Exception {
        String json = "{\"status\":0,\"data\":[]}";
        registerOkHandler("/message/queryMessageByTopic.query", json);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("topic", "test");
        args.put("beginTime", "0");
        args.put("endTime", "9999999999999");
        Map<String, Object> result = client.messageQueryByTime(args);

        assertEquals(0, result.get("status"));
    }

    // ---- namespaceList tests --------------------------------------------------

    @Test
    public void testNamespaceListSuccess() throws Exception {
        String json = "{\"status\":0,\"data\":[]}";
        registerOkHandler("/namespace/list.query", json);

        Map<String, Object> result = client.namespaceList(null);

        assertEquals(0, result.get("status"));
    }

    // ---- clientList tests -----------------------------------------------------

    @Test
    public void testClientListSuccess() throws Exception {
        String json = "{\"status\":0,\"data\":[]}";
        registerOkHandler("/client/list.query", json);

        Map<String, Object> result = client.clientList(null);

        assertEquals(0, result.get("status"));
    }

    // ---- aclList tests --------------------------------------------------------

    @Test
    public void testAclListSuccess() throws Exception {
        String json = "{\"status\":0,\"data\":[]}";
        registerOkHandler("/acl/list.query", json);

        Map<String, Object> result = client.aclList(null);

        assertEquals(0, result.get("status"));
    }

    // ---- setDashboardBaseUrl tests --------------------------------------------

    @Test
    public void testSetDashboardBaseUrl() {
        client.setDashboardBaseUrl("http://myhost:9090/");
    }

    @Test
    public void testSetDashboardBaseUrlNull() {
        client.setDashboardBaseUrl(null);
    }

    // ---- HTTP 500 error test --------------------------------------------------

    @Test
    public void testHttpServerError() throws Exception {
        registerErrorHandler("/topic/list.query", 500, "Internal Server Error");

        Map<String, Object> result = client.topicList(null);

        assertEquals(-1, result.get("status"));
        assertTrue(result.get("errMsg").toString().contains("HTTP 500"));
    }

    // ---- Connection refused test (no server handler) --------------------------

    @Test
    public void testConnectionRefused() throws Exception {
        // Use a port that has no server
        client.setDashboardBaseUrl("http://127.0.0.1:1");

        Map<String, Object> result = client.topicList(null);

        assertEquals(-1, result.get("status"));
        assertTrue(result.get("errMsg").toString().contains("Failed to reach"));
    }
}