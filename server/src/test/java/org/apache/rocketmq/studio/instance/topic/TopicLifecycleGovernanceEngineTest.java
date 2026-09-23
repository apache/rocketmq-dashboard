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
package org.apache.rocketmq.studio.instance.topic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicLifecycleGovernanceEngineTest {

    private TopicLifecycleGovernanceEngine engine;

    @BeforeEach
    void setUp() {
        engine = new TopicLifecycleGovernanceEngine();
    }

    @Test
    void testAuditLifecycleEmpty() {
        TopicLifecycleAuditReportVO report = engine.auditLifecycle("inst-1", Collections.emptyList());
        assertNotNull(report);
        assertEquals(0, report.getTotalTopicsAudited());
        assertTrue(report.getGovernanceSuggestions().get(0).contains("No user topics found"));
    }

    @Test
    void testAuditLifecycleSkipSystemTopics() {
        TopicVO sysTopic = new TopicVO();
        sysTopic.setName("RMQ_SYS_TRANS_HALF_TOPIC");
        sysTopic.setTps(10.0);

        TopicLifecycleAuditReportVO report = engine.auditLifecycle("inst-1", List.of(sysTopic));
        assertNotNull(report);
        assertEquals(0, report.getTotalTopicsAudited());
    }

    @Test
    void testAuditLifecycleActiveAndDormant() {
        TopicVO activeTopic = new TopicVO();
        activeTopic.setName("TopicActive");
        activeTopic.setTps(5.2);
        activeTopic.setMessageCount(5000);
        activeTopic.setConsumerGroupCount(2);

        TopicVO dormantTopic = new TopicVO();
        dormantTopic.setName("TopicDormant");
        dormantTopic.setTps(0.0);
        dormantTopic.setMessageCount(100);
        dormantTopic.setConsumerGroupCount(1); // Has subscribers, 0 TPS

        TopicLifecycleAuditReportVO report = engine.auditLifecycle("inst-1", List.of(activeTopic, dormantTopic));
        assertNotNull(report);
        assertEquals(2, report.getTotalTopicsAudited());
        assertEquals(1, report.getActiveTopicCount());
        assertEquals(1, report.getDormantTopicCount());
        assertEquals("ACTIVE", report.getTopicAssessments().get(0).getLifecyclePhase());
        assertEquals("DORMANT", report.getTopicAssessments().get(1).getLifecyclePhase());
    }

    @Test
    void testAuditLifecycleZombieAndOrphan() {
        TopicVO zombieTopic = new TopicVO();
        zombieTopic.setName("TopicZombie");
        zombieTopic.setTps(0.0);
        zombieTopic.setMessageCount(0);
        zombieTopic.setConsumerGroupCount(0); // 0 msgs, 0 subs

        TopicVO orphanTopic = new TopicVO();
        orphanTopic.setName("TopicOrphan");
        orphanTopic.setTps(0.0);
        orphanTopic.setMessageCount(500); // 500 msgs, 0 subs
        orphanTopic.setConsumerGroupCount(0);

        TopicLifecycleAuditReportVO report = engine.auditLifecycle("inst-1", List.of(zombieTopic, orphanTopic));
        assertNotNull(report);
        assertEquals(2, report.getTotalTopicsAudited());
        assertEquals(1, report.getZombieTopicCount());
        assertEquals(1, report.getOrphanTopicCount());

        TopicLifecycleAuditReportVO.TopicLifecycleAssessmentVO zombie = report.getTopicAssessments().get(0);
        assertEquals("ZOMBIE", zombie.getLifecyclePhase());
        assertEquals("SAFE_TO_CLEANUP", zombie.getRiskLevel());

        TopicLifecycleAuditReportVO.TopicLifecycleAssessmentVO orphan = report.getTopicAssessments().get(1);
        assertEquals("ORPHAN", orphan.getLifecyclePhase());
        assertEquals("OBSERVATION_REQUIRED", orphan.getRiskLevel());

        assertTrue(report.getGovernanceSuggestions().size() >= 2);
        assertTrue(report.getGovernanceSuggestions().stream().anyMatch(s -> s.contains("ZOMBIE topic(s)")));
        assertTrue(report.getGovernanceSuggestions().stream().anyMatch(s -> s.contains("ORPHAN topic(s)")));
    }

    @Test
    void testAuditLifecycleHealthyStateAllActive() {
        TopicVO activeTopic1 = new TopicVO();
        activeTopic1.setName("Active1");
        activeTopic1.setTps(1.0);
        activeTopic1.setConsumerGroupCount(1);

        TopicVO activeTopic2 = new TopicVO();
        activeTopic2.setName("Active2");
        activeTopic2.setTps(2.0);
        activeTopic2.setConsumerGroupCount(2);

        TopicLifecycleAuditReportVO report = engine.auditLifecycle("inst-1", List.of(activeTopic1, activeTopic2));
        assertNotNull(report);
        assertEquals(2, report.getActiveTopicCount());
        assertEquals(0, report.getZombieTopicCount());
        assertEquals(0, report.getOrphanTopicCount());
        assertTrue(report.getGovernanceSuggestions().get(0).contains("Cluster topic lifecycle is completely healthy"));
    }
}
