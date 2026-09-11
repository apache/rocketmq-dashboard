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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Envelope encryption for stored provider credentials (AK/SK secret keys).
 *
 * <p>Secrets are sealed with AES-256-GCM and persisted as {@code enc:v1:<base64(iv || ciphertext || tag)>}.
 * GCM is authenticated, so a tampered row fails to open instead of silently returning corrupted key
 * material, and every value carries a fresh 96-bit random IV so identical secrets do not collide in
 * the database. This replaces the earlier scheme that merely base64-<em>encoded</em> secrets, which
 * provided obfuscation but no confidentiality: anyone with read access to {@code rmq_cloud_credential}
 * could recover the plaintext key.</p>
 *
 * <p>The AES key comes from {@code studio.credential.encryption-key} (Base64-encoded 32 bytes, wired to
 * {@code STUDIO_CREDENTIAL_ENCRYPTION_KEY}). When the property is absent - convenient for local runs and
 * the bundled dev profile - a deterministic key is derived from a fixed application salt and a WARNING is
 * logged. That fallback still hides plaintext from the database, but it is <em>not</em> secret; production
 * deployments must set the property so the key lives outside the database it protects.</p>
 *
 * <p>Reading tolerates rows written by the legacy base64 scheme: values without the {@code enc:v1:} prefix
 * are decoded with {@link CredentialUtils#decodeBase64(String)} so an existing deployment keeps working and
 * can be re-sealed by {@code CredentialEncryptionMigration}.</p>
 */
@Component
@Slf4j
public class CredentialCipher {

    /** Marks a persisted value as AES-GCM sealed by this class; the version allows future rotation. */
    public static final String PREFIX = "enc:v1:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;
    private static final String FALLBACK_KEY_SALT = "rocketmq-studio/credential-encryption/v1";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKey key;
    private final boolean configuredKey;

    @Autowired
    public CredentialCipher(
            @Value("${studio.credential.encryption-key:}") String configuredKey) {
        if (configuredKey != null && !configuredKey.isBlank()) {
            this.key = keyFromBase64(configuredKey);
            this.configuredKey = true;
        } else {
            this.key = keyFromPassphrase(FALLBACK_KEY_SALT);
            this.configuredKey = false;
        }
    }

    /** Derives a stable AES-256 key from a Base64-encoded 32-byte value. */
    static SecretKey keyFromBase64(String configuredKey) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configuredKey.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "studio.credential.encryption-key must be a Base64-encoded 32-byte AES key",
                    exception);
        }
        if (decoded.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("studio.credential.encryption-key must decode to exactly "
                    + KEY_LENGTH_BYTES + " bytes but was " + decoded.length);
        }
        return new SecretKeySpec(decoded, KEY_ALGORITHM);
    }

    /** Derives a stable AES-256 key from an arbitrary passphrase (dev fallback only). */
    static SecretKey keyFromPassphrase(String passphrase) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(passphrase.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, KEY_ALGORITHM);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to derive the credential encryption key", exception);
        }
    }

    /** True when the stored value was sealed by this class rather than by the legacy base64 scheme. */
    public static boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    /** True when the operator supplied an explicit key instead of the documented dev fallback. */
    public boolean usesConfiguredKey() {
        return configuredKey;
    }

    /**
     * Seals a plaintext secret. {@code null} passes through so callers can persist partially populated
     * rows without inventing ciphertext.
     */
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] sealed = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, sealed, 0, iv.length);
            System.arraycopy(cipherText, 0, sealed, iv.length, cipherText.length);
            return PREFIX + Base64.getEncoder().encodeToString(sealed);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt the cloud credential secret", exception);
        }
    }

    /**
     * Opens a stored secret. Values sealed by this class are decrypted; legacy values are decoded from
     * base64. A sealed value that cannot be opened - wrong key or tampered ciphertext - is reported as a
     * server error instead of being returned as garbage.
     */
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!isEncrypted(stored)) {
            return CredentialUtils.decodeBase64(stored);
        }
        try {
            byte[] sealed = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (sealed.length <= IV_LENGTH_BYTES) {
                throw new GeneralSecurityException("sealed value is shorter than its IV");
            }
            byte[] iv = Arrays.copyOfRange(sealed, 0, IV_LENGTH_BYTES);
            byte[] cipherText = Arrays.copyOfRange(sealed, IV_LENGTH_BYTES, sealed.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new BusinessException(500,
                    "Stored cloud credential secret could not be decrypted; verify that "
                            + "studio.credential.encryption-key still matches the key the credential was "
                            + "sealed with");
        }
    }
}
