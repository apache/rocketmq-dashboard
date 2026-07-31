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
package org.apache.rocketmq.studio.cluster.broker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Outcome of a live connectivity probe against a RocketMQ NameServer.
 *
 * <p>Returned by the {@code POST /api/clusters/test-connection} endpoint so the UI can confirm a
 * NameServer is reachable and preview the topology it exposes before the cluster is registered.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterProbeResult {

    /** Whether the NameServer accepted the connection and returned cluster info. */
    private boolean connected;

    /** The probed NameServer address list. */
    private String namesrvAddr;

    /** First cluster name reported by the NameServer, if any. */
    private String clusterName;

    /** Number of brokers registered with the cluster. */
    private int brokerCount;

    /** Names of the registered brokers. */
    private List<String> brokerNames;

    /** Round-trip time of the probe in milliseconds. */
    private long elapsedMillis;

    /** Human-readable outcome message. */
    private String message;
}
