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

import org.apache.rocketmq.dashboard.model.TopicDeduplicationHealthReport;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TopicDeduplicationHealthServiceImplTest {

    private TopicDeduplicationHealthServiceImpl topicDeduplicationHealthService;

    @Before
    public void setUp() {
        topicDeduplicationHealthService = new TopicDeduplicationHealthServiceImpl();
    }

    @Test
    public void testInspectDeduplicationHealth() {
        TopicDeduplicationHealthReport report =
            topicDeduplicationHealthService.inspectDeduplicationHealth("test-topic", 1000);

        Assert.assertNotNull(report);
        Assert.assertEquals("test-topic", report.getTopic());
        Assert.assertEquals(1000, report.getSampledMessageCount());
        Assert.assertTrue(report.getUniqueKeyCount() > 0);
        Assert.assertNotNull(report.getTopDuplicateKeys());
        Assert.assertFalse(report.getTopDuplicateKeys().isEmpty());
        Assert.assertNotNull(report.getTimeWindowDistribution());
        Assert.assertFalse(report.getTimeWindowDistribution().isEmpty());
        Assert.assertNotNull(report.getIdempotencyHealthScore());
        Assert.assertNotNull(report.getIdempotencyRecommendations());
    }

    @Test
    public void testInspectDeduplicationHealthDefaultTopic() {
        TopicDeduplicationHealthReport report =
            topicDeduplicationHealthService.inspectDeduplicationHealth("", 500);

        Assert.assertNotNull(report);
        Assert.assertEquals("DEFAULT_TOPIC", report.getTopic());
    }
}
