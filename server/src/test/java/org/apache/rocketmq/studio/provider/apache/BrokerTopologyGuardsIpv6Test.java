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
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrokerTopologyGuardsIpv6Test {

    @ParameterizedTest
    @ValueSource(strings = {"2001:db8::1:10911", "[2001:db8::1]:10911",
        "[2001:0DB8:0000:0000:0000:0000:0000:0001]:10911", "2001:db8:0:0:0:0:0:1:10911"})
    void equivalentIpv6BrokerEndpointsPermitOffsetLookupTest(String advertised) throws Exception {
        String messageId = messageId("2001:db8::1");
        MQAdminExt admin = adminWithEndpoint(advertised);

        assertThat(BrokerTopologyGuards.isWithinKnownBrokerTopology(admin, messageId)).isTrue();
        assertThat(BrokerTopologyGuards.validatedBrokerAddr(
                admin, messageId, MessageDecoder.decodeMessageId(messageId)))
                .isEqualTo("2001:db8:0:0:0:0:0:1:10911");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[2001:db8::2]:10911", "[2001:db8::1]:10912",
        "[2001:db8::1/64]:10911", "[not:an:ipv6]:10911", "broker.example.invalid:10911"})
    void differentOrInvalidIpv6EndpointsRemainRejectedTest(String advertised) throws Exception {
        assertThat(BrokerTopologyGuards.isWithinKnownBrokerTopology(
                adminWithEndpoint(advertised), messageId("2001:db8::1"))).isFalse();
    }

    @Test
    void ipv4EndpointMatchingIsPreservedTest() throws Exception {
        assertThat(BrokerTopologyGuards.isWithinKnownBrokerTopology(
                adminWithEndpoint("192.0.2.1:10911"), messageId("192.0.2.1"))).isTrue();
        assertThat(BrokerTopologyGuards.isWithinKnownBrokerTopology(
                adminWithEndpoint("192.0.2.2:10911"), messageId("192.0.2.1"))).isFalse();
    }

    private static String messageId(String address) {
        return MessageDecoder.createMessageId(new InetSocketAddress(address, 10911), 12345L);
    }

    private static MQAdminExt adminWithEndpoint(String endpoint) throws Exception {
        BrokerData broker = new BrokerData();
        broker.setBrokerAddrs(new HashMap<>(Map.of(0L, endpoint)));
        ClusterInfo cluster = new ClusterInfo();
        cluster.setBrokerAddrTable(new HashMap<>(Map.of("broker-a", broker)));
        MQAdminExt admin = mock(MQAdminExt.class);
        when(admin.examineBrokerClusterInfo()).thenReturn(cluster);
        return admin;
    }
}
