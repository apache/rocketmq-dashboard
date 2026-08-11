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
package org.apache.rocketmq.dashboard.service.impl;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DashboardCollectServiceImplTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final DashboardCollectServiceImpl service = new DashboardCollectServiceImpl();

    @Test
    public void testJsonDataFile2mapReturnsEmptyMapForEmptyFile() throws Exception {
        File file = temporaryFolder.newFile("empty.json");

        Assert.assertTrue(service.jsonDataFile2map(file).isEmpty());
    }

    @Test
    public void testJsonDataFile2mapReturnsEmptyMapForWhitespaceFile() throws Exception {
        File file = temporaryFolder.newFile("whitespace.json");
        Files.writeString(file.toPath(), " \n\t\r", StandardCharsets.UTF_8);

        Assert.assertTrue(service.jsonDataFile2map(file).isEmpty());
    }

    @Test
    public void testJsonDataFile2mapPreservesNonEmptyData() throws Exception {
        File file = temporaryFolder.newFile("data.json");
        Files.writeString(file.toPath(),
            "{\"broker-a\":[\"topic-a\",\"topic-b\"],\"broker-b\":null}", StandardCharsets.UTF_8);

        Map<String, List<String>> result = service.jsonDataFile2map(file);

        Assert.assertEquals(Collections.singleton("broker-a"), result.keySet());
        Assert.assertEquals(Arrays.asList("topic-a", "topic-b"), result.get("broker-a"));
    }
}
