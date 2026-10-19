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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AliyunResponseValidatorTest {
    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {true})
    void presentBodyWithTrueOrOmittedFlagRemainsReadableTest(Boolean success) {
        Body body = new Body(success, "ignored-code", "ignored-message");
        assertThat(validate(body)).isSameAs(body);
    }

    @Test
    void explicitFailureIsBusinessRejectionWithProviderMessageTest() {
        assertThatThrownBy(() -> validate(new Body(false, "Denied", "permission denied")))
                .isInstanceOf(BusinessException.class).hasMessage("Aliyun test read rejected: permission denied")
                .extracting("code").isEqualTo(422);
    }

    @Test
    void blankMessageFallsBackToProviderCodeTest() {
        assertThatThrownBy(() -> validate(new Body(false, "Denied", "  ")))
                .isInstanceOf(BusinessException.class).hasMessage("Aliyun test read rejected: Denied")
                .extracting("code").isEqualTo(422);
    }

    @Test
    void missingMessageAndCodeUseBusinessFallbackTest() {
        assertThatThrownBy(() -> validate(new Body(false, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Aliyun test read rejected: provider rejected the request")
                .extracting("code").isEqualTo(422);
    }

    @Test
    void missingBodyIsMalformedUpstreamResponseTest() {
        assertThatThrownBy(() -> validate(null)).isInstanceOf(BusinessException.class)
                .hasMessage("Aliyun test read returned an empty response body")
                .extracting("code").isEqualTo(502);
    }

    private static Body validate(Body body) {
        return AliyunResponseValidator.requireReadableBody("test read", body,
                Body::success, Body::code, Body::message);
    }

    private record Body(Boolean success, String code, String message) {
    }
}
