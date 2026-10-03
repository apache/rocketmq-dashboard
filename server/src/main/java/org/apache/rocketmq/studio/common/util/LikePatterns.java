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

import org.springframework.util.StringUtils;

/**
 * Escapes user-supplied search text so a {@code LIKE} search matches it literally.
 *
 * <p>MyBatis-Plus renders {@code like(column, term)} as {@code column LIKE '%term%'}. The term is a
 * bound parameter, but {@code %} and {@code _} inside it are still pattern metacharacters: a search
 * for {@code _} matches every row, a search for {@code topic_a} also matches {@code topicXa}, and a
 * search for {@code %DLQ%} matches anything containing "DLQ". Prefixing the two metacharacters (and
 * the escape character itself) keeps the term literal.
 *
 * <p>The backslash is replaced first: escaping {@code %} and {@code _} first would double-escape
 * the backslashes this method inserts itself.
 */
public final class LikePatterns {

    private LikePatterns() {
    }

    /**
     * Returns {@code term} with {@code \}, {@code %} and {@code _} escaped for a LIKE pattern.
     * Null and blank values are returned unchanged, so a caller can pass an absent filter through.
     */
    public static String escape(String term) {
        if (!StringUtils.hasText(term)) {
            return term;
        }
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
