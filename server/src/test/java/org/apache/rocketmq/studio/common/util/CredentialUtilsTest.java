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

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialUtilsTest {

    @Test
    void decodeBase64ShouldPreserveLegacyTextWhenDecodedBytesAreNotUtf8() {
        String legacyValue = Base64.getEncoder().encodeToString(new byte[]{(byte) 0xC3, 0x28});

        assertThat(CredentialUtils.decodeBase64(legacyValue)).isEqualTo(legacyValue);
    }

    @Test
    void maskShouldPreserveAsciiBehavior() {
        assertThat(CredentialUtils.mask("abcdefghijklmnopq"))
                .isEqualTo("abcd****nopq");
        assertThat(CredentialUtils.mask("abcdefghijklmnop"))
                .isEqualTo("****");
    }

    @Test
    void maskShouldUseSupplementaryCodePointBoundaries() {
        String value = "abc🚀" + "123456789" + "🛰xyz";

        String masked = CredentialUtils.mask(value);

        assertThat(masked).isEqualTo("abc🚀****🛰xyz");
        assertThat(hasIsolatedSurrogate(masked)).isFalse();
    }

    @Test
    void maskShouldKeepFourCodePointsAtEachEnd() {
        String value = "🚀abc" + "123456789" + "xyz🛰";

        assertThat(CredentialUtils.mask(value)).isEqualTo("🚀abc****xyz🛰");
    }

    @Test
    void maskShouldMeasureShortValuesInCodePoints() {
        assertThat(CredentialUtils.mask("🚀".repeat(16))).isEqualTo("****");
        assertThat(CredentialUtils.mask("🚀".repeat(17)))
                .isEqualTo("🚀".repeat(4) + "****" + "🚀".repeat(4));
    }

    @Test
    void maskShouldPreserveNullAndEmptyValues() {
        assertThat(CredentialUtils.mask(null)).isNull();
        assertThat(CredentialUtils.mask("")).isEmpty();
    }

    @Test
    void maskShouldFullyHideMalformedUtf16WithoutLeakingSurrogates() {
        String malformed = "abcd" + '\uD83D' + "1234567890123456";

        String masked = CredentialUtils.mask(malformed);

        assertThat(masked).isEqualTo("****");
        assertThat(hasIsolatedSurrogate(masked)).isFalse();
    }

    @Test
    void encodeBase64ShouldRoundTripAsciiSecrets() {
        String secret = "AKIAIOSFODNN7EXAMPLE";

        String encoded = CredentialUtils.encodeBase64(secret);

        assertThat(encoded).isNotEqualTo(secret);
        assertThat(CredentialUtils.decodeBase64(encoded)).isEqualTo(secret);
    }

    @Test
    void encodeBase64ShouldRoundTripMultibyteText() {
        String secret = "accessKey\u4e2d\u6587\u5bc6\u94a5\ud83d\ude80";

        String encoded = CredentialUtils.encodeBase64(secret);

        assertThat(encoded).isNotEqualTo(secret);
        assertThat(CredentialUtils.decodeBase64(encoded)).isEqualTo(secret);
    }

    @Test
    void encodeBase64ShouldPreserveNull() {
        assertThat(CredentialUtils.encodeBase64(null)).isNull();
    }

    @Test
    void decodeBase64ShouldPreserveNull() {
        assertThat(CredentialUtils.decodeBase64(null)).isNull();
    }

    @Test
    void decodeBase64ShouldReturnInvalidPlaintextAsIs() {
        String legacy = "plain-text-access-key!";

        assertThat(CredentialUtils.decodeBase64(legacy)).isEqualTo(legacy);
    }

    private static boolean hasIsolatedSurrogate(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return true;
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                return true;
            }
        }
        return false;
    }
}
