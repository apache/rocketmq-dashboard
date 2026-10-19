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
package org.apache.rocketmq.studio.ops.ai;

import java.net.URI;

/** Normalizes endpoint paths without changing raw query parameters or fragments. */
final class LlmApiBase {
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private LlmApiBase() {
    }

    static String normalize(String apiBase) {
        String trimmed = apiBase.trim();
        int suffixStart = suffixStart(trimmed);
        String base = trimTrailingSlashes(trimmed.substring(0, suffixStart));
        if (base.endsWith(CHAT_COMPLETIONS_PATH)) {
            base = trimTrailingSlashes(base.substring(0, base.length() - CHAT_COMPLETIONS_PATH.length()));
        }
        return base + trimmed.substring(suffixStart);
    }

    static URI endpoint(String apiBase, String path) {
        String normalized = normalize(apiBase);
        int suffixStart = suffixStart(normalized);
        return URI.create(normalized.substring(0, suffixStart) + path + normalized.substring(suffixStart));
    }

    private static int suffixStart(String value) {
        int query = value.indexOf('?');
        int fragment = value.indexOf('#');
        return Math.min(query < 0 ? value.length() : query, fragment < 0 ? value.length() : fragment);
    }

    private static String trimTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
