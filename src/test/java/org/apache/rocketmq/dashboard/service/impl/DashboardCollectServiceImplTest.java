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

import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class DashboardCollectServiceImplTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @InjectMocks
    private DashboardCollectServiceImpl dashboardCollectService;

    @Mock
    private RMQConfigure configure;

    private String dataLocationPath;

    private static final String DATE = "2026-07-28";

    @Before
    public void setUp() {
        dataLocationPath = tempFolder.getRoot().getAbsolutePath() + File.separator;
        when(configure.getDashboardCollectData()).thenReturn(dataLocationPath);
    }

    private void writeJsonFile(String fileName, String content) throws Exception {
        File file = new File(dataLocationPath + fileName);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    @Test
    public void testGetAllLoadingCaches() throws Exception {
        Assert.assertNotNull(dashboardCollectService.getBrokerMap());
        Assert.assertNotNull(dashboardCollectService.getTopicMap());
        Assert.assertNotNull(dashboardCollectService.getAccumulationMap());
        Assert.assertNotNull(dashboardCollectService.getTransactionMap());
        Assert.assertNotNull(dashboardCollectService.getStorageLatencyMap());
        Assert.assertNotNull(dashboardCollectService.getNetworkThroughputMap());
        Assert.assertNotNull(dashboardCollectService.getReplicaSyncMap());
        Assert.assertNotNull(dashboardCollectService.getHotTopicMap());
        // CacheLoader loads an empty list for a missing key
        Assert.assertEquals(0, dashboardCollectService.getBrokerMap().get("no_such_key").size());
    }

    @Test
    public void testJsonDataFile2map() throws Exception {
        writeJsonFile("data.json", "{\"broker-a:0\":[\"1000,1.0\",\"2000,2.0\"],\"broker-b:0\":null}");
        Map<String, List<String>> map =
                dashboardCollectService.jsonDataFile2map(new File(dataLocationPath + "data.json"));
        Assert.assertEquals(1, map.size());
        Assert.assertEquals(2, map.get("broker-a:0").size());
        Assert.assertEquals("1000,1.0", map.get("broker-a:0").get(0));
    }

    @Test(expected = RuntimeException.class)
    public void testJsonDataFile2mapFileNotExist() {
        dashboardCollectService.jsonDataFile2map(new File(dataLocationPath + "not_exist.json"));
    }

    @Test
    public void testGetBrokerCache() throws Exception {
        Assert.assertEquals(0, dashboardCollectService.getBrokerCache(DATE).size());
        writeJsonFile(DATE + ".json", "{\"broker-a:0\":[\"1000,1.0\"]}");
        Map<String, List<String>> map = dashboardCollectService.getBrokerCache(DATE);
        Assert.assertEquals(1, map.size());
    }

    @Test
    public void testGetTopicCache() throws Exception {
        Assert.assertEquals(0, dashboardCollectService.getTopicCache(DATE).size());
        writeJsonFile(DATE + "_topic.json", "{\"topic_test\":[\"1000,1.0\"]}");
        Map<String, List<String>> map = dashboardCollectService.getTopicCache(DATE);
        Assert.assertEquals(1, map.size());
    }

    @Test
    public void testGetAccumulationCache() throws Exception {
        Assert.assertEquals(0, dashboardCollectService.getAccumulationCache(DATE).size());
        writeJsonFile(DATE + "_accumulation.json", "{\"topic_test\":[\"1000,6\"]}");
        Map<String, List<String>> map = dashboardCollectService.getAccumulationCache(DATE);
        Assert.assertEquals(1, map.size());
    }

    @Test
    public void testGetTransactionCache() throws Exception {
        Assert.assertEquals(0, dashboardCollectService.getTransactionCache(DATE).size());
        writeJsonFile(DATE + "_transaction.json", "{\"topic_test\":[\"1000,1.0,10,1.0,10\"]}");
        Map<String, List<String>> map = dashboardCollectService.getTransactionCache(DATE);
        Assert.assertEquals(1, map.size());
    }

    @Test
    public void testGetStorageLatencyCache() throws Exception {
        Assert.assertEquals(0, dashboardCollectService.getStorageLatencyCache(DATE).size());
        writeJsonFile(DATE + "_storageLatency.json", "{\"topic_test\":[\"1000,1.0,1.0,10,1.0\"]}");
        Map<String, List<String>> map = dashboardCollectService.getStorageLatencyCache(DATE);
        Assert.assertEquals(1, map.size());
    }

    @Test
    public void testGetNetworkThroughputCache() throws Exception {
        Assert.assertEquals(0, dashboardCollectService.getNetworkThroughputCache(DATE).size());
        writeJsonFile(DATE + "_networkThroughput.json", "{\"broker-a\":[\"1000,1.0,10,1.0,10\"]}");
        Map<String, List<String>> map = dashboardCollectService.getNetworkThroughputCache(DATE);
        Assert.assertEquals(1, map.size());
    }

    @Test
    public void testGetReplicaSyncCache() throws Exception {
        Assert.assertEquals(0, dashboardCollectService.getReplicaSyncCache(DATE).size());
        writeJsonFile(DATE + "_replicaSync.json", "{\"broker-a\":[\"1000,100,2048,1,2\"]}");
        Map<String, List<String>> map = dashboardCollectService.getReplicaSyncCache(DATE);
        Assert.assertEquals(1, map.size());
    }

    @Test
    public void testGetHotTopicCache() throws Exception {
        Assert.assertEquals(0, dashboardCollectService.getHotTopicCache(DATE).size());
        writeJsonFile(DATE + "_hotTopic.json", "{\"topic_test\":[\"1000,1.0,10,1.0,10\"]}");
        Map<String, List<String>> map = dashboardCollectService.getHotTopicCache(DATE);
        Assert.assertEquals(1, map.size());
    }
}
