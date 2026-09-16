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
}
