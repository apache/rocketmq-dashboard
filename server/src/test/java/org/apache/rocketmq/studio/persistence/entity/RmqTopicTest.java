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
 * Pins the stored topic entity {@link RmqTopic}: it maps to the {@code rmq_instance_topic}
 * table and every column round-trips - the topic identity under its instance, the queue counts
 * and permission, and the lifecycle audit columns.
 */
class RmqTopicTest {

    @Test
    void theEntityMapsToTheInstanceTopicTable() {
        TableName annotation = RmqTopic.class.getAnnotation(TableName.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("rmq_instance_topic");
    }

    @Test
    void everyColumnRoundTrips() {
        RmqTopic topic = new RmqTopic();
        topic.setId(7L);
        topic.setClusterId("cluster-1");
        topic.setInstanceId("instance-1");
        topic.setName("orders");
        topic.setTopicType("NORMAL");
        topic.setReadQueueNums(16);
        topic.setWriteQueueNums(8);
        topic.setPerm(6);
        topic.setRemark("order events");
        topic.setStatus("ACTIVE");
        topic.setCreatedBy("alice");

        assertThat(topic.getId()).isEqualTo(7L);
        assertThat(topic.getClusterId()).isEqualTo("cluster-1");
        assertThat(topic.getInstanceId()).isEqualTo("instance-1");
        assertThat(topic.getName()).isEqualTo("orders");
        assertThat(topic.getTopicType()).isEqualTo("NORMAL");
        assertThat(topic.getReadQueueNums()).isEqualTo(16);
        assertThat(topic.getWriteQueueNums()).isEqualTo(8);
        assertThat(topic.getPerm()).isEqualTo(6);
        assertThat(topic.getRemark()).isEqualTo("order events");
        assertThat(topic.getStatus()).isEqualTo("ACTIVE");
        assertThat(topic.getCreatedBy()).isEqualTo("alice");
    }
}
