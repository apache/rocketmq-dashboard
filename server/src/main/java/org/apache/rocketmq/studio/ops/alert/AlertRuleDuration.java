/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.alert;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the compact duration syntax accepted by alert-rule requests. */
final class AlertRuleDuration {
    private static final Pattern PART = Pattern.compile("(\\d+)(ms|s|m|h|d|w|y)");

    private AlertRuleDuration() {
    }

    static Duration parse(String value) {
        if (!StringUtils.hasText(value)) {
            return Duration.ZERO;
        }
        Matcher matcher = PART.matcher(value.trim());
        Duration result = Duration.ZERO;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) {
                throw invalid(value);
            }
            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException error) {
                throw invalid(value);
            }
            try {
                result = result.plus(toDuration(amount, matcher.group(2), value));
            } catch (ArithmeticException error) {
                throw invalid(value);
            }
            end = matcher.end();
        }
        if (end != value.trim().length()) {
            throw invalid(value);
        }
        return result;
    }

    private static BusinessException invalid(String value) {
        return new BusinessException(400, "Invalid alert duration: " + value);
    }

    private static Duration toDuration(long amount, String unit, String source) {
        return switch (unit) {
            case "ms" -> Duration.ofMillis(amount);
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            case "w" -> Duration.ofDays(Math.multiplyExact(amount, 7));
            case "y" -> Duration.ofDays(Math.multiplyExact(amount, 365));
            default -> throw invalid(source);
        };
    }
}
