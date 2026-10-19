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
 * Pins the AI conversation entity {@link RmqAiConversation}: it maps to the
 * {@code rmq_ai_conversation} table and every column round-trips - including the runtime
 * session id used for {@code --resume} and the cached lastSeq high-water mark whose authority
 * is {@code MAX(rmq_ai_event.seq)}, not this column.
 */
class RmqAiConversationTest {

    @Test
    void theEntityMapsToTheConversationTable() {
        TableName annotation = RmqAiConversation.class.getAnnotation(TableName.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("rmq_ai_conversation");
    }

    @Test
    void everyColumnRoundTrips() {
        RmqAiConversation conversation = new RmqAiConversation();
        conversation.setId(7L);
        conversation.setTitle("order triage");
        conversation.setOwner("alice");
        conversation.setEngine("claude-code");
        conversation.setModel("claude-sonnet");
        conversation.setMode("agent");
        conversation.setInstanceId("instance-1");
        conversation.setRuntimeSessionId("sess-42");
        conversation.setLastSeq(100);
        conversation.setArchived(true);

        assertThat(conversation.getId()).isEqualTo(7L);
        assertThat(conversation.getTitle()).isEqualTo("order triage");
        assertThat(conversation.getOwner()).isEqualTo("alice");
        assertThat(conversation.getEngine()).isEqualTo("claude-code");
        assertThat(conversation.getModel()).isEqualTo("claude-sonnet");
        assertThat(conversation.getMode()).isEqualTo("agent");
        assertThat(conversation.getInstanceId()).isEqualTo("instance-1");
        assertThat(conversation.getRuntimeSessionId()).isEqualTo("sess-42");
        assertThat(conversation.getLastSeq()).isEqualTo(100);
        assertThat(conversation.getArchived()).isTrue();
    }

    @Test
    void aFreshConversationIsNotArchived() {
        assertThat(new RmqAiConversation().getArchived()).isNull();
    }
}
