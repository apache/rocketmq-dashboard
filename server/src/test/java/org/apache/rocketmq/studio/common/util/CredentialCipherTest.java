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

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialCipherTest {

    private static final String KEY_A = key(1);
    private static final String KEY_B = key(2);

    @Test
    void encryptShouldSealWithTheVersionedPrefixAndHideThePlaintextTest() {
        CredentialCipher cipher = new CredentialCipher(KEY_A);

        String sealed = cipher.encrypt("super-secret-value");

        assertThat(sealed).startsWith(CredentialCipher.PREFIX);
        assertThat(sealed).doesNotContain("super-secret-value");
        assertThat(cipher.decrypt(sealed)).isEqualTo("super-secret-value");
    }

    @Test
    void encryptShouldUseAFreshIvPerCallTest() {
        CredentialCipher cipher = new CredentialCipher(KEY_A);

        String first = cipher.encrypt("same-value");
        String second = cipher.encrypt("same-value");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("same-value");
        assertThat(cipher.decrypt(second)).isEqualTo("same-value");
    }

    @Test
    void decryptShouldReadLegacyBase64ValuesTest() {
        CredentialCipher cipher = new CredentialCipher(KEY_A);
        String legacy = CredentialUtils.encodeBase64("legacy-secret");

        assertThat(cipher.decrypt(legacy)).isEqualTo("legacy-secret");
    }

    @Test
    void decryptShouldReadLegacyValuesThatAreNotBase64Test() {
        CredentialCipher cipher = new CredentialCipher(KEY_A);

        assertThat(cipher.decrypt("not base64 !!!")).isEqualTo("not base64 !!!");
    }

    @Test
    void decryptShouldRejectTamperedCiphertextTest() {
        CredentialCipher cipher = new CredentialCipher(KEY_A);
        String sealed = cipher.encrypt("super-secret-value");

        String body = sealed.substring(CredentialCipher.PREFIX.length());
        byte[] raw = Base64.getDecoder().decode(body);
        raw[raw.length - 1] ^= 0x01;
        String tampered = CredentialCipher.PREFIX + Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("could not be decrypted")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(500));
    }

    @Test
    void decryptShouldRejectValuesSealedWithAnotherKeyTest() {
        String sealedWithOtherKey = new CredentialCipher(KEY_B).encrypt("super-secret-value");

        assertThatThrownBy(() -> new CredentialCipher(KEY_A).decrypt(sealedWithOtherKey))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("could not be decrypted");
    }

    @Test
    void encryptAndDecryptShouldPassNullThroughTest() {
        CredentialCipher cipher = new CredentialCipher(KEY_A);

        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    void isEncryptedShouldOnlyMatchTheSealedPrefixTest() {
        assertThat(CredentialCipher.isEncrypted("enc:v1:AAAA")).isTrue();
        assertThat(CredentialCipher.isEncrypted("c2VjcmV0")).isFalse();
        assertThat(CredentialCipher.isEncrypted("enc:v0:AAAA")).isFalse();
        assertThat(CredentialCipher.isEncrypted(null)).isFalse();
    }

    @Test
    void constructorShouldReportAConfiguredKeyTest() {
        assertThat(new CredentialCipher(KEY_A).usesConfiguredKey()).isTrue();
        assertThat(new CredentialCipher("").usesConfiguredKey()).isFalse();
        assertThat(new CredentialCipher(null).usesConfiguredKey()).isFalse();
    }

    @Test
    void constructorShouldRejectAKeyThatIsNot32BytesTest() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new CredentialCipher(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly 32 bytes");
    }

    @Test
    void constructorShouldRejectAKeyThatIsNotBase64Test() {
        assertThatThrownBy(() -> new CredentialCipher("not-base64!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64-encoded 32-byte AES key");
    }

    @Test
    void fallbackKeyShouldStayStableAcrossInstancesTest() {
        CredentialCipher first = new CredentialCipher("");
        CredentialCipher second = new CredentialCipher(null);

        String sealed = first.encrypt("shared-secret");

        assertThat(second.decrypt(sealed)).isEqualTo("shared-secret");
    }

    private static String key(int seed) {
        byte[] bytes = new byte[32];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) (seed + index);
        }
        return Base64.getEncoder().encodeToString(bytes);
    }
}
