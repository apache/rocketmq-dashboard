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
package org.apache.rocketmq.dashboard.controller;

import com.google.common.collect.Maps;
import com.google.common.io.Files;
import org.apache.rocketmq.dashboard.service.impl.DashboardCollectServiceImpl;
import org.apache.rocketmq.dashboard.service.impl.DashboardServiceImpl;
import org.apache.rocketmq.dashboard.util.JsonUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.File;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class DashboardControllerTest extends BaseControllerTest {

    @InjectMocks
    private DashboardController dashboardController;

    @Spy
    private DashboardServiceImpl dashboardService;

    @Spy
    private DashboardCollectServiceImpl dashboardCollectService;

    private String nowDateStr;

    private String yesterdayDateStr;

    private File topicDataFile;

    private File brokerDataFile;

    @Before
    public void init() throws Exception {
        super.mockRmqConfigure();
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        nowDateStr = format.format(new Date());
        yesterdayDateStr = format.format(new Date((System.currentTimeMillis() - 24 * 60 * 60 * 1000)));
        // generate today's brokerData and topicData cache file
        brokerDataFile = this.createBrokerTestCollectDataFile(nowDateStr);
        topicDataFile = this.createTopicTestCollectDataFile(nowDateStr);
        when(configure.getDashboardCollectData()).thenReturn("");
    }

    @After
    public void after() {
        // delete test file
        if (brokerDataFile != null && brokerDataFile.exists()) {
            brokerDataFile.delete();
        }
        if (topicDataFile != null && topicDataFile.exists()) {
            topicDataFile.delete();
        }
    }

    @Test
    public void testBroker() throws Exception {
        final String url = "/dashboard/broker.query";

        // 1、no broker cache data
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("date", yesterdayDateStr);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.data").isEmpty());

        // 2、the broker's data is cached locally
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("date", nowDateStr);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andExpect(jsonPath("$.data", hasKey("broker-a:0")))
                .andExpect(jsonPath("$.data.broker-a:0").isArray())
                .andExpect(jsonPath("$.data.broker-a:0", hasSize(100)));

    }

    @Test
    public void testTopic() throws Exception {
        final String url = "/dashboard/topic.query";
        // 1、topicName is empty
        // 1.1、no topic cache data
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("date", yesterdayDateStr);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.data").isEmpty());

        // 1.2、the topic's data is cached locally
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("date", nowDateStr);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andExpect(jsonPath("$.data", hasKey("topic_test")))
                .andExpect(jsonPath("$.data.topic_test").isArray())
                .andExpect(jsonPath("$.data.topic_test", hasSize(100)));

        // 2、topicName is not empty
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("date", nowDateStr);
        requestBuilder.param("topicName", "topic_test");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(100)));

        // 2、topicName is not empty but the no topic cache data
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("date", nowDateStr);
        requestBuilder.param("topicName", "topic_test1");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk());

    }

    @Test
    public void testTopicCurrent() throws Exception {
        final String url = "/dashboard/topicCurrent.query";
        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("topic_test,100"));
    }

    @Test
    public void testAccumulation() throws Exception {
        final String url = "/dashboard/accumulation.query";
        Map<String, List<String>> allData = Maps.newHashMap();
        allData.put("topic_test", java.util.Arrays.asList("1000,1"));
        // use doReturn on the @Spy to avoid invoking the real implementation
        doReturn(allData).when(dashboardService).queryAccumulationData(anyString());
        doReturn(java.util.Arrays.asList("1000,1")).when(dashboardService)
                .queryAccumulationData(anyString(), anyString());

        // 1. topicName is empty
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("date", nowDateStr);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasKey("topic_test")));

        // 2. topicName is not empty
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("date", nowDateStr);
        requestBuilder.param("topicName", "topic_test");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    public void testTransaction() throws Exception {
        final String url = "/dashboard/transaction.query";
        Map<String, List<String>> allData = Maps.newHashMap();
        allData.put("topic_test", java.util.Arrays.asList("1000,2,2"));
        doReturn(allData).when(dashboardService).queryTransactionData(anyString());
        doReturn(java.util.Arrays.asList("1000,2,2")).when(dashboardService)
                .queryTransactionData(anyString(), anyString());

        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("date", nowDateStr);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasKey("topic_test")));

        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("date", nowDateStr);
        requestBuilder.param("topicName", "topic_test");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    public void testStorageLatency() throws Exception {
        final String url = "/dashboard/storageLatency.query";
        Map<String, List<String>> allData = Maps.newHashMap();
        allData.put("topic_test", java.util.Arrays.asList("1000,3,3,0,0"));
        doReturn(allData).when(dashboardService).queryStorageLatencyData(anyString());
        doReturn(java.util.Arrays.asList("1000,3,3,0,0")).when(dashboardService)
                .queryStorageLatencyData(anyString(), anyString());

        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("date", nowDateStr);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasKey("topic_test")));

        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("date", nowDateStr);
        requestBuilder.param("topicName", "topic_test");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    public void testNetworkThroughput() throws Exception {
        final String url = "/dashboard/networkThroughput.query";
        Map<String, List<String>> allData = Maps.newHashMap();
        allData.put("broker-a:0", java.util.Arrays.asList("1000,4,4,4,4"));
        doReturn(allData).when(dashboardService).queryNetworkThroughputData(anyString());
        doReturn(java.util.Arrays.asList("1000,4,4,4,4")).when(dashboardService)
                .queryNetworkThroughputData(anyString(), anyString());

        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("date", nowDateStr);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasKey("broker-a:0")));

        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("date", nowDateStr);
        requestBuilder.param("brokerName", "broker-a");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    public void testReplicaSync() throws Exception {
        final String url = "/dashboard/replicaSync.query";
        Map<String, List<String>> allData = Maps.newHashMap();
        allData.put("broker-a:0", java.util.Arrays.asList("1000,5,5,1,1"));
        doReturn(allData).when(dashboardService).queryReplicaSyncData(anyString());
        doReturn(java.util.Arrays.asList("1000,5,5,1,1")).when(dashboardService)
                .queryReplicaSyncData(anyString(), anyString());

        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("date", nowDateStr);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasKey("broker-a:0")));

        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("date", nowDateStr);
        requestBuilder.param("brokerName", "broker-a");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    public void testHotTopic() throws Exception {
        final String url = "/dashboard/hotTopic.query";
        Map<String, List<String>> allData = Maps.newHashMap();
        allData.put("topic_test", java.util.Arrays.asList("1000,6,6,6,6"));
        doReturn(allData).when(dashboardService).queryHotTopicData(anyString());
        doReturn(java.util.Arrays.asList("1000,6,6,6,6")).when(dashboardService)
                .queryHotTopicData(anyString(), anyString());

        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("date", nowDateStr);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasKey("topic_test")));

        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("date", nowDateStr);
        requestBuilder.param("topicName", "topic_test");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    public void testConsumerConcurrency() throws Exception {
        final String url = "/dashboard/consumerConcurrency.query";
        Map<String, Object> row = Maps.newHashMap();
        row.put("groupName", "group_test");
        row.put("clientCount", 2);
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(row);
        doReturn(data).when(dashboardService).queryConsumerConcurrency();

        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].groupName").value("group_test"));
    }

    @Test
    public void testBrokerJvmStats() throws Exception {
        final String url = "/dashboard/brokerJvmStats.query";
        Map<String, Object> row = Maps.newHashMap();
        row.put("brokerName", "broker-a");
        row.put("heapUsed", 1024L);
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(row);
        doReturn(data).when(dashboardService).queryBrokerJvmStats();

        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].brokerName").value("broker-a"));
    }

    @Override
    protected Object getTestController() {
        return dashboardController;
    }

    private File createBrokerTestCollectDataFile(String date) throws Exception {
        File brokerFile = new File(date + ".json");
        brokerFile.createNewFile();
        Map<String /*brokerName:brokerId*/, List<String/*timestamp,tps*/>> resultMap = Maps.newHashMap();
        List<String> brokerData = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            BigDecimal tps = new BigDecimal(i).divide(BigDecimal.valueOf(10), 3, BigDecimal.ROUND_HALF_UP);
            brokerData.add((new Date().getTime() + i * 60 * 1000) + "," + tps.toString());
        }
        resultMap.put("broker-a:0", brokerData);
        Files.write(JsonUtil.obj2String(resultMap).getBytes(), brokerFile);
        return brokerFile;
    }

    private File createTopicTestCollectDataFile(String date) throws Exception {
        File topicFile = new File(date + "_topic" + ".json");
        topicFile.createNewFile();
        Map<String /*topicName*/, List<String/*timestamp,inTps,inMsgCntToday,outTps,outMsgCntToday*/>> resultMap = Maps.newHashMap();
        List<String> topicData = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            String inTps = new BigDecimal(i).divide(BigDecimal.valueOf(10), 3, BigDecimal.ROUND_HALF_UP).toString();
            String outTps = inTps;
            StringBuilder sb = new StringBuilder();
            sb.append((new Date().getTime() + i * 60 * 1000))
                    .append(',').append(inTps)
                    .append(',').append(i)
                    .append(',').append(outTps)
                    .append(',').append(i);
            topicData.add(sb.toString());
        }
        resultMap.put("topic_test", topicData);
        Files.write(JsonUtil.obj2String(resultMap).getBytes(), topicFile);
        return topicFile;
    }
}
