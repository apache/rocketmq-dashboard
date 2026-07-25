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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySettingsRepositoryTest {

    private final InMemorySettingsRepository repository = new InMemorySettingsRepository();

    @Test
    void saveDataSourceShouldSupportCreateAndDeleteByKey() {
        DataSourceVO dataSource = DataSourceVO.builder()
                .key("source-1")
                .name("Prometheus")
                .type("Prometheus")
                .url("http://localhost:9090")
                .build();

        repository.saveDataSource(dataSource);

        assertThat(repository.findDataSourceByKey("source-1")).containsSame(dataSource);
        assertThat(repository.findAllDataSources()).containsExactly(dataSource);

        repository.deleteDataSource("source-1");

        assertThat(repository.findDataSourceByKey("source-1")).isEmpty();
        assertThat(repository.findAllDataSources()).isEmpty();
    }

    @Test
    void replaceDataSourceShouldUpdateExistingEntry() {
        DataSourceVO existing = DataSourceVO.builder().key("source-1").name("Prometheus").build();
        DataSourceVO replacement = DataSourceVO.builder().key("source-1").name("Updated Prometheus").build();
        repository.saveDataSource(existing);

        boolean replaced = repository.replaceDataSource(replacement);

        assertThat(replaced).isTrue();
        assertThat(repository.findAllDataSources()).containsExactly(replacement);
    }

    @Test
    void replaceDataSourceShouldNotInsertUnknownEntry() {
        DataSourceVO replacement = DataSourceVO.builder().key("missing").name("Unexpected DS").build();

        boolean replaced = repository.replaceDataSource(replacement);

        assertThat(replaced).isFalse();
        assertThat(repository.findAllDataSources()).isEmpty();
    }
}
