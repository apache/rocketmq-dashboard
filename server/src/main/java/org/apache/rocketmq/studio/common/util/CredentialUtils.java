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

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Shared credential helpers: secrets are stored base64-encoded (never plain text) and only
 * partially shown in list views.
 */
public final class CredentialUtils {

    private static final int VISIBLE_CREDENTIAL_CHARS = 4;
    private static final int MIN_PARTIALLY_MASKED_CREDENTIAL_CHARS = 17;
    private static final String CREDENTIAL_MASK = "****";

    private CredentialUtils() {
    }

    public static String encodeBase64(String plainText) {
        if (plainText == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(plainText.getBytes(StandardCharsets.UTF_8));
    }

    public static String decodeBase64(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(stored);
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(decoded))
                    .toString();
        } catch (IllegalArgumentException | CharacterCodingException ex) {
            // Tolerate legacy plaintext, including text that is syntactically Base64 but does
            // not decode to valid UTF-8. Returning replacement characters would corrupt it.
            return stored;
        }
    }

    /**
     * Keeps the first and last few Unicode code points visible and hides everything else. Short
     * values and malformed UTF-16 input are fully masked. Using code-point boundaries prevents a
     * supplementary character from being split into an isolated surrogate in API responses.
     */
    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (hasUnpairedSurrogate(value)) {
            return CREDENTIAL_MASK;
        }
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount < MIN_PARTIALLY_MASKED_CREDENTIAL_CHARS) {
            return CREDENTIAL_MASK;
        }
        int visiblePrefixEnd = value.offsetByCodePoints(0, VISIBLE_CREDENTIAL_CHARS);
        int visibleSuffixStart = value.offsetByCodePoints(
                0, codePointCount - VISIBLE_CREDENTIAL_CHARS);
        return value.substring(0, visiblePrefixEnd)
                + CREDENTIAL_MASK
                + value.substring(visibleSuffixStart);
    }

    private static boolean hasUnpairedSurrogate(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return true;
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                return true;
            }
        }
        return false;
    }
}
