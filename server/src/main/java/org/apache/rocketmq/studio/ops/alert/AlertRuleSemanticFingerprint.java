/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.alert;

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
        StringBuilder value = new StringBuilder();
        append(value, rule.getDomain() == null ? AlertDomain.BUSINESS.name() : rule.getDomain().name());
        append(value, normalize(rule.getInstanceId()));
        append(value, normalize(rule.getMetric()));
        append(value, normalizeUpper(rule.getOperator()));
        append(value, normalizeThreshold(rule));
        append(value, normalizeLower(rule.getDuration()));
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
        if ("%".equals(rule.getThresholdUnit()) && RATIO_METRICS.contains(rule.getMetric())) {
            return rule.getThreshold() / 100D;
        }
        return rule.getThreshold();
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
