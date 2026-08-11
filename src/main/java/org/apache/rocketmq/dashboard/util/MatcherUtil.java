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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.rocketmq.dashboard.util;

import java.util.regex.Pattern;

public class MatcherUtil {
    private static final String REGEX_META_CHARACTERS = "\\.^$|?*+()[]{}";

    public static boolean match(String accessUrl, String reqPath) {
        String regPath = getRegPath(accessUrl);
        return Pattern.compile(regPath).matcher(reqPath).matches();
    }

    private static String getRegPath(String path) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char current = path.charAt(i);
            if (current == '*') {
                if (i + 1 < path.length() && path.charAt(i + 1) == '*') {
                    sb.append(".*");
                    i++;
                } else {
                    sb.append("[^/]*");
                }
            } else if (current == '?') {
                sb.append('.');
            } else {
                appendLiteral(sb, current);
            }
        }
        return sb.toString();
    }

    private static void appendLiteral(StringBuilder regex, char current) {
        if (REGEX_META_CHARACTERS.indexOf(current) >= 0) {
            regex.append('\\');
        }
        regex.append(current);
    }
}
