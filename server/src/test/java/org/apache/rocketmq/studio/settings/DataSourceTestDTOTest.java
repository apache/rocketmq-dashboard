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
package org.apache.rocketmq.studio.settings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceTestDTOTest {

    @Test
    void toStringShouldNotExposeCredentials() {
        DataSourceTestDTO request = DataSourceTestDTO.builder()
            .url("http://prometheus:9090")
            .type("prometheus")
            .auth("bearer token")
            .username("prometheus-user")
            .password("plain-password")
            .bearerToken("plain-token")
            .build();

        String value = request.toString();

        assertThat(value).contains("url=http://prometheus:9090");
        assertThat(value).contains("username=prometheus-user");
        assertThat(value).doesNotContain("plain-password");
        assertThat(value).doesNotContain("plain-token");
    }
}
