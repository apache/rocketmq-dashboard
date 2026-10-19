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
package org.apache.rocketmq.studio.cluster.metrics;

import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/** Appends a query endpoint without treating an existing query or fragment as part of the path. */
public final class PrometheusQueryUri {
    private PrometheusQueryUri() {
    }

    public static URI appendPath(URI baseUri, String queryPath) {
        URI encodedBase = URI.create(baseUri.toASCIIString());
        String path = encodedBase.getRawPath();
        if (path == null) {
            path = "";
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return UriComponentsBuilder.fromUri(encodedBase)
                .replacePath(path + queryPath)
                .build(true)
                .toUri();
    }
}
