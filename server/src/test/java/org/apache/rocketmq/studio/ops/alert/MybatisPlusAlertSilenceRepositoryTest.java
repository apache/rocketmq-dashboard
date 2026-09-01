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
package org.apache.rocketmq.studio.ops.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.persistence.entity.RmqAlertSilence;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertSilenceMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MybatisPlusAlertSilenceRepositoryTest {
    @Test
    void findAllToleratesMalformedStoredDomainValuesTest() {
        RmqAlertSilenceMapper mapper = mock(RmqAlertSilenceMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(
                silence(1L, " business "),
                silence(2L, "LEGACY"),
                silence(3L, "CLUSTER")));
        MybatisPlusAlertSilenceRepository repository =
                new MybatisPlusAlertSilenceRepository(mapper, new ObjectMapper());

        List<AlertSilenceVO> result = repository.findAll();

        assertThat(result).extracting(AlertSilenceVO::getDomain)
                .containsExactly(AlertDomain.BUSINESS, AlertDomain.BUSINESS, AlertDomain.CLUSTER);
    }

    @Test
    void findAllKeepsUnscopedSilencesDomainlessTest() {
        RmqAlertSilenceMapper mapper = mock(RmqAlertSilenceMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(silence(1L, null)));
        MybatisPlusAlertSilenceRepository repository =
                new MybatisPlusAlertSilenceRepository(mapper, new ObjectMapper());

        List<AlertSilenceVO> result = repository.findAll();

        assertThat(result).singleElement()
                .satisfies(vo -> assertThat(vo.getDomain()).isNull());
    }

    private static RmqAlertSilence silence(Long id, String domain) {
        RmqAlertSilence entity = new RmqAlertSilence();
        entity.setId(id);
        entity.setDomain(domain);
        entity.setStartsAt(LocalDateTime.of(2026, 8, 22, 9, 0));
        entity.setEndsAt(LocalDateTime.of(2026, 8, 22, 10, 0));
        entity.setCreatedBy("admin");
        return entity;
    }
}
