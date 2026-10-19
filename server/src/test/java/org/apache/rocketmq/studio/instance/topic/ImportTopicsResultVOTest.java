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
package org.apache.rocketmq.studio.instance.topic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the topic import result of {@link ImportTopicsResultVO}: the imported/failed counters
 * with the imported topics and the per-entry failures (index, name, message) the import dialog
 * renders row by row.
 */
class ImportTopicsResultVOTest {

    @Test
    void theResultCarriesCountersTopicsAndFailures() {
        TopicVO imported = new TopicVO();
        imported.setName("orders");
        ImportTopicsResultVO.Failure failure = ImportTopicsResultVO.Failure.builder()
                .index(2)
                .name("payments")
                .message("already exists")
                .build();
        ImportTopicsResultVO result = ImportTopicsResultVO.builder()
                .imported(1)
                .failed(1)
                .topics(List.of(imported))
                .failures(List.of(failure))
                .build();

        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getTopics()).containsExactly(imported);
        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.getFailures().get(0).getIndex()).isEqualTo(2);
        assertThat(result.getFailures().get(0).getName()).isEqualTo("payments");
        assertThat(result.getFailures().get(0).getMessage()).isEqualTo("already exists");
    }

    @Test
    void aFreshResultCountsNothing() {
        ImportTopicsResultVO result = new ImportTopicsResultVO();
        assertThat(result.getImported()).isZero();
        assertThat(result.getFailed()).isZero();
        assertThat(result.getTopics()).isNull();
        assertThat(result.getFailures()).isNull();
    }
}
