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
package org.apache.rocketmq.studio.model;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Acl2PolicyContextTest {

    private Acl2PolicyContext validContext() {
        Acl2PolicyContext context = new Acl2PolicyContext();
        context.setAccessKey("AK-1");
        context.setPolicyName("policy-1");
        context.setBoundType("USER");
        context.setRules(List.of(Acl2PolicyContext.AuthorizationRule.defaultAllowRule("order-*")));
        return context;
    }

    @Test
    void defaultAllowRuleCarriesExpectedShape() {
        Acl2PolicyContext.AuthorizationRule rule =
                Acl2PolicyContext.AuthorizationRule.defaultAllowRule("order-*");

        assertEquals("order-*", rule.getResourcePattern());
        assertEquals(List.of("READ", "WRITE"), rule.getActions());
        assertEquals("Allow", rule.getEffect());
        assertEquals(100, rule.getPriority());
    }

    @Test
    void denyAllRuleCarriesExpectedShape() {
        Acl2PolicyContext.AuthorizationRule rule =
                Acl2PolicyContext.AuthorizationRule.denyAllRule();

        assertEquals("**", rule.getResourcePattern());
        assertEquals(List.of("*"), rule.getActions());
        assertEquals("Deny", rule.getEffect());
        assertEquals(0, rule.getPriority());
    }

    @Test
    void validateAcceptsCompleteContext() {
        validContext().validate();
    }

    @Test
    void validateRejectsBlankAccessKey() {
        Acl2PolicyContext context = validContext();
        context.setAccessKey(" ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, context::validate);

        assertTrue(ex.getMessage().contains("accessKey cannot be empty"));
    }

    @Test
    void validateRejectsBlankPolicyName() {
        Acl2PolicyContext context = validContext();
        context.setPolicyName(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, context::validate);

        assertTrue(ex.getMessage().contains("policyName cannot be empty"));
    }

    @Test
    void validateRejectsEmptyRulesList() {
        Acl2PolicyContext context = validContext();
        context.setRules(List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, context::validate);

        assertTrue(ex.getMessage().contains("rules list must not be empty"));
    }

    @Test
    void validateRejectsBlankResourcePatternInRule() {
        Acl2PolicyContext context = validContext();
        Acl2PolicyContext.AuthorizationRule rule =
                Acl2PolicyContext.AuthorizationRule.defaultAllowRule("order-*");
        rule.setResourcePattern(" ");
        context.setRules(List.of(rule));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, context::validate);

        assertTrue(ex.getMessage().contains("rules[0].resourcePattern cannot be empty"));
    }

    @Test
    void validateRejectsEmptyActionsInRule() {
        Acl2PolicyContext context = validContext();
        Acl2PolicyContext.AuthorizationRule rule =
                Acl2PolicyContext.AuthorizationRule.defaultAllowRule("order-*");
        rule.setActions(List.of());
        context.setRules(List.of(rule));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, context::validate);

        assertTrue(ex.getMessage().contains("rules[0].actions cannot be empty"));
    }

    @Test
    void validateDefaultsNullEffectToAllow() {
        Acl2PolicyContext context = validContext();
        Acl2PolicyContext.AuthorizationRule rule =
                Acl2PolicyContext.AuthorizationRule.defaultAllowRule("order-*");
        rule.setEffect(null);
        context.setRules(List.of(rule));

        context.validate();

        assertEquals("Allow", rule.getEffect());
    }

    @Test
    void validateRejectsUnknownEffect() {
        Acl2PolicyContext context = validContext();
        Acl2PolicyContext.AuthorizationRule rule =
                Acl2PolicyContext.AuthorizationRule.defaultAllowRule("order-*");
        rule.setEffect("Grant");
        context.setRules(List.of(rule));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, context::validate);

        assertTrue(ex.getMessage().contains("effect must be 'Allow' or 'Deny'"));
    }

    @Test
    void validateRejectsUnknownBoundType() {
        Acl2PolicyContext context = validContext();
        context.setBoundType("ROLE");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, context::validate);

        assertTrue(ex.getMessage().contains("boundType must be USER, GROUP, or SERVICE_ACCOUNT"));
    }

    @Test
    void validateAcceptsAllBoundTypes() {
        for (String boundType : List.of("USER", "GROUP", "SERVICE_ACCOUNT")) {
            Acl2PolicyContext context = validContext();
            context.setBoundType(boundType);
            context.validate();
        }
    }

    @Test
    void validateAllowsNullRules() {
        Acl2PolicyContext context = validContext();
        context.setRules(null);

        context.validate();
    }

    @Test
    void adminFlagDefaultsToFalse() {
        Acl2PolicyContext context = new Acl2PolicyContext();

        assertFalse(context.isIsAdmin());
        assertNull(context.getAccessKey());
    }
}
