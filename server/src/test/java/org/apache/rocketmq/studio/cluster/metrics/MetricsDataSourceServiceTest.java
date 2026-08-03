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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.model.MetricsDataSourceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsDataSourceServiceTest {

    @Mock
    private MetricsDataSourceRepository repository;

    @Mock
    private MetricsSourceFactory sourceFactory;

    @Mock
    private MetricsSource metricsSource;

    @InjectMocks
    private MetricsDataSourceService service;

    @Test
    void listDataSourcesShouldDelegateToRepository() {
        MetricsDataSourceConfig config = config("prometheus-prod");
        when(repository.findAll()).thenReturn(List.of(config));

        assertThat(service.listDataSources()).containsExactly(config);
    }

    @Test
    void createDataSourceShouldRequireName() {
        assertThatThrownBy(() -> service.createDataSource(new MetricsDataSourceConfig()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode()).isEqualTo(400));
        verify(repository, never()).save(any());
    }

    @Test
    void updateDataSourceShouldRequireExistingName() {
        MetricsDataSourceConfig config = config("missing");
        when(repository.findByName("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateDataSource(config))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode()).isEqualTo(404));
    }

    @Test
    void updateDataSourceShouldPersistWhenFound() {
        MetricsDataSourceConfig config = config("present");
        when(repository.findByName("present")).thenReturn(Optional.of(config));
        when(repository.save(config)).thenReturn(config);

        assertThat(service.updateDataSource(config)).isSameAs(config);
        verify(repository).save(config);
    }

    @Test
    void deleteDataSourceShouldRequireName() {
        assertThatThrownBy(() -> service.deleteDataSource(" "))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode()).isEqualTo(400));
        verify(repository, never()).deleteByName(any());
    }

    @Test
    void queryShouldBuildSourceForNamedDataSourceAndDelegate() {
        MetricsDataSourceConfig config = config("victoriametrics-prod");
        when(repository.findByName("victoriametrics-prod")).thenReturn(Optional.of(config));
        when(sourceFactory.create(config)).thenReturn(metricsSource);
        MetricDataVO data = MetricDataVO.builder().resultType("matrix").series(List.of()).warnings(List.of()).build();
        when(metricsSource.query(any(MetricQueryDTO.class))).thenReturn(data);

        MetricQueryDTO query = MetricQueryDTO.builder().metric("up").start(1L).end(2L).step("30s").build();
        MetricDataVO result = service.query("victoriametrics-prod", query);

        assertThat(result).isSameAs(data);
        verify(sourceFactory).create(config);
        verify(metricsSource).query(query);
    }

    @Test
    void queryShouldRejectBlankDataSourceName() {
        assertThatThrownBy(() -> service.query(" ", MetricQueryDTO.builder().metric("up").build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode()).isEqualTo(400));
    }

    @Test
    void queryShouldThrowWhenDataSourceNotFound() {
        when(repository.findByName("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.query("ghost", MetricQueryDTO.builder().metric("up").build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode()).isEqualTo(404));
    }

    private MetricsDataSourceConfig config(String name) {
        MetricsDataSourceConfig config = new MetricsDataSourceConfig();
        config.setName(name);
        config.setProviderType("PROMETHEUS");
        return config;
    }
}
