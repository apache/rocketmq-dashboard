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

import java.util.Collections;
import java.util.List;
import org.apache.rocketmq.dashboard.exception.ServiceException;
import org.apache.rocketmq.dashboard.model.AlertRuleVO;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AlertRuleServiceImplTest {

    private AlertRuleServiceImpl alertRuleService;

    @Before
    public void setUp() {
        alertRuleService = new AlertRuleServiceImpl();
    }

    private AlertRuleVO buildRule(String alert) {
        return AlertRuleVO.builder()
            .alert(alert)
            .group("broker")
            .expr("up{job=\"broker\"} == 0")
            .forDuration("5m")
            .severity("critical")
            .team("broker")
            .summary("Broker is down")
            .channels(Collections.singletonList("email"))
            .enabled(true)
            .build();
    }

    @Test
    public void testListRulesInitiallyEmpty() {
        assertTrue(alertRuleService.listRules().isEmpty());
    }

    @Test
    public void testCreateRuleAssignsIdAndTimestamps() {
        AlertRuleVO created = alertRuleService.createRule(buildRule("BrokerDownAlert"));
        assertNotNull(created.getId());
        assertNotNull(created.getCreatedAt());
        assertEquals(created.getCreatedAt(), created.getUpdatedAt());

        List<AlertRuleVO> rules = alertRuleService.listRules();
        assertEquals(1, rules.size());
        assertEquals("BrokerDownAlert", rules.get(0).getAlert());
    }

    @Test
    public void testUpdateRulePreservesCreatedAt() {
        AlertRuleVO created = alertRuleService.createRule(buildRule("BrokerDownAlert"));

        AlertRuleVO update = buildRule("BrokerDownAlertRenamed");
        update.setId(created.getId());
        AlertRuleVO updated = alertRuleService.updateRule(update);

        assertEquals(created.getId(), updated.getId());
        assertEquals(created.getCreatedAt(), updated.getCreatedAt());
        assertNotNull(updated.getUpdatedAt());
        assertEquals("BrokerDownAlertRenamed", alertRuleService.listRules().get(0).getAlert());
    }

    @Test
    public void testUpdateRuleNotFound() {
        AlertRuleVO update = buildRule("NotExist");
        update.setId("no-such-id");
        try {
            alertRuleService.updateRule(update);
            fail("expect ServiceException");
        } catch (ServiceException e) {
            assertEquals(404, e.getCode());
        }
    }

    @Test
    public void testToggleRule() {
        AlertRuleVO created = alertRuleService.createRule(buildRule("BrokerDownAlert"));
        AlertRuleVO toggled = alertRuleService.toggleRule(created.getId(), false);
        assertFalse(toggled.isEnabled());
        assertFalse(alertRuleService.listRules().get(0).isEnabled());

        toggled = alertRuleService.toggleRule(created.getId(), true);
        assertTrue(toggled.isEnabled());
    }

    @Test
    public void testToggleRuleNotFound() {
        try {
            alertRuleService.toggleRule("no-such-id", true);
            fail("expect ServiceException");
        } catch (ServiceException e) {
            assertEquals(404, e.getCode());
        }
    }

    @Test
    public void testDeleteRule() {
        AlertRuleVO created = alertRuleService.createRule(buildRule("BrokerDownAlert"));
        alertRuleService.deleteRule(created.getId());
        assertTrue(alertRuleService.listRules().isEmpty());
        // deleting a non-existent rule is a no-op
        alertRuleService.deleteRule("no-such-id");
    }
}
