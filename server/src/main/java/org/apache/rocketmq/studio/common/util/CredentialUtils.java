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
            return new String(Base64.getDecoder().decode(stored), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            // tolerate legacy values that were stored without encoding
            return stored;
        }
    }

    /**
     * Keeps the first and last few characters visible, hides everything else; short values are
     * fully masked.
     */
    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.length() < MIN_PARTIALLY_MASKED_CREDENTIAL_CHARS) {
            return CREDENTIAL_MASK;
        }
        return value.substring(0, VISIBLE_CREDENTIAL_CHARS)
                + CREDENTIAL_MASK
                + value.substring(value.length() - VISIBLE_CREDENTIAL_CHARS);
    }
}
