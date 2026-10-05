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

/**
 * Case variants of system topic names must be recognized as system topics.
 *
 * The paged SQL filter compares under the column collation (utf8mb4,
 * case-insensitive on production MySQL), so "RMQ_SYS_foo" was creatable but
 * then silently hidden from the paged console list while the non-paged list
 * showed it — the two paths disagreed.
 */
class SystemTopicCaseVariantTest {
    @Test
    void caseVariantSystemTopicPrefixesAreSystemTest() {
        assertThat(SystemTopicFilter.isSystem("RMQ_SYS_foo")).isTrue();
        assertThat(SystemTopicFilter.isSystem("rmq_sys_foo")).isTrue();
    }

    @Test
    void caseVariantRetryAndDlqPrefixesAreSystemTest() {
        assertThat(SystemTopicFilter.isSystem("%retry%mygroup")).isTrue();
        assertThat(SystemTopicFilter.isSystem("%RETRY%mygroup")).isTrue();
        assertThat(SystemTopicFilter.isSystem("%dlq%mygroup")).isTrue();
    }

    @Test
    void canonicalSystemNamesStillSystemTest() {
        assertThat(SystemTopicFilter.isSystem("TBW102")).isTrue();
        assertThat(SystemTopicFilter.isSystem("SCHEDULE_TOPIC_XXXX")).isTrue();
    }

    @Test
    void normalTopicsAreNotSystemTest() {
        assertThat(SystemTopicFilter.isSystem("my-app-topic")).isFalse();
        assertThat(SystemTopicFilter.isSystem("orders")).isFalse();
    }
}
