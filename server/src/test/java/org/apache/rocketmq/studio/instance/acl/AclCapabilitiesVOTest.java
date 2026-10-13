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

import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link AclCapabilitiesVO}, the answer to "are ACL operations backed by the selected
 * instance": the remote read and remote write verdicts are separate — a provider can answer
 * one and not the other — and they travel with the vendor, the access type and where the
 * state came from, so the ACL page can explain its verdict instead of guessing.
 */
class AclCapabilitiesVOTest {

    @Test
    void aFullyBackedInstanceAnswersBothVerdicts() {
        AclCapabilitiesVO capabilities = new AclCapabilitiesVO(
                7L, InstanceVendor.APACHE, InstanceType.PROXY_LOCAL, "instance", true, true);

        assertThat(capabilities.instanceId()).isEqualTo(7L);
        assertThat(capabilities.vendor()).isEqualTo(InstanceVendor.APACHE);
        assertThat(capabilities.instanceType()).isEqualTo(InstanceType.PROXY_LOCAL);
        assertThat(capabilities.stateSource()).isEqualTo("instance");
        assertThat(capabilities.remoteReadSupported()).isTrue();
        assertThat(capabilities.remoteWriteSupported()).isTrue();
    }

    @Test
    void theReadAndWriteVerdictsAreIndependent() {
        AclCapabilitiesVO readOnly = new AclCapabilitiesVO(
                8L, InstanceVendor.ALIYUN, InstanceType.CLOUD, "provider", true, false);
        assertThat(readOnly.remoteReadSupported()).isTrue();
        assertThat(readOnly.remoteWriteSupported()).isFalse();

        AclCapabilitiesVO writeOnly = new AclCapabilitiesVO(
                9L, InstanceVendor.TENCENT, InstanceType.CLOUD, "provider", false, true);
        assertThat(writeOnly.remoteReadSupported()).isFalse();
        assertThat(writeOnly.remoteWriteSupported()).isTrue();
    }

    @Test
    void identicalVerdictsAreValueEqual() {
        AclCapabilitiesVO left = new AclCapabilitiesVO(
                7L, InstanceVendor.APACHE, InstanceType.PROXY_LOCAL, "instance", true, false);
        AclCapabilitiesVO right = new AclCapabilitiesVO(
                7L, InstanceVendor.APACHE, InstanceType.PROXY_LOCAL, "instance", true, false);

        assertThat(left).isEqualTo(right);
        assertThat(left).isNotEqualTo(new AclCapabilitiesVO(
                7L, InstanceVendor.APACHE, InstanceType.PROXY_LOCAL, "instance", false, false));
    }
}
