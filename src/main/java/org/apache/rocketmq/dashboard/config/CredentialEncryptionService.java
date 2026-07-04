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
package org.apache.rocketmq.dashboard.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Credential encryption service for cloud provider access keys.
 *
 * <p>Provides AES-256-GCM authenticated encryption for sensitive credentials
 * (accessKey/secretKey) stored in {@link org.apache.rocketmq.dashboard.model.CloudProviderConfig}.</p>
 *
 * <p>Per RIP-1 AUTH-01, cloud provider credentials must be encrypted at rest.
 * This service ensures that credentials are never stored in plain text in memory
 * caches or persistent storage.</p>
 *
 * <h3>Encryption Algorithm</h3>
 * <ul>
 *   <li>AES-256-GCM (authenticated encryption with associated data)</li>
 *   <li>12-byte IV (nonce) per encryption operation</li>
 *   <li>128-bit authentication tag</li>
 *   <li>Output format: Base64(IV + ciphertext + tag)</li>
 * </ul>
 *
 * <h3>Key Management</h3>
 * <p>The encryption key is configured via {@code rocketmq.config.credentialEncryptionKey}
 * in application.yml or environment variable. If not configured, a random key is
 * generated at startup (credentials will not survive restart).</p>
 *
 * <h3>Thread Safety</h3>
 * <p>This service is thread-safe. Each encryption/decryption operation creates
 * a new Cipher instance with a fresh IV.</p>
 */
@Service
public class CredentialEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(CredentialEncryptionService.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String KEY_ALGORITHM = "AES";

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec keySpec;

    /**
     * Construct the encryption service with the configured key.
     *
     * @param encryptionKey the AES-256 key (must be 32 bytes when Base64-decoded),
     *                      or a plain-text key that will be padded/truncated to 32 bytes.
     *                      If empty, a random key is generated (credentials won't survive restart).
     */
    public CredentialEncryptionService(
            @Value("${rocketmq.config.credentialEncryptionKey:}") String encryptionKey) {
        if (encryptionKey == null || encryptionKey.trim().isEmpty()) {
            // Generate a random key - credentials won't survive restart
            byte[] randomKey = new byte[32];
            secureRandom.nextBytes(randomKey);
            this.keySpec = new SecretKeySpec(randomKey, KEY_ALGORITHM);
            log.warn("No credentialEncryptionKey configured. Using random key - "
                + "encrypted credentials will NOT survive application restart. "
                + "Please set 'rocketmq.config.credentialEncryptionKey' in application.yml.");
        } else {
            byte[] keyBytes = deriveKey(encryptionKey);
            this.keySpec = new SecretKeySpec(keyBytes, KEY_ALGORITHM);
            log.info("CredentialEncryptionService initialized with configured key");
        }
    }

    /**
     * Encrypt a plain-text credential string.
     *
     * @param plainText the credential to encrypt (e.g., accessKey, secretKey)
     * @return Base64-encoded encrypted value (IV + ciphertext + tag), or empty string if input is null/empty
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return "";
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new CredentialEncryptionException("Failed to encrypt credential", e);
        }
    }

    /**
     * Decrypt an encrypted credential string.
     *
     * @param encryptedText Base64-encoded encrypted value (IV + ciphertext + tag)
     * @return decrypted plain-text credential, or empty string if input is null/empty
     * @throws CredentialEncryptionException if decryption fails (wrong key, corrupted data)
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return "";
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText);

            // Extract IV from the beginning
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new CredentialEncryptionException("Failed to decrypt credential. "
                + "This may indicate a wrong encryption key or corrupted data.", e);
        }
    }

    /**
     * Check if a string appears to be encrypted (Base64-encoded with sufficient length).
     * This is a heuristic check - not cryptographic verification.
     *
     * @param value the string to check
     * @return true if the value appears to be encrypted
     */
    public boolean isEncrypted(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            // Encrypted values must have at least IV (12) + tag (16) = 28 bytes
            return decoded.length >= GCM_IV_LENGTH + 16;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Derive a 32-byte AES key from the configured key string.
     * Uses simple padding/truncation for plain-text keys.
     * For production, consider using PBKDF2 or HKDF.
     */
    private byte[] deriveKey(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] derived = new byte[32]; // AES-256 requires 32 bytes

        if (keyBytes.length >= 32) {
            System.arraycopy(keyBytes, 0, derived, 0, 32);
        } else {
            // Pad with repeated key bytes
            for (int i = 0; i < 32; i++) {
                derived[i] = keyBytes[i % keyBytes.length];
            }
        }
        return derived;
    }

    /**
     * Exception thrown when credential encryption/decryption fails.
     */
    public static class CredentialEncryptionException extends RuntimeException {
        public CredentialEncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}