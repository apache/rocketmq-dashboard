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
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceDTOTest {

    @Test
    void shouldCanonicalizeProviderTypeAndInstanceBindings() {
        DataSourceDTO request = validDataSource();
        request.setType(" victoria metrics ");
        request.setInstanceIds(List.of(" instance-a ", "instance-b", "instance-a"));

        DataSourceVO dataSource = request.toDataSourceVO();

        assertThat(dataSource.getType()).isEqualTo("VictoriaMetrics");
        assertThat(dataSource.getInstanceIds()).containsExactly("instance-a", "instance-b");
    }

    @Test
    void shouldRejectBlankInstanceBindings() {
        DataSourceDTO request = validDataSource();
        request.setInstanceIds(List.of("instance-a", " "));

        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(request))
                    .anyMatch(violation -> "instanceIds must not contain blank values"
                            .equals(violation.getMessage()));
        }
    }

    @Test
    void shouldKeepNullInstanceBindingsForGlobalDataSources() {
        DataSourceDTO request = validDataSource();

        assertThat(request.toDataSourceVO().getInstanceIds()).isNull();
    }

    private DataSourceDTO validDataSource() {
        DataSourceDTO request = new DataSourceDTO();
        request.setName("Production metrics");
        request.setType("Prometheus");
        request.setUrl("https://metrics.example.test");
        return request;
    }

    @Test
    void shouldRejectUnsupportedTypeAuthAndMissingCoreFields() {
        DataSourceDTO request = validDataSource();
        request.setType("influxdb");
        request.setAuth("api-key");
        request.setName(" ");
        request.setUrl("");

        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var messages = validatorFactory.getValidator().validate(request).stream()
                    .map(violation -> violation.getMessage()).toList();
            assertThat(messages)
                    .contains("Unsupported metrics data source type",
                            "Unsupported metrics data source authentication",
                            "name is required", "url is required");
        }
    }

    @Test
    void toDataSourceVONormalizesAuthAndDefaultsUnknownType() {
        DataSourceDTO request = validDataSource();
        request.setType("future-backend");
        request.setAuth(" bearer token ");

        DataSourceVO dataSource = request.toDataSourceVO();

        assertThat(dataSource.getType()).isEqualTo("Prometheus");
        assertThat(dataSource.getAuth()).isEqualTo("bearer token");
    }
}
