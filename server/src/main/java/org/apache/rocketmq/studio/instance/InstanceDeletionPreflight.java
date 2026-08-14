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
package org.apache.rocketmq.studio.instance;

/** Result of checking remote managed resources before deleting an instance registration. */
final class InstanceDeletionPreflight {

    private final int topicCount;
    private final int consumerGroupCount;
    private final String failureSummary;

    private InstanceDeletionPreflight(int topicCount, int consumerGroupCount, String failureSummary) {
        this.topicCount = topicCount;
        this.consumerGroupCount = consumerGroupCount;
        this.failureSummary = failureSummary;
    }

    static InstanceDeletionPreflight verified(int topicCount, int consumerGroupCount) {
        return new InstanceDeletionPreflight(topicCount, consumerGroupCount, null);
    }

    static InstanceDeletionPreflight unavailable(RuntimeException failure) {
        return new InstanceDeletionPreflight(0, 0, summarizeFailure(failure));
    }

    boolean isUnavailable() {
        return failureSummary != null;
    }

    boolean hasManagedResources() {
        return !isUnavailable() && (topicCount > 0 || consumerGroupCount > 0);
    }

    int topicCount() {
        return topicCount;
    }

    int consumerGroupCount() {
        return consumerGroupCount;
    }

    String failureSummary() {
        return failureSummary;
    }

    private static String summarizeFailure(RuntimeException failure) {
        String type = failure.getClass().getSimpleName();
        return type.isBlank() ? RuntimeException.class.getSimpleName() : type;
    }
}
