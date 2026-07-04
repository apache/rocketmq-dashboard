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

package org.apache.rocketmq.dashboard.architecture.impl;

import java.lang.reflect.Field;
import org.apache.rocketmq.dashboard.architecture.ClusterAccessType;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class GrpcAdminClientTest {

    private static final String PROXY_ADDRESS = "192.168.1.100:10911";
    private static final String DIFFERENT_PROXY = "10.0.0.1:9876";

    private MQAdminExt mqAdminExt;
    private Object grpcClient;

    @Before
    public void setUp() {
        mqAdminExt = mock(MQAdminExt.class);
        grpcClient = mock(Object.class, "grpcClientStub");
    }

    @After
    public void tearDown() {
        // No shared static state to clean up
    }

    // ==================== Constructor: two-arg (without grpcClient) ====================

    @Test
    public void testConstructorWithoutGrpcClient() {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);

        assertEquals(ClusterAccessType.V5_PROXY_CLUSTER, client.getClientType());
        assertFalse("grpcAvailable should be false when constructed without grpcClient",
                client.isGrpcAvailable());
        assertEquals(PROXY_ADDRESS, client.getProxyAddress());
    }

    @Test
    public void testConstructorWithoutGrpcClientReturnsV5ProxyCluster() {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);

        assertSame(ClusterAccessType.V5_PROXY_CLUSTER, client.getClientType());
    }

    // ==================== Constructor: three-arg with grpcClient ====================

    @Test
    public void testConstructorWithGrpcClientEnablesGrpc() {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt, grpcClient);

        assertTrue("grpcAvailable should be true when constructed with non-null grpcClient",
                client.isGrpcAvailable());
        assertEquals(PROXY_ADDRESS, client.getProxyAddress());
        assertEquals(ClusterAccessType.V5_PROXY_CLUSTER, client.getClientType());
    }

    // ==================== Constructor: three-arg with null grpcClient ====================

    @Test
    public void testConstructorWithNullGrpcClientDisablesGrpc() {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt, null);

        assertFalse("grpcAvailable should be false when grpcClient is null",
                client.isGrpcAvailable());
        assertEquals(PROXY_ADDRESS, client.getProxyAddress());
    }

    // ==================== Constructor validation: null proxyAddress ====================

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullProxyAddressThrowsException() {
        new GrpcAdminClient(null, mqAdminExt);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullProxyAddressAndGrpcClientThrowsException() {
        new GrpcAdminClient(null, mqAdminExt, grpcClient);
    }

    // ==================== Constructor validation: empty proxyAddress ====================

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithEmptyProxyAddressThrowsException() {
        new GrpcAdminClient("", mqAdminExt);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithBlankProxyAddressThrowsException() {
        new GrpcAdminClient("   ", mqAdminExt);
    }

    // ==================== Constructor validation: null mqAdminExt ====================

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullMqAdminExtThrowsException() {
        new GrpcAdminClient(PROXY_ADDRESS, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullMqAdminExtAndGrpcClientThrowsException() {
        new GrpcAdminClient(PROXY_ADDRESS, null, grpcClient);
    }

    // ==================== getClientType ====================

    @Test
    public void testGetClientTypeAlwaysReturnsV5ProxyCluster() {
        GrpcAdminClient clientWithoutGrpc = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        GrpcAdminClient clientWithGrpc = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt, grpcClient);

        assertEquals(ClusterAccessType.V5_PROXY_CLUSTER, clientWithoutGrpc.getClientType());
        assertEquals(ClusterAccessType.V5_PROXY_CLUSTER, clientWithGrpc.getClientType());
    }

    // ==================== getProxyAddress ====================

    @Test
    public void testGetProxyAddressReturnsCorrectValue() {
        GrpcAdminClient client = new GrpcAdminClient(DIFFERENT_PROXY, mqAdminExt);

        assertEquals(DIFFERENT_PROXY, client.getProxyAddress());
    }

    @Test
    public void testGetProxyAddressPreservesOriginalValue() {
        String specialAddress = "proxy-host.internal:8080";
        GrpcAdminClient client = new GrpcAdminClient(specialAddress, mqAdminExt);

        assertEquals(specialAddress, client.getProxyAddress());
    }

    // ==================== isGrpcAvailable ====================

    @Test
    public void testIsGrpcAvailableReflectsStateWhenDisabled() {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);

        assertFalse(client.isGrpcAvailable());
    }

    @Test
    public void testIsGrpcAvailableReflectsStateWhenEnabled() {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt, grpcClient);

        assertTrue(client.isGrpcAvailable());
    }

    @Test
    public void testIsGrpcAvailableWithNullGrpcClient() {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt, null);

        assertFalse(client.isGrpcAvailable());
    }

    // ==================== shutdown prevents subsequent operations ====================

    @Test
    public void testShutdownPreventsGetClusterInfo() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.getClusterInfo();
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsGetTopicList() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.getTopicList();
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsGetConsumerGroupList() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.getConsumerGroupList();
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsGetTopicRoute() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.getTopicRoute("test-topic");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsGetBrokerRuntimeStats() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.getBrokerRuntimeStats("127.0.0.1:10911");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsGetTopicStats() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.getTopicStats("test-topic");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsCreateOrUpdateTopic() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.createOrUpdateTopic("test-topic", null);
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsDeleteTopic() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.deleteTopic("test-topic", "DefaultCluster");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsGetTopicListFromBroker() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.getTopicListFromBroker("127.0.0.1:10911");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsGetConsumerConnection() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.getConsumerConnection("test-group");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsGetGroupConsumeInfo() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.getGroupConsumeInfo("test-group");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsResetConsumeOffset() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.resetConsumeOffset("test-group", "test-topic", 0L, false);
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsCreateOrUpdateConsumerGroup() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.createOrUpdateConsumerGroup("test-group", null);
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsDeleteConsumerGroup() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.deleteConsumerGroup("test-group", "127.0.0.1:10911");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsGetProducerConnection() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.getProducerConnection("test-group", "test-topic");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsQueryMessage() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.queryMessage("test-topic", "key", 0L, System.currentTimeMillis(), 10);
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsViewMessage() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.viewMessage("test-topic", "msgId");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsConsumeMessageDirectly() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.consumeMessageDirectly("test-group", "test-topic", "msgId");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsReplayMessage() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.replayMessage("test-group", "test-topic", "msgId");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsGetNameServerConfig() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.getNameServerConfig("127.0.0.1:9876");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsUpdateBrokerConfig() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.updateBrokerConfig("127.0.0.1:10911", null);
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    // ==================== getAccessControlList throws UnsupportedOperationException ====================

    @Test(expected = UnsupportedOperationException.class)
    public void testGetAccessControlListThrowsUnsupportedOperationException() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);

        client.getAccessControlList("127.0.0.1:10911");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetAccessControlListThrowsEvenWithGrpcEnabled() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt, grpcClient);

        client.getAccessControlList("127.0.0.1:10911");
    }

    // ==================== updateAccessControlList throws UnsupportedOperationException ====================

    @Test(expected = UnsupportedOperationException.class)
    public void testUpdateAccessControlListThrowsUnsupportedOperationException() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);

        client.updateAccessControlList("127.0.0.1:10911", null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testUpdateAccessControlListThrowsEvenWithGrpcEnabled() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt, grpcClient);

        client.updateAccessControlList("127.0.0.1:10911", null);
    }

    // ==================== shutdown idempotency ====================

    @Test
    public void testShutdownIsIdempotent() {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);

        client.shutdown();
        // Second shutdown should not throw
        client.shutdown();

        // After shutdown, operations should still be blocked
        try {
            client.getClusterInfo();
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        } catch (Exception e) {
            fail("Expected IllegalStateException but got " + e.getClass().getSimpleName());
        }
    }

    // ==================== Helper methods ====================

    @SuppressWarnings("unchecked")
    private <T> T getFieldValue(Object target, String fieldName) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private void setFieldValue(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
