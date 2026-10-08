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
import org.apache.rocketmq.studio.ops.ai.conversation.event.TimelineEvent;
import org.apache.rocketmq.studio.persistence.entity.RmqAiEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link AiEventCodec}: the one place a TimelineEvent becomes rmq_ai_event.payload and back.
 * The two rules that live here because getting either wrong is silent corruption: the type column
 * is read out of the serialised payload itself (so column and payload can never disagree), and a
 * row that cannot be decoded is skipped, not fatal.
 */
class AiEventCodecTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static RmqAiEvent row(String payload) {
        RmqAiEvent row = new RmqAiEvent();
        row.setId(42L);
        row.setConversationId(7L);
        row.setSeq(3);
        row.setPayload(payload);
        return row;
    }

    @Test
    void writeReadsTheTypeColumnOutOfTheSerialisedPayload() {
        Optional<AiEventCodec.Payload> payload =
                AiEventCodec.write(mapper, 1L, new TimelineEvent.User("hello", "enhanced"));

        assertThat(payload).isPresent();
        assertThat(payload.get().type()).isEqualTo("user");
        assertThat(payload.get().json()).contains("\"text\":\"hello\"");
    }

    @Test
    void writeOfEverySubtypeProducesItsDiscriminator() {
        assertThat(AiEventCodec.write(mapper, 1L, new TimelineEvent.Error("code", "boom", null))
                .map(AiEventCodec.Payload::type)).contains("error");
        assertThat(AiEventCodec.write(mapper, 1L,
                        new TimelineEvent.Notice("info", "note"))
                .map(AiEventCodec.Payload::type)).contains("notice");
    }

    @Test
    void writeOfANullEventIsEmpty() {
        assertThat(AiEventCodec.write(mapper, 1L, null)).isEmpty();
    }

    @Test
    void writeOfAnUnserialisableEventIsEmptyNotFatal() throws IOException {
        ObjectMapper broken = mock(ObjectMapper.class);
        when(broken.writeValueAsString(any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("disk full") {
                });

        assertThat(AiEventCodec.write(broken, 1L, new TimelineEvent.User("hello", null))).isEmpty();
    }

    @Test
    void writeOfAnEventWithoutATypeDiscriminatorIsEmpty() throws IOException {
        // the discriminator is the polymorphic handle: a payload without one
        // cannot be read back, so it must not be persisted at all
        ObjectMapper discriminatorless = mock(ObjectMapper.class);
        when(discriminatorless.writeValueAsString(any())).thenReturn("{\"text\":\"hello\"}");
        when(discriminatorless.readTree(anyString()))
                .thenReturn(mapper.readTree("{\"text\":\"hello\"}"));

        assertThat(AiEventCodec.write(discriminatorless, 1L, new TimelineEvent.User("hello", null)))
                .isEmpty();
    }

    @Test
    void readDecodesARowBackIntoTheEvent() throws IOException {
        TimelineEvent.User original = new TimelineEvent.User("hello", "enhanced");
        String json = mapper.writeValueAsString(original);

        Optional<TimelineEvent> decoded = AiEventCodec.read(mapper, row(json));

        assertThat(decoded).contains(original);
    }

    @Test
    void readOfANullRowOrBlankPayloadIsEmpty() {
        assertThat(AiEventCodec.read(mapper, null)).isEmpty();
        assertThat(AiEventCodec.read(mapper, row(null))).isEmpty();
        assertThat(AiEventCodec.read(mapper, row("   "))).isEmpty();
    }

    @Test
    void readOfAnUnreadableRowIsEmptyNeverFatal() {
        // a payload written by an older build or truncated by a full disk must
        // not cost the user the whole conversation
        assertThat(AiEventCodec.read(mapper, row("{\"type\":\"user\",\"text\":"))).isEmpty();
        assertThat(AiEventCodec.read(mapper, row("not json at all"))).isEmpty();
        assertThat(AiEventCodec.read(mapper, row("{\"type\":42}"))).isEmpty();
    }

    @Test
    void theRoundTripPreservesTheSubtype() throws IOException {
        TimelineEvent.Error original = new TimelineEvent.Error("llm.provider.error", "boom", "retry");
        String json = AiEventCodec.write(mapper, 9L, original).orElseThrow().json();

        Optional<TimelineEvent> decoded = AiEventCodec.read(mapper, row(json));

        assertThat(decoded).contains(original);
    }
}
