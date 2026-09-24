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
package org.apache.rocketmq.studio.ops.ai.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.ops.ai.conversation.event.TimelineEvent;
import org.apache.rocketmq.studio.persistence.entity.RmqAiEvent;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Optional;

/**
 * The one place a {@link TimelineEvent} becomes {@code rmq_ai_event.payload} and back.
 *
 * <p>Two rules live here because getting either wrong is silent corruption:
 * <ul>
 *   <li>The {@code type} column is read out of the serialised payload rather than from a second switch
 *       over the sealed hierarchy. The discriminator is already in the JSON — it is what lets the row be
 *       deserialised polymorphically — so deriving the column from it makes it impossible for the column
 *       and the payload to disagree, and a new subtype needs no change here.</li>
 *   <li>A row that cannot be decoded is skipped, not fatal. One payload written by an older build, or
 *       truncated by a full disk, must not cost the user the whole conversation; the caller logs and
 *       carries on.</li>
 * </ul>
 */
@Slf4j
final class AiEventCodec {

    private AiEventCodec() {
    }

    /**
     * @return the type tag and the JSON to store, or empty when the event cannot be serialised — which
     *     is a contract bug and is logged as one, but must not fail the run that produced it
     */
    static Optional<Payload> write(ObjectMapper objectMapper, long runId, TimelineEvent event) {
        if (event == null) {
            return Optional.empty();
        }
        try {
            String json = objectMapper.writeValueAsString(event);
            String type = objectMapper.readTree(json).path("type").asText(null);
            if (!StringUtils.hasText(type)) {
                log.warn("timeline event of run {} serialised without a type discriminator", runId);
                return Optional.empty();
            }
            return Optional.of(new Payload(type, json));
        } catch (RuntimeException | IOException exception) {
            log.warn("could not serialise a {} event of run {}: {}",
                    event.getClass().getSimpleName(), runId, exception.toString());
            return Optional.empty();
        }
    }

    /**
     * @return the decoded event, or empty when the row is unreadable. Never throws: the timeline read
     *     serves a whole conversation and one bad row is not a reason to fail all of it.
     */
    static Optional<TimelineEvent> read(ObjectMapper objectMapper, RmqAiEvent row) {
        if (row == null || !StringUtils.hasText(row.getPayload())) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(objectMapper.readValue(row.getPayload(), TimelineEvent.class));
        } catch (RuntimeException | IOException exception) {
            log.warn("skipping unreadable AI event {} of conversation {} (seq {}): {}",
                    row.getId(), row.getConversationId(), row.getSeq(), exception.toString());
            return Optional.empty();
        }
    }

    /** A serialised timeline event and the type tag its row is filed under. */
    record Payload(String type, String json) {
    }
}
