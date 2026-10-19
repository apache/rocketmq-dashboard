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
 * Pins the AI event entity {@link RmqAiEvent}: it maps to the {@code rmq_ai_event} table and
 * every column round-trips - the append-only timeline row whose {@code seq} is the authority
 * for conversation cursors, with its typed payload.
 */
class RmqAiEventTest {

    @Test
    void theEntityMapsToTheAiEventTable() {
        TableName annotation = RmqAiEvent.class.getAnnotation(TableName.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("rmq_ai_event");
    }

    @Test
    void everyColumnRoundTrips() {
        RmqAiEvent event = new RmqAiEvent();
        event.setId(7L);
        event.setConversationId(3L);
        event.setRunId(9L);
        event.setTurn(2);
        event.setSeq(100);
        event.setType("text");
        event.setPayload("{\"text\":\"hello\"}");
        event.setGmtCreate(LocalDateTime.of(2026, 1, 2, 3, 4));
        event.setGmtModified(LocalDateTime.of(2026, 1, 2, 3, 4));

        assertThat(event.getId()).isEqualTo(7L);
        assertThat(event.getConversationId()).isEqualTo(3L);
        assertThat(event.getRunId()).isEqualTo(9L);
        assertThat(event.getTurn()).isEqualTo(2);
        assertThat(event.getSeq()).isEqualTo(100);
        assertThat(event.getType()).isEqualTo("text");
        assertThat(event.getPayload()).isEqualTo("{\"text\":\"hello\"}");
        assertThat(event.getGmtCreate()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
    }
}
