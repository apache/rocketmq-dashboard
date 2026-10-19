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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the update contract of {@link UpdateInstanceDTO}: the instance id is required, and
 * {@code toInstanceVO()} carries every updatable field (name, type, endpoint, remark and the
 * admin credential reference) into the view.
 */
class UpdateInstanceDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(UpdateInstanceDTO request) {
        Set<ConstraintViolation<UpdateInstanceDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void theInstanceIdIsRequired() {
        UpdateInstanceDTO request = new UpdateInstanceDTO();
        request.setInstanceId("   ");
        assertThat(violationsOf(request)).containsExactly("instanceId is required");

        request.setInstanceId(null);
        assertThat(violationsOf(request)).containsExactly("instanceId is required");
    }

    @Test
    void toInstanceVOCarriesEveryUpdatableField() {
        UpdateInstanceDTO request = new UpdateInstanceDTO();
        request.setInstanceId("instance-1");
        request.setName("renamed");
        request.setType(InstanceType.PROXY_CLUSTER);
        request.setEndpoint("10.0.0.1:9876");
        request.setRemark("updated by test");
        request.setAdminCredentialRef("cred-ref-1");

        InstanceVO vo = request.toInstanceVO();

        assertThat(vo.getName()).isEqualTo("renamed");
        assertThat(vo.getType()).isEqualTo(InstanceType.PROXY_CLUSTER);
        assertThat(vo.getEndpoint()).isEqualTo("10.0.0.1:9876");
        assertThat(vo.getRemark()).isEqualTo("updated by test");
        assertThat(vo.getAdminCredentialRef()).isEqualTo("cred-ref-1");
    }

    @Test
    void anEmptyUpdateStillProducesAView() {
        UpdateInstanceDTO request = new UpdateInstanceDTO();
        request.setInstanceId("instance-1");

        InstanceVO vo = request.toInstanceVO();

        assertThat(vo.getName()).isNull();
        assertThat(vo.getEndpoint()).isNull();
        assertThat(vo.getAdminCredentialRef()).isNull();
    }
}
