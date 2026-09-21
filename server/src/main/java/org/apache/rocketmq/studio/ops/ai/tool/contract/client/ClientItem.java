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
package org.apache.rocketmq.studio.ops.ai.tool.contract.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.rocketmq.studio.cluster.client.ClientConnectionVO;

/**
 * One online client connection as reported by the selected Instance. Optional metadata
 * (protocol, language, version, connect time, cluster) stays absent instead of being
 * invented when the underlying connection query cannot report it; {@code partial} marks
 * rows kept from an incomplete lookup.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientItem(
        String clientId,
        String type,
        String groupOrTopic,
        String producerGroup,
        String protocol,
        String address,
        String language,
        String version,
        String connectedAt,
        boolean partial,
        String clusterName) {

    public static ClientItem from(ClientConnectionVO connection) {
        return new ClientItem(
                connection.getClientId(),
                connection.getType() == null ? null : connection.getType().name(),
                connection.getGroupOrTopic(),
                connection.getProducerGroup(),
                connection.getProtocol() == null ? null : connection.getProtocol().name(),
                connection.getAddress(),
                connection.getLanguage() == null ? null : connection.getLanguage().name(),
                connection.getVersion(),
                connection.getConnectedAt() == null ? null : connection.getConnectedAt().toString(),
                connection.isPartial(),
                connection.getClusterName());
    }
}
