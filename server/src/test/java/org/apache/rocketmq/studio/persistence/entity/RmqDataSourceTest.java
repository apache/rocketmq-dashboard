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
package org.apache.rocketmq.studio.persistence.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RmqDataSource}: the persisted data-source row stores its resolved
 * configuration as JSON (which can embed passwords/tokens), so {@code toString} must not
 * expose it while equality still covers it.
 */
class RmqDataSourceTest {

    @Test
    void toStringOmitsThePersistedJson() {
        RmqDataSource dataSource = new RmqDataSource();
        dataSource.setId(1L);
        dataSource.setDsKey("prometheus");
        dataSource.setJson("{\"authType\":\"bearer\",\"bearerToken\":\"sk-secret\"}");

        String value = dataSource.toString();

        assertThat(value).contains("dsKey=prometheus");
        assertThat(value).doesNotContain("json").doesNotContain("sk-secret");
    }

    @Test
    void dataEqualityCoversTheJson() {
        RmqDataSource first = new RmqDataSource();
        first.setId(1L);
        first.setDsKey("prometheus");
        first.setJson("{\"authType\":\"none\"}");

        RmqDataSource same = new RmqDataSource();
        same.setId(1L);
        same.setDsKey("prometheus");
        same.setJson("{\"authType\":\"none\"}");

        RmqDataSource changed = new RmqDataSource();
        changed.setId(1L);
        changed.setDsKey("prometheus");
        changed.setJson("{\"authType\":\"basic\"}");

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(changed);
    }
}
