/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.group;

import java.util.List;

public final class ConsumerRequestMode {
    private ConsumerRequestMode() { }

    public record Broker(String brokerName, String address, String mode, int popShareQueueNum,
            boolean explicit, String serverLoadBalancerEnable) { }

    public record Preview(String topic, String group, List<Broker> brokers) { }

    public record Request(String instanceId, String topic, String group, String mode,
            int popShareQueueNum, List<Broker> expected) { }

    public record Outcome(Broker before, String status, Broker observed) { }

    public record Receipt(List<Outcome> brokers) { }
}
