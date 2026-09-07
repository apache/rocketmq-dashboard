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
package org.apache.rocketmq.studio.ops.ai.tool;

import org.springframework.util.StringUtils;

import java.util.Locale;

public record ToolPermission(String resource, String action) {

    public static ToolPermission parse(String rawPermission) {
        if (!StringUtils.hasText(rawPermission)) {
            return new ToolPermission("", "");
        }
        String[] parts = rawPermission.trim().split(":", 2);
        String resource = parts[0].trim().toLowerCase(Locale.ROOT);
        String action = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "";
        return new ToolPermission(resource, action);
    }

    public boolean isReadOnly() {
        return "read".equals(action);
    }
}
