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

package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.common.protocol.body.ClusterInfo;
import org.apache.rocketmq.common.protocol.route.BrokerData;
import org.apache.rocketmq.dashboard.model.BrokerColdReadReport;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;

import static org.mockito.Mockito.when;

public class BrokerColdReadInspectorServiceImplTest {

    @Mock
    private MQAdminExt mqAdminExt;

    @InjectMocks
    private BrokerColdReadInspectorServiceImpl brokerColdReadInspectorService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testInspectColdDataReadWithCluster() throws Exception {
        ClusterInfo clusterInfo = new ClusterInfo();
        HashMap<String, BrokerData> brokerAddrTable = new HashMap<>();
        BrokerData bd = new BrokerData();
        bd.setBrokerName("broker-a");
        HashMap<Long, String> addrs = new HashMap<>();
        addrs.put(0L, "127.0.0.1:10911");
        bd.setBrokerAddrs(addrs);
        brokerAddrTable.put("broker-a", bd);
        clusterInfo.setBrokerAddrTable(brokerAddrTable);

        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);

        BrokerColdReadReport report = brokerColdReadInspectorService.inspectColdDataRead("DefaultCluster");
        Assert.assertNotNull(report);
        Assert.assertEquals("DefaultCluster", report.getClusterName());
        Assert.assertNotNull(report.getBrokerStats());
        Assert.assertEquals(1, report.getBrokerStats().size());
        Assert.assertNotNull(report.getTopColdConsumerGroups());
        Assert.assertNotNull(report.getDiskPressureStatus());
    }

    @Test
    public void testInspectColdDataReadFallback() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenThrow(new RuntimeException("Cluster offline"));

        BrokerColdReadReport report = brokerColdReadInspectorService.inspectColdDataRead("DefaultCluster");
        Assert.assertNotNull(report);
        Assert.assertNotNull(report.getBrokerStats());
        Assert.assertEquals(2, report.getBrokerStats().size());
        Assert.assertTrue(report.getOverallPageCacheHitRatePercent() > 0);
    }
}
