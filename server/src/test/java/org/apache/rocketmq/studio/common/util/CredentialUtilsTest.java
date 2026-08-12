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

class CredentialUtilsTest {

    @Test
    void encodeDecodeRoundTrips() {
        String secret = "sk-test-credential-12345";
        assertThat(CredentialUtils.decodeBase64(CredentialUtils.encodeBase64(secret)))
                .isEqualTo(secret);
    }

    @Test
    void decodeReturnsLegacyPlainTextWhenItIsNotCanonicalBase64() {
        // A legacy plain-text key that happens to be valid base64 characters must come back
        // verbatim instead of being decoded into garbage.
        assertThat(CredentialUtils.decodeBase64("abcd1234")).isEqualTo("abcd1234");
        assertThat(CredentialUtils.decodeBase64("not-base64!!")).isEqualTo("not-base64!!");
    }

    @Test
    void decodeHandlesNullAndEmpty() {
        assertThat(CredentialUtils.decodeBase64(null)).isNull();
        assertThat(CredentialUtils.decodeBase64("")).isEmpty();
    }
}