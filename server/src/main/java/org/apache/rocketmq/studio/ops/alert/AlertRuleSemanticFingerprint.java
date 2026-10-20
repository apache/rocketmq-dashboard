/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.alert;

import org.apache.rocketmq.studio.common.exception.BusinessException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable identity for the fields that determine when a rule evaluates to true. */
final class AlertRuleSemanticFingerprint {
    private static final java.util.Set<String> RATIO_METRICS = java.util.Set.of(
            "broker.disk.usage_ratio",
            "broker.jvm.heap.usage_ratio",
            "broker.send_queue.usage_ratio");

    private AlertRuleSemanticFingerprint() {
    }

    static String of(AlertRuleVO rule) {
        return of(rule, normalizeLower(rule.getDuration()));
    }

    /** Runtime equivalence only; keep the persisted unique fingerprint's existing format. */
    static String evaluationIdentity(AlertRuleVO rule) {
        String duration = normalizeLower(rule.getDuration());
        try {
            duration = AlertRuleDuration.parse(duration).toString();
        } catch (BusinessException invalidLegacyDuration) {
            // Legacy external rules may contain invalid text; comparing identities must still work.
        }
        return of(rule, duration);
    }

    private static String of(AlertRuleVO rule, String duration) {
        StringBuilder value = new StringBuilder();
        append(value, rule.getDomain() == null ? AlertDomain.BUSINESS.name() : rule.getDomain().name());
        append(value, normalize(rule.getInstanceId()));
        append(value, normalize(rule.getMetric()));
        append(value, normalizeUpper(rule.getOperator()));
        append(value, normalizeThreshold(rule));
        append(value, duration);
        append(value, normalizeUpper(defaultIfBlank(rule.getAggregation(), "LAST")));
        append(value, Integer.toString(Math.max(0, rule.getWindowSeconds())));
        append(value, normalize(rule.getBrokerName()));
        append(value, normalize(rule.getClusterName()));
        append(value, normalize(rule.getConsumerGroup()));
        append(value, normalize(rule.getTopic()));
        append(value, Integer.toString(Math.max(1, rule.getConsecutiveSamples())));
        return sha256(value.toString());
    }

    private static void append(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeUpper(String value) {
        return normalize(value).toUpperCase(java.util.Locale.ROOT);
    }

    private static String normalizeLower(String value) {
        return normalize(value).toLowerCase(java.util.Locale.ROOT);
    }

    private static String defaultIfBlank(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? fallback : normalized;
    }

    static double normalizedThreshold(AlertRuleVO rule) {
        return isPercentRatioRule(rule) ? rule.getThreshold() / 100D : rule.getThreshold();
    }

    /** True when the rule compares a stored ratio fraction against a percent-unit threshold. */
    static boolean isPercentRatioRule(AlertRuleVO rule) {
        return "%".equals(rule.getThresholdUnit()) && RATIO_METRICS.contains(normalize(rule.getMetric()));
    }

    /**
     * Scales a stored ratio fraction to the rule's percent unit, so the currentValue written onto
     * alert events and rule-test samples reads in the same unit as the configured threshold.
     * Scaling through BigDecimal keeps the exact decimal form: plain double arithmetic turns a
     * stored 0.29 into 28.999999999999996.
     */
    static Double displayValue(AlertRuleVO rule, Double value) {
        if (value == null || !isPercentRatioRule(rule) || !Double.isFinite(value)) {
            return value;
        }
        return BigDecimal.valueOf(value).movePointRight(2).doubleValue();
    }

    private static String normalizeThreshold(AlertRuleVO rule) {
        double threshold = normalizedThreshold(rule);
        if (threshold == 0D) {
            return "0";
        }
        return BigDecimal.valueOf(threshold).stripTrailingZeros().toPlainString();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
