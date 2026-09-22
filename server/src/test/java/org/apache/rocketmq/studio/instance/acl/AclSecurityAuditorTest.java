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
package org.apache.rocketmq.studio.instance.acl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AclSecurityAuditorTest {

    private AclSecurityAuditor auditor;

    @BeforeEach
    void setUp() {
        auditor = new AclSecurityAuditor();
    }

    @Test
    void testAuditRulesEmpty() {
        AclAuditReportVO report = auditor.auditRules("inst-001", List.of());
        assertNotNull(report);
        assertEquals("inst-001", report.getInstanceId());
        assertEquals(0, report.getTotalRulesAudited());
        assertTrue(report.getFindings().isEmpty());
    }

    @Test
    void testAuditRulesWildcardExposure() {
        AclRuleVO r1 = AclRuleVO.builder()
                .id("rule-1")
                .principal("user-dev")
                .resource("TopicTest")
                .sourceIp("*")
                .decision("ALLOW")
                .build();
        AclRuleVO r2 = AclRuleVO.builder()
                .id("rule-2")
                .principal("user-prod")
                .resource("TopicOrder")
                .sourceIp("")
                .decision("ALLOW")
                .build();

        AclAuditReportVO report = auditor.auditRules("inst-001", List.of(r1, r2));
        assertNotNull(report);
        assertEquals(2, report.getTotalRulesAudited());
        assertEquals(2, report.getWildcardSourceIpCount());
        assertEquals(2, report.getFindings().size());
        assertTrue(report.getFindings().stream().allMatch(f -> "WILDCARD_EXPOSURE".equals(f.getCategory())));
    }

    @Test
    void testAuditRulesOverlappingSubnet() {
        AclRuleVO r1 = AclRuleVO.builder()
                .id("rule-10")
                .principal("user-1")
                .resource("OrderTopic")
                .sourceIp("192.168.1.1-50")
                .decision("ALLOW")
                .build();
        AclRuleVO r2 = AclRuleVO.builder()
                .id("rule-20")
                .principal("user-2")
                .resource("OrderTopic")
                .sourceIp("192.168.1.20-80")
                .decision("ALLOW")
                .build();

        AclAuditReportVO report = auditor.auditRules("inst-001", List.of(r1, r2));
        assertNotNull(report);
        assertEquals(1, report.getOverlappingSubnetCount());
        assertEquals(1, report.getFindings().size());
        assertEquals("OVERLAPPING_SUBNET", report.getFindings().get(0).getCategory());
        assertEquals("rule-10", report.getFindings().get(0).getRuleId());
        assertEquals("rule-20", report.getFindings().get(0).getConflictingExpressions().get(0));
    }

    @Test
    void testAuditRulesSyntaxWarningAndSensitiveResource() {
        AclRuleVO r1 = AclRuleVO.builder()
                .id("rule-sens")
                .principal("admin")
                .resource("%RETRY%GroupA")
                .sourceIp("192.168.1.100")
                .decision("ALLOW")
                .build();
        AclRuleVO r2 = AclRuleVO.builder()
                .id("rule-bad-syntax")
                .principal("user-test")
                .resource("NormalTopic")
                .sourceIp("999.999.1.1/wrong")
                .decision("ALLOW")
                .build();

        AclAuditReportVO report = auditor.auditRules("inst-001", List.of(r1, r2));
        assertNotNull(report);
        assertEquals(1, report.getSyntaxWarningCount());
        boolean hasSensitiveFinding = report.getFindings().stream()
                .anyMatch(f -> "SENSITIVE_RESOURCE_ACCESS".equals(f.getCategory()));
        boolean hasSyntaxFinding = report.getFindings().stream()
                .anyMatch(f -> "SYNTAX_WARNING".equals(f.getCategory()));
        assertTrue(hasSensitiveFinding);
        assertTrue(hasSyntaxFinding);
    }

    @Test
    void testParseIntervals() {
        List<AclSecurityAuditor.IpInterval> intervals =
                AclSecurityAuditor.parseIntervals("10.0.0.1, 192.168.1.1-20, 172.16.1.*");
        assertEquals(3, intervals.size());
        assertTrue(intervals.get(0).start <= intervals.get(0).end);
        assertTrue(intervals.get(1).start < intervals.get(1).end);
        assertTrue(intervals.get(2).start < intervals.get(2).end);
    }
}
