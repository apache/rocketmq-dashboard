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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstanceDeletionPreflightTest {

    @Test
    void unavailableShouldNotCopyProviderMessagesIntoAuditDetails() {
        InstanceDeletionPreflight preflight = InstanceDeletionPreflight.unavailable(
                new IllegalStateException("request to https://user:pass@broker failed\n"
                        + "token=top-secret password: hunter2"));

        assertThat(preflight.failureSummary()).isEqualTo("IllegalStateException");
    }

    @Test
    void unavailableShouldUseABoundedFallbackForAnonymousExceptions() {
        InstanceDeletionPreflight preflight = InstanceDeletionPreflight.unavailable(
                new RuntimeException("secret provider text") { });

        assertThat(preflight.failureSummary()).isEqualTo("RuntimeException");
    }

    @Test
    void verifiedShouldExposeManagedResourceCounts() {
        InstanceDeletionPreflight preflight = InstanceDeletionPreflight.verified(2, 3);

        assertThat(preflight.isUnavailable()).isFalse();
        assertThat(preflight.hasManagedResources()).isTrue();
        assertThat(preflight.topicCount()).isEqualTo(2);
        assertThat(preflight.consumerGroupCount()).isEqualTo(3);
    }
}
