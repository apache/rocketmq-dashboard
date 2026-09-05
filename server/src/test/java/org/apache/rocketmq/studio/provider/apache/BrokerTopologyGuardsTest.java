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

import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.message.MessageId;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BrokerTopologyGuards}, the shared guards that reject offset-style
 * message ids whose embedded broker address is outside the selected instance topology
 * before remoting is allowed to connect to it directly.
 */
@ExtendWith(MockitoExtension.class)
class BrokerTopologyGuardsTest {

    @Mock
    private MQAdminExt admin;

    private static String offsetMsgId(String ip, int port) {
        StringBuilder hex = new StringBuilder();
        for (String octet : ip.split("\\.")) {
            hex.append(String.format("%02x", Integer.parseInt(octet)));
        }
        hex.append(String.format("%08x", port));
        hex.append("0000000000000000");
        return hex.toString();
    }

    private static MessageId decodedMessageId(String ip, int port) throws Exception {
        return MessageDecoder.decodeMessageId(offsetMsgId(ip, port));
    }

    private static ClusterInfo topologyOf(String... endpoints) {
        ClusterInfo clusterInfo = new ClusterInfo();
        Map<String, BrokerData> table = new HashMap<>();
        BrokerData broker = new BrokerData();
        HashMap<Long, String> addresses = new HashMap<>();
        long index = 0L;
        for (String endpoint : endpoints) {
            addresses.put(index++, endpoint);
        }
        broker.setBrokerAddrs(addresses);
        table.put("broker-1", broker);
        clusterInfo.setBrokerAddrTable(table);
        return clusterInfo;
    }

    @Test
    void treatsNonOffsetMessageIdsAsSafeWithoutQueryingTheTopology() throws Exception {
        assertThat(BrokerTopologyGuards.isWithinKnownBrokerTopology(admin, "not-an-offset-id")).isTrue();
        verifyNoInteractions(admin);
    }

    @Test
    void acceptsMsgIdsWhoseEmbeddedBrokerBelongsToTheTopology() throws Exception {
        when(admin.examineBrokerClusterInfo()).thenReturn(topologyOf("127.0.0.1:9876", "10.0.0.9:9876"));

        assertThat(BrokerTopologyGuards.isWithinKnownBrokerTopology(
                admin, offsetMsgId("127.0.0.1", 9876))).isTrue();
        assertThat(BrokerTopologyGuards.isWithinKnownBrokerTopology(
                admin, offsetMsgId("10.0.0.9", 9876))).isTrue();
    }

    @Test
    void rejectsMsgIdsWhoseEmbeddedBrokerIsOutsideTheTopology() throws Exception {
        when(admin.examineBrokerClusterInfo()).thenReturn(topologyOf("127.0.0.1:9876"));

        assertThat(BrokerTopologyGuards.isWithinKnownBrokerTopology(
                admin, offsetMsgId("192.168.1.50", 9876))).isFalse();
        assertThat(BrokerTopologyGuards.validatedBrokerAddr(
                admin, "msg", decodedMessageId("192.168.1.50", 9876))).isNull();
    }

    @Test
    void rejectsWhenTheTopologyCannotBeVerified() throws Exception {
        when(admin.examineBrokerClusterInfo()).thenThrow(new RuntimeException("admin down"));

        assertThat(BrokerTopologyGuards.isWithinKnownBrokerTopology(
                admin, offsetMsgId("127.0.0.1", 9876))).isFalse();
    }

    @Test
    void validatesOnlyKnownBrokerEndpoints() throws Exception {
        when(admin.examineBrokerClusterInfo()).thenReturn(topologyOf("127.0.0.1:9876"));

        assertThat(BrokerTopologyGuards.validatedBrokerAddr(
                admin, "msg", decodedMessageId("127.0.0.1", 9876)))
                .isEqualTo("127.0.0.1:9876");
        assertThat(BrokerTopologyGuards.validatedBrokerAddr(
                admin, "msg", decodedMessageId("203.0.113.9", 9876))).isNull();
    }

    @Test
    void ignoresUnresolvableOrNonInetAddresses() throws Exception {
        SocketAddress custom = new SocketAddress() {
        };
        assertThat(BrokerTopologyGuards.decodedBrokerAddr(new MessageId(custom, 0L))).isNull();
        assertThat(BrokerTopologyGuards.decodedBrokerAddr(new MessageId(
                InetSocketAddress.createUnresolved("broker-host", 9876), 0L))).isNull();
        // An address-less message id never reaches the topology query.
        assertThat(BrokerTopologyGuards.validatedBrokerAddr(
                admin, "msg", new MessageId(custom, 0L))).isNull();
        verifyNoInteractions(admin);
    }

    @Test
    void collectsKnownEndpointsAcrossAllBrokersWithTrimming() throws Exception {
        ClusterInfo clusterInfo = new ClusterInfo();
        Map<String, BrokerData> table = new HashMap<>();
        BrokerData first = new BrokerData();
        HashMap<Long, String> firstAddresses = new HashMap<>();
        firstAddresses.put(0L, " 127.0.0.1:9876 ");
        firstAddresses.put(1L, "");
        first.setBrokerAddrs(firstAddresses);
        table.put("broker-1", first);
        BrokerData second = new BrokerData();
        second.setBrokerAddrs(new HashMap<>());
        table.put("broker-2", second);
        clusterInfo.setBrokerAddrTable(table);

        when(admin.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        assertThat(BrokerTopologyGuards.knownBrokerEndpoints(admin))
                .containsExactly("127.0.0.1:9876");

        when(admin.examineBrokerClusterInfo()).thenReturn(null);
        assertThat(BrokerTopologyGuards.knownBrokerEndpoints(admin)).isEmpty();
    }
}
