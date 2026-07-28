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

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.dashboard.BaseTest;
import org.apache.rocketmq.dashboard.config.CollectExecutorConfig;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.service.ConsumerService;
import org.apache.rocketmq.dashboard.service.impl.DashboardCollectServiceImpl;
import org.apache.rocketmq.dashboard.util.JsonUtil;
import org.apache.rocketmq.dashboard.util.MockObjectUtil;
import org.apache.rocketmq.remoting.protocol.body.BrokerStatsData;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.GroupList;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DashboardCollectTaskTest extends BaseTest {

    @Spy
    private DashboardCollectTask dashboardCollectTask;

    @Spy
    private DashboardCollectServiceImpl dashboardCollectService;

    @Mock
    private MQAdminExt mqAdminExt;

    @Mock
    private RMQConfigure rmqConfigure;

    @Mock
    private ExecutorService collectExecutor;

    @Mock
    private ConsumerService consumerService;

    private int taskExecuteNum = 10;

    private File brokerFile;

    private File topicFile;

    @Before
    public void init() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(rmqConfigure.getDashboardCollectData()).thenReturn("/tmp/rocketmq-console/test/data");
        ClusterInfo clusterInfo = MockObjectUtil.createClusterInfo();
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        String dataLocationPath = rmqConfigure.getDashboardCollectData();
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        String nowDateStr = format.format(new Date());
        brokerFile = new File(dataLocationPath + nowDateStr + ".json");
        topicFile = new File(dataLocationPath + nowDateStr + "_topic" + ".json");
        autoInjection();
    }

    @Test
    public void testCollectTopic() throws Exception {
        // enableDashBoardCollect = false
        when(rmqConfigure.isEnableDashBoardCollect()).thenReturn(false);
        dashboardCollectTask.collectTopic();
        {
            TopicList topicList = new TopicList();
            Set<String> topicSet = new HashSet<>();
            topicSet.add("rmq_sys_xxx");
            topicSet.add("topic_test");
            topicSet.add("%RETRY%group_test");
            topicSet.add("%DLQ%group_test");
            topicList.setTopicList(topicSet);
            when(mqAdminExt.fetchAllTopicList())
                    .thenThrow(new RuntimeException("fetchAllTopicList exception"))
                    .thenReturn(topicList);
            TopicRouteData topicRouteData = MockObjectUtil.createTopicRouteData();
            when(mqAdminExt.examineTopicRouteInfo(anyString())).thenReturn(topicRouteData);
            GroupList list = new GroupList();
            list.setGroupList(Sets.newHashSet("group_test"));
            when(mqAdminExt.queryTopicConsumeByWho(anyString())).thenReturn(list);
            BrokerStatsData brokerStatsData = MockObjectUtil.createBrokerStatsData();
            when(mqAdminExt.viewBrokerStatsData(anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("viewBrokerStatsData TOPIC_PUT_NUMS exception"))
                    .thenThrow(new RuntimeException("viewBrokerStatsData GROUP_GET_NUMS exception"))
                    .thenReturn(brokerStatsData);
            when(rmqConfigure.isEnableDashBoardCollect()).thenReturn(true);
        }
        // fetchAllTopicList exception
        try {
            dashboardCollectTask.collectTopic();
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), "fetchAllTopicList exception");
        }
        dashboardCollectTask.collectTopic();

        // multiple topic collection
        CollectExecutorConfig config = new CollectExecutorConfig();
        config.setCoreSize(10);
        config.setMaxSize(10);
        config.setQueueSize(500);
        config.setKeepAliveTime(3000);
        ExecutorService collectExecutor = config.collectExecutor(config);
        for (int i = 0; i < taskExecuteNum; i++) {
            CollectTaskRunnble collectTask = new CollectTaskRunnble("topic_test" + i, mqAdminExt, dashboardCollectService);
            collectExecutor.submit(collectTask);
        }
        collectExecutor.shutdown();
        boolean loop = true;
        do {
            // Wait for all collectTasks to complete
            loop = !collectExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.MINUTES);
        }
        while (loop);
        LoadingCache<String, List<String>> map = dashboardCollectService.getTopicMap();
        Assert.assertEquals(map.size(), taskExecuteNum);
        dashboardCollectTask.saveData();
        Assert.assertEquals(topicFile.exists(), true);
        Map<String, List<String>> topicData =
                JsonUtil.string2Obj(MixAll.file2String(topicFile),
                        new TypeReference<Map<String, List<String>>>() {
                        });
        Assert.assertEquals(topicData.size(), taskExecuteNum);
    }

    @Test
    public void testCollectBroker() throws Exception {
        // enableDashBoardCollect = false
        when(rmqConfigure.isEnableDashBoardCollect()).thenReturn(false);
        dashboardCollectTask.collectBroker();
        {
            HashMap<String, String> result = new HashMap<>();
            result.put("getTotalTps", "0.0 0.033330000333300004 0.03332972261338355");
            result.put("commitLogMinOffset", "0");
            KVTable kvTable = new KVTable();
            kvTable.setTable(result);
            when(mqAdminExt.fetchBrokerRuntimeStats(anyString()))
                    .thenThrow(new RuntimeException("fetchBrokerRuntimeStats exception"))
                    .thenReturn(kvTable);
            when(rmqConfigure.isEnableDashBoardCollect()).thenReturn(true);
        }
        // fetchBrokerRuntimeStats exception
        try {
            dashboardCollectTask.collectBroker();
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), "fetchBrokerRuntimeStats exception");
        }

        for (int i = 0; i < taskExecuteNum; i++) {
            dashboardCollectTask.collectBroker();
        }
        LoadingCache<String, List<String>> map = dashboardCollectService.getBrokerMap();
        Assert.assertEquals(map.size(), 1);
        Assert.assertEquals(map.get("broker-a" + ":" + MixAll.MASTER_ID).size(), taskExecuteNum);
        mockBrokerFileExistBeforeSaveData();
        dashboardCollectTask.saveData();
        Assert.assertEquals(brokerFile.exists(), true);
        Map<String, List<String>> brokerData =
                JsonUtil.string2Obj(MixAll.file2String(brokerFile),
                        new TypeReference<Map<String, List<String>>>() {
                        });
        Assert.assertEquals(brokerData.get("broker-a" + ":" + MixAll.MASTER_ID).size(), taskExecuteNum + 2);
    }

    @Test
    public void testCollectConsumer() {
        dashboardCollectTask.collectConsumer();
        verify(consumerService, times(1)).queryGroupList(false, null);
    }

    @Test
    public void testCollectAccumulationAndTransactionAndStorageLatency() throws Exception {
        // enableDashBoardCollect = false
        when(rmqConfigure.isEnableDashBoardCollect()).thenReturn(false);
        dashboardCollectTask.collectAccumulation();
        dashboardCollectTask.collectTransaction();
        dashboardCollectTask.collectStorageLatency();
        verify(collectExecutor, never()).submit(any(Runnable.class));

        when(rmqConfigure.isEnableDashBoardCollect()).thenReturn(true);
        TopicList topicList = new TopicList();
        Set<String> topicSet = new HashSet<>();
        topicSet.add("rmq_sys_xxx");
        topicSet.add("topic_test");
        topicSet.add("%RETRY%group_test");
        topicSet.add("%DLQ%group_test");
        topicList.setTopicList(topicSet);
        when(mqAdminExt.fetchAllTopicList())
                .thenThrow(new RuntimeException("fetchAllTopicList exception"))
                .thenReturn(topicList);

        // fetchAllTopicList exception is swallowed by these collect methods
        dashboardCollectTask.collectAccumulation();
        verify(collectExecutor, never()).submit(any(Runnable.class));

        // only topic_test survives the filter, one task submitted per method
        dashboardCollectTask.collectAccumulation();
        dashboardCollectTask.collectTransaction();
        dashboardCollectTask.collectStorageLatency();
        verify(collectExecutor, times(3)).submit(any(Runnable.class));
    }

    @Test
    public void testCollectClusterLevelTasks() {
        // enableDashBoardCollect = false
        when(rmqConfigure.isEnableDashBoardCollect()).thenReturn(false);
        dashboardCollectTask.collectNetworkThroughput();
        dashboardCollectTask.collectReplicaSync();
        dashboardCollectTask.collectHotTopic();
        verify(collectExecutor, never()).submit(any(Runnable.class));

        when(rmqConfigure.isEnableDashBoardCollect()).thenReturn(true);
        dashboardCollectTask.collectNetworkThroughput();
        dashboardCollectTask.collectReplicaSync();
        dashboardCollectTask.collectHotTopic();
        verify(collectExecutor, times(3)).submit(any(Runnable.class));

        // submit exception is swallowed
        when(collectExecutor.submit(any(Runnable.class)))
                .thenThrow(new RuntimeException("submit exception"));
        dashboardCollectTask.collectNetworkThroughput();
        dashboardCollectTask.collectReplicaSync();
        dashboardCollectTask.collectHotTopic();
        verify(collectExecutor, times(6)).submit(any(Runnable.class));
    }

    @Test
    public void testSaveDataWithDateChanged() throws Exception {
        when(rmqConfigure.isEnableDashBoardCollect()).thenReturn(true);
        dashboardCollectService.getBrokerMap().put("broker-a:0", Lists.newArrayList("1000,1.0"));
        dashboardCollectService.getTopicMap().put("topic_test", Lists.newArrayList("1000,1.0"));
        // yesterday, cache should be invalidated on date change
        Date yesterday = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
        ReflectionTestUtils.setField(dashboardCollectTask, "currentDate", yesterday);

        dashboardCollectTask.saveData();

        Assert.assertEquals(0, dashboardCollectService.getBrokerMap().size());
        Assert.assertEquals(0, dashboardCollectService.getTopicMap().size());
        Assert.assertEquals(true, brokerFile.exists());
        Assert.assertEquals(true, topicFile.exists());
    }

    @After
    public void after() {
        String dataLocationPath = "/tmp/rocketmq-console/test/data";
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        String nowDateStr = format.format(new Date());
        String[] suffixes = {"", "_topic", "_accumulation", "_transaction", "_storageLatency",
            "_networkThroughput", "_replicaSync", "_hotTopic"};
        for (String suffix : suffixes) {
            File file = new File(dataLocationPath + nowDateStr + suffix + ".json");
            if (file.exists()) {
                file.delete();
            }
        }
    }

    private void mockBrokerFileExistBeforeSaveData() throws Exception {
        Map<String, List<String>> map = new HashMap<>();
        map.put("broker-a" + ":" + MixAll.MASTER_ID, Lists.asList("1000", new String[]{"1000"}));
        map.put("broker-b" + ":" + MixAll.MASTER_ID, Lists.asList("1000", new String[]{"1000"}));
        MixAll.string2File(JsonUtil.obj2String(map), brokerFile.getAbsolutePath());
    }
}
