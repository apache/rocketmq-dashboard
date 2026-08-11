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
import org.apache.rocketmq.dashboard.model.ConsumerMonitorConfig;
import org.apache.rocketmq.dashboard.util.JsonUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MonitorServiceImplTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private MonitorServiceImpl monitorService;
    private File configFile;

    @Before
    public void setUp() throws Exception {
        File dataDirectory = temporaryFolder.newFolder("dashboard-data");
        File monitorDirectory = new File(dataDirectory, "monitor");
        Assert.assertTrue(monitorDirectory.mkdirs());
        configFile = new File(monitorDirectory, "consumerMonitorConfig.json");

        RMQConfigure configure = mock(RMQConfigure.class);
        when(configure.getRocketMqDashboardDataPath()).thenReturn(dataDirectory.getAbsolutePath());
        monitorService = new MonitorServiceImpl();
        ReflectionTestUtils.setField(monitorService, "configure", configure);
    }

    @Test
    public void testLoadDataFallsBackToBackupWhenPrimaryFileIsCorrupted() throws Exception {
        Files.write(configFile.toPath(), "not-json".getBytes(StandardCharsets.UTF_8));
        Map<String, ConsumerMonitorConfig> backupConfig = Collections.singletonMap(
                "group-test", new ConsumerMonitorConfig(1, 100));
        Files.write(new File(configFile.getPath() + ".bak").toPath(),
                JsonUtil.obj2String(backupConfig).getBytes(StandardCharsets.UTF_8));

        ReflectionTestUtils.invokeMethod(monitorService, "loadData");

        ConsumerMonitorConfig loaded = monitorService.queryConsumerMonitorConfigByGroupName("group-test");
        Assert.assertNotNull(loaded);
        Assert.assertEquals(1, loaded.getMinCount());
        Assert.assertEquals(100, loaded.getMaxDiffTotal());
    }

    @Test
    public void testLoadDataUsesEmptyMapWhenPrimaryAndBackupAreCorrupted() throws Exception {
        Files.write(configFile.toPath(), "not-json".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(configFile.getPath() + ".bak").toPath(),
                "also-not-json".getBytes(StandardCharsets.UTF_8));

        ReflectionTestUtils.invokeMethod(monitorService, "loadData");

        Assert.assertNotNull(monitorService.queryConsumerMonitorConfig());
        Assert.assertTrue(monitorService.queryConsumerMonitorConfig().isEmpty());
        Assert.assertTrue(monitorService.createOrUpdateConsumerMonitor(
                "group-test", new ConsumerMonitorConfig(2, 200)));
    }
}
