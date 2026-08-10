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
package org.apache.rocketmq.studio.provider.alibaba;

import com.aliyun.auth.credentials.Credential;
import com.aliyun.auth.credentials.provider.StaticCredentialProvider;
import com.aliyun.sdk.gateway.pop.exception.PopClientException;
import com.aliyun.sdk.gateway.pop.exception.PopServerException;
import com.aliyun.sdk.service.rocketmq20220801.AsyncClient;
import darabonba.core.client.ClientOverrideConfiguration;
import darabonba.core.exception.ClientException;
import darabonba.core.exception.ServerException;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.studio.provider.credential.CloudCredentialRepository;
import org.apache.rocketmq.studio.provider.credential.CloudCredentialVO;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Builds and caches Aliyun RocketMQ 5.x OpenAPI async clients per credential#region, and
 * converts SDK failures into {@link BusinessException} with meaningful HTTP-style codes.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class AliyunClientFactory {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(20);
    static final long DEFAULT_CALL_TIMEOUT_SECONDS = 30L;

    private final CloudCredentialRepository credentialRepository;
    private final Map<String, AsyncClient> clients = new ConcurrentHashMap<>();
    private long callTimeoutSeconds = DEFAULT_CALL_TIMEOUT_SECONDS;

    public AsyncClient client(String credentialId, String region) {
        String key = cacheKey(credentialId, region);
        return clients.computeIfAbsent(key, ignored -> createClient(credentialId, region));
    }

    /**
     * Releases all clients created with a credential so the next call observes rotated secrets.
     */
    public void invalidateCredential(String credentialId) {
        String prefix = credentialId + "#";
        clients.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith(prefix)) {
                return false;
            }
            entry.getValue().close();
            return true;
        });
    }

    /**
     * Executes an SDK call with a bounded wait and unified exception mapping.
     */
    public <T> T call(String credentialId, String region, Function<AsyncClient, CompletableFuture<T>> action) {
        AsyncClient client = client(credentialId, region);
        CompletableFuture<T> future;
        try {
            future = action.apply(client);
        } catch (RuntimeException ex) {
            throw mapToBusinessException(ex);
        }
        try {
            return future.get(callTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new BusinessException(504,
                    "Aliyun OpenAPI request timed out after " + callTimeoutSeconds + " seconds");
        } catch (InterruptedException ex) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new BusinessException(502, "Aliyun OpenAPI request was interrupted");
        } catch (ExecutionException ex) {
            throw mapToBusinessException(ex.getCause() == null ? ex : ex.getCause());
        }
    }

    @PreDestroy
    public void close() {
        clients.values().forEach(AsyncClient::close);
        clients.clear();
    }

    void setCallTimeoutSeconds(long callTimeoutSeconds) {
        this.callTimeoutSeconds = callTimeoutSeconds;
    }

    static String cacheKey(String credentialId, String region) {
        return credentialId + "#" + region;
    }

    static String endpointFor(String region) {
        return "rocketmq." + region + ".aliyuncs.com";
    }

    protected AsyncClient createClient(String credentialId, String region) {
        CloudCredentialVO credential = credentialRepository.findById(credentialId)
                .orElseThrow(() -> new BusinessException(404, "Cloud credential not found: " + credentialId));
        StaticCredentialProvider credentialProvider = StaticCredentialProvider.create(
                Credential.builder()
                        .accessKeyId(credential.getAccessKey())
                        .accessKeySecret(credential.getSecretKey())
                        .build());
        return AsyncClient.builder()
                .region(region)
                .credentialsProvider(credentialProvider)
                .overrideConfiguration(ClientOverrideConfiguration.create()
                        .setEndpointOverride(endpointFor(region))
                        .setConnectTimeout(CONNECT_TIMEOUT)
                        .setResponseTimeout(RESPONSE_TIMEOUT))
                .build();
    }

    static BusinessException mapToBusinessException(Throwable raw) {
        Throwable cause = raw;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof BusinessException) {
            return (BusinessException) cause;
        }
        Integer statusCode = null;
        String errCode = null;
        String message = cause.getMessage();
        if (cause instanceof PopServerException) {
            PopServerException ex = (PopServerException) cause;
            statusCode = ex.getStatusCode();
            errCode = ex.getErrCode();
            message = firstNonBlank(ex.getErrMessage(), message);
        } else if (cause instanceof PopClientException) {
            PopClientException ex = (PopClientException) cause;
            statusCode = ex.getStatusCode();
            errCode = ex.getErrCode();
            message = firstNonBlank(ex.getErrMessage(), message);
        } else if (cause instanceof ServerException) {
            statusCode = ((ServerException) cause).getStatusCode();
        } else if (cause instanceof ClientException) {
            statusCode = ((ClientException) cause).getStatusCode();
        }
        int status = statusCode == null ? 0 : statusCode;
        log.warn("Aliyun OpenAPI failure: status={}, errCode={}, message={}", status, errCode, message);
        if (status == 401 || "InvalidAccessKeyId".equals(errCode) || "SignatureDoesNotMatch".equals(errCode)) {
            return new BusinessException(401, "Cloud credential is invalid");
        }
        if (status == 403) {
            return new BusinessException(403, defaultIfBlank(message, "Aliyun OpenAPI access denied"));
        }
        if (status == 404 || errCode != null && errCode.contains("NotFound")) {
            return new BusinessException(404, defaultIfBlank(message, "Aliyun resource not found"));
        }
        String detail = message != null ? message : errCode != null ? errCode : cause.getClass().getSimpleName();
        return new BusinessException(502, "Aliyun OpenAPI error: " + detail);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private static String defaultIfBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
