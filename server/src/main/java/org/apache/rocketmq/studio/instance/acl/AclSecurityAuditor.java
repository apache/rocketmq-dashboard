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

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.studio.instance.acl.AclAuditReportVO.AclAuditFindingVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class AclSecurityAuditor {

    private static final Set<String> SYSTEM_SENSITIVE_PREFIXES = Set.of(
            "CID_RMQ_SYS_",
            "TBW102",
            "BenchmarkTest",
            "OFFSET_MOVED_EVENT",
            "%RETRY%",
            "%DLQ%"
    );

    public AclAuditReportVO auditRules(String instanceId, List<AclRuleVO> rules) {
        AclAuditReportVO report = AclAuditReportVO.builder()
                .instanceId(instanceId)
                .totalRulesAudited(rules == null ? 0 : rules.size())
                .findings(new ArrayList<>())
                .build();

        if (rules == null || rules.isEmpty()) {
            return report;
        }

        int wildcardCount = 0;
        int overlapCount = 0;
        int syntaxWarningCount = 0;

        List<ParsedRule> parsedRules = new ArrayList<>();

        for (AclRuleVO rule : rules) {
            String sourceIp = rule.getSourceIp();
            String principal = rule.getPrincipal();
            String resource = rule.getResource();
            String ruleId = rule.getId();

            if (isSensitiveResource(resource) && "ALLOW".equalsIgnoreCase(rule.getDecision())) {
                report.getFindings().add(AclAuditFindingVO.builder()
                        .ruleId(ruleId)
                        .principal(principal)
                        .resource(resource)
                        .severity("HIGH")
                        .category("SENSITIVE_RESOURCE_ACCESS")
                        .description("Allow policy on sensitive internal topic/group: " + resource)
                        .sourceIpExpression(sourceIp)
                        .build());
            }

            if (StringUtils.isBlank(sourceIp) || isWildcard(sourceIp)) {
                wildcardCount++;
                report.getFindings().add(AclAuditFindingVO.builder()
                        .ruleId(ruleId)
                        .principal(principal)
                        .resource(resource)
                        .severity("CRITICAL")
                        .category("WILDCARD_EXPOSURE")
                        .description("Unrestricted remote network access granted via wildcard or empty source IP: "
                                + (StringUtils.isBlank(sourceIp) ? "<EMPTY>" : sourceIp))
                        .sourceIpExpression(sourceIp)
                        .build());
            } else if (!PlainAclRemoteAddressValidator.isValid(sourceIp)) {
                syntaxWarningCount++;
                report.getFindings().add(AclAuditFindingVO.builder()
                        .ruleId(ruleId)
                        .principal(principal)
                        .resource(resource)
                        .severity("HIGH")
                        .category("SYNTAX_WARNING")
                        .description("Source IP does not conform to RocketMQ plain ACL address grammar: " + sourceIp)
                        .sourceIpExpression(sourceIp)
                        .build());
            } else {
                List<IpInterval> intervals = parseIntervals(sourceIp);
                if (!intervals.isEmpty()) {
                    parsedRules.add(new ParsedRule(rule, intervals));
                }
            }
        }

        // Detect mutual overlap across rules protecting the same resource
        for (int i = 0; i < parsedRules.size(); i++) {
            for (int j = i + 1; j < parsedRules.size(); j++) {
                ParsedRule r1 = parsedRules.get(i);
                ParsedRule r2 = parsedRules.get(j);

                if (!StringUtils.equals(r1.rule.getResource(), r2.rule.getResource())) {
                    continue;
                }

                if (hasOverlap(r1.intervals, r2.intervals)) {
                    overlapCount++;
                    report.getFindings().add(AclAuditFindingVO.builder()
                            .ruleId(r1.rule.getId())
                            .principal(r1.rule.getPrincipal())
                            .resource(r1.rule.getResource())
                            .severity("MEDIUM")
                            .category("OVERLAPPING_SUBNET")
                            .description(String.format("Overlapping IP subnet definition with rule [%s] for resource [%s]",
                                    r2.rule.getId(), r1.rule.getResource()))
                            .sourceIpExpression(r1.rule.getSourceIp())
                            .conflictingExpressions(Collections.singletonList(r2.rule.getSourceIp()))
                            .build());
                }
            }
        }

        report.setWildcardSourceIpCount(wildcardCount);
        report.setOverlappingSubnetCount(overlapCount);
        report.setSyntaxWarningCount(syntaxWarningCount);
        return report;
    }

    private boolean isSensitiveResource(String resource) {
        if (resource == null) {
            return false;
        }
        for (String prefix : SYSTEM_SENSITIVE_PREFIXES) {
            if (resource.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWildcard(String expr) {
        if (expr == null) {
            return true;
        }
        String trimmed = expr.trim();
        return "*".equals(trimmed)
                || "*.*.*.*".equals(trimmed)
                || "*:*:*:*:*:*:*:*".equals(trimmed)
                || "0.0.0.0/0".equals(trimmed);
    }

    private boolean hasOverlap(List<IpInterval> l1, List<IpInterval> l2) {
        for (IpInterval i1 : l1) {
            for (IpInterval i2 : l2) {
                if (i1.start <= i2.end && i2.start <= i1.end) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<IpInterval> parseIntervals(String expression) {
        List<IpInterval> result = new ArrayList<>();
        if (expression == null || expression.isBlank()) {
            return result;
        }
        String[] parts = expression.split(",", -1);
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.matches("^(\\d{1,3}\\.){3}\\d{1,3}$")) {
                long val = ipv4ToLong(trimmed);
                result.add(new IpInterval(val, val));
            } else if (trimmed.contains("-") && trimmed.split("\\.").length == 4) {
                // e.g. 192.168.1.1-50
                String[] segments = trimmed.split("\\.");
                String lastSegment = segments[3];
                if (lastSegment.contains("-")) {
                    String[] rangeParts = lastSegment.split("-");
                    try {
                        int startLast = Integer.parseInt(rangeParts[0]);
                        int endLast = Integer.parseInt(rangeParts[1]);
                        String prefix = segments[0] + "." + segments[1] + "." + segments[2] + ".";
                        long s = ipv4ToLong(prefix + startLast);
                        long e = ipv4ToLong(prefix + endLast);
                        result.add(new IpInterval(Math.min(s, e), Math.max(s, e)));
                    } catch (Exception ignored) {
                    }
                }
            } else if (trimmed.endsWith(".*")) {
                // e.g. 192.168.1.*
                String prefix = trimmed.substring(0, trimmed.length() - 2);
                String[] segments = prefix.split("\\.");
                if (segments.length == 3) {
                    long s = ipv4ToLong(prefix + ".0");
                    long e = ipv4ToLong(prefix + ".255");
                    result.add(new IpInterval(s, e));
                }
            }
        }
        return result;
    }

    private static long ipv4ToLong(String ip) {
        String[] octets = ip.split("\\.");
        long res = 0;
        for (int i = 0; i < 4; i++) {
            res = (res << 8) | (Integer.parseInt(octets[i]) & 0xFF);
        }
        return res;
    }

    public static class IpInterval {
        final long start;
        final long end;

        public IpInterval(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    private static class ParsedRule {
        final AclRuleVO rule;
        final List<IpInterval> intervals;

        ParsedRule(AclRuleVO rule, List<IpInterval> intervals) {
            this.rule = rule;
            this.intervals = intervals;
        }
    }
}
