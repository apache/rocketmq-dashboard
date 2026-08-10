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
package org.apache.rocketmq.studio.cluster.broker;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for the default RocketMQ cluster the studio connects to.
 *
 * <p>When {@code namesrvAddr} is left blank the {@link RealClusterProvider} performs no discovery
 * and callers rely on the interactive connection-test endpoint instead.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "studio.cluster.admin")
public class MqAdminProperties {

    /** NameServer address list, e.g. {@code host1:9876;host2:9876}; blank disables discovery. */
    private String namesrvAddr;

    /**
     * Externally supplied credentials for Apache RocketMQ admin calls.
     *
     * <p>Instances persist only a reference into this map. Access keys and secret keys must be
     * supplied through externalized Spring configuration, such as environment variables or a
     * Kubernetes Secret, and must never be persisted in the Studio database.
     */
    private Map<String, Credential> credentials = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class Credential {
        private String accessKey;
        private String secretKey;
    }
}
