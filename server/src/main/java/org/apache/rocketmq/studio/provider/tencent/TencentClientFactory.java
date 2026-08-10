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
package org.apache.rocketmq.studio.provider.tencent;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.trocket.v20230308.TrocketClient;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.provider.credential.CloudCredentialRepository;
import org.apache.rocketmq.studio.provider.credential.CloudCredentialVO;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds and caches Tencent Cloud RocketMQ 5.x (Trocket v20230308) clients per credential#region.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class TencentClientFactory {

    static final int CONNECT_TIMEOUT_SECONDS = 10;
    static final int READ_TIMEOUT_SECONDS = 20;
    static final String ENDPOINT = "trocket.tencentcloudapi.com";

    private final CloudCredentialRepository credentialRepository;
    private final Map<String, TrocketClient> clients = new ConcurrentHashMap<>();

    public TrocketClient client(String credentialId, String region) {
        String key = cacheKey(credentialId, region);
        return clients.computeIfAbsent(key, ignored -> createClient(credentialId, region));
    }

    public void invalidateCredential(String credentialId) {
        String prefix = credentialId + "#";
        clients.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public <T> T call(String credentialId, String region, TencentCall<T> action) {
        try {
            return action.execute(client(credentialId, region));
        } catch (TencentCloudSDKException ex) {
            throw mapToBusinessException(ex);
        } catch (RuntimeException ex) {
            if (ex instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(502, "Tencent Cloud OpenAPI error: " + ex.getMessage());
        }
    }

    static String cacheKey(String credentialId, String region) {
        return credentialId + "#" + region;
    }

    protected TrocketClient createClient(String credentialId, String region) {
        CloudCredentialVO credential = credentialRepository.findById(credentialId)
                .orElseThrow(() -> new BusinessException(404, "Cloud credential not found: " + credentialId));
        Credential sdkCredential = new Credential(credential.getAccessKey(), credential.getSecretKey());
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint(endpointFor(region));
        httpProfile.setConnTimeout(CONNECT_TIMEOUT_SECONDS);
        httpProfile.setReadTimeout(READ_TIMEOUT_SECONDS);
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        return new TrocketClient(sdkCredential, region, clientProfile);
    }

    static String endpointFor(String region) {
        if (region != null && region.endsWith("-fsi")) {
            return "trocket." + region + ".tencentcloudapi.com";
        }
        return ENDPOINT;
    }

    static BusinessException mapToBusinessException(TencentCloudSDKException ex) {
        String code = ex.getErrorCode();
        String message = ex.getMessage();
        log.warn("Tencent Cloud OpenAPI failure: code={}, requestId={}, message={}",
                code, ex.getRequestId(), message);
        if (code != null && (code.contains("UnauthorizedOperation") || code.contains("AccessDenied"))) {
            return new BusinessException(403, defaultIfBlank(message, "Tencent Cloud OpenAPI access denied"));
        }
        if (code != null && (code.contains("AuthFailure") || code.contains("InvalidCredential")
                || code.contains("InvalidSecretId") || code.contains("SignatureFailure"))) {
            return new BusinessException(401, "Cloud credential is invalid");
        }
        if (code != null && (code.contains("NotFound") || code.contains("ResourceNotFound"))) {
            return new BusinessException(404, defaultIfBlank(message, "Tencent Cloud resource not found"));
        }
        return new BusinessException(502,
                "Tencent Cloud OpenAPI error: " + defaultIfBlank(message, code));
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @FunctionalInterface
    public interface TencentCall<T> {
        T execute(TrocketClient client) throws TencentCloudSDKException;
    }
}
