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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link DataSourceVO}, one configured metrics data source: the name, backend type and
 * url are all required (a source without any of them is unusable), and the instance scoping
 * list is optional — an empty list keeps the source globally available, which is a different
 * state from "serves no instance" and must stay expressible.
 */
class DataSourceVOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(DataSourceVO source) {
        Set<ConstraintViolation<DataSourceVO>> violations = validator.validate(source);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void aCompleteSourcePassesValidation() {
        DataSourceVO source = DataSourceVO.builder()
                .key("prometheus-prod")
                .name("Production Prometheus")
                .type("prometheus")
                .url("http://10.0.0.1:9090")
                .auth("bearer")
                .status("reachable")
                .instanceIds(List.of("instance-1", "instance-2"))
                .build();

        assertThat(violationsOf(source)).isEmpty();
        assertThat(source.getInstanceIds()).containsExactly("instance-1", "instance-2");
    }

    @Test
    void theNameTypeAndUrlAreEachRequired() {
        DataSourceVO blank = DataSourceVO.builder().build();
        assertThat(violationsOf(blank)).containsExactlyInAnyOrder(
                "name is required", "type is required", "url is required");

        DataSourceVO missingUrl = DataSourceVO.builder()
                .name("Production Prometheus")
                .type("prometheus")
                .url("   ")
                .build();
        assertThat(violationsOf(missingUrl)).containsExactly("url is required");
    }

    @Test
    void anEmptyScopeListMeansGloballyAvailable() {
        DataSourceVO global = DataSourceVO.builder()
                .name("Production Prometheus")
                .type("prometheus")
                .url("http://10.0.0.1:9090")
                .instanceIds(List.of())
                .build();

        assertThat(violationsOf(global)).isEmpty();
        assertThat(global.getInstanceIds()).isEmpty();

        DataSourceVO unscoped = DataSourceVO.builder()
                .name("Production Prometheus")
                .type("prometheus")
                .url("http://10.0.0.1:9090")
                .build();
        assertThat(unscoped.getInstanceIds()).isNull();
    }
}
