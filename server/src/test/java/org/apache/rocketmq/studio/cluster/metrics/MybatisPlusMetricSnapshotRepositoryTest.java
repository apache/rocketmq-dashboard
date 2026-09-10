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

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.ops.alert.AlertDomain;
import org.apache.rocketmq.studio.persistence.entity.RmqMetricSnapshot;
import org.apache.rocketmq.studio.persistence.mapper.RmqMetricSnapshotMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MybatisPlusMetricSnapshotRepositoryTest {

    @Mock
    private RmqMetricSnapshotMapper mapper;

    @Test
    void saveAllShouldUseOneTransactionForTheWholeSampleBatchTest() throws Exception {
        Method saveAll = MybatisPlusMetricSnapshotRepository.class
                .getMethod("saveAll", List.class);

        assertThat(saveAll.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void nullClusterScopeShouldOnlyReadUnscopedSnapshotsTest() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        MybatisPlusMetricSnapshotRepository repository =
                new MybatisPlusMetricSnapshotRepository(mapper, new ObjectMapper());
        MetricSample scope = new MetricSample("broker.availability", AlertDomain.CLUSTER,
                "local", null, Map.of(), 1D, MetricAvailability.AVAILABLE, Instant.now());

        repository.findRecent(scope, Instant.EPOCH);

        ArgumentCaptor<Wrapper<RmqMetricSnapshot>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(queryCaptor.capture());
        QueryWrapper<RmqMetricSnapshot> query = (QueryWrapper<RmqMetricSnapshot>) queryCaptor.getValue();
        assertThat(query.getSqlSegment()).contains("cluster_id IS NULL");
    }

    @Test
    void clusterScopedQueryFiltersByClusterAvailabilityAndTimeTest() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        MybatisPlusMetricSnapshotRepository repository =
                new MybatisPlusMetricSnapshotRepository(mapper, new ObjectMapper());
        MetricSample scope = new MetricSample("broker.availability", AlertDomain.CLUSTER,
                "local", "cluster-1", Map.of("broker", "b1"), 1D, MetricAvailability.AVAILABLE,
                Instant.parse("2026-07-01T10:00:00Z"));

        repository.findRecent(scope, Instant.parse("2026-07-01T09:00:00Z"));

        ArgumentCaptor<Wrapper<RmqMetricSnapshot>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(queryCaptor.capture());
        QueryWrapper<RmqMetricSnapshot> query = (QueryWrapper<RmqMetricSnapshot>) queryCaptor.getValue();
        String sql = query.getSqlSegment();
        assertThat(sql).contains("instance_id =").contains("metric_key =").contains("domain =")
                .contains("labels_hash =").contains("cluster_id =").contains("availability =")
                .contains("collected_at >=").contains("ORDER BY collected_at");
    }

    @Test
    void deleteBeforeBuildsACutoffQueryAndReturnsTheDeletedCountTest() {
        when(mapper.delete(any(Wrapper.class))).thenReturn(3);
        MybatisPlusMetricSnapshotRepository repository =
                new MybatisPlusMetricSnapshotRepository(mapper, new ObjectMapper());

        int deleted = repository.deleteBefore(Instant.parse("2026-07-01T10:00:00Z"));

        assertThat(deleted).isEqualTo(3);
        ArgumentCaptor<Wrapper<RmqMetricSnapshot>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).delete(queryCaptor.capture());
        QueryWrapper<RmqMetricSnapshot> query = (QueryWrapper<RmqMetricSnapshot>) queryCaptor.getValue();
        assertThat(query.getSqlSegment()).contains("collected_at <");
    }
}
