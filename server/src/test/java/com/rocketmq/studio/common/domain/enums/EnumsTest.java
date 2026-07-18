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
package com.rocketmq.studio.common.domain.enums;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnumsTest {

    @Nested
    class AlertLevelTest {
        @Test
        void values_shouldContainAllLevels() {
            AlertLevel[] values = AlertLevel.values();
            assertEquals(3, values.length);
            assertTrue(contains(values, AlertLevel.error));
            assertTrue(contains(values, AlertLevel.warning));
            assertTrue(contains(values, AlertLevel.info));
        }

        @Test
        void valueOf_shouldReturnCorrectEnum() {
            assertEquals(AlertLevel.error, AlertLevel.valueOf("error"));
            assertEquals(AlertLevel.warning, AlertLevel.valueOf("warning"));
            assertEquals(AlertLevel.info, AlertLevel.valueOf("info"));
        }
    }

    @Nested
    class BrokerStatusTest {
        @Test
        void values_shouldContainAllStatuses() {
            assertEquals(3, BrokerStatus.values().length);
        }

        @Test
        void valueOf_shouldReturnCorrectEnum() {
            assertEquals(BrokerStatus.running, BrokerStatus.valueOf("running"));
            assertEquals(BrokerStatus.readonly, BrokerStatus.valueOf("readonly"));
            assertEquals(BrokerStatus.maintenance, BrokerStatus.valueOf("maintenance"));
        }
    }

    @Nested
    class CertStatusTest {
        @Test
        void values_shouldContainAllStatuses() {
            assertEquals(3, CertStatus.values().length);
        }

        @Test
        void valueOf_shouldReturnCorrectEnum() {
            assertEquals(CertStatus.valid, CertStatus.valueOf("valid"));
            assertEquals(CertStatus.expiring, CertStatus.valueOf("expiring"));
            assertEquals(CertStatus.expired, CertStatus.valueOf("expired"));
        }
    }

    @Nested
    class CertTypeTest {
        @Test
        void values_shouldContainAllTypes() {
            assertEquals(3, CertType.values().length);
        }

        @Test
        void valueOf_shouldReturnCorrectEnum() {
            assertEquals(CertType.TLS, CertType.valueOf("TLS"));
            assertEquals(CertType.mTLS, CertType.valueOf("mTLS"));
            assertEquals(CertType.ServiceAccount, CertType.valueOf("ServiceAccount"));
        }
    }

    @Nested
    class ClientLanguageTest {
        @Test
        void values_shouldContainAllLanguages() {
            assertEquals(8, ClientLanguage.values().length);
        }

        @Test
        void valueOf_shouldReturnCorrectEnum() {
            assertEquals(ClientLanguage.Java, ClientLanguage.valueOf("Java"));
            assertEquals(ClientLanguage.Go, ClientLanguage.valueOf("Go"));
            assertEquals(ClientLanguage.Python, ClientLanguage.valueOf("Python"));
        }
    }

    @Nested
    class ClientTypeTest {
        @Test
        void values_shouldContainAllTypes() {
            assertEquals(2, ClientType.values().length);
        }

        @Test
        void valueOf_shouldReturnCorrectEnum() {
            assertEquals(ClientType.Producer, ClientType.valueOf("Producer"));
            assertEquals(ClientType.Consumer, ClientType.valueOf("Consumer"));
        }
    }

    @Nested
    class ClusterStatusTest {
        @Test
        void values_shouldContainAllStatuses() {
            assertEquals(4, ClusterStatus.values().length);
        }

        @Test
        void valueOf_shouldReturnCorrectEnum() {
            assertEquals(ClusterStatus.healthy, ClusterStatus.valueOf("healthy"));
            assertEquals(ClusterStatus.warning, ClusterStatus.valueOf("warning"));
            assertEquals(ClusterStatus.error, ClusterStatus.valueOf("error"));
            assertEquals(ClusterStatus.offline, ClusterStatus.valueOf("offline"));
        }
    }

    @Nested
    class ClusterTypeTest {
        @Test
        void values_shouldContainAllTypes() {
            assertEquals(3, ClusterType.values().length);
        }

        @Test
        void valueOf_shouldReturnCorrectEnum() {
            assertEquals(ClusterType.V4_DIRECT, ClusterType.valueOf("V4_DIRECT"));
            assertEquals(ClusterType.V5_PROXY_LOCAL, ClusterType.valueOf("V5_PROXY_LOCAL"));
            assertEquals(ClusterType.V5_PROXY_CLUSTER, ClusterType.valueOf("V5_PROXY_CLUSTER"));
        }
    }

    @Nested
    class ConsumeTypeTest {
        @Test
        void values_shouldContainAllTypes() {
            assertEquals(2, ConsumeType.values().length);
        }

        @Test
        void valueOf_shouldReturnCorrectEnum() {
            assertEquals(ConsumeType.CLUSTERING, ConsumeType.valueOf("CLUSTERING"));
            assertEquals(ConsumeType.BROADCASTING, ConsumeType.valueOf("BROADCASTING"));
        }
    }

    @Nested
    class DeliveryStatusTest {
        @Test
        void values_shouldContainAllStatuses() {
            assertEquals(3, DeliveryStatus.values().length);
        }

        @Test
        void valueOf_shouldReturnCorrectEnum() {
            assertEquals(DeliveryStatus.success, DeliveryStatus.valueOf("success"));
            assertEquals(DeliveryStatus.failed, DeliveryStatus.valueOf("failed"));
            assertEquals(DeliveryStatus.pending, DeliveryStatus.valueOf("pending"));
        }
    }

    @Nested
    class FlushDiskTypeTest {
        @Test
        void values_shouldContainAllTypes() {
            assertEquals(2, FlushDiskType.values().length);
        }

        @Test
        void valueOf_shouldReturnCorrectEnum() {
            assertEquals(FlushDiskType.ASYNC_FLUSH, FlushDiskType.valueOf("ASYNC_FLUSH"));
            assertEquals(FlushDiskType.SYNC_FLUSH, FlushDiskType.valueOf("SYNC_FLUSH"));
        }
    }

    @Nested
    class InstanceTypeTest {
        @Test
        void values_shouldContainAllTypes() {
            assertEquals(2, InstanceType.values().length);
        }

        @Test
        void valueOf_shouldReturnCorrectEnum() {
            assertEquals(InstanceType.PROXY, InstanceType.valueOf("PROXY"));
            assertEquals(InstanceType.DIRECT, InstanceType.valueOf("DIRECT"));
        }
    }

    @Nested
    class ProtocolTest {
        @Test
        void values_shouldContainAllProtocols() {
            assertEquals(2, Protocol.values().length);
        }

        @Test
        void valueOf_shouldReturnCorrectEnum() {
            assertEquals(Protocol.gRPC, Protocol.valueOf("gRPC"));
            assertEquals(Protocol.Remoting, Protocol.valueOf("Remoting"));
        }
    }

    @Nested
    class SubscriptionModeTest {
        @Test
        void values_shouldContainAllModes() {
            assertEquals(2, SubscriptionMode.values().length);
        }

        @Test
        void valueOf_shouldReturnCorrectEnum() {
            assertEquals(SubscriptionMode.Push, SubscriptionMode.valueOf("Push"));
            assertEquals(SubscriptionMode.Pop, SubscriptionMode.valueOf("Pop"));
        }
    }

    @Nested
    class TopicPermTest {
        @Test
        void values_shouldContainAllPerms() {
            assertEquals(3, TopicPerm.values().length);
        }

        @Test
        void valueOf_shouldReturnCorrectEnum() {
            assertEquals(TopicPerm.RW, TopicPerm.valueOf("RW"));
            assertEquals(TopicPerm.RO, TopicPerm.valueOf("RO"));
            assertEquals(TopicPerm.WO, TopicPerm.valueOf("WO"));
        }
    }

    @Nested
    class TopicTypeTest {
        @Test
        void values_shouldContainAllTypes() {
            assertEquals(5, TopicType.values().length);
        }

        @Test
        void valueOf_shouldReturnCorrectEnum() {
            assertEquals(TopicType.NORMAL, TopicType.valueOf("NORMAL"));
            assertEquals(TopicType.FIFO, TopicType.valueOf("FIFO"));
            assertEquals(TopicType.DELAY, TopicType.valueOf("DELAY"));
            assertEquals(TopicType.TRANSACTION, TopicType.valueOf("TRANSACTION"));
            assertEquals(TopicType.LITE, TopicType.valueOf("LITE"));
        }
    }

    private static <T> boolean contains(T[] array, T value) {
        for (T item : array) {
            if (item == value) return true;
        }
        return false;
    }
}