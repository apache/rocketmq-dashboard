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
package org.apache.rocketmq.dashboard.model;

import org.apache.rocketmq.dashboard.config.CredentialEncryptionService;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration model for cloud provider cluster access.
 *
 * <p>Encapsulates the credentials and connection parameters needed to
 * access a cloud-hosted RocketMQ instance (Aliyun, Tencent Cloud, Huawei Cloud, etc.).</p>
 *
 * <p>Per RIP-1 AUTH-01, cloud provider credentials (accessKey/secretKey) must be
 * encrypted at rest. This class supports transparent encryption/decryption via
 * {@link CredentialEncryptionService}:</p>
 * <ul>
 *   <li>{@link #encryptCredentials(CredentialEncryptionService)} - encrypts plain-text
 *       accessKey/secretKey and stores them in encryptedAccessKey/encryptedSecretKey</li>
 *   <li>{@link #decryptCredentials(CredentialEncryptionService)} - decrypts
 *       encryptedAccessKey/encryptedSecretKey back to accessKey/secretKey for use</li>
 *   <li>{@link #maskCredentials()} - returns a copy with credentials masked for API responses</li>
 * </ul>
 *
 * <p>Per RIP-1 ARCH-01, cloud providers are supported via SPI/Plugin pattern.
 * This config is used by {@link org.apache.rocketmq.dashboard.architecture.impl.cloud.CloudProviderFactory}
 * to instantiate the correct provider implementation.</p>
 */
public class CloudProviderConfig {

    /**
     * Cloud provider type identifier.
     * Matches {@link org.apache.rocketmq.dashboard.architecture.ClusterAccessType} cloud enum values.
     * Example: "cloud-aliyun", "cloud-tencent", "cloud-huawei"
     */
    private String providerType;

    /**
     * Cloud instance ID (e.g., Aliyun MQ instance ID).
     */
    private String instanceId;

    /**
     * AccessKey for cloud API authentication.
     * Holds plain-text value after decryption; null when encrypted at rest.
     * Never logged or returned in API responses.
     */
    private String accessKey;

    /**
     * SecretKey for cloud API authentication.
     * Holds plain-text value after decryption; null when encrypted at rest.
     * Never logged or returned in API responses.
     */
    private String secretKey;

    /**
     * Encrypted accessKey (Base64-encoded AES-256-GCM ciphertext).
     * Used for persistent storage and in-memory caching.
     */
    private String encryptedAccessKey;

    /**
     * Encrypted secretKey (Base64-encoded AES-256-GCM ciphertext).
     * Used for persistent storage and in-memory caching.
     */
    private String encryptedSecretKey;

    /**
     * Whether credentials are currently stored in encrypted form.
     */
    private boolean credentialsEncrypted = false;

    /**
     * Cloud region ID (e.g., "cn-hangzhou", "ap-southeast-1").
     */
    private String regionId;

    /**
     * Optional endpoint override for cloud API access.
     * If empty, the provider's default regional endpoint is used.
     */
    private String endpoint;

    /**
     * Display name for this cloud cluster in the dashboard.
     */
    private String displayName;

    /**
     * Whether this cloud cluster is currently enabled/active.
     */
    private boolean enabled = true;

    /**
     * Optional extended configuration parameters.
     * Cloud-specific settings that don't fit into standard fields.
     * Example: {"mqInstanceType": "premium", "vpcId": "vpc-xxx"}
     */
    private Map<String, String> extendedConfig = new HashMap<>();

    // ==================== Getters and Setters ====================

    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getEncryptedAccessKey() { return encryptedAccessKey; }
    public void setEncryptedAccessKey(String encryptedAccessKey) { this.encryptedAccessKey = encryptedAccessKey; }

    public String getEncryptedSecretKey() { return encryptedSecretKey; }
    public void setEncryptedSecretKey(String encryptedSecretKey) { this.encryptedSecretKey = encryptedSecretKey; }

    public boolean isCredentialsEncrypted() { return credentialsEncrypted; }
    public void setCredentialsEncrypted(boolean credentialsEncrypted) { this.credentialsEncrypted = credentialsEncrypted; }

    public String getRegionId() { return regionId; }
    public void setRegionId(String regionId) { this.regionId = regionId; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Map<String, String> getExtendedConfig() { return extendedConfig; }
    public void setExtendedConfig(Map<String, String> extendedConfig) { this.extendedConfig = extendedConfig; }

    // ==================== Credential Encryption ====================

    /**
     * Encrypt the plain-text accessKey/secretKey using the provided encryption service.
     *
     * <p>After encryption, the plain-text fields are set to null and
     * {@code credentialsEncrypted} is set to true. The encrypted values
     * are stored in {@code encryptedAccessKey} and {@code encryptedSecretKey}.</p>
     *
     * @param encryptionService the encryption service to use
     * @throws CredentialEncryptionService.CredentialEncryptionException if encryption fails
     */
    public void encryptCredentials(CredentialEncryptionService encryptionService) {
        if (accessKey != null && !accessKey.isEmpty()) {
            this.encryptedAccessKey = encryptionService.encrypt(accessKey);
        }
        if (secretKey != null && !secretKey.isEmpty()) {
            this.encryptedSecretKey = encryptionService.encrypt(secretKey);
        }
        // Clear plain-text credentials from memory
        this.accessKey = null;
        this.secretKey = null;
        this.credentialsEncrypted = true;
    }

    /**
     * Decrypt the encrypted accessKey/secretKey using the provided encryption service.
     *
     * <p>After decryption, the plain-text fields are populated for use by
     * cloud provider implementations. The encrypted values remain intact
     * for re-encryption if needed.</p>
     *
     * @param encryptionService the encryption service to use
     * @throws CredentialEncryptionService.CredentialEncryptionException if decryption fails
     */
    public void decryptCredentials(CredentialEncryptionService encryptionService) {
        if (encryptedAccessKey != null && !encryptedAccessKey.isEmpty()) {
            this.accessKey = encryptionService.decrypt(encryptedAccessKey);
        }
        if (encryptedSecretKey != null && !encryptedSecretKey.isEmpty()) {
            this.secretKey = encryptionService.decrypt(encryptedSecretKey);
        }
        this.credentialsEncrypted = false;
    }

    /**
     * Create a masked copy of this config for API responses.
     *
     * <p>The returned copy has accessKey/secretKey replaced with "********"
     * and encryptedAccessKey/encryptedSecretKey set to null to prevent
     * credential leakage in API responses and logs.</p>
     *
     * @return a new CloudProviderConfig with credentials masked
     */
    public CloudProviderConfig maskCredentials() {
        CloudProviderConfig masked = new CloudProviderConfig();
        masked.setProviderType(this.providerType);
        masked.setInstanceId(this.instanceId);
        masked.setAccessKey(this.accessKey != null ? "********" : null);
        masked.setSecretKey(this.secretKey != null ? "********" : null);
        masked.setEncryptedAccessKey(null); // Never expose encrypted values
        masked.setEncryptedSecretKey(null);
        masked.setCredentialsEncrypted(this.credentialsEncrypted);
        masked.setRegionId(this.regionId);
        masked.setEndpoint(this.endpoint);
        masked.setDisplayName(this.displayName);
        masked.setEnabled(this.enabled);
        masked.setExtendedConfig(this.extendedConfig);
        return masked;
    }

    // ==================== Validation ====================

    /**
     * Validate the essential fields for cloud provider creation.
     *
     * <p>Accepts either plain-text or encrypted credentials:
     * <ul>
     *   <li>If credentials are encrypted, validates encrypted fields are present</li>
     *   <li>If credentials are not encrypted, validates plain-text fields are present</li>
     * </ul></p>
     *
     * @throws IllegalArgumentException if required fields are missing
     */
    public void validate() {
        if (providerType == null || providerType.trim().isEmpty()) {
            throw new IllegalArgumentException("providerType is required");
        }
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("instanceId is required");
        }
        if (regionId == null || regionId.trim().isEmpty()) {
            throw new IllegalArgumentException("regionId is required");
        }

        if (credentialsEncrypted) {
            // Validate encrypted credentials are present
            if (encryptedAccessKey == null || encryptedAccessKey.isEmpty()) {
                throw new IllegalArgumentException("encryptedAccessKey is required when credentials are encrypted");
            }
            if (encryptedSecretKey == null || encryptedSecretKey.isEmpty()) {
                throw new IllegalArgumentException("encryptedSecretKey is required when credentials are encrypted");
            }
        } else {
            // Validate plain-text credentials are present
            if (accessKey == null || accessKey.trim().isEmpty()) {
                throw new IllegalArgumentException("accessKey is required");
            }
            if (secretKey == null || secretKey.trim().isEmpty()) {
                throw new IllegalArgumentException("secretKey is required");
            }
        }
    }

    // ==================== Convenience Methods ====================

    /**
     * Check if this config represents an Aliyun cloud instance.
     */
    public boolean isAliyun() {
        return "cloud-aliyun".equals(providerType);
    }

    /**
     * Check if this config represents a Tencent Cloud instance.
     */
    public boolean isTencent() {
        return "cloud-tencent".equals(providerType);
    }

    /**
     * Check if this config represents a Huawei Cloud instance.
     */
    public boolean isHuawei() {
        return "cloud-huawei".equals(providerType);
    }

    @Override
    public String toString() {
        return "CloudProviderConfig{" +
            "providerType='" + providerType + '\'' +
            ", instanceId='" + instanceId + '\'' +
            ", regionId='" + regionId + '\'' +
            ", endpoint='" + endpoint + '\'' +
            ", displayName='" + displayName + '\'' +
            ", enabled=" + enabled +
            ", credentialsEncrypted=" + credentialsEncrypted +
            '}';
        // Intentionally excludes accessKey, secretKey, encryptedAccessKey, encryptedSecretKey
    }
}