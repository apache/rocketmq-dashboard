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
package org.apache.rocketmq.dashboard.task;

import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.dashboard.service.impl.DashboardCollectServiceImpl;
import org.apache.rocketmq.dashboard.util.MockObjectUtil;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.HARuntimeInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ReplicaSyncCollectTaskTest {

    private static final String BROKER_NAME = "broker-a";

    @Mock
    private MQAdminExt mqAdminExt;

    private DashboardCollectServiceImpl dashboardCollectService;

    private ReplicaSyncCollectTask task;

    @Before
    public void setUp() {
        dashboardCollectService = new DashboardCollectServiceImpl();
        task = new ReplicaSyncCollectTask(mqAdminExt, dashboardCollectService);
    }

    private HARuntimeInfo buildHARuntimeInfo(boolean master) {
        HARuntimeInfo haRuntimeInfo = new HARuntimeInfo();
        haRuntimeInfo.setMaster(master);
        haRuntimeInfo.setInSyncSlaveNums(1);
        List<HARuntimeInfo.HAConnectionRuntimeInfo> connList = new ArrayList<>();
        HARuntimeInfo.HAConnectionRuntimeInfo connInfo = new HARuntimeInfo.HAConnectionRuntimeInfo();
        connInfo.setDiff(100L);
        connInfo.setTransferredByteInSecond(2048L);
        connList.add(connInfo);
        HARuntimeInfo.HAConnectionRuntimeInfo connInfo2 = new HARuntimeInfo.HAConnectionRuntimeInfo();
        connInfo2.setDiff(50L);
        connInfo2.setTransferredByteInSecond(1024L);
        connList.add(connInfo2);
        haRuntimeInfo.setHaConnectionInfo(connList);
        return haRuntimeInfo;
    }

    @Test
    public void testRunCollectSuccess() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(MockObjectUtil.createClusterInfo());
        when(mqAdminExt.getBrokerHAStatus(anyString())).thenReturn(buildHARuntimeInfo(true));

        task.run();

        List<String> list = dashboardCollectService.getReplicaSyncMap().asMap().get(BROKER_NAME);
        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());
        String[] parts = list.get(0).split(",");
        // timestamp,maxDiff,totalTransferredBytes,inSyncSlaveNums,slaveCount
        Assert.assertEquals(5, parts.length);
        Assert.assertEquals("100", parts[1]);
        Assert.assertEquals("3072", parts[2]);
        Assert.assertEquals("1", parts[3]);
        Assert.assertEquals("2", parts[4]);
    }

    @Test
    public void testRunNotMaster() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(MockObjectUtil.createClusterInfo());
        when(mqAdminExt.getBrokerHAStatus(anyString())).thenReturn(buildHARuntimeInfo(false));

        task.run();

        Assert.assertEquals(0, dashboardCollectService.getReplicaSyncMap().asMap().size());
    }

    @Test
    public void testRunNullHaConnectionInfo() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(MockObjectUtil.createClusterInfo());
        HARuntimeInfo haRuntimeInfo = new HARuntimeInfo();
        haRuntimeInfo.setMaster(true);
        haRuntimeInfo.setInSyncSlaveNums(0);
        haRuntimeInfo.setHaConnectionInfo(null);
        when(mqAdminExt.getBrokerHAStatus(anyString())).thenReturn(haRuntimeInfo);

        task.run();

        List<String> list = dashboardCollectService.getReplicaSyncMap().asMap().get(BROKER_NAME);
        Assert.assertNotNull(list);
        String[] parts = list.get(0).split(",");
        Assert.assertEquals("0", parts[1]);
        Assert.assertEquals("0", parts[4]);
    }

    @Test
    public void testRunGetBrokerHAStatusFailed() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(MockObjectUtil.createClusterInfo());
        when(mqAdminExt.getBrokerHAStatus(anyString()))
                .thenThrow(new RuntimeException("getBrokerHAStatus exception"));

        // per-broker exception is swallowed
        task.run();

        Assert.assertEquals(0, dashboardCollectService.getReplicaSyncMap().asMap().size());
    }

    @Test
    public void testRunNoMasterBroker() throws Exception {
        ClusterInfo clusterInfo = MockObjectUtil.createClusterInfo();
        BrokerData brokerData = clusterInfo.getBrokerAddrTable().get(BROKER_NAME);
        HashMap<Long, String> brokerAddrs = new HashMap<>();
        brokerAddrs.put(MixAll.MASTER_ID + 1, "127.0.0.1:10912");
        brokerData.setBrokerAddrs(brokerAddrs);
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);

        task.run();

        Assert.assertEquals(0, dashboardCollectService.getReplicaSyncMap().asMap().size());
    }

    @Test
    public void testRunExamineBrokerClusterInfoFailed() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo())
                .thenThrow(new RuntimeException("examineBrokerClusterInfo exception"));

        // exception should be swallowed
        task.run();

        Assert.assertEquals(0, dashboardCollectService.getReplicaSyncMap().asMap().size());
    }
}
