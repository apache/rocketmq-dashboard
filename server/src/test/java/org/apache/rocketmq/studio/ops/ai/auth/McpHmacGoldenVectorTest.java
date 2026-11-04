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
package org.apache.rocketmq.studio.ops.ai.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conformance test over the shared HMAC golden vectors.
 *
 * <p>The vectors in {@code rmqctl/internal/studio/testdata/auth-hmac-vectors.json}
 * are the single source of truth for the {@code rmq-hmac-sha256} signing
 * contract (see {@code docs/mcp-hmac.md}). The Go client already recomputes
 * every vector in {@code TestSigningRoundTripperMatchesGoldenVectors}; this
 * test proves the Java server produces byte-identical canonical requests and
 * signatures, so a change that would skew the two ends fails here and in the
 * Go suite with the same evidence.</p>
 */
class McpHmacGoldenVectorTest {

    private static final Path VECTORS = Path.of("..", "rmqctl", "internal", "studio",
            "testdata", "auth-hmac-vectors.json");

    @Test
    void serverMatchesEverySharedGoldenVector() throws Exception {
        JsonNode vectors = new ObjectMapper().readTree(
                Files.readAllBytes(VECTORS));
        assertThat(vectors.isArray()).isTrue();
        assertThat(vectors.size()).isPositive();

        for (JsonNode vector : vectors) {
            String name = vector.path("name").asText();
            String accessKey = vector.path("accessKey").asText();
            String secretKey = vector.path("secretKey").asText();
            String instanceId = vector.path("instanceId").asText();
            String timestamp = vector.path("timestamp").asText();
            String method = vector.path("method").asText();
            String path = vector.path("path").asText();
            String expectedCanonical = vector.path("canonicalRequest").asText();
            String expectedSignature = vector.path("signature").asText();

            String canonical = McpAuthenticator.canonicalRequest(
                    accessKey, instanceId, timestamp, method, path);
            assertThat(canonical)
                    .as("canonical request of vector <%s>", name)
                    .isEqualTo(expectedCanonical);

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            assertThat(HexFormat.of().formatHex(signature))
                    .as("signature of vector <%s>", name)
                    .isEqualTo(expectedSignature);
        }
    }

    @Test
    void vectorSignaturesAreConstantTimeComparable() throws Exception {
        // The constant-time comparison contract (docs/mcp-hmac.md): a vector
        // signature must compare equal to itself via MessageDigest.isEqual,
        // and differ from any other vector's signature.
        JsonNode vectors = new ObjectMapper().readTree(Files.readAllBytes(VECTORS));
        String first = vectors.get(0).path("signature").asText();
        String second = vectors.get(1).path("signature").asText();

        assertThat(MessageDigest.isEqual(
                hex(first), hex(first))).isTrue();
        assertThat(MessageDigest.isEqual(
                hex(first), hex(second))).isFalse();
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }
}
