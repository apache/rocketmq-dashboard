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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the wire shape of {@link DataSourceTestResultVO}, the answer of the settings
 * "test connection" endpoint: the UI renders a success toast or an error banner purely from
 * {@code success} + {@code message}, so the verdict must survive a JSON round-trip and all
 * three construction paths (builder, all-args, no-args + setters) must agree.
 */
class DataSourceTestResultVOTest {

    @Test
    void theVerdictRoundTripsThroughJson() throws Exception {
        DataSourceTestResultVO result = DataSourceTestResultVO.builder()
                .success(false)
                .message("Connection refused: jdbc:mysql://10.0.0.1:3306")
                .build();

        String json = new ObjectMapper().writeValueAsString(result);
        assertThat(json).contains("\"success\":false");
        assertThat(json).contains("\"message\":\"Connection refused: jdbc:mysql://10.0.0.1:3306\"");

        DataSourceTestResultVO back = new ObjectMapper().readValue(json, DataSourceTestResultVO.class);
        assertThat(back.isSuccess()).isFalse();
        assertThat(back.getMessage()).isEqualTo("Connection refused: jdbc:mysql://10.0.0.1:3306");
    }

    @Test
    void allConstructionPathsAgree() {
        DataSourceTestResultVO allArgs = new DataSourceTestResultVO(true, "reachable");
        DataSourceTestResultVO built = DataSourceTestResultVO.builder()
                .success(true)
                .message("reachable")
                .build();
        DataSourceTestResultVO defaulted = new DataSourceTestResultVO();
        defaulted.setSuccess(true);
        defaulted.setMessage("reachable");

        assertThat(allArgs).isEqualTo(built).isEqualTo(defaulted);
        assertThat(allArgs.isSuccess()).isTrue();
        assertThat(allArgs.getMessage()).isEqualTo("reachable");
    }
}
