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
package org.apache.rocketmq.studio.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlLikeUtilsTest {

    @Test
    void shouldEscapeEveryWildcardAndTheEscapeCharacterTest() {
        assertThat(SqlLikeUtils.escape("100%_done")).isEqualTo("100\\%\\_done");
        assertThat(SqlLikeUtils.escape("%")).isEqualTo("\\%");
        assertThat(SqlLikeUtils.escape("_")).isEqualTo("\\_");
        assertThat(SqlLikeUtils.escape("a\\b")).isEqualTo("a\\\\b");
        assertThat(SqlLikeUtils.escape("\\%_")).isEqualTo("\\\\\\%\\_");
    }

    @Test
    void shouldLeavePlainTextUntouchedTest() {
        assertThat(SqlLikeUtils.escape("orders")).isEqualTo("orders");
        assertThat(SqlLikeUtils.escape("prod-cn 01")).isEqualTo("prod-cn 01");
        assertThat(SqlLikeUtils.escape("a-b.c")).isEqualTo("a-b.c");
    }

    @Test
    void shouldReturnBlankValuesUnchangedTest() {
        assertThat(SqlLikeUtils.escape(null)).isNull();
        assertThat(SqlLikeUtils.escape("")).isEmpty();
        assertThat(SqlLikeUtils.escape("   ")).isEqualTo("   ");
    }

    @Test
    void containsShouldEscapeAndWrapTheTermTest() {
        assertThat(SqlLikeUtils.contains("prod_user")).isEqualTo("%prod\\_user%");
        assertThat(SqlLikeUtils.contains("100%")).isEqualTo("%100\\%%");
    }

    @Test
    void containsShouldReturnNullForABlankTermTest() {
        // The callers use the condition and the bound value as a pair, so a blank term must not
        // turn into a %% pattern that matches everything.
        assertThat(SqlLikeUtils.contains(null)).isNull();
        assertThat(SqlLikeUtils.contains("")).isNull();
        assertThat(SqlLikeUtils.contains("   ")).isNull();
    }

    @Test
    void shouldNameTheEscapeCharacterInThePredicateTest() {
        assertThat(SqlLikeUtils.likePredicate("name"))
                .isEqualTo("name LIKE {0} ESCAPE CHAR(92)");
        // One bound value, so callers pair it with contains().
        assertThat(SqlLikeUtils.likePredicate("resource_name").split("\\{").length - 1).isEqualTo(1);
    }

    @Test
    void escapeClauseShouldSpellTheBackslashByCodePointTest() {
        // A quoted backslash cannot be written once for both engines: ESCAPE '\' is a syntax error
        // on MySQL and ESCAPE '\\' is rejected by H2. See SqlLikeEscapingAgainstDatabaseTest for
        // the executable form of this check.
        assertThat(SqlLikeUtils.LIKE_ESCAPE_CLAUSE)
                .isEqualTo(" ESCAPE CHAR(92)")
                .doesNotContain("'");
    }
}
