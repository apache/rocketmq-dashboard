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
package org.apache.rocketmq.studio.ops.ai.conversation.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.config.LegacyJackson2Config;
import org.junit.jupiter.params.provider.Arguments;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.stream.Stream;

/**
 * The two Jackson mappers this server can hand an event to, as one test seam.
 *
 * <p>Jackson 3 is the Spring Boot 4 default and serialises SSE frames; Jackson 2 is still on the
 * classpath through {@link LegacyJackson2Config} and is what a caller gets when it injects
 * {@code ObjectMapper}. {@link LiveEvent} and {@link TimelineEvent} spell out
 * {@code @JsonSubTypes} precisely so both work, and every wire assertion in this package runs
 * against both to keep that promise honest.
 */
final class AiEventCodecs {

    /** Tree view of a written payload. Mapper-agnostic: the codecs already produced plain JSON. */
    static final JsonMapper TREES = JsonMapper.builder().build();

    private AiEventCodecs() {
    }

    static List<Codec> all() {
        return List.of(new Jackson3(), new Jackson2());
    }

    static Stream<Arguments> asArguments() {
        return all().stream().map(codec -> Arguments.of(codec.label(), codec));
    }

    interface Codec {

        String label();

        String write(Object event);

        <T> T read(String json, Class<T> baseType);
    }

    private static final class Jackson3 implements Codec {

        private final JsonMapper mapper = JsonMapper.builder().build();

        @Override
        public String label() {
            return "jackson3";
        }

        @Override
        public String write(Object event) {
            return mapper.writeValueAsString(event);
        }

        @Override
        public <T> T read(String json, Class<T> baseType) {
            return mapper.readValue(json, baseType);
        }
    }

    private static final class Jackson2 implements Codec {

        private final ObjectMapper mapper = new LegacyJackson2Config().jackson2ObjectMapper();

        @Override
        public String label() {
            return "jackson2";
        }

        @Override
        public String write(Object event) {
            try {
                return mapper.writeValueAsString(event);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("jackson2 cannot serialise " + event, e);
            }
        }

        @Override
        public <T> T read(String json, Class<T> baseType) {
            try {
                return mapper.readValue(json, baseType);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("jackson2 cannot deserialise " + json, e);
            }
        }
    }
}
