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
package org.apache.rocketmq.studio.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    void codesWithinTheErrorBandsShouldPassThrough() {
        assertThat(new BusinessException(400, "bad").getCode()).isEqualTo(400);
        assertThat(new BusinessException(401, "auth").getCode()).isEqualTo(401);
        assertThat(new BusinessException(404, "missing").getCode()).isEqualTo(404);
        assertThat(new BusinessException(422, "unprocessable").getCode()).isEqualTo(422);
        assertThat(new BusinessException(502, "upstream").getCode()).isEqualTo(502);
        assertThat(new BusinessException(599, "upper bound").getCode()).isEqualTo(599);
    }

    @Test
    void codesOutsideTheErrorBandsShouldFallBackToBadRequest() {
        assertThat(new BusinessException(0, "zero").getCode()).isEqualTo(400);
        assertThat(new BusinessException(200, "success-looking").getCode()).isEqualTo(400);
        assertThat(new BusinessException(301, "redirect-looking").getCode()).isEqualTo(400);
        assertThat(new BusinessException(600, "future").getCode()).isEqualTo(400);
        assertThat(new BusinessException(-7, "negative").getCode()).isEqualTo(400);
    }

    @Test
    void messageShouldPassThroughUnchanged() {
        BusinessException exception = new BusinessException(404, "row not found");

        assertThat(exception.getMessage()).isEqualTo("row not found");
    }
}
