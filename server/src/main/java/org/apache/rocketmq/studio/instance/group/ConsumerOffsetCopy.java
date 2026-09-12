/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.group;

import java.util.List;

public final class ConsumerOffsetCopy {
    private ConsumerOffsetCopy() { }

    public record Expected(String brokerName, String brokerAddr, int queueId,
            String sourceOffset, String targetOffset) { }

    public record Queue(Expected expected, String minOffset, String maxOffset) { }

    public record Preview(String topic, String sourceGroup, String targetGroup, List<Queue> queues) { }

    public record Request(String instanceId, String topic, String sourceGroup, String targetGroup,
            List<Expected> expected) { }

    public enum Status { CONFIRMED, UNCHANGED, UNKNOWN, NOT_ATTEMPTED }

    public record Outcome(Expected queue, Status status, String observedOffset) { }

    public record Receipt(List<Outcome> queues) { }
}
