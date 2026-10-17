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

import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.provider.InstanceCapability;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link InstanceCapabilitiesVO}, the capability contract of a single instance:
 * {@code instanceId} is the canonical, globally unique identifier (the instance name),
 * never the numeric primary key — and the capability list is the exact vocabulary the
 * frontend gates its actions on.
 */
class InstanceCapabilitiesVOTest {

    @Test
    void anInstanceCarriesItsIdentityAndCapabilities() {
        InstanceCapabilitiesVO capabilities = new InstanceCapabilitiesVO(
                "production-cluster",
                InstanceVendor.APACHE,
                InstanceType.PROXY_LOCAL,
                List.of(InstanceCapability.TOPIC_MANAGEMENT, InstanceCapability.MESSAGE_QUERY));

        assertThat(capabilities.instanceId()).isEqualTo("production-cluster");
        assertThat(capabilities.vendor()).isEqualTo(InstanceVendor.APACHE);
        assertThat(capabilities.accessType()).isEqualTo(InstanceType.PROXY_LOCAL);
        assertThat(capabilities.capabilities()).containsExactly(
                InstanceCapability.TOPIC_MANAGEMENT, InstanceCapability.MESSAGE_QUERY);
    }

    @Test
    void anInstanceWithoutCapabilitiesIsExpressible() {
        InstanceCapabilitiesVO bare = new InstanceCapabilitiesVO(
                "staging-cluster", InstanceVendor.TENCENT, InstanceType.CLOUD, List.of());

        assertThat(bare.capabilities()).isEmpty();
        assertThat(bare).isEqualTo(new InstanceCapabilitiesVO(
                "staging-cluster", InstanceVendor.TENCENT, InstanceType.CLOUD, List.of()));
        assertThat(bare).isNotEqualTo(new InstanceCapabilitiesVO(
                "staging-cluster", InstanceVendor.TENCENT, InstanceType.CLOUD,
                List.of(InstanceCapability.MESSAGE_QUERY)));
    }
}
