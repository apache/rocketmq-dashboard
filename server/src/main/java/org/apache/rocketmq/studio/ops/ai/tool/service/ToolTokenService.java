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
package org.apache.rocketmq.studio.ops.ai.tool.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolError;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ToolTokenService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(10);
    private static final String TOKEN_VERSION = "v1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SIGNATURE_BYTES = 32;
    private static final String PLAN_TOKEN_VERSION = "v1p";
    // Base64 of "v1p." + 19 decimal digits + "." + 64 digest digits + "." + 32 HMAC bytes.
    private static final int MAX_TOKEN_LENGTH = 164;
    private static final Pattern TOKEN_PREFIX = Pattern.compile(
            "v1\\.([1-9][0-9]{0,18})\\.|v1p\\.([1-9][0-9]{0,18})\\.([0-9a-f]{64})\\.");

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final byte[] secret;

    @Autowired
    public ToolTokenService(
            ObjectMapper objectMapper,
            @Value("${studio.ai.token-secret:}") String secret) {
        this(objectMapper, Clock.systemUTC(), resolveSecret(secret));
    }

    ToolTokenService(ObjectMapper objectMapper, Clock clock, byte[] secret) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.secret = secret.clone();
    }

    private static byte[] resolveSecret(String configuredSecret) {
        if (!StringUtils.hasText(configuredSecret)) {
            // Console startup and read-only tools do not need confirmation tokens.
            // Missing configuration disables token operations; never invent a key.
            return new byte[0];
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(configuredSecret.trim());
            if (decoded.length < 32) {
                throw new IllegalStateException(
                        "studio.ai.token-secret must be at least 32 bytes after base64 decoding, "
                                + "got " + decoded.length + " bytes");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "studio.ai.token-secret must be valid base64", exception);
        }
    }

    public String issue(ToolExecutionContext context) {
        requireConfiguredSecret();
        long expiresAt = clock.instant().plus(TOKEN_TTL).getEpochSecond();
        byte[] signature = sign(signingPayload(context, expiresAt));
        return new ConfirmationToken(expiresAt, signature, null).format();
    }

    public String issue(ToolExecutionContext context, Map<String, Object> confirmationState) {
        requireConfiguredSecret();
        long expiresAt = clock.instant().plus(TOKEN_TTL).getEpochSecond();
        String digest = planDigest(confirmationState);
        byte[] signature = sign(signingPayload(context, expiresAt, digest));
        return new ConfirmationToken(expiresAt, signature, digest).format();
    }

    public void verify(ToolExecutionContext context) {
        authenticatedToken(context);
    }

    public void verifyPlan(ToolExecutionContext context, Map<String, Object> confirmationState) {
        ConfirmationToken token = authenticatedToken(context);
        if (token.planDigest() == null) {
            throw ToolError.CONFIRMATION_TOKEN_INVALID.exception(context.definition().name());
        }
        if (!MessageDigest.isEqual(token.planDigest().getBytes(StandardCharsets.US_ASCII),
                planDigest(confirmationState).getBytes(StandardCharsets.US_ASCII))) {
            throw ToolError.CONFIRMATION_PLAN_CHANGED.exception(context.definition().name());
        }
    }

    private ConfirmationToken authenticatedToken(ToolExecutionContext context) {
        requireConfiguredSecret();
        String toolName = context.definition().name();
        ConfirmationToken token = ConfirmationToken.parse(context.confirmToken(), toolName);
        if (clock.instant().getEpochSecond() >= token.expiresAt()) {
            throw ToolError.CONFIRMATION_TOKEN_INVALID.exception(toolName);
        }
        byte[] expected = sign(signingPayload(context, token.expiresAt(), token.planDigest()));
        if (!MessageDigest.isEqual(expected, token.signature())) {
            throw ToolError.CONFIRMATION_TOKEN_INVALID.exception(toolName);
        }
        return token;
    }

    private void requireConfiguredSecret() {
        if (secret.length == 0) {
            throw ToolError.TOKEN_UNAVAILABLE.exception();
        }
    }

    private byte[] signingPayload(ToolExecutionContext context, long expiresAt) {
        try {
            SignaturePayload payload = new SignaturePayload(
                    TOKEN_VERSION,
                    expiresAt,
                    context.definition().name(),
                    subjectBinding(context),
                    context.instanceId(),
                    canonicalInput(context.businessInput()));
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to create tool confirm token payload", e);
        }
    }

    private byte[] signingPayload(ToolExecutionContext context, long expiresAt, String digest) {
        if (digest == null) {
            return signingPayload(context, expiresAt);
        }
        try {
            return objectMapper.writeValueAsBytes(new PlanSignaturePayload(
                    PLAN_TOKEN_VERSION, expiresAt, context.definition().name(),
                    subjectBinding(context), context.instanceId(),
                    canonicalInput(context.businessInput()), digest));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to create tool confirm token payload", exception);
        }
    }

    private String planDigest(Map<String, Object> confirmationState) {
        try {
            byte[] state = objectMapper.writeValueAsBytes(canonicalInput(confirmationState));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(state));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to digest tool confirmation state", exception);
        }
    }

    private static String subjectBinding(ToolExecutionContext context) {
        if (context.principal() != null && !context.principal().isBlank()) {
            return "principal:" + context.principal();
        }
        return "instance:" + context.instanceId();
    }

    private static Map<String, Object> canonicalInput(Map<?, ?> input) {
        Map<String, Object> sorted = new TreeMap<>();
        input.forEach((key, value) -> {
            if (key instanceof String stringKey) {
                sorted.put(stringKey, canonicalValue(value));
            }
        });
        return sorted;
    }

    private static Object canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return canonicalInput(map);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(ToolTokenService::canonicalValue).toList();
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return value;
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(payload);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Unable to sign tool confirm token", e);
        }
    }

    private record ConfirmationToken(long expiresAt, byte[] signature, String planDigest) {

        private static ConfirmationToken parse(String token, String toolName) {
            if (token == null || token.isBlank()) {
                throw ToolError.CONFIRMATION_TOKEN_REQUIRED.exception(toolName);
            }
            if (token.length() > MAX_TOKEN_LENGTH) {
                throw ToolError.CONFIRMATION_TOKEN_INVALID.exception(toolName);
            }
            try {
                byte[] content = Base64.getDecoder().decode(token);
                int prefixLength = content.length - SIGNATURE_BYTES;
                if (prefixLength <= 0) {
                    throw ToolError.CONFIRMATION_TOKEN_INVALID.exception(toolName);
                }
                // HMAC bytes can contain dots and invalid UTF-8; only the prefix is text.
                Matcher matcher = TOKEN_PREFIX.matcher(new String(content, 0, prefixLength, StandardCharsets.UTF_8));
                if (!matcher.matches()) {
                    throw ToolError.CONFIRMATION_TOKEN_INVALID.exception(toolName);
                }
                String expiry = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                return new ConfirmationToken(Long.parseLong(expiry),
                        Arrays.copyOfRange(content, prefixLength, content.length), matcher.group(3));
            } catch (IllegalArgumentException exception) {
                throw ToolError.CONFIRMATION_TOKEN_INVALID.exception(toolName);
            }
        }

        private String format() {
            String header = planDigest == null ? TOKEN_VERSION + "." + expiresAt + "."
                    : PLAN_TOKEN_VERSION + "." + expiresAt + "." + planDigest + ".";
            byte[] prefix = header.getBytes(StandardCharsets.UTF_8);
            byte[] content = ByteBuffer.allocate(prefix.length + signature.length)
                    .put(prefix)
                    .put(signature)
                    .array();
            return Base64.getEncoder().encodeToString(content);
        }
    }

    private record PlanSignaturePayload(
            String version,
            long expiresAt,
            String tool,
            String subject,
            String instanceId,
            Map<String, Object> input,
            String planDigest) {
    }

    private record SignaturePayload(
            String version,
            long expiresAt,
            String tool,
            String subject,
            String instanceId,
            Map<String, Object> input) {
    }
}
