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
package org.apache.rocketmq.studio.provider.apache;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.message.MessageId;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BrokerTopologyGuardsHostnameTest {

    @Test
    void acceptsRegisteredHostnameAndKeepsDecodedIpForRemoting() throws Exception {
        MQAdminExt admin = adminWithEndpoints(" localhost:10911 ");
        String msgId = MessageDecoder.createMessageId(new InetSocketAddress("127.0.0.1", 10911), 123L);

        assertThat(BrokerTopologyGuards.isWithinKnownBrokerTopology(admin, msgId)).isTrue();
        assertThat(BrokerTopologyGuards.validatedBrokerAddr(admin, msgId, MessageDecoder.decodeMessageId(msgId)))
                .isEqualTo("127.0.0.1:10911");
    }

    @Test
    void matchesAnyAddressResolvedFromRegisteredHostname() throws Exception {
        MQAdminExt admin = adminWithEndpoints("broker.test:10911");
        MessageId messageId = messageId("192.0.2.2", 10911);
        InetAddress first = InetAddress.getByName("192.0.2.1");
        InetAddress second = InetAddress.getByName("192.0.2.2");

        try (MockedStatic<InetAddress> addresses = mockStatic(InetAddress.class)) {
            addresses.when(() -> InetAddress.getAllByName("broker.test"))
                    .thenReturn(new InetAddress[]{first, second});

            assertThat(BrokerTopologyGuards.validatedBrokerAddr(admin, "offset-id", messageId))
                    .isEqualTo("192.0.2.2:10911");
            addresses.verify(() -> InetAddress.getAllByName("broker.test"));
            addresses.verifyNoMoreInteractions();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"192.0.2.2", "2001:db8::2"})
    void rejectsAddressNotResolvedFromRegisteredHostname(String ip) throws Exception {
        MQAdminExt admin = adminWithEndpoints("broker.test:10911");
        MessageId messageId = messageId(ip, 10911);
        InetAddress registeredAddress = InetAddress.getByName("192.0.2.1");

        try (MockedStatic<InetAddress> addresses = mockStatic(InetAddress.class)) {
            addresses.when(() -> InetAddress.getAllByName("broker.test"))
                    .thenReturn(new InetAddress[]{registeredAddress});

            assertThat(BrokerTopologyGuards.validatedBrokerAddr(admin, "offset-id", messageId)).isNull();
            addresses.verify(() -> InetAddress.getAllByName("broker.test"));
            addresses.verifyNoMoreInteractions();
        }
    }

    @Test
    void rejectsUnresolvableRegisteredHostname() throws Exception {
        MQAdminExt admin = adminWithEndpoints("missing.test:10911");
        MessageId messageId = messageId("127.0.0.1", 10911);

        try (MockedStatic<InetAddress> addresses = mockStatic(InetAddress.class)) {
            addresses.when(() -> InetAddress.getAllByName("missing.test"))
                    .thenThrow(new UnknownHostException("missing.test"));

            assertThat(BrokerTopologyGuards.validatedBrokerAddr(admin, "offset-id", messageId)).isNull();
            addresses.verify(() -> InetAddress.getAllByName("missing.test"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"broker-a.test", "broker-b.test"})
    void unresolvableEndpointDoesNotHideMatchingRegisteredHostname(String matchingHost) throws Exception {
        MQAdminExt admin = adminWithEndpoints("broker-a.test:10911", "broker-b.test:10911");
        String missingHost = matchingHost.equals("broker-a.test") ? "broker-b.test" : "broker-a.test";
        MessageId messageId = messageId("192.0.2.1", 10911);
        InetAddress registeredAddress = InetAddress.getByName("192.0.2.1");

        try (MockedStatic<InetAddress> addresses = mockStatic(InetAddress.class)) {
            addresses.when(() -> InetAddress.getAllByName(missingHost))
                    .thenThrow(new UnknownHostException(missingHost));
            addresses.when(() -> InetAddress.getAllByName(matchingHost))
                    .thenReturn(new InetAddress[]{registeredAddress});

            assertThat(BrokerTopologyGuards.validatedBrokerAddr(admin, "offset-id", messageId))
                    .isEqualTo("192.0.2.1:10911");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"[2001:db8::1]:10911", "2001:db8::1:10911", "[2001:DB8:0:0:0:0:0:1]:10911"})
    void acceptsEquivalentIpv6Representations(String endpoint) throws Exception {
        MQAdminExt admin = adminWithEndpoints(endpoint);
        String msgId = MessageDecoder.createMessageId(new InetSocketAddress("2001:db8::1", 10911), 123L);

        assertThat(BrokerTopologyGuards.isWithinKnownBrokerTopology(admin, msgId)).isTrue();
        assertThat(BrokerTopologyGuards.validatedBrokerAddr(admin, msgId, MessageDecoder.decodeMessageId(msgId)))
                .isEqualTo("2001:db8:0:0:0:0:0:1:10911");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "broker.test:10912", "broker.test", ":10911", "broker.test:",
        "broker.test:invalid", "broker.test:65536", "broker.test:-1"
    })
    void rejectsMismatchedOrMalformedPortWithoutResolvingHosts(String endpoint) throws Exception {
        MQAdminExt admin = adminWithEndpoints(endpoint);
        MessageId messageId = messageId("127.0.0.1", 10911);

        try (MockedStatic<InetAddress> addresses = mockStatic(InetAddress.class)) {
            assertThat(BrokerTopologyGuards.validatedBrokerAddr(admin, "offset-id", messageId)).isNull();
            addresses.verifyNoInteractions();
        }
    }

    @Test
    void exactIpMatchDoesNotRequireDnsForOtherRegisteredBrokers() throws Exception {
        MQAdminExt admin = adminWithEndpoints("missing.test:10911", "192.0.2.1:10911");
        MessageId messageId = messageId("192.0.2.1", 10911);

        try (MockedStatic<InetAddress> addresses = mockStatic(InetAddress.class)) {
            assertThat(BrokerTopologyGuards.validatedBrokerAddr(admin, "offset-id", messageId))
                    .isEqualTo("192.0.2.1:10911");
            addresses.verifyNoInteractions();
        }
    }

    private static MessageId messageId(String ip, int port) {
        return new MessageId(new InetSocketAddress(ip, port), 123L);
    }

    private static MQAdminExt adminWithEndpoints(String... endpoints) throws Exception {
        MQAdminExt admin = mock(MQAdminExt.class);
        ClusterInfo clusterInfo = new ClusterInfo();
        HashMap<String, BrokerData> brokers = new HashMap<>();
        for (int i = 0; i < endpoints.length; i++) {
            BrokerData broker = new BrokerData();
            HashMap<Long, String> addresses = new HashMap<>();
            addresses.put(0L, endpoints[i]);
            broker.setBrokerAddrs(addresses);
            brokers.put("broker-" + i, broker);
        }
        clusterInfo.setBrokerAddrTable(brokers);
        when(admin.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        return admin;
    }
}
