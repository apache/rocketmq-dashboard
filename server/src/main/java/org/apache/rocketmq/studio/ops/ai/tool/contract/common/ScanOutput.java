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
package org.apache.rocketmq.studio.ops.ai.tool.contract.common;

import java.util.List;

/**
 * Output of a platform-level scan that aggregates several Instances (decision 26): what it
 * collected, whether every scanned Instance answered, and which ones did not. A scan that skips a
 * failing Instance without saying so reads as a complete inventory - the reason the
 * producer-connection scan reports {@code complete} and its failed scopes as well.
 */
public record ScanOutput<T>(List<T> items, Scan scan) {

    public static <T> ScanOutput<T> of(List<T> items, List<String> failedInstances) {
        List<String> failed = failedInstances == null ? List.of() : List.copyOf(failedInstances);
        return new ScanOutput<>(items, new Scan(failed.isEmpty(), failed));
    }

    /** Whether every scanned Instance answered, and the ones that did not. */
    public record Scan(boolean complete, List<String> failedInstances) {
    }
}
