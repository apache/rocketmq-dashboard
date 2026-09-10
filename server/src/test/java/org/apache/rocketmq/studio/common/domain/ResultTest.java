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
package org.apache.rocketmq.studio.common.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResultTest {

    @Test
    void okShouldCarryDataWithTheSuccessContract() {
        Result<String> result = Result.ok("payload");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMessage()).isEqualTo("success");
        assertThat(result.getData()).isEqualTo("payload");
    }

    @Test
    void okWithoutDataShouldLeaveDataNull() {
        Result<Void> result = Result.ok();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMessage()).isEqualTo("success");
        assertThat(result.getData()).isNull();
    }

    @Test
    void errorShouldKeepTheCodeAndMessageWithoutData() {
        Result<Object> result = Result.error(400, "bad request");

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).isEqualTo("bad request");
        assertThat(result.getData()).isNull();
    }

    @Test
    void errorShouldCarryTheBusinessStatusCodeForNon2xxFailures() {
        Result<Object> conflict = Result.error(409, "conflict");
        Result<Object> unavailable = Result.error(503, "unavailable");

        assertThat(conflict.getCode()).isEqualTo(409);
        assertThat(unavailable.getCode()).isEqualTo(503);
    }
}
