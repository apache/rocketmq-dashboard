/*
 * Licensed to the Apache Software Foundation (ASF) under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Acl2PolicyContextTest {

    private static Acl2PolicyContext validPolicy() {
        Acl2PolicyContext policy = new Acl2PolicyContext();
        policy.setAccessKey("ak-1");
        policy.setPolicyName("orders-rw");
        return policy;
    }

    @Test
    void aMinimalPolicyWithOnlyTheRequiredFieldsIsValidates() {
        assertThatCode(() -> validPolicy().validate()).doesNotThrowAnyException();
    }

    @Test
    void aBlankAccessKeyIsRejected() {
        Acl2PolicyContext policy = validPolicy();
        policy.setAccessKey("   ");

        assertThatThrownBy(policy::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accessKey cannot be empty");
    }

    @Test
    void aBlankPolicyNameIsRejected() {
        Acl2PolicyContext policy = validPolicy();
        policy.setPolicyName("");

        assertThatThrownBy(policy::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policyName cannot be empty");
    }

    @Test
    void anEmptyRulesListIsRejectedWhileANullOneIsAllowed() {
        Acl2PolicyContext empty = validPolicy();
        empty.setRules(List.of());
        assertThatThrownBy(empty::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rules list must not be empty");

        Acl2PolicyContext absent = validPolicy();
        assertThatCode(absent::validate).doesNotThrowAnyException();
    }

    @Test
    void aRuleWithoutAResourcePatternIsRejectedWithItsIndex() {
        Acl2PolicyContext policy = validPolicy();
        Acl2PolicyContext.AuthorizationRule rule = new Acl2PolicyContext.AuthorizationRule();
        rule.setActions(List.of("READ"));
        policy.setRules(List.of(rule));

        assertThatThrownBy(policy::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rules[0].resourcePattern cannot be empty");
    }

    @Test
    void aRuleWithoutActionsIsRejectedWithItsIndex() {
        Acl2PolicyContext policy = validPolicy();
        Acl2PolicyContext.AuthorizationRule rule = new Acl2PolicyContext.AuthorizationRule();
        rule.setResourcePattern("topic/orders/*");
        policy.setRules(List.of(rule));

        assertThatThrownBy(policy::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rules[0].actions cannot be empty");
    }

    @Test
    void anUnknownEffectIsRejectedAndTheCheckIsCaseInsensitive() {
        Acl2PolicyContext policy = validPolicy();
        Acl2PolicyContext.AuthorizationRule rule = Acl2PolicyContext.AuthorizationRule.defaultAllowRule("t/*");
        rule.setEffect("Maybe");
        policy.setRules(List.of(rule));

        assertThatThrownBy(policy::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rules[0].effect must be 'Allow' or 'Deny', got: Maybe");
    }

    @Test
    void aMissingEffectDefaultsToAllowAndIsWrittenBack() {
        Acl2PolicyContext policy = validPolicy();
        Acl2PolicyContext.AuthorizationRule rule = Acl2PolicyContext.AuthorizationRule.defaultAllowRule("t/*");
        rule.setEffect(null);
        policy.setRules(List.of(rule));

        policy.validate();

        assertThat(rule.getEffect()).isEqualTo("Allow");
    }

    @Test
    void lowerCaseEffectsAreAcceptedVerbatim() {
        Acl2PolicyContext policy = validPolicy();
        Acl2PolicyContext.AuthorizationRule rule = Acl2PolicyContext.AuthorizationRule.defaultAllowRule("t/*");
        rule.setEffect("deny");
        policy.setRules(List.of(rule));

        assertThatCode(policy::validate).doesNotThrowAnyException();
    }

    @Test
    void anUnknownBoundTypeIsRejectedWithAllThreeLegalValues() {
        Acl2PolicyContext policy = validPolicy();
        policy.setBoundType("ROLE");

        assertThatThrownBy(policy::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("boundType must be USER, GROUP, or SERVICE_ACCOUNT, got: ROLE");
    }

    @Test
    void everyLegalBoundTypePasses() {
        for (String type : List.of("USER", "GROUP", "SERVICE_ACCOUNT")) {
            Acl2PolicyContext policy = validPolicy();
            policy.setBoundType(type);
            assertThatCode(policy::validate).as("boundType %s", type).doesNotThrowAnyException();
        }
    }

    @Test
    void theDefaultAllowRuleCarriesTheBackwardCompatibleShape() {
        Acl2PolicyContext.AuthorizationRule rule = Acl2PolicyContext.AuthorizationRule.defaultAllowRule("topic/*");

        assertThat(rule.getResourcePattern()).isEqualTo("topic/*");
        assertThat(rule.getActions()).containsExactly("READ", "WRITE");
        assertThat(rule.getEffect()).isEqualTo("Allow");
        assertThat(rule.getPriority()).isEqualTo(100);
    }

    @Test
    void theDenyAllRuleHasTheHighestPrecedenceAndEveryAction() {
        Acl2PolicyContext.AuthorizationRule rule = Acl2PolicyContext.AuthorizationRule.denyAllRule();

        assertThat(rule.getResourcePattern()).isEqualTo("**");
        assertThat(rule.getActions()).containsExactly("*");
        assertThat(rule.getEffect()).isEqualTo("Deny");
        // Priority 0 sorts before the default 100: the deny wins on conflict.
        assertThat(rule.getPriority()).isZero();
    }

    @Test
    void theAdminFlagRoundTripsThroughItsExplicitAccessors() {
        Acl2PolicyContext policy = validPolicy();

        policy.setIsAdmin(true);

        assertThat(policy.isIsAdmin()).isTrue();
    }
}
