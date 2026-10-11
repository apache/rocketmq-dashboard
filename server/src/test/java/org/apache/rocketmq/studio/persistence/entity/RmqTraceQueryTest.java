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
 * Pins the trace query entity {@link RmqTraceQuery}: it maps to the
 * {@code rmq_instance_trace} table and every column round-trips - the query history row the
 * trace page lists, with its message identity and result shape.
 */
class RmqTraceQueryTest {

    @Test
    void theEntityMapsToTheTraceTable() {
        TableName annotation = RmqTraceQuery.class.getAnnotation(TableName.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("rmq_instance_trace");
    }

    @Test
    void everyColumnRoundTrips() {
        RmqTraceQuery query = new RmqTraceQuery();
        query.setId(7L);
        query.setMsgId("msg-1");
        query.setTopic("topic-1");
        query.setTraceTopic("rmq_sys_trace");
        query.setNodeCount(3);
        query.setConsumerCount(2);
        query.setClusterId("cluster-1");
        query.setQueriedBy("alice");

        assertThat(query.getId()).isEqualTo(7L);
        assertThat(query.getMsgId()).isEqualTo("msg-1");
        assertThat(query.getTopic()).isEqualTo("topic-1");
        assertThat(query.getTraceTopic()).isEqualTo("rmq_sys_trace");
        assertThat(query.getNodeCount()).isEqualTo(3);
        assertThat(query.getConsumerCount()).isEqualTo(2);
        assertThat(query.getClusterId()).isEqualTo("cluster-1");
        assertThat(query.getQueriedBy()).isEqualTo("alice");
    }
}
