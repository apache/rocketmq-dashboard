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
package org.apache.rocketmq.studio.cluster.client;

import java.util.List;

/** Producer connection rows plus the coverage gaps encountered while scanning them. */
public record ProducerConnectionScanResult(
        List<ClientConnectionVO> connections,
        List<String> failedBrokers,
        List<String> failedProducerGroups) {

    public ProducerConnectionScanResult {
        connections = connections == null ? List.of() : List.copyOf(connections);
        failedBrokers = failedBrokers == null ? List.of() : List.copyOf(failedBrokers);
        failedProducerGroups = failedProducerGroups == null
                ? List.of() : List.copyOf(failedProducerGroups);
    }

    public static ProducerConnectionScanResult complete(List<ClientConnectionVO> connections) {
        return new ProducerConnectionScanResult(connections, List.of(), List.of());
    }

    public boolean complete() {
        return failedBrokers.isEmpty() && failedProducerGroups.isEmpty();
    }
}
