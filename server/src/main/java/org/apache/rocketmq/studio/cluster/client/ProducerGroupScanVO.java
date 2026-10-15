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

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Producer group suggestions for the selector, with the coverage gap that produced them. A selector
 * that silently drops the groups of an unreachable broker otherwise looks like a complete list.
 */
@Data
@NoArgsConstructor
public class ProducerGroupScanVO {
    private List<String> groups = List.of();
    private boolean complete = true;
    private List<String> failedBrokers = List.of();

    public ProducerGroupScanVO(List<String> groups, boolean complete, List<String> failedBrokers) {
        this.groups = groups == null ? List.of() : List.copyOf(groups);
        this.complete = complete;
        this.failedBrokers = failedBrokers == null ? List.of() : List.copyOf(failedBrokers);
    }

    public static ProducerGroupScanVO from(ProducerGroupScanResult scan) {
        return new ProducerGroupScanVO(scan.groups(), scan.complete(), scan.failedBrokers());
    }
}
