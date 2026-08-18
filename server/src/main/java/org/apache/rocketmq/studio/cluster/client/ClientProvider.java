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
package org.apache.rocketmq.studio.cluster.client;

import org.apache.rocketmq.studio.common.exception.BusinessException;

import java.util.List;

public interface ClientProvider {
    List<ClientConnectionVO> findConnections(String instanceId, String clusterId, String type);

    default List<ClientConnectionVO> findConnectionsAt(String namesrvAddr, String clusterId, String type) {
        throw new BusinessException(501, "Client connection provider does not support nameserver lookup");
    }

    List<String> findProducerGroups(String instanceId, String topic, String query, int limit);

    List<ClientConnectionVO> findProducerConnections(String instanceId, String topic, String producerGroup);
}
