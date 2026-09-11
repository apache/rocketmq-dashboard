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
 * Helpers for building SQL {@code LIKE} predicates from user-supplied search strings.
 *
 * <p>MyBatis-Plus {@code like()} wraps the value with {@code %} and hands it to the SQL
 * {@code LIKE} operator, which treats {@code %}, {@code _} and {@code \} as wildcard/escape
 * characters. Search strings are user input, so those characters must be escaped to keep a
 * literal match (a search for {@code prod_user} must not also match {@code prodXuser}).
 */
public final class SqlLikeUtils {

    private SqlLikeUtils() {
    }

    /** Escape {@code \}, {@code %} and {@code _} so the value matches literally in a LIKE clause. */
    public static String escape(String search) {
        if (!StringUtils.hasText(search)) {
            return search;
        }
        return search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
