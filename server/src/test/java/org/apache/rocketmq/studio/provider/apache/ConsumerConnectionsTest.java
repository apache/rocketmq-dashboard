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
 * Pins {@link ConsumerConnections#toInstances}: a null connection or a connection without a
 * connection set yields no instances, and every connection becomes an online instance carrying
 * its client id and address - the single source both the group listing and the group detail
 * derive the online instances from.
 */
class ConsumerConnectionsTest {

    @Test
    void aNullConnectionYieldsNoInstances() {
        assertThat(ConsumerConnections.toInstances(null)).isEmpty();
    }

    @Test
    void aConnectionWithoutAConnectionSetYieldsNoInstances() {
        ConsumerConnection connection = new ConsumerConnection();
        connection.setConnectionSet(null);
        assertThat(ConsumerConnections.toInstances(connection)).isEmpty();
    }

    @Test
    void everyConnectionBecomesAnInstanceWithItsClientIdAndAddress() {
        Connection first = new Connection();
        first.setClientId("consumer-1");
        first.setClientAddr("10.0.0.1:10920");

        Connection second = new Connection();
        second.setClientId("consumer-2");
        second.setClientAddr("10.0.0.2:10921");

        ConsumerConnection connection = new ConsumerConnection();
        HashSet<Connection> connectionSet = new HashSet<>();
        connectionSet.add(first);
        connectionSet.add(second);
        connection.setConnectionSet(connectionSet);

        List<ConsumerInstanceVO> instances = ConsumerConnections.toInstances(connection);

        assertThat(instances).hasSize(2);
        assertThat(instances)
                .extracting(ConsumerInstanceVO::getClientId)
                .containsExactlyInAnyOrder("consumer-1", "consumer-2");
        assertThat(instances)
                .extracting(ConsumerInstanceVO::getAddress)
                .containsExactlyInAnyOrder("10.0.0.1:10920", "10.0.0.2:10921");
    }
}
