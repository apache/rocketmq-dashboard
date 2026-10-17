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
 * Pins the message query entity {@link RmqMessageQuery}: it maps to the
 * {@code rmq_instance_message} table and every column round-trips - the query history row the
 * message page lists, with its filter shape, result count and result snapshot.
 */
class RmqMessageQueryTest {

    @Test
    void theEntityMapsToTheMessageTable() {
        TableName annotation = RmqMessageQuery.class.getAnnotation(TableName.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("rmq_instance_message");
    }

    @Test
    void everyColumnRoundTrips() {
        RmqMessageQuery query = new RmqMessageQuery();
        query.setId(7L);
        query.setQueryType("KEY");
        query.setTopic("topic-1");
        query.setMsgId("msg-1");
        query.setTag("tag-a");
        query.setMessageKey("key-1");
        query.setStartTime(1784112606000L);
        query.setEndTime(1784114406000L);
        query.setResultCount(42);
        query.setResultSnapshot("[...]");
        query.setClusterId("cluster-1");
        query.setQueriedBy("alice");

        assertThat(query.getId()).isEqualTo(7L);
        assertThat(query.getQueryType()).isEqualTo("KEY");
        assertThat(query.getTopic()).isEqualTo("topic-1");
        assertThat(query.getMsgId()).isEqualTo("msg-1");
        assertThat(query.getTag()).isEqualTo("tag-a");
        assertThat(query.getMessageKey()).isEqualTo("key-1");
        assertThat(query.getStartTime()).isEqualTo(1784112606000L);
        assertThat(query.getEndTime()).isEqualTo(1784114406000L);
        assertThat(query.getResultCount()).isEqualTo(42);
        assertThat(query.getResultSnapshot()).isEqualTo("[...]");
        assertThat(query.getClusterId()).isEqualTo("cluster-1");
        assertThat(query.getQueriedBy()).isEqualTo("alice");
    }
}
