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

import org.apache.rocketmq.dashboard.model.ConsumerMonitorConfig;
import org.apache.rocketmq.dashboard.model.GroupConsumeInfo;
import org.apache.rocketmq.dashboard.service.ConsumerService;
import org.apache.rocketmq.dashboard.service.MonitorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class MonitorTaskTest {

    @InjectMocks
    private MonitorTask monitorTask;

    @Mock
    private MonitorService monitorService;

    @Mock
    private ConsumerService consumerService;

    private Map<String, ConsumerMonitorConfig> configMap;

    @Before
    public void setUp() {
        configMap = new HashMap<>();
    }

    private GroupConsumeInfo buildGroupConsumeInfo(int count, long diffTotal) {
        GroupConsumeInfo info = new GroupConsumeInfo();
        info.setCount(count);
        info.setDiffTotal(diffTotal);
        return info;
    }

    @Test
    public void testScanProblemConsumeGroupTriggersAlert() {
        // count < minCount -> problem group
        configMap.put("group_low_count", new ConsumerMonitorConfig(2, 100));
        // diffTotal > maxDiffTotal -> problem group
        configMap.put("group_large_diff", new ConsumerMonitorConfig(0, 10));
        when(monitorService.queryConsumerMonitorConfig()).thenReturn(configMap);
        when(consumerService.queryGroup(eq("group_low_count"), isNull()))
                .thenReturn(buildGroupConsumeInfo(1, 0));
        when(consumerService.queryGroup(eq("group_large_diff"), isNull()))
                .thenReturn(buildGroupConsumeInfo(1, 100));

        monitorTask.scanProblemConsumeGroup();

        verify(consumerService, times(2)).queryGroup(org.mockito.ArgumentMatchers.anyString(), isNull());
    }

    @Test
    public void testScanProblemConsumeGroupNormal() {
        configMap.put("group_ok", new ConsumerMonitorConfig(1, 100));
        when(monitorService.queryConsumerMonitorConfig()).thenReturn(configMap);
        when(consumerService.queryGroup(eq("group_ok"), isNull()))
                .thenReturn(buildGroupConsumeInfo(2, 10));

        monitorTask.scanProblemConsumeGroup();

        verify(consumerService, times(1)).queryGroup(eq("group_ok"), isNull());
    }

    @Test
    public void testScanProblemConsumeGroupEmptyConfig() {
        when(monitorService.queryConsumerMonitorConfig()).thenReturn(new HashMap<>());

        monitorTask.scanProblemConsumeGroup();

        verify(consumerService, never()).queryGroup(org.mockito.ArgumentMatchers.anyString(), isNull());
    }
}
