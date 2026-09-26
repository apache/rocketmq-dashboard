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
package org.apache.rocketmq.studio.instance.group;

import org.apache.rocketmq.common.protocol.heartbeat.SubscriptionData;
import org.apache.rocketmq.remoting.protocol.body.ConsumerRunningInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumerSubscriptionConsistencyValidatorTest {

    private ConsumerSubscriptionConsistencyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ConsumerSubscriptionConsistencyValidator();
    }

    @Test
    void testValidateEmptyClients() {
        SubscriptionConsistencyReportVO report = validator.validate("GroupA", Collections.emptyMap());
        assertNotNull(report);
        assertTrue(report.isConsistent());
        assertEquals(0, report.getTotalClientsChecked());
        assertTrue(report.getWarnings().get(0).contains("No consumer client"));
    }

    @Test
    void testValidateSingleClient() {
        ConsumerRunningInfo info = new ConsumerRunningInfo();
        SubscriptionConsistencyReportVO report = validator.validate("GroupA", Map.of("client-1", info));
        assertNotNull(report);
        assertTrue(report.isConsistent());
        assertEquals(1, report.getTotalClientsChecked());
        assertTrue(report.getWarnings().get(0).contains("Single client instance"));
    }

    @Test
    void testValidateConsistentSubscriptions() {
        ConsumerRunningInfo info1 = new ConsumerRunningInfo();
        SubscriptionData sub1 = new SubscriptionData("TopicOrders", "TagA || TagB");
        sub1.setExpressionType("TAG");
        sub1.setTagsSet(Set.of("TagA", "TagB"));
        info1.setSubscriptionTable(new java.util.TreeSet<>(Set.of(sub1)));

        ConsumerRunningInfo info2 = new ConsumerRunningInfo();
        SubscriptionData sub2 = new SubscriptionData("TopicOrders", "TagA || TagB");
        sub2.setExpressionType("TAG");
        sub2.setTagsSet(Set.of("TagA", "TagB"));
        info2.setSubscriptionTable(new java.util.TreeSet<>(Set.of(sub2)));

        Map<String, ConsumerRunningInfo> map = new LinkedHashMap<>();
        map.put("client-1", info1);
        map.put("client-2", info2);

        SubscriptionConsistencyReportVO report = validator.validate("GroupOrders", map);
        assertNotNull(report);
        assertTrue(report.isConsistent());
        assertEquals(2, report.getTotalClientsChecked());
        assertEquals(0, report.getInconsistentTopicCount());
        assertTrue(report.getMismatches().isEmpty());
    }

    @Test
    void testValidateExpressionMismatch() {
        ConsumerRunningInfo info1 = new ConsumerRunningInfo();
        SubscriptionData sub1 = new SubscriptionData("TopicOrders", "TagA");
        sub1.setExpressionType("TAG");
        info1.setSubscriptionTable(new java.util.TreeSet<>(Set.of(sub1)));

        ConsumerRunningInfo info2 = new ConsumerRunningInfo();
        SubscriptionData sub2 = new SubscriptionData("TopicOrders", "TagB");
        sub2.setExpressionType("TAG");
        info2.setSubscriptionTable(new java.util.TreeSet<>(Set.of(sub2)));

        Map<String, ConsumerRunningInfo> map = new LinkedHashMap<>();
        map.put("client-1", info1);
        map.put("client-2", info2);

        SubscriptionConsistencyReportVO report = validator.validate("GroupOrders", map);
        assertNotNull(report);
        assertFalse(report.isConsistent());
        assertEquals(1, report.getInconsistentTopicCount());
        assertEquals("EXPRESSION_MISMATCH", report.getMismatches().get(0).getIssueType());
    }

    @Test
    void testValidatePartialSubscription() {
        ConsumerRunningInfo info1 = new ConsumerRunningInfo();
        SubscriptionData sub1 = new SubscriptionData("TopicOrders", "*");
        info1.setSubscriptionTable(new java.util.TreeSet<>(Set.of(sub1)));

        ConsumerRunningInfo info2 = new ConsumerRunningInfo();
        info2.setSubscriptionTable(new java.util.TreeSet<>()); // Client 2 does not subscribe to TopicOrders!

        Map<String, ConsumerRunningInfo> map = new LinkedHashMap<>();
        map.put("client-1", info1);
        map.put("client-2", info2);

        SubscriptionConsistencyReportVO report = validator.validate("GroupOrders", map);
        assertNotNull(report);
        assertFalse(report.isConsistent());
        assertEquals(1, report.getInconsistentTopicCount());
        assertEquals("PARTIAL_SUBSCRIPTION", report.getMismatches().get(0).getIssueType());
        assertTrue(report.getMismatches().get(0).getDescription().contains("subscribed by only 1 of 2"));
    }
}
