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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the upsert contract of {@link UpsertPlainAccessConfigDTO}: the access key is required and
 * trimmed on mapping, the secret key is optional (an update keeps the stored secret), the
 * Lombok-generated toString never exposes either key, and every permission field maps into the
 * view.
 */
class UpsertPlainAccessConfigDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(UpsertPlainAccessConfigDTO request) {
        Set<ConstraintViolation<UpsertPlainAccessConfigDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    private UpsertPlainAccessConfigDTO validRequest() {
        UpsertPlainAccessConfigDTO request = new UpsertPlainAccessConfigDTO();
        request.setAccessKey("ak-123");
        return request;
    }

    @Test
    void theAccessKeyIsRequired() {
        UpsertPlainAccessConfigDTO blank = validRequest();
        blank.setAccessKey("   ");
        assertThat(violationsOf(blank)).containsExactly("accessKey is required");

        UpsertPlainAccessConfigDTO missing = validRequest();
        missing.setAccessKey(null);
        assertThat(violationsOf(missing)).containsExactly("accessKey is required");

        assertThat(violationsOf(validRequest())).isEmpty();
    }

    @Test
    void theAccessKeyIsTrimmedOnMappingButTheSecretIsLeftAlone() {
        UpsertPlainAccessConfigDTO request = validRequest();
        request.setAccessKey("  ak-123  ");
        request.setSecretKey("  sk-456  ");

        PlainAccessConfigVO config = request.toPlainAccessConfigVO();

        assertThat(config.getAccessKey()).isEqualTo("ak-123");
        assertThat(config.getSecretKey()).isEqualTo("  sk-456  ");
    }

    @Test
    void aNullAccessKeyMapsToNullWithoutThrowing() {
        UpsertPlainAccessConfigDTO request = new UpsertPlainAccessConfigDTO();
        assertThat(request.toPlainAccessConfigVO().getAccessKey()).isNull();
    }

    @Test
    void toStringNeverExposesTheAccessKeyOrTheSecretKey() {
        UpsertPlainAccessConfigDTO request = validRequest();
        request.setSecretKey("sk-plain-secret-789");

        String value = request.toString();

        assertThat(value).doesNotContain("ak-123");
        assertThat(value).doesNotContain("sk-plain-secret-789");
    }

    @Test
    void everyPermissionFieldMapsIntoTheView() {
        UpsertPlainAccessConfigDTO request = validRequest();
        request.setSecretKey("sk-456");
        request.setWhiteRemoteAddress("10.0.0.0/8");
        request.setAdmin(true);
        request.setDefaultTopicPerm("RO");
        request.setDefaultGroupPerm("RW");
        request.setTopicPerms(List.of("topic-1=RO"));
        request.setGroupPerms(List.of("group-1=RW"));

        PlainAccessConfigVO config = request.toPlainAccessConfigVO();

        assertThat(config.getSecretKey()).isEqualTo("sk-456");
        assertThat(config.getWhiteRemoteAddress()).isEqualTo("10.0.0.0/8");
        assertThat(config.isAdmin()).isTrue();
        assertThat(config.getDefaultTopicPerm()).isEqualTo("RO");
        assertThat(config.getDefaultGroupPerm()).isEqualTo("RW");
        assertThat(config.getTopicPerms()).containsExactly("topic-1=RO");
        assertThat(config.getGroupPerms()).containsExactly("group-1=RW");
    }
}
