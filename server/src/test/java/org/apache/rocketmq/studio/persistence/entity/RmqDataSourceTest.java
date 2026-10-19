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

import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the data source entity {@link RmqDataSource}: it maps to the {@code rmq_data_source}
 * table and every column round-trips - the keyed JSON blob the settings store persists
 * arbitrary data source configurations as.
 */
class RmqDataSourceTest {

    @Test
    void theEntityMapsToTheDataSourceTable() {
        TableName annotation = RmqDataSource.class.getAnnotation(TableName.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("rmq_data_source");
    }

    @Test
    void everyColumnRoundTrips() {
        RmqDataSource source = new RmqDataSource();
        source.setId(7L);
        source.setDsKey("general");
        source.setJson("{\"theme\":\"dark\"}");
        source.setGmtCreate(LocalDateTime.of(2026, 1, 2, 3, 4));
        source.setGmtModified(LocalDateTime.of(2026, 1, 3, 4, 5));

        assertThat(source.getId()).isEqualTo(7L);
        assertThat(source.getDsKey()).isEqualTo("general");
        assertThat(source.getJson()).isEqualTo("{\"theme\":\"dark\"}");
        assertThat(source.getGmtCreate()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
        assertThat(source.getGmtModified()).isEqualTo(LocalDateTime.of(2026, 1, 3, 4, 5));
    }
}
