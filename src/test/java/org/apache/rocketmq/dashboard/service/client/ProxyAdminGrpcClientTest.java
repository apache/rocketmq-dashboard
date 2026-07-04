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

package org.apache.rocketmq.dashboard.service.client;

import io.grpc.ManagedChannel;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProxyAdminGrpcClientTest {

    private static final String TEST_HOST = "192.168.1.100";
    private static final int DEFAULT_ADMIN_PORT = 8082;
    private static final int CUSTOM_DATA_PORT = 9090;

    private ProxyAdminGrpcClient client;

    @Before
    public void setUp() {
        client = new ProxyAdminGrpcClient(TEST_HOST + ":" + CUSTOM_DATA_PORT, DEFAULT_ADMIN_PORT);
    }

    @After
    public void tearDown() {
        if (client != null) {
            client.shutdown();
        }
    }

    // ==================== extractHost behavior (tested via constructor) ====================

    @Test
    public void testDefaultPortIs8082() throws Exception {
        ProxyAdminGrpcClient defaultClient = new ProxyAdminGrpcClient("somehost:10911");
        int port = getFieldValue(defaultClient, "proxyAdminPort");
        assertEquals(DEFAULT_ADMIN_PORT, port);
    }

    @Test
    public void testAdminPortFromTwoArgConstructor() throws Exception {
        int port = getFieldValue(client, "proxyAdminPort");
        assertEquals(DEFAULT_ADMIN_PORT, port);
    }

    @Test
    public void testCustomPortExplicit() throws Exception {
        ProxyAdminGrpcClient customClient = new ProxyAdminGrpcClient("host:8080", 9999);
        int port = getFieldValue(customClient, "proxyAdminPort");
        assertEquals(9999, port);
    }

    @Test
    public void testExtractHostWithPort() throws Exception {
        ProxyAdminGrpcClient hostClient = new ProxyAdminGrpcClient("myhost:10911");
        String host = getFieldValue(hostClient, "proxyHost");
        assertEquals("myhost", host);
    }

    @Test
    public void testExtractHostWithoutPort() throws Exception {
        ProxyAdminGrpcClient hostClient = new ProxyAdminGrpcClient("myhost");
        String host = getFieldValue(hostClient, "proxyHost");
        assertEquals("myhost", host);
    }

    @Test
    public void testExtractHostNullReturnsLocalhost() throws Exception {
        ProxyAdminGrpcClient hostClient = new ProxyAdminGrpcClient(null);
        String host = getFieldValue(hostClient, "proxyHost");
        assertEquals("localhost", host);
    }

    @Test
    public void testExtractHostEmptyReturnsLocalhost() throws Exception {
        ProxyAdminGrpcClient hostClient = new ProxyAdminGrpcClient("");
        String host = getFieldValue(hostClient, "proxyHost");
        assertEquals("localhost", host);
    }

    @Test
    public void testExtractHostWithIpAndPort() throws Exception {
        ProxyAdminGrpcClient hostClient = new ProxyAdminGrpcClient("10.0.0.1:9876");
        String host = getFieldValue(hostClient, "proxyHost");
        assertEquals("10.0.0.1", host);
    }

    @Test
    public void testExtractHostWithIpv6AndPort() throws Exception {
        ProxyAdminGrpcClient hostClient = new ProxyAdminGrpcClient("[::1]:8080");
        // lastIndexOf(':') on "[::1]:8080" finds the colon after the bracket -> "[::1]"
        String host = getFieldValue(hostClient, "proxyHost");
        assertEquals("[::1]", host);
    }

    // ==================== isAvailable() before initialization ====================

    @Test
    public void testIsAvailableReturnsFalseBeforeInitialization() {
        assertFalse(client.isAvailable());
    }

    // ==================== listClients returns empty when not available ====================

    @Test
    public void testListClientsReturnsEmptyWhenNotAvailable() {
        List<?> result = client.listClients("group", "topic", "prefix", 1, 10);
        assertNotNull("Result should not be null", result);
        assertTrue("Result should be empty when not available", result.isEmpty());
    }

    @Test
    public void testListClientsReturnsEmptyWithNullFiltersWhenNotAvailable() {
        List<?> result = client.listClients(null, null, null, 1, 10);
        assertNotNull("Result should not be null", result);
        assertTrue("Result should be empty when not available", result.isEmpty());
    }

    // ==================== describeClient returns null when not available ====================

    @Test
    public void testDescribeClientReturnsNullWhenNotAvailable() {
        Object result = client.describeClient("testClientId");
        assertNull("Should return null when not available", result);
    }

    @Test
    public void testDescribeClientReturnsNullForNullClientId() {
        Object result = client.describeClient(null);
        assertNull("Should return null for null clientId", result);
    }

    @Test
    public void testDescribeClientReturnsNullForEmptyClientId() {
        Object result = client.describeClient("");
        assertNull("Should return null for empty clientId", result);
    }

    // ==================== listClientsByGroup null/empty handling ====================

    @Test
    public void testListClientsByGroupReturnsEmptyForNullGroup() {
        List<?> result = client.listClientsByGroup(null, 1, 10);
        assertNotNull("Result should not be null", result);
        assertTrue("Result should be empty for null group", result.isEmpty());
    }

    @Test
    public void testListClientsByGroupReturnsEmptyForEmptyGroup() {
        List<?> result = client.listClientsByGroup("", 1, 10);
        assertNotNull("Result should not be null", result);
        assertTrue("Result should be empty for empty group", result.isEmpty());
    }

    // ==================== listClientsByTopic null/empty handling ====================

    @Test
    public void testListClientsByTopicReturnsEmptyForNullTopic() {
        List<?> result = client.listClientsByTopic(null, 1, 10);
        assertNotNull("Result should not be null", result);
        assertTrue("Result should be empty for null topic", result.isEmpty());
    }

    @Test
    public void testListClientsByTopicReturnsEmptyForEmptyTopic() {
        List<?> result = client.listClientsByTopic("", 1, 10);
        assertNotNull("Result should not be null", result);
        assertTrue("Result should be empty for empty topic", result.isEmpty());
    }

    // ==================== shutdown behavior ====================

    @Test
    public void testShutdownSetsAvailableFalse() throws Exception {
        ManagedChannel mockChannel = mock(ManagedChannel.class);
        when(mockChannel.isShutdown()).thenReturn(false);

        setFieldValue(client, "available", true);
        setFieldValue(client, "channel", mockChannel);

        assertTrue("Should be available before shutdown with mock channel", client.isAvailable());

        client.shutdown();

        assertFalse("Should not be available after shutdown", client.isAvailable());
    }

    @Test
    public void testShutdownAlreadyShutDownChannel() throws Exception {
        ManagedChannel mockChannel = mock(ManagedChannel.class);
        when(mockChannel.isShutdown()).thenReturn(true);

        setFieldValue(client, "available", true);
        setFieldValue(client, "channel", mockChannel);

        assertFalse("Should not be available when channel already shut down", client.isAvailable());

        client.shutdown();

        assertFalse("Should remain unavailable after shutdown of already-closed channel",
            client.isAvailable());
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
