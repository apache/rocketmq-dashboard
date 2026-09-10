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
 * Unit tests for {@link RemoteAclReadResult}, the provider-backed ACL 2.0 read result that
 * flags a read as partial only when some brokers answered with policies while others failed.
 */
class RemoteAclReadResultTest {

    private static RemoteAclReadResult result(Map<String, List<RemoteAclPolicyVO>> policies,
            Map<String, String> failures) {
        return RemoteAclReadResult.builder()
                .source("broker")
                .policiesByBroker(policies)
                .failuresByBroker(failures)
                .build();
    }

    @Test
    void aCompleteReadIsNotPartial() {
        assertThat(result(Map.of("broker-1", List.<RemoteAclPolicyVO>of()), Map.of())
                .isPartial()).isFalse();
    }

    @Test
    void aReadWithOnlyFailuresOrOnlyPoliciesIsNotPartial() {
        assertThat(result(Map.of(), Map.of("broker-1", "timeout")).isPartial()).isFalse();
        assertThat(result(Map.of("broker-1", List.<RemoteAclPolicyVO>of()), Map.of())
                .isPartial()).isFalse();
    }

    @Test
    void aMixedReadIsPartialAndKeepsItsSourceProvenance() {
        RemoteAclReadResult result = result(
                Map.of("broker-1", List.<RemoteAclPolicyVO>of()),
                Map.of("broker-2", "timeout"));

        assertThat(result.isPartial()).isTrue();
        assertThat(result.getSource()).isEqualTo("broker");
        assertThat(result.getPoliciesByBroker()).containsKey("broker-1");
        assertThat(result.getFailuresByBroker()).containsKey("broker-2");
    }
}
