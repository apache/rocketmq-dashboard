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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Where a thinking block came from. Serialised as the lowercase wire name
 * ({@code "model"} / {@code "enhance"}) in both directions.
 *
 * <p>This single field is what keeps two very different things apart. The agent CLI streams
 * model reasoning (its chain of thought) as thinking deltas, and Studio's own prompt-enhancement
 * step also produces a rewritten prompt that used to be streamed through the same channel. Without
 * a source the UI coalesced both into one block under a heading that says chain of thought, i.e.
 * the prompt rewrite was presented to the user as model reasoning. Blocks with different sources
 * must never be merged, on either the live or the persisted side.
 */
public enum ThinkingSource {

    /** Reasoning produced by the model itself. Rendered under the chain-of-thought heading. */
    MODEL("model"),

    /** Studio's prompt-enhancement rewrite. Rendered as the enhanced prompt, never as reasoning. */
    ENHANCE("enhance");

    private final String wireName;

    ThinkingSource(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    /**
     * Strict on purpose: an unknown source means the two sides drifted, and silently falling back
     * to {@link #MODEL} would reintroduce the coalescing bug this enum exists to prevent.
     */
    @JsonCreator
    public static ThinkingSource fromWireName(String wireName) {
        for (ThinkingSource source : values()) {
            if (source.wireName.equals(wireName)) {
                return source;
            }
        }
        throw new IllegalArgumentException("unknown thinking source: " + wireName);
    }
}
