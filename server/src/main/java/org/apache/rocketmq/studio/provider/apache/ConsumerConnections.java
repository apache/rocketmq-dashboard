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
package org.apache.rocketmq.studio.provider.apache;

import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.studio.instance.group.ConsumerInstanceVO;

import java.util.List;

/**
 * Maps a broker (or proxy) consumer connection set to the online instance view, so the group
 * listing and the group detail always derive the online instances from the same source.
 */
final class ConsumerConnections {

    private ConsumerConnections() {
    }

    static List<ConsumerInstanceVO> toInstances(ConsumerConnection connection) {
        if (connection == null || connection.getConnectionSet() == null) {
            return List.of();
        }
        return connection.getConnectionSet().stream()
                .map(conn -> ConsumerInstanceVO.builder()
                        .clientId(conn.getClientId())
                        .address(conn.getClientAddr())
                        .build())
                .toList();
    }
}
