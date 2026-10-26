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

/**
 * Escapes the wildcards a MyBatis-Plus {@code like} condition would otherwise honour, so a search
 * term containing {@code %}, {@code _} or a backslash matches itself literally instead of acting
 * as a pattern. Four repositories used to carry private copies of this; the escape is a property
 * of the SQL LIKE operator, not of any one table.
 */
public final class LikePatterns {

    private LikePatterns() {
    }

    /**
     * @param value raw search term, may be null
     * @return the escaped term, or null when {@code value} is null
     */
    public static String escape(String value) {
        return value == null ? null
                : value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
