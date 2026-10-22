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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The suggestion parser is the half of the follow-up feature that can lie to the operator: whatever
 * the model returns becomes a clickable chip whose text is sent verbatim as the next question, so
 * numbering, bullets and quotes the model adds despite being told not to must not survive into it.
 * Fixtures are ASCII because checkstyle forbids literal CJK characters in Java sources; the parser
 * itself is language-agnostic, and its CJK decoration stripping is spelled as unicode escapes.
 */
class AiFollowupServiceTest {

    @Test
    void parsesOneSuggestionPerLineUpToThreeTest() {
        assertThat(AiFollowupService.parse(
                "show consumer offsets\ndiagnose the backlog\nlist groups\na fourth line is dropped"))
                .containsExactly("show consumer offsets", "diagnose the backlog", "list groups");
    }

    @Test
    void stripsNumberingBulletsAndQuotesTheModelAddsAnywayTest() {
        assertThat(AiFollowupService.parse(
                "1. show consumer offsets\n- diagnose the backlog\n* \"reset the offset\""))
                .containsExactly("show consumer offsets", "diagnose the backlog",
                        "reset the offset");
    }

    @Test
    void stripsFullWidthAndCjkDecorationSpelledAsEscapesTest() {
        // U+FF1A full-width colon, U+3001 CJK enumeration comma, U+201C/U+201D curly quotes, all
        // spelled as escapes because checkstyle forbids the literals in Java sources.
        assertThat(AiFollowupService.parse(
                "\uFF1Ashow consumer offsets\n\u3001diagnose\n\u201Creset the offset\u201D"))
                .containsExactly("show consumer offsets", "diagnose", "reset the offset");
    }

    @Test
    void dropsBlankOverlongAndDuplicateLinesTest() {
        String overlong = "x".repeat(61);
        assertThat(AiFollowupService.parse("\n   \n" + overlong + "\nshow offsets\nshow offsets"))
                .containsExactly("show offsets");
    }

    @Test
    void returnsEmptyForAnEmptyReplyTest() {
        assertThat(AiFollowupService.parse("")).isEqualTo(List.of());
        assertThat(AiFollowupService.parse("\n\n")).isEqualTo(List.of());
    }
}
