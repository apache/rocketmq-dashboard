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
package org.apache.rocketmq.studio.ops.ai.tool.catalog;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class ToolCatalogTestSupport {

    private ToolCatalogTestSupport() {
    }

    static ToolCatalog loadCatalog(String version, Resource... shards) {
        Resource manifest = new ByteArrayResource(("version: " + version + "\n").getBytes(StandardCharsets.UTF_8));
        return new ToolCatalog(new PathMatchingResourcePatternResolver() {
            @Override
            public Resource getResource(String location) {
                return ToolCatalog.MANIFEST_RESOURCE.equals(location) ? manifest : super.getResource(location);
            }

            @Override
            public Resource[] getResources(String locationPattern) throws IOException {
                return ToolCatalog.SHARD_PATTERN.equals(locationPattern) ? shards : super.getResources(locationPattern);
            }
        });
    }
}
