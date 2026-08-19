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
package org.apache.rocketmq.studio.instance.acl;

import org.apache.rocketmq.remoting.protocol.body.AclInfo;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApacheAclReadServiceTest {
    @Test
    void retainsSuccessfulBrokerDataWhenAnotherBrokerFails() throws Exception {
        RuntimeAdminClientResolver resolver = mock(RuntimeAdminClientResolver.class);
        MQAdminExt admin = mock(MQAdminExt.class);
        ClusterInfo clusterInfo = new ClusterInfo();
        BrokerData healthy = new BrokerData();
        healthy.setBrokerAddrs(new HashMap<>(Map.of(0L, "broker-a:10911")));
        BrokerData failing = new BrokerData();
        failing.setBrokerAddrs(new HashMap<>(Map.of(0L, "broker-b:10911")));
        clusterInfo.setBrokerAddrTable(Map.of("a", healthy, "b", failing));
        when(admin.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        when(admin.listAcl("broker-a:10911", null, null)).thenReturn(List.of());
        when(admin.listAcl("broker-b:10911", null, null)).thenThrow(new IllegalStateException("unavailable"));
        when(resolver.execute(eq("instance-1"), any())).thenAnswer(invocation -> {
            MqAdminExtFactory.AdminAction<?> action = invocation.getArgument(1);
            return action.apply(admin);
        });

        RemoteAclReadResult result = new ApacheAclReadService(resolver).listRules("instance-1", null, null);

        assertThat(result.getPoliciesByBroker()).containsKey("broker-a:10911");
        assertThat(result.getFailuresByBroker()).containsEntry("broker-b:10911", "unavailable");
        assertThat(result.isPartial()).isTrue();
    }

    @Test
    void treatsNullBrokerPolicyListAsEmptySuccess() throws Exception {
        RuntimeAdminClientResolver resolver = mock(RuntimeAdminClientResolver.class);
        MQAdminExt admin = mock(MQAdminExt.class);
        when(admin.examineBrokerClusterInfo()).thenReturn(clusterInfo("broker-a:10911"));
        when(admin.listAcl("broker-a:10911", null, null)).thenReturn(null);
        executeWith(resolver, admin);

        RemoteAclReadResult result = new ApacheAclReadService(resolver).listRules("instance-1", null, null);

        assertThat(result.getPoliciesByBroker()).containsEntry("broker-a:10911", List.of());
        assertThat(result.getFailuresByBroker()).isEmpty();
        assertThat(result.isPartial()).isFalse();
    }

    @Test
    void retainsValidPoliciesWhenResponseContainsNullRows() throws Exception {
        RuntimeAdminClientResolver resolver = mock(RuntimeAdminClientResolver.class);
        MQAdminExt admin = mock(MQAdminExt.class);
        AclInfo.PolicyEntryInfo entry = AclInfo.PolicyEntryInfo.of(
                "Topic:orders", Arrays.asList("PUB", null), List.of("10.0.0.0/8"), "ALLOW");
        AclInfo.PolicyInfo group = new AclInfo.PolicyInfo();
        group.setPolicyType("CUSTOM");
        group.setEntries(Arrays.asList(entry, null));
        AclInfo policy = new AclInfo();
        policy.setSubject("User:alice");
        policy.setPolicies(Arrays.asList(group, null));
        when(admin.examineBrokerClusterInfo()).thenReturn(clusterInfo("broker-a:10911"));
        when(admin.listAcl("broker-a:10911", null, null)).thenReturn(Arrays.asList(policy, null));
        executeWith(resolver, admin);

        RemoteAclReadResult result = new ApacheAclReadService(resolver).listRules("instance-1", null, null);

        assertThat(result.getFailuresByBroker()).isEmpty();
        assertThat(result.getPoliciesByBroker().get("broker-a:10911"))
                .singleElement()
                .satisfies(remotePolicy -> {
                    assertThat(remotePolicy.subject()).isEqualTo("User:alice");
                    assertThat(remotePolicy.policies()).singleElement().satisfies(remoteGroup -> {
                        assertThat(remoteGroup.policyType()).isEqualTo("CUSTOM");
                        assertThat(remoteGroup.entries()).singleElement().satisfies(remoteEntry -> {
                            assertThat(remoteEntry.resource()).isEqualTo("Topic:orders");
                            assertThat(remoteEntry.actions()).containsExactly("PUB");
                        });
                    });
                });
    }

    private ClusterInfo clusterInfo(String address) {
        BrokerData broker = new BrokerData();
        broker.setBrokerAddrs(new HashMap<>(Map.of(0L, address)));
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setBrokerAddrTable(Map.of("broker", broker));
        return clusterInfo;
    }

    private void executeWith(RuntimeAdminClientResolver resolver, MQAdminExt admin) throws Exception {
        when(resolver.execute(eq("instance-1"), any())).thenAnswer(invocation -> {
            MqAdminExtFactory.AdminAction<?> action = invocation.getArgument(1);
            return action.apply(admin);
        });
    }
}
