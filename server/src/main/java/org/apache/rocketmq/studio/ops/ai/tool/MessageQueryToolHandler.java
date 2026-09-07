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
package org.apache.rocketmq.studio.ops.ai.tool;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Queries messages in a RocketMQ instance by topic, message id, business key or time range. Read-only
 * and safe for AI/MCP/CLI callers; delegates to the standard message service used by the web UI.
 */
@Component
@RequiredArgsConstructor
public class MessageQueryToolHandler implements ToolHandler {

    private static final String NAME = "rmq.message.query";

    private final MessageService messageService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Object execute(Map<String, Object> input) {
        String instanceId = (String) input.get("cluster");
        String topic = (String) input.get("topic");
        String msgId = (String) input.get("msgId");
        String tag = (String) input.get("tag");
        String key = (String) input.get("key");
        Long startTime = asLong(input.get("startTime"));
        Long endTime = asLong(input.get("endTime"));
        return messageService.queryMessages(instanceId, topic, msgId, tag, key, startTime, endTime).stream()
                .map(MessageQueryToolHandler::safeProjection)
                .toList();
    }

    private static Map<String, Object> safeProjection(MessageRecordVO message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("msgId", require(message.getMsgId(), "msgId"));
        result.put("topic", blankIfNull(message.getTopic()));
        result.put("tag", blankIfNull(message.getTag()));
        result.put("key", blankIfNull(message.getKey()));
        result.put("storeTime", message.getStoreTime());
        result.put("storeHost", blankIfNull(message.getStoreHost()));
        result.put("bornHost", blankIfNull(message.getBornHost()));
        result.put("body", blankIfNull(message.getBody()));
        result.put("bodyEncoding", blankIfNull(message.getBodyEncoding()));
        result.put("bodyTruncated", message.isBodyTruncated());
        result.put("size", message.getSize());
        return result;
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return toEpochMillis(number);
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(
                    400, "startTime and endTime must be integer epoch-milliseconds");
        }
    }

    /**
     * Convert a caller-supplied numeric timestamp to epoch-milliseconds without silent
     * overflow. Jackson may deliver out-of-range values as {@link BigInteger} (or as a
     * floating-point number); {@code Number.longValue()} would silently wrap these into a
     * nonsense, possibly negative, timestamp. Reject values that do not fit instead.
     */
    private static long toEpochMillis(Number number) {
        if (number instanceof BigInteger bigInteger) {
            try {
                return bigInteger.longValueExact();
            } catch (ArithmeticException ex) {
                throw new BusinessException(
                        400, "startTime and endTime must fit in a 64-bit epoch-milliseconds value");
            }
        }
        if (number instanceof Double || number instanceof Float) {
            double doubleValue = number.doubleValue();
            if (!Double.isFinite(doubleValue) || doubleValue > Long.MAX_VALUE
                    || doubleValue < Long.MIN_VALUE) {
                throw new BusinessException(
                        400, "startTime and endTime must fit in a 64-bit epoch-milliseconds value");
            }
        }
        return number.longValue();
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Message " + field + " is unavailable");
        }
        return value;
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
