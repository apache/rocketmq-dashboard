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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrokerTopologyGuardsTest {

    private FakeBrokerHostResolver hostResolver;

    @BeforeEach
    void setUp() {
        hostResolver = new FakeBrokerHostResolver();
    }

    @Test
    void acceptsAnOffsetIdWhoseBrokerIsRegisteredWithAHostnameTest() throws Exception {
        hostResolver.registered("broker-a.example.com", "172.30.10.100");
        String msgId = offsetMsgId(InetAddress.getByName("172.30.10.100"), 10911);
        MQAdminExt admin = adminWithBrokerAddresses("broker-a.example.com:10911");

        assertThat(BrokerTopologyGuards.isWithinKnownBrokerTopology(admin, msgId, hostResolver)).isTrue();
        assertThat(BrokerTopologyGuards.validatedBrokerAddr(
                admin, msgId, MessageDecoder.decodeMessageId(msgId), hostResolver))
                .isEqualTo("172.30.10.100:10911");
        assertThat(hostResolver.lookups()).contains("broker-a.example.com");
    }

    @Test
    void acceptsAnIdWhoseAddressMatchesOneOfSeveralRegisteredEndpointsTest() throws Exception {
        hostResolver.registered("broker-b.example.com", "172.30.10.101");
        String msgId = offsetMsgId(InetAddress.getByName("172.30.10.101"), 10911);
        MQAdminExt admin = adminWithBrokerAddresses("172.30.10.100:10911", "broker-b.example.com:10911");

        assertThat(BrokerTopologyGuards.isWithinKnownBrokerTopology(admin, msgId, hostResolver)).isTrue();
        assertThat(hostResolver.lookupCount("broker-b.example.com")).isEqualTo(1);
    }

    @Test
    void reusesOneResolutionAcrossRepeatedEvaluationsOfTheSameGuardTest() throws Exception {
        hostResolver.registered("broker-a.example.com", "172.30.10.100");
        BrokerHostResolver perQuery = BrokerHostResolver.caching(hostResolver);
        String msgId = offsetMsgId(InetAddress.getByName("172.30.10.100"), 10911);
        MQAdminExt admin = adminWithBrokerAddresses("broker-a.example.com:10911");

        // queryByMsgId evaluates the guard before viewMessage and again in the offset fallback.
        assertThat(BrokerTopologyGuards.isWithinKnownBrokerTopology(admin, msgId, perQuery)).isTrue();
        assertThat(BrokerTopologyGuards.validatedBrokerAddr(
                admin, msgId, MessageDecoder.decodeMessageId(msgId), perQuery)).isNotNull();

        assertThat(hostResolver.lookupCount("broker-a.example.com")).isEqualTo(1);
    }

    @Test
    void matchesNumericRegisteredEndpointsWithoutAnyLookupTest() throws Exception {
        String msgId = offsetMsgId(InetAddress.getByName("172.30.10.100"), 10911);
        MQAdminExt admin = adminWithBrokerAddresses("172.30.10.100:10911");

        assertThat(BrokerTopologyGuards.isWithinKnownBrokerTopology(admin, msgId, hostResolver)).isTrue();
        assertThat(hostResolver.lookups()).isEmpty();
    }

    @Test
    void stillRejectsAnOffsetIdWhoseResolvedBrokerIsOutsideTheTopologyTest() throws Exception {
        hostResolver.registered("broker-a.example.com", "172.30.10.100");
        String msgId = offsetMsgId(InetAddress.getByName("10.2.3.4"), 10911);
        MQAdminExt admin = adminWithBrokerAddresses("broker-a.example.com:10911");

        assertThat(BrokerTopologyGuards.isWithinKnownBrokerTopology(admin, msgId, hostResolver)).isFalse();
    }

    @Test
    void requiresThePortToMatchTheRegisteredEndpointTest() throws Exception {
        hostResolver.registered("broker-a.example.com", "172.30.10.100");
        String msgId = offsetMsgId(InetAddress.getByName("172.30.10.100"), 10911);
        MQAdminExt admin = adminWithBrokerAddresses("broker-a.example.com:10912");

        assertThat(BrokerTopologyGuards.isWithinKnownBrokerTopology(admin, msgId, hostResolver)).isFalse();
    }

    @Test
    void rejectsAnOffsetIdWhenTheRegisteredHostnameCannotBeResolvedTest() throws Exception {
        String msgId = offsetMsgId(InetAddress.getByName("172.30.10.100"), 10911);
        MQAdminExt admin = adminWithBrokerAddresses("broker-0.invalid:10911");

        assertThat(BrokerTopologyGuards.isWithinKnownBrokerTopology(admin, msgId, hostResolver)).isFalse();
        assertThat(hostResolver.lookups()).contains("broker-0.invalid");
    }

    @Test
    void staysClosedWhenTheTopologyCannotBeVerifiedTest() throws Exception {
        hostResolver.registered("broker-a.example.com", "172.30.10.100");
        String msgId = offsetMsgId(InetAddress.getByName("172.30.10.100"), 10911);
        MQAdminExt admin = mock(MQAdminExt.class);
        when(admin.examineBrokerClusterInfo()).thenThrow(new IllegalStateException("nameserver unreachable"));

        assertThat(BrokerTopologyGuards.isWithinKnownBrokerTopology(admin, msgId, hostResolver)).isFalse();
    }

    @Test
    void letsAnIdThatIsNotAnOffsetIdThroughWithoutReadingTheTopologyTest() throws Exception {
        MQAdminExt admin = mock(MQAdminExt.class);

        assertThat(BrokerTopologyGuards.isWithinKnownBrokerTopology(admin, "uniq-key-1", hostResolver)).isTrue();
        assertThat(hostResolver.lookups()).isEmpty();
    }

    private static String offsetMsgId(InetAddress address, int port) {
        return MessageDecoder.createMessageId(new InetSocketAddress(address, port), 12345L);
    }

    private static MQAdminExt adminWithBrokerAddresses(String... brokerAddresses) throws Exception {
        ClusterInfo clusterInfo = new ClusterInfo();
        Map<String, BrokerData> brokerAddrTable = new HashMap<>();
        for (int index = 0; index < brokerAddresses.length; index++) {
            BrokerData brokerData = new BrokerData();
            brokerData.setBrokerName("broker-" + index);
            brokerData.setCluster("cluster-a");
            HashMap<Long, String> brokerAddrs = new HashMap<>();
            brokerAddrs.put(0L, brokerAddresses[index]);
            brokerData.setBrokerAddrs(brokerAddrs);
            brokerAddrTable.put(brokerData.getBrokerName(), brokerData);
        }
        clusterInfo.setBrokerAddrTable(brokerAddrTable);
        MQAdminExt admin = mock(MQAdminExt.class);
        when(admin.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        return admin;
    }
}
