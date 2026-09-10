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

import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.studio.instance.group.ConsumerInstanceVO;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ConsumerConnections}, the shared mapping from a broker/proxy
 * consumer-connection set to the online instance view used by the group listing and detail.
 */
class ConsumerConnectionsTest {

    private static Connection connection(String clientId, String clientAddr) {
        Connection connection = new Connection();
        connection.setClientId(clientId);
        connection.setClientAddr(clientAddr);
        return connection;
    }

    private static ConsumerConnection withConnections(Connection... connections) {
        ConsumerConnection consumerConnection = new ConsumerConnection();
        consumerConnection.setConnectionSet(new HashSet<>(List.of(connections)));
        return consumerConnection;
    }

    @Test
    void returnsEmptyWhenNoConnectionPayloadIsPresent() {
        assertThat(ConsumerConnections.toInstances(null)).isEmpty();

        ConsumerConnection emptySet = new ConsumerConnection();
        assertThat(ConsumerConnections.toInstances(emptySet)).isEmpty();
    }

    @Test
    void mapsEveryConnectionToItsOnlineInstanceView() {
        List<ConsumerInstanceVO> instances = ConsumerConnections.toInstances(withConnections(
                connection("client-a@host1", "10.0.0.1:8088"),
                connection("client-b@host2", "10.0.0.2:8088")));

        assertThat(instances).extracting(ConsumerInstanceVO::getClientId)
                .containsExactlyInAnyOrder("client-a@host1", "client-b@host2");
        assertThat(instances).extracting(ConsumerInstanceVO::getAddress)
                .containsExactlyInAnyOrder("10.0.0.1:8088", "10.0.0.2:8088");
    }
}
