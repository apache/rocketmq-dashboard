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
package org.apache.rocketmq.studio.cluster.metrics;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "studio.alerting")
public class AlertingProperties {
    private String collectionInterval = "PT30S";
    /** Maximum number of instances collected concurrently during one pass. */
    private int collectionParallelism = 4;
    /** Maximum time that one instance may consume during a collection pass. */
    private String collectionTimeout = "PT15S";
    /**
     * A shared database lease prevents multiple Studio replicas from evaluating the same
     * native samples and emitting duplicate alert events.
     */
    private String collectionLeaseDuration = "PT1M";
    /** Maximum time between lease heartbeats; the scheduler clamps this below the lease duration. */
    private String collectionLeaseRenewalInterval = "PT15S";
    /** Retain short-lived diagnostic samples without allowing the snapshot table to grow indefinitely. */
    private String snapshotRetention = "PT24H";
    /** Retain terminal notification deliveries before deleting them from the outbox. */
    private String notificationRetention = "P30D";
    /** Maximum number of terminal notification deliveries deleted per cleanup batch. */
    private int notificationCleanupBatchSize = 500;
    /** Maximum number of cleanup batches executed during one scheduled pass. */
    private int notificationCleanupMaxBatches = 10;
    /** How long a notification dispatcher may hold an outbox row without renewing it. */
    private String notificationClaimTimeout = "PT1M";
    /** How often an in-flight notification claim is renewed. */
    private String notificationClaimRenewalInterval = "PT20S";
    /** Bounded daemon threads used for notification claim renewal. */
    private int notificationHeartbeatThreads = 2;
}
