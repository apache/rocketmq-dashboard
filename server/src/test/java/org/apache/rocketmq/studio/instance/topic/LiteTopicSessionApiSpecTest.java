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

package org.apache.rocketmq.studio.instance.topic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The session endpoint's field table is what an API client codes against, so it has to name exactly
 * the properties the controller serializes: a fabricated row sends clients after a value that never
 * arrives, and a missing row hides a field that changes how the payload must be read.
 */
class LiteTopicSessionApiSpecTest {

    private static final String SESSION_SECTION = "### 5.10 ";

    private static final Pattern FIELD_ROW = Pattern.compile("^\\|\\s*`([A-Za-z_][\\w.]*)`\\s*\\|");

    @Test
    void documentsExactlyTheSessionFieldsTheApiReturnsTest() throws IOException {
        List<String> documented = documentedSessionFields(readApiSpec());
        Map<String, Object> wire = new ObjectMapper()
                .convertValue(LiteTopicSessionVO.builder().build(), Map.class);

        assertThat(documented).containsExactlyInAnyOrderElementsOf(wire.keySet());
    }

    private static List<String> documentedSessionFields(String spec) {
        int start = spec.indexOf(SESSION_SECTION);
        assertThat(start)
                .as("api-spec.md must keep a '%s' section for the session endpoint", SESSION_SECTION)
                .isGreaterThanOrEqualTo(0);
        int next = spec.indexOf("\n### ", start + SESSION_SECTION.length());
        String section = next < 0 ? spec.substring(start) : spec.substring(start, next);

        List<String> fields = new ArrayList<>();
        for (String line : section.split("\n")) {
            Matcher matcher = FIELD_ROW.matcher(line);
            if (matcher.find()) {
                fields.add(matcher.group(1));
            }
        }
        return fields;
    }

    private static String readApiSpec() throws IOException {
        Path directory = Paths.get("").toAbsolutePath();
        for (int depth = 0; directory != null && depth < 6; depth++, directory = directory.getParent()) {
            Path candidate = directory.resolve("docs").resolve("api-spec.md");
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("docs/api-spec.md not found above " + Paths.get("").toAbsolutePath());
    }
}
