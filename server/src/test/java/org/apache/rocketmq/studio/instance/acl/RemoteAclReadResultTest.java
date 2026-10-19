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
package org.apache.rocketmq.studio.instance.acl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the remote ACL read of {@link RemoteAclReadResult}: policies per broker with the
 * per-broker failures, and isPartial() - some brokers answered and some did not - is the
 * verdict that makes the diff view show a partial badge instead of an error.
 */
class RemoteAclReadResultTest {

    @Test
    void aCompleteReadIsNotPartial() {
        RemoteAclReadResult result = new RemoteAclReadResult(
                "store",
                Map.of("broker-a", List.of(new RemoteAclPolicyVO("account-1", java.util.List.of()))),
                Map.of());
        assertThat(result.isPartial()).isFalse();
        assertThat(result.getSource()).isEqualTo("store");
        assertThat(result.getPoliciesByBroker()).containsKey("broker-a");
        assertThat(result.getFailuresByBroker()).isEmpty();
    }

    @Test
    void aFullyFailedReadIsNotPartialEither() {
        RemoteAclReadResult result = new RemoteAclReadResult(
                "store",
                Map.of(),
                Map.of("broker-a", "connection refused"));
        assertThat(result.isPartial()).isFalse();
        assertThat(result.getFailuresByBroker()).containsEntry("broker-a", "connection refused");
    }

    @Test
    void someAnswersAndSomeFailuresIsPartial() {
        RemoteAclReadResult result = new RemoteAclReadResult(
                "store",
                Map.of("broker-a", List.of(new RemoteAclPolicyVO("account-1", java.util.List.of()))),
                Map.of("broker-b", "timeout"));
        assertThat(result.isPartial()).isTrue();
    }
}
