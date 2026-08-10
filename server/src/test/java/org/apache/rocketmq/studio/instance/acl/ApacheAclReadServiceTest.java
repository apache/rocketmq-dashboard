/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.acl;

import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.HashMap;
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
}
