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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the stored consumer group entity {@link RmqGroup}: it maps to the
 * {@code rmq_instance_group} table and every column round-trips - the group identity under its
 * instance, the consumption settings and the lifecycle audit columns.
 */
class RmqGroupTest {

    @Test
    void theEntityMapsToTheInstanceGroupTable() {
        TableName annotation = RmqGroup.class.getAnnotation(TableName.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("rmq_instance_group");
    }

    @Test
    void everyColumnRoundTrips() {
        RmqGroup group = new RmqGroup();
        group.setId(7L);
        group.setClusterId("cluster-1");
        group.setInstanceId("instance-1");
        group.setName("order-group");
        group.setConsumeType("CONCURRENTLY");
        group.setMessageModel("CLUSTERING");
        group.setMaxRetry(16);
        group.setStatus("ACTIVE");
        group.setCreatedBy("alice");

        assertThat(group.getId()).isEqualTo(7L);
        assertThat(group.getClusterId()).isEqualTo("cluster-1");
        assertThat(group.getInstanceId()).isEqualTo("instance-1");
        assertThat(group.getName()).isEqualTo("order-group");
        assertThat(group.getConsumeType()).isEqualTo("CONCURRENTLY");
        assertThat(group.getMessageModel()).isEqualTo("CLUSTERING");
        assertThat(group.getMaxRetry()).isEqualTo(16);
        assertThat(group.getStatus()).isEqualTo("ACTIVE");
        assertThat(group.getCreatedBy()).isEqualTo("alice");
    }
}
