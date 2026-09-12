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
    // Base64 of "v1." + at most 19 decimal digits + "." + 32 signature bytes.
    private static final int MAX_TOKEN_LENGTH = 76;
    private static final Pattern TOKEN_PREFIX = Pattern.compile(
            Pattern.quote(TOKEN_VERSION) + "\\.([1-9][0-9]{0,18})\\.");

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
        return new ConfirmationToken(expiresAt, signature).format();
    }

    public void verify(ToolExecutionContext context) {
        requireConfiguredSecret();
        String toolName = context.definition().name();
        ConfirmationToken token = ConfirmationToken.parse(context.confirmToken(), toolName);
        if (clock.instant().getEpochSecond() >= token.expiresAt()) {
            throw ToolError.CONFIRMATION_TOKEN_INVALID.exception(toolName);
        }
        byte[] expected = sign(signingPayload(context, token.expiresAt()));
        if (!MessageDigest.isEqual(expected, token.signature())) {
            throw ToolError.CONFIRMATION_TOKEN_INVALID.exception(toolName);
        }
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
                    context.cluster(),
                    canonicalInput(context.businessInput()));
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to create tool confirm token payload", e);
        }
    }

    private static String subjectBinding(ToolExecutionContext context) {
        if (context.principal() != null && !context.principal().isBlank()) {
            return "principal:" + context.principal();
        }
        return "instance:" + context.cluster();
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

    private record ConfirmationToken(long expiresAt, byte[] signature) {

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
                return new ConfirmationToken(Long.parseLong(matcher.group(1)),
                        Arrays.copyOfRange(content, prefixLength, content.length));
            } catch (IllegalArgumentException exception) {
                throw ToolError.CONFIRMATION_TOKEN_INVALID.exception(toolName);
            }
        }

        private String format() {
            byte[] prefix = (TOKEN_VERSION + "." + expiresAt + ".").getBytes(StandardCharsets.UTF_8);
            byte[] content = ByteBuffer.allocate(prefix.length + signature.length)
                    .put(prefix)
                    .put(signature)
                    .array();
            return Base64.getEncoder().encodeToString(content);
        }
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
