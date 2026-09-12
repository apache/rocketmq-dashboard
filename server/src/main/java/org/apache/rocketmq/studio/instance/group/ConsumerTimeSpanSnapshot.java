/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.group;

import java.time.Instant;
import java.util.List;

public record ConsumerTimeSpanSnapshot(String topic, String group, Instant sampledAt, List<Queue> queues) {
    public record Queue(String brokerName, int queueId, String minOffset, String maxOffset,
            String consumerOffset, String earliestTime, String latestTime, String cursorTime,
            CursorState cursorState, boolean spanAvailable) {
    }

    public enum CursorState {
        RECORDED_OFFSET_REFERENCE,
        EARLIEST_MESSAGE_FALLBACK,
        OUTSIDE_RETAINED_RANGE,
        OFFSET_UNAVAILABLE,
        TIMESTAMP_UNAVAILABLE
    }
}
