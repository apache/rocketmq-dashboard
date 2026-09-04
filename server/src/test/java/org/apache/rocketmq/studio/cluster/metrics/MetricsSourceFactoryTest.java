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
package org.apache.rocketmq.studio.cluster.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.model.MetricsDataSourceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class MetricsSourceFactoryTest {

    private final MetricsSourceFactory factory = new MetricsSourceFactory(stubbedBuilder(), new ObjectMapper());

    private RestClient.Builder stubbedBuilder() {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        lenient().when(builder.requestFactory(any())).thenReturn(builder);
        lenient().when(builder.build()).thenReturn(mock(RestClient.class));
        return builder;
    }

    private MetricsDataSourceConfig config(String providerType) {
        MetricsDataSourceConfig config = new MetricsDataSourceConfig();
        config.setName("metrics");
        config.setUrl("http://127.0.0.1:9090");
        config.setProviderType(providerType);
        return config;
    }

    @Test
    void rejectsNullConfigurations() {
        assertThatThrownBy(() -> factory.create(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("required");
    }

    @Test
    void buildsPrometheusSourceForNormalizedProviderType() {
        MetricsSource source = factory.create(config(" prometheus "));

        assertThat(source).isInstanceOf(PrometheusMetricsSource.class);
    }
}
