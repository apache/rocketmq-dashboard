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
package org.apache.rocketmq.dashboard.llm;

import java.util.Arrays;
import java.util.List;

/**
 * Provides cluster capability detection result.
 * Used to filter tools based on what a given cluster supports.
 */
public class ClusterCapability {

    private final List<String> supportedResources;

    public ClusterCapability(List<String> supportedResources) {
        this.supportedResources = supportedResources;
    }

    /** Default capability supporting all resources. */
    public static ClusterCapability all() {
        return new ClusterCapability(Arrays.asList(
                "cluster", "namespace", "topic", "group",
                "message", "client", "acl", "broker", "metrics", "capabilities"));
    }

    /** Return a capability with limited resources. */
    public static ClusterCapability of(String... resources) {
        return new ClusterCapability(Arrays.asList(resources));
    }

    /** Check whether a given resource type is supported. */
    public boolean supports(String resource) {
        return supportedResources.contains(resource);
    }
}
