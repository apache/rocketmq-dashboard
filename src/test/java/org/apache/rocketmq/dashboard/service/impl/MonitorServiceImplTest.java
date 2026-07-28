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

import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.model.ConsumerMonitorConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.util.Map;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class MonitorServiceImplTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @InjectMocks
    private MonitorServiceImpl monitorService;

    @Mock
    private RMQConfigure configure;

    private String dataPath;

    @Before
    public void setUp() {
        dataPath = tempFolder.getRoot().getAbsolutePath();
        when(configure.getRocketMqDashboardDataPath()).thenReturn(dataPath);
    }

    private String configFilePath() {
        return dataPath + File.separatorChar + "monitor" + File.separatorChar + "consumerMonitorConfig.json";
    }

    @Test
    public void testCreateOrUpdateAndQueryAndDelete() {
        ConsumerMonitorConfig config = new ConsumerMonitorConfig(1, 100);
        Assert.assertTrue(monitorService.createOrUpdateConsumerMonitor("group_test", config));
        Assert.assertTrue(new File(configFilePath()).exists());

        Map<String, ConsumerMonitorConfig> configMap = monitorService.queryConsumerMonitorConfig();
        Assert.assertEquals(1, configMap.size());

        ConsumerMonitorConfig queried = monitorService.queryConsumerMonitorConfigByGroupName("group_test");
        Assert.assertNotNull(queried);
        Assert.assertEquals(1, queried.getMinCount());
        Assert.assertEquals(100, queried.getMaxDiffTotal());
        Assert.assertNull(monitorService.queryConsumerMonitorConfigByGroupName("group_not_exist"));

        Assert.assertTrue(monitorService.deleteConsumerMonitor("group_test"));
        Assert.assertEquals(0, monitorService.queryConsumerMonitorConfig().size());
    }

    @Test
    public void testCreateOrUpdateWriteFileFailed() throws Exception {
        // point data path under a regular file so that mkdir fails
        File blockingFile = tempFolder.newFile("blocking");
        when(configure.getRocketMqDashboardDataPath()).thenReturn(blockingFile.getAbsolutePath());
        try {
            monitorService.createOrUpdateConsumerMonitor("group_test", new ConsumerMonitorConfig(1, 100));
            Assert.fail("Expected RuntimeException but no exception was thrown");
        } catch (RuntimeException e) {
            Assert.assertNotNull(e);
        }
    }

    @Test
    public void testLoadDataFromConfigFile() throws Exception {
        monitorService.createOrUpdateConsumerMonitor("group_test", new ConsumerMonitorConfig(2, 200));
        // reset in-memory map then reload from file
        MonitorServiceImpl newService = new MonitorServiceImpl();
        ReflectionTestUtils.setField(newService, "configure", configure);
        ReflectionTestUtils.invokeMethod(newService, "loadData");
        ConsumerMonitorConfig loaded = newService.queryConsumerMonitorConfigByGroupName("group_test");
        Assert.assertNotNull(loaded);
        Assert.assertEquals(2, loaded.getMinCount());
        Assert.assertEquals(200, loaded.getMaxDiffTotal());
    }

    @Test
    public void testLoadDataFromBackupFile() throws Exception {
        MixAll.string2File("{\"group_bak\":{\"minCount\":3,\"maxDiffTotal\":300}}",
                configFilePath() + ".bak");
        MonitorServiceImpl newService = new MonitorServiceImpl();
        ReflectionTestUtils.setField(newService, "configure", configure);
        ReflectionTestUtils.invokeMethod(newService, "loadData");
        ConsumerMonitorConfig loaded = newService.queryConsumerMonitorConfigByGroupName("group_bak");
        Assert.assertNotNull(loaded);
        Assert.assertEquals(3, loaded.getMinCount());
    }

    @Test
    public void testLoadDataWithNoFile() {
        MonitorServiceImpl newService = new MonitorServiceImpl();
        ReflectionTestUtils.setField(newService, "configure", configure);
        // no config file and no backup -> should return silently
        ReflectionTestUtils.invokeMethod(newService, "loadData");
        Assert.assertEquals(0, newService.queryConsumerMonitorConfig().size());
    }
}
