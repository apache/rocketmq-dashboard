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
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link BusinessException}, the error envelope every REST endpoint answers with: the
 * numeric code plus the human message. The {@link HttpStatus} convenience constructor must
 * delegate the status's numeric value as the code — the global exception handler maps code
 * back to the HTTP response, so a wrong delegation would turn a 404 into a 500.
 */
class BusinessExceptionTest {

    @Test
    void theNumericConstructorCarriesCodeAndMessage() {
        BusinessException exception = new BusinessException(501, "Provider is not configured");

        assertThat(exception.getCode()).isEqualTo(501);
        assertThat(exception.getMessage()).isEqualTo("Provider is not configured");
    }

    @Test
    void theHttpStatusConstructorDelegatesTheNumericValue() {
        assertThat(new BusinessException(HttpStatus.NOT_FOUND, "no such topic").getCode()).isEqualTo(404);
        assertThat(new BusinessException(HttpStatus.BAD_GATEWAY, "upstream broke").getCode()).isEqualTo(502);
        assertThat(new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "overloaded").getCode()).isEqualTo(503);

        assertThat(new BusinessException(HttpStatus.NOT_FOUND, "no such topic").getMessage())
                .isEqualTo("no such topic");
    }

    @Test
    void itIsARuntimeExceptionSoItTravelsUnchecked() {
        BusinessException exception = new BusinessException(400, "bad request");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat((RuntimeException) exception).hasMessage("bad request");
    }
}
