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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.rocketmq.dashboard.model.SubscriptionInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class GrpcClientCollectorTest {

    @Mock
    private ProxyAdminGrpcClient mockGrpcClient;

    private GrpcClientCollector collector;

    private static final String TEST_CLIENT_ID = "testClientId";
    private static final String TEST_GROUP = "testGroup";
    private static final String TEST_TOPIC = "testTopic";

    @Before
    public void setUp() {
        collector = new GrpcClientCollector(mockGrpcClient);
    }

    // ==================== hasDataAvailable() delegation ====================

    @Test
    public void testHasDataAvailableDelegatesToGrpcClient() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);

        assertTrue("hasDataAvailable should return true when grpcClient is available",
            collector.hasDataAvailable());
    }

    @Test
    public void testHasDataAvailableReturnsFalseWhenGrpcClientNotAvailable() {
        when(mockGrpcClient.isAvailable()).thenReturn(false);

        assertFalse("hasDataAvailable should return false when grpcClient is not available",
            collector.hasDataAvailable());
    }

    @Test
    public void testHasDataAvailableReturnsFalseForNullGrpcClient() {
        GrpcClientCollector nullCollector = new GrpcClientCollector(null);

        assertFalse("hasDataAvailable should return false when grpcClient is null",
            nullCollector.hasDataAvailable());
    }

    // ==================== listClientInstances when not available ====================

    @Test
    public void testListClientInstancesReturnsEmptyWhenNotAvailable() {
        when(mockGrpcClient.isAvailable()).thenReturn(false);

        List<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            collector.listClientInstances(Optional.of(TEST_TOPIC), Optional.of(TEST_GROUP));

        assertNotNull("Result should not be null", result);
        assertTrue("Result should be empty when grpc client is not available", result.isEmpty());
    }

    @Test
    public void testListClientInstancesReturnsEmptyWhenGrpcReturnsEmpty() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);
        when(mockGrpcClient.listClients(anyString(), anyString(), isNull(), anyInt(), anyInt()))
            .thenReturn(Collections.emptyList());

        List<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            collector.listClientInstances(Optional.of(TEST_TOPIC), Optional.of(TEST_GROUP));

        assertNotNull("Result should not be null", result);
        assertTrue("Result should be empty when grpc returns empty list", result.isEmpty());
    }

    @Test
    public void testListClientInstancesWithEmptyOptionals() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);
        when(mockGrpcClient.listClients(isNull(), isNull(), isNull(), anyInt(), anyInt()))
            .thenReturn(Collections.emptyList());

        List<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            collector.listClientInstances(Optional.empty(), Optional.empty());

        assertNotNull("Result should not be null", result);
        assertTrue("Result should be empty when grpc returns empty", result.isEmpty());
    }

    @Test
    public void testListClientInstancesHandlesException() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);
        when(mockGrpcClient.listClients(anyString(), anyString(), isNull(), anyInt(), anyInt()))
            .thenThrow(new RuntimeException("gRPC connection failed"));

        List<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            collector.listClientInstances(Optional.of(TEST_TOPIC), Optional.empty());

        assertNotNull("Result should not be null after exception", result);
        assertTrue("Result should be empty when grpc throws exception", result.isEmpty());
    }

    // ==================== getClientInstance when not available ====================

    @Test
    public void testGetClientInstanceReturnsEmptyOptionalWhenNotAvailable() {
        when(mockGrpcClient.isAvailable()).thenReturn(false);

        Optional<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            collector.getClientInstance(TEST_CLIENT_ID);

        assertNotNull("Result should not be null", result);
        assertFalse("Result should be empty when not available", result.isPresent());
    }

    @Test
    public void testGetClientInstanceWithNullClientId() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);

        Optional<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            collector.getClientInstance(null);

        assertNotNull("Result should not be null", result);
        assertFalse("Result should be empty for null clientId", result.isPresent());
    }

    @Test
    public void testGetClientInstanceWithEmptyClientId() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);

        Optional<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            collector.getClientInstance("");

        assertNotNull("Result should not be null", result);
        assertFalse("Result should be empty for empty clientId", result.isPresent());
    }

    @Test
    public void testGetClientInstanceHandlesException() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);
        when(mockGrpcClient.describeClient(TEST_CLIENT_ID))
            .thenThrow(new RuntimeException("gRPC connection failed"));

        Optional<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            collector.getClientInstance(TEST_CLIENT_ID);

        assertNotNull("Result should not be null after exception", result);
        assertFalse("Result should be empty when grpc throws exception", result.isPresent());
    }

    // ==================== getClientSubscriptions ====================

    @Test
    public void testGetClientSubscriptionsReturnsEmptyWhenNotAvailable() {
        when(mockGrpcClient.isAvailable()).thenReturn(false);

        List<SubscriptionInfo> result = collector.getClientSubscriptions(TEST_CLIENT_ID);

        assertNotNull("Result should not be null", result);
        assertTrue("Result should be empty when not available", result.isEmpty());
    }

    @Test
    public void testGetClientSubscriptionsWithNullClientId() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);

        List<SubscriptionInfo> result = collector.getClientSubscriptions(null);

        assertNotNull("Result should not be null", result);
        assertTrue("Result should be empty for null clientId", result.isEmpty());
    }

    @Test
    public void testGetClientSubscriptionsWithEmptyClientId() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);

        List<SubscriptionInfo> result = collector.getClientSubscriptions("");

        assertNotNull("Result should not be null", result);
        assertTrue("Result should be empty for empty clientId", result.isEmpty());
    }

    @Test
    public void testGetClientSubscriptionsHandlesException() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);
        when(mockGrpcClient.describeClient(TEST_CLIENT_ID))
            .thenThrow(new RuntimeException("gRPC connection failed"));

        List<SubscriptionInfo> result = collector.getClientSubscriptions(TEST_CLIENT_ID);

        assertNotNull("Result should not be null after exception", result);
        assertTrue("Result should be empty when grpc throws exception", result.isEmpty());
    }

    // ==================== Null grpcClient method behavior ====================

    @Test(expected = NullPointerException.class)
    public void testListClientInstancesThrowsNpeForNullGrpcClient() {
        GrpcClientCollector nullCollector = new GrpcClientCollector(null);
        nullCollector.listClientInstances(Optional.of(TEST_TOPIC), Optional.of(TEST_GROUP));
    }

    @Test(expected = NullPointerException.class)
    public void testGetClientInstanceThrowsNpeForNullGrpcClient() {
        GrpcClientCollector nullCollector = new GrpcClientCollector(null);
        nullCollector.getClientInstance(TEST_CLIENT_ID);
    }

    @Test(expected = NullPointerException.class)
    public void testGetClientSubscriptionsThrowsNpeForNullGrpcClient() {
        GrpcClientCollector nullCollector = new GrpcClientCollector(null);
        nullCollector.getClientSubscriptions(TEST_CLIENT_ID);
    }
}
