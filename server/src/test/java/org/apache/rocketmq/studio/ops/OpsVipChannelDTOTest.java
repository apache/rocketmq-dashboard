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
package org.apache.rocketmq.studio.ops;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the toggle contract of {@link OpsVipChannelDTO}: the VIP-channel setting must be
 * explicitly present - an absent flag must never fall through to a silent default when the
 * operations endpoint rewrites the client's channel mode.
 */
class OpsVipChannelDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(OpsVipChannelDTO request) {
        Set<ConstraintViolation<OpsVipChannelDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void bothToggleStatesAreValid() {
        OpsVipChannelDTO enabled = new OpsVipChannelDTO();
        enabled.setUseVIPChannel(true);
        assertThat(violationsOf(enabled)).isEmpty();
        assertThat(enabled.getUseVIPChannel()).isTrue();

        OpsVipChannelDTO disabled = new OpsVipChannelDTO();
        disabled.setUseVIPChannel(false);
        assertThat(violationsOf(disabled)).isEmpty();
        assertThat(disabled.getUseVIPChannel()).isFalse();
    }

    @Test
    void anAbsentToggleIsRejected() {
        assertThat(violationsOf(new OpsVipChannelDTO()))
                .containsExactly("useVIPChannel is required");
    }
}
