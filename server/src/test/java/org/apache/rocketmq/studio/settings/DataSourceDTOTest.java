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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceDTOTest {

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

        assertThat(violationMessages(request))
                .anyMatch(message -> message.equals("instanceIds must not contain blank values"));
    }

    @Test
    void shouldKeepNullInstanceBindingsForGlobalDataSources() {
        DataSourceDTO request = validDataSource();

        assertThat(request.toDataSourceVO().getInstanceIds()).isNull();
    }

    @Test
    void shouldTrimAuthModeAndNormalizeCommonAliases() {
        DataSourceDTO request = validDataSource();
        request.setType("thanos");
        request.setAuth(" basic auth ");

        DataSourceVO dataSource = request.toDataSourceVO();

        assertThat(dataSource.getType()).isEqualTo("Thanos");
        assertThat(dataSource.getAuth()).isEqualTo("basic auth");
    }

    @Test
    void shouldNormalizePrometheusAndVictoriaAliases() {
        DataSourceDTO request = validDataSource();
        request.setType("victoria_metrics");

        assertThat(request.toDataSourceVO().getType()).isEqualTo("VictoriaMetrics");

        request.setType("Prometheus");
        assertThat(request.toDataSourceVO().getType()).isEqualTo("Prometheus");
    }

    @Test
    void shouldRejectMissingNameTypeAndUrl() {
        DataSourceDTO request = validDataSource();
        request.setName(null);
        request.setType(" ");
        request.setUrl(null);

        Set<String> messages = violationMessages(request);
        assertThat(messages).contains("name is required", "type is required", "url is required");
    }

    @Test
    void shouldRejectUnsupportedDataSourceType() {
        DataSourceDTO request = validDataSource();
        request.setType("influxdb");

        assertThat(violationMessages(request)).contains("Unsupported metrics data source type");
    }

    @Test
    void shouldRejectUnsupportedAuthMode() {
        DataSourceDTO request = validDataSource();
        request.setAuth("client certificate");

        assertThat(violationMessages(request))
                .contains("Unsupported metrics data source authentication");
    }

    @Test
    void shouldAcceptEverySupportedAuthMode() {
        for (String auth : List.of("none", "basic auth", "bearer token")) {
            DataSourceDTO request = validDataSource();
            request.setAuth(auth);
            assertThat(violationMessages(request))
                    .doesNotContain("Unsupported metrics data source authentication");
        }
    }

    private Set<String> violationMessages(DataSourceDTO request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.toSet());
    }

    private DataSourceDTO validDataSource() {
        DataSourceDTO request = new DataSourceDTO();
        request.setName("Production metrics");
        request.setType("Prometheus");
        request.setUrl("https://metrics.example.test");
        return request;
    }
}
