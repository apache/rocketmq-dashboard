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
package org.apache.rocketmq.studio.settings;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceTestDTOTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void toStringShouldNotExposeCredentials() {
        DataSourceTestDTO request = DataSourceTestDTO.builder()
            .url("http://prometheus:9090")
            .type("prometheus")
            .auth("bearer token")
            .username("prometheus-user")
            .password("plain-password")
            .bearerToken("plain-token")
            .build();

        String value = request.toString();

        assertThat(value).contains("url=http://prometheus:9090");
        assertThat(value).contains("type=prometheus");
        assertThat(value).contains("auth=bearer token");
        assertThat(value).contains("username=prometheus-user");
        assertThat(value).doesNotContain("plain-password");
        assertThat(value).doesNotContain("plain-token");
    }

    @Test
    void shouldRejectMissingUrlAndType() {
        DataSourceTestDTO request = DataSourceTestDTO.builder().build();

        Set<String> messages = validator.validate(request).stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.toSet());

        assertThat(messages).contains("url is required", "type is required");
    }

    @Test
    void shouldAcceptBasicAuthCredentialsWithoutBearerToken() {
        DataSourceTestDTO request = DataSourceTestDTO.builder()
                .url("http://prometheus:9090")
                .type("prometheus")
                .auth("basic auth")
                .username("ops")
                .password("pw")
                .build();

        String value = request.toString();

        assertThat(value).contains("username=ops");
        assertThat(value).doesNotContain("pw");
        assertThat(value).doesNotContain("bearerToken");
    }
}
