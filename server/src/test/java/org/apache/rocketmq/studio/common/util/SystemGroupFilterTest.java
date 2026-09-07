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

class SystemGroupFilterTest {

    @Test
    void shouldRecognizeKnownSystemConsumerGroupPrefixesTest() {
        assertThat(SystemGroupFilter.isSystem(null)).isTrue();
        assertThat(SystemGroupFilter.isSystem("")).isTrue();
        assertThat(SystemGroupFilter.isSystem("CID_RMQ_SYS_TRANS")).isTrue();
        assertThat(SystemGroupFilter.isSystem("CID_ONSAPI_OWNER")).isTrue();
        assertThat(SystemGroupFilter.isSystem("CID_SYS_RMQ_TRANS")).isTrue();
        assertThat(SystemGroupFilter.isSystem("CID_HOUSEKEEPING")).isTrue();
        assertThat(SystemGroupFilter.isSystem("rmq_sys_TRACE_DATA")).isTrue();
        assertThat(SystemGroupFilter.isSystem("%RETRY%consumer-a")).isTrue();
        assertThat(SystemGroupFilter.isSystem("%DLQ%consumer-a")).isTrue();
        assertThat(SystemGroupFilter.isSystem("DEFAULT_CONSUMER")).isTrue();
        assertThat(SystemGroupFilter.isSystem("TOOLS_CONSUMER")).isTrue();
        assertThat(SystemGroupFilter.isSystem("SCHEDULE_CONSUMER")).isTrue();
        assertThat(SystemGroupFilter.isSystem("FILTERSRV_CONSUMER")).isTrue();
        assertThat(SystemGroupFilter.isSystem("__MONITOR_CONSUMER")).isTrue();
        assertThat(SystemGroupFilter.isSystem("SELF_TEST_C_GROUP")).isTrue();

        assertThat(SystemGroupFilter.isSystem("order-service-consumer")).isFalse();
        assertThat(SystemGroupFilter.isSystem("TOOLS_CONSUMER_monitor")).isFalse();
        assertThat(SystemGroupFilter.isSystem("FILTERSRV_CONSUMER_filter")).isFalse();
        assertThat(SystemGroupFilter.isSystem("SELF_TEST_GROUP")).isFalse();
    }
}
