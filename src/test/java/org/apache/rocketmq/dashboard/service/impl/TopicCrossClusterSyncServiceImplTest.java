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

import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.dashboard.model.TopicCrossClusterSyncReport;
import org.apache.rocketmq.dashboard.service.TopicService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class TopicCrossClusterSyncServiceImplTest {

    @Mock
    private TopicService topicService;

    @InjectMocks
    private TopicCrossClusterSyncServiceImpl topicCrossClusterSyncService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testCompareTopicAcrossClusters() {
        TopicConfig config = new TopicConfig("BenchmarkTopic");
        config.setReadQueueNums(16);
        config.setWriteQueueNums(16);
        when(topicService.examineTopicConfig(anyString())).thenReturn(config);

        TopicCrossClusterSyncReport report =
            topicCrossClusterSyncService.compareTopicAcrossClusters("BenchmarkTopic", "Cluster-East", "Cluster-West");

        Assert.assertNotNull(report);
        Assert.assertEquals("BenchmarkTopic", report.getTopic());
        Assert.assertEquals("Cluster-East", report.getSourceCluster());
        Assert.assertEquals("Cluster-West", report.getTargetCluster());
        Assert.assertNotNull(report.getAttributeDiffs());
        Assert.assertEquals(4, report.getAttributeDiffs().size());
        Assert.assertNotNull(report.getSyncActionLog());
    }

    @Test
    public void testSynchronizeTopicConfig() {
        TopicCrossClusterSyncReport report =
            topicCrossClusterSyncService.synchronizeTopicConfig("BenchmarkTopic", "Cluster-East", "Cluster-West");

        Assert.assertNotNull(report);
        Assert.assertTrue(report.isConfigurationInSync());
        Assert.assertEquals(0, report.getTotalDiscrepancies());
        Assert.assertEquals("SYNCHRONIZED", report.getSyncStatus());
    }
}
