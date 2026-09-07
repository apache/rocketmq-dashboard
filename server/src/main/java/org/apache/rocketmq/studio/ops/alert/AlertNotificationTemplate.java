/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.alert;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renders the fixed, documented placeholders allowed in an alert notification. */
final class AlertNotificationTemplate {
    static final String DEFAULT_TEMPLATE = "[${level}] ${title} - ${description}\nLabels: ${labels}";
    private static final Set<String> RATIO_METRICS = Set.of(
            "broker.disk.usage_ratio",
            "broker.jvm.heap.usage_ratio",
            "broker.send_queue.usage_ratio");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z][A-Za-z0-9]*)}");

    private AlertNotificationTemplate() {
    }

    static String render(String template, SystemAlertVO alert, AlertRuleVO rule) {
        String source = hasText(template) ? template.trim() : DEFAULT_TEMPLATE;
        Map<String, String> replacements = values(alert, rule);
        Matcher matcher = PLACEHOLDER.matcher(source);
        StringBuilder result = new StringBuilder(source.length());
        while (matcher.find()) {
            String replacement = replacements.get(matcher.group(1));
            if (replacement == null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static Map<String, String> values(SystemAlertVO alert, AlertRuleVO rule) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("ruleName", text(rule == null ? null : rule.getName()));
        values.put("title", text(alert.getTitle()));
        values.put("description", text(alert.getDescription()));
        values.put("transition", text(alert.getTransition()));
        values.put("metric", text(rule == null ? null : rule.getMetric()));
        values.put("instanceId", text(alert.getInstanceId()));
        values.put("value", formattedValue(alert, rule));
        values.put("threshold", rule == null ? "" : String.valueOf(rule.getThreshold()));
        values.put("thresholdUnit", text(rule == null ? null : rule.getThresholdUnit()));
        values.put("level", text(alert.getLevel()));
        values.put("time", alert.getTime() == null ? "" : alert.getTime().toString());
        values.put("labels", formatLabels(alert.getLabels()));
        return values;
    }

    private static String formattedValue(SystemAlertVO alert, AlertRuleVO rule) {
        if (alert.getCurrentValue() == null) {
            return "";
        }
        if (rule != null && "%".equals(rule.getThresholdUnit()) && RATIO_METRICS.contains(rule.getMetric())) {
            return String.valueOf(alert.getCurrentValue() * 100);
        }
        return String.valueOf(alert.getCurrentValue());
    }

    private static String formatLabels(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) return "";
        return labels.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
