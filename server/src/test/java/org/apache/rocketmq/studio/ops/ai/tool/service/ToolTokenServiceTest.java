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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolTokenServiceTest {

    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-11T02:00:00Z"), ZoneOffset.UTC);
    private static final Map<String, Object> INPUT = Map.of("cluster", "instance-dev", "topic", "orders", "writeQueues", 8);
    private static final String TOKEN = "djEuMTc4OTA5MjYwMC5gRwUvzs+4BUqDX0svsV1C/jUjLzfv/GDPZpjcG9bvBg==";
    private final ToolCatalog catalog = new ToolCatalog(new DefaultResourceLoader());
    private final ToolTokenService tokens = new ToolTokenService(new ObjectMapper(), CLOCK, SECRET);

    @Test
    void issuesCompactTokenAndVerifiesReorderedInputWithoutControlFields() {
        assertThat(tokens.issue(context(INPUT))).isEqualTo(TOKEN).hasSize(64);
        byte[] content = Base64.getDecoder().decode(TOKEN);
        assertThat(content).hasSize(46);
        assertThat(new String(content, 0, 14, StandardCharsets.UTF_8)).isEqualTo("v1.1789092600.");

        Map<String, Object> apply = new LinkedHashMap<>();
        apply.put("writeQueues", 8);
        apply.put("topic", "orders");
        apply.put("cluster", "instance-dev");
        apply.put("dry_run", false);
        apply.put("break_glass", true);
        apply.put("reason", "reviewed");
        assertThatCode(() -> tokens.verify(withToken(context(apply), TOKEN))).doesNotThrowAnyException();

        Map<String, Object> largeInput = new LinkedHashMap<>(INPUT);
        largeInput.put("description", "x".repeat(100_000));
        assertThat(tokens.issue(context(largeInput))).hasSize(64);
    }

    @Test
    void bindsToolCallerInstanceAndBusinessInput() {
        List<ToolExecutionContext> changedRequests = List.of(
                ToolExecutionContext.of("instance-dev", catalog.getDefinition("rmq.topic.delete"), INPUT, "alice"),
                ToolExecutionContext.of("instance-dev", catalog.getDefinition("rmq.topic.create"), INPUT, "bob"),
                ToolExecutionContext.of("other-instance", catalog.getDefinition("rmq.topic.create"), INPUT, "alice"),
                context(Map.of("cluster", "instance-dev", "topic", "orders", "writeQueues", 16)));
        changedRequests.forEach(request -> assertInvalid(tokens, withToken(request, TOKEN)));
    }

    @Test
    void rejectsExpiredTokenAndChangesToVersionExpiryOrSignature() {
        ToolExecutionContext request = withToken(context(INPUT), TOKEN);
        ToolTokenService beforeExpiry = new ToolTokenService(new ObjectMapper(),
                Clock.offset(CLOCK, Duration.ofSeconds(599)), SECRET);
        assertThatCode(() -> beforeExpiry.verify(request)).doesNotThrowAnyException();
        ToolTokenService atExpiry = new ToolTokenService(new ObjectMapper(),
                Clock.offset(CLOCK, Duration.ofMinutes(10)), SECRET);
        assertInvalid(atExpiry, request);

        // Each mutation preserves the rest of the authenticated token.
        for (int offset : new int[] {1, 12, 45}) {
            byte[] changed = Base64.getDecoder().decode(TOKEN);
            changed[offset] ^= 1;
            assertInvalid(tokens, withToken(context(INPUT), Base64.getEncoder().encodeToString(changed)));
        }
    }

    @Test
    void rejectsMalformedAndLegacyTokens() {
        List<String> invalid = List.of("not base64!", "A".repeat(77),
                Base64.getEncoder().encodeToString(new byte[32]),
                Base64.getEncoder().encodeToString(("v1.invalid." + "x".repeat(32)).getBytes(StandardCharsets.UTF_8)),
                "RMQ-HMAC-SHA256 ExpiresAt=1789092600, Signature=" + "A".repeat(64));
        invalid.forEach(token -> assertInvalid(tokens, withToken(context(INPUT), token)));
    }

    @Test
    void acceptsRawSignatureContainingDotsAndNonUtf8Bytes() {
        ToolExecutionContext request = context(Map.of("cluster", "instance-dev", "topic", "orders-6", "writeQueues", 8));
        String token = tokens.issue(request);
        assertThat(token).isEqualTo("djEuMTc4OTA5MjYwMC7zC5yHz0AaCGORzV7ZLGn0TGM56j74zi5atWjYC9VlMA==");
        byte[] content = Base64.getDecoder().decode(token);
        assertThat(Arrays.copyOfRange(content, content.length - 32, content.length)).contains((byte) '.', (byte) 0xf3);
        assertThatCode(() -> tokens.verify(withToken(request, token))).doesNotThrowAnyException();
    }

    private ToolExecutionContext context(Map<String, Object> input) {
        return ToolExecutionContext.of("instance-dev", catalog.getDefinition("rmq.topic.create"), input, "alice");
    }

    private static ToolExecutionContext withToken(ToolExecutionContext context, String token) {
        Map<String, Object> input = new LinkedHashMap<>(context.input());
        input.put("confirm_token", token);
        return ToolExecutionContext.of(context.cluster(), context.definition(), input, context.principal());
    }

    private static void assertInvalid(ToolTokenService service, ToolExecutionContext context) {
        assertThatThrownBy(() -> service.verify(context)).isInstanceOfSatisfying(ToolExecutionException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo("INVALID_ARGUMENT"));
    }
}
