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

    public static boolean match(String accessUrl, String reqPath) {
        String regPath = getRegPath(accessUrl);
        return Pattern.compile(regPath).matcher(reqPath).matches();
    }

    private static String getRegPath(String path) {
        char[] chars = path.toCharArray();
        int len = chars.length;
        StringBuilder sb = new StringBuilder();
        boolean preX = false;
        for (int i = 0; i < len; i++) {
            if (chars[i] == '*') {
                if (preX) {
                    sb.append(".*");
                    preX = false;
                } else if (i + 1 == len) {
                    sb.append("[^/]*");
                } else {
                    preX = true;
                    continue;
                }
            } else {
                if (preX) {
                    sb.append("[^/]*");
                    preX = false;
                }
                if (chars[i] == '?') {
                    sb.append('.');
                } else {
                    sb.append(chars[i]);
                }
            }
        }
        return sb.toString();
    }
}
