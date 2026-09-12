/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.cluster.broker;

import java.time.Instant;
import java.util.List;

public record ControllerReplicaSnapshot(String brokerName, String configSource, String mode,
        Instant sampledAt, List<Node> controllers, String agreement, Membership membership,
        String membershipError) {
    public record Node(String address, String group, String leaderId, String leaderAddress,
            Boolean leader, String peers, String error) { }

    public record Membership(String discoveryAddress, String masterBrokerId, String masterAddress,
            int masterEpoch, int syncStateSetEpoch, List<Replica> replicas) { }

    public record Replica(String brokerId, String address, boolean inSyncSet, Boolean alive) { }
}
