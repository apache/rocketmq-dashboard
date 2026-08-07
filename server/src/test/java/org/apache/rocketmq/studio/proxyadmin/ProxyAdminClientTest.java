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
package org.apache.rocketmq.studio.proxyadmin;

import apache.rocketmq.v2.ClientInstance;
import apache.rocketmq.v2.ClientRole;
import apache.rocketmq.v2.Language;
import apache.rocketmq.v2.Resource;
import apache.rocketmq.v2.RouteChangeEvent;
import apache.rocketmq.v2.RouteChangeEventType;
import apache.rocketmq.v2.TopicRouteSnapshot;
import com.google.protobuf.Timestamp;
import org.apache.rocketmq.studio.cluster.client.ClientConnectionVO;
import org.apache.rocketmq.studio.common.domain.enums.ClientLanguage;
import org.apache.rocketmq.studio.common.domain.enums.ClientType;
import org.apache.rocketmq.studio.common.domain.enums.Protocol;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProxyAdminClientTest {

    private final ProxyAdminClient client = new ProxyAdminClient("", "");

    /**
     * RFC 2202 test case 2: key="Jefe", data="what do ya want for nothing?" ->
     * HMAC-SHA1 = effcdf6ae5eb2fa2d27416d5f184df9c259a7c79. Guards the ACL signing scheme
     * (the proxy rejects anything but hex of the raw HMAC bytes).
     */
    @Test
    void signProducesRfc2202HmacSha1Hex() {
        String signature = ProxyAdminClient.sign("what do ya want for nothing?", "Jefe");
        assertThat(signature).isEqualTo("effcdf6ae5eb2fa2d27416d5f184df9c259a7c79");
    }

    @Test
    void parseTypeHandlesCaseAndInvalidInput() {
        assertThat(client.parseType("Producer")).isEqualTo(ClientType.Producer);
        assertThat(client.parseType("consumer")).isEqualTo(ClientType.Consumer);
        assertThat(client.parseType("CONSUMER")).isEqualTo(ClientType.Consumer);
        assertThat(client.parseType(null)).isNull();
        assertThat(client.parseType("  ")).isNull();
        assertThat(client.parseType("unknown")).isNull();
    }

    @Test
    void mapLanguageCoversProtoValuesAndUnspecified() {
        assertThat(client.mapLanguage("JAVA")).isEqualTo(ClientLanguage.Java);
        assertThat(client.mapLanguage("GOLANG")).isEqualTo(ClientLanguage.Go);
        assertThat(client.mapLanguage("PYTHON")).isEqualTo(ClientLanguage.Python);
        assertThat(client.mapLanguage("CPP")).isEqualTo(ClientLanguage.Cpp);
        assertThat(client.mapLanguage("DOT_NET")).isEqualTo(ClientLanguage.CSharp);
        assertThat(client.mapLanguage("NODE_JS")).isEqualTo(ClientLanguage.NodeJS);
        assertThat(client.mapLanguage("LANGUAGE_UNSPECIFIED")).isNull();
        assertThat(client.mapLanguage("")).isNull();
        assertThat(client.mapLanguage("SOME_NEW_LANG")).isNull();
    }

    @Test
    void toMillisConvertsProtobufTimestamp() {
        assertThat(client.toMillis(Timestamp.newBuilder().setSeconds(1700000000).setNanos(500_000_000).build()))
                .isEqualTo(1700000000500L);
        assertThat(client.toMillis(null)).isZero();
    }

    @Test
    void toConnectionVOMapsConsumerWithGroup() {
        ClientInstance instance = ClientInstance.newBuilder()
                .setClientId("cid-1")
                .setRole(ClientRole.CLIENT_ROLE_PUSH_CONSUMER)
                .setLanguage(Language.JAVA)
                .setClientVersion("5.0.7")
                .setAccessPoint("10.0.0.1:8081")
                .setProxyEndpoint("10.0.0.9:8083")
                .addGroups("group-a")
                .addTopics("topic-x")
                .build();
        ClientConnectionVO vo = client.toConnectionVO(instance);
        assertThat(vo.getClientId()).isEqualTo("cid-1");
        assertThat(vo.getType()).isEqualTo(ClientType.Consumer);
        assertThat(vo.getProtocol()).isEqualTo(Protocol.gRPC);
        assertThat(vo.getLanguage()).isEqualTo(ClientLanguage.Java);
        assertThat(vo.getVersion()).isEqualTo("5.0.7");
        assertThat(vo.getAddress()).isEqualTo("10.0.0.1:8081");
        assertThat(vo.getClusterName()).isEqualTo("10.0.0.9:8083");
        assertThat(vo.getGroupOrTopic()).isEqualTo("group-a");
        assertThat(vo.isPartial()).isFalse();
    }

    @Test
    void toConnectionVOMapsProducerWithTopicWhenNoGroup() {
        ClientInstance instance = ClientInstance.newBuilder()
                .setClientId("cid-2")
                .setRole(ClientRole.CLIENT_ROLE_PRODUCER)
                .addTopics("topic-y")
                .build();
        ClientConnectionVO vo = client.toConnectionVO(instance);
        assertThat(vo.getType()).isEqualTo(ClientType.Producer);
        assertThat(vo.getGroupOrTopic()).isEqualTo("topic-y");
        assertThat(client.isProducer(instance)).isTrue();
    }

    @Test
    void toRouteEventVOCarriesSnapshotCounts() {
        RouteChangeEvent event = RouteChangeEvent.newBuilder()
                .setEventType(RouteChangeEventType.QUEUE_SCALE)
                .setTopic("topic-a")
                .setCluster("DefaultCluster")
                .setBrokerName("broker-a")
                .setPreviousReadQueueNums(4)
                .setCurrentReadQueueNums(8)
                .setTimestamp(Timestamp.newBuilder().setSeconds(1700000000).build())
                .setRouteSnapshot(TopicRouteSnapshot.newBuilder()
                        .setTopic("topic-a")
                        .addBrokers(apache.rocketmq.v2.BrokerInfo.newBuilder().setBrokerName("broker-a"))
                        .addQueues(apache.rocketmq.v2.QueueInfo.newBuilder().setBrokerName("broker-a"))
                        .build())
                .build();
        ProxyAdminDiagnosticsVO.RouteEvent vo = client.toRouteEventVO(event);
        assertThat(vo.getEventType()).isEqualTo("QUEUE_SCALE");
        assertThat(vo.getTopic()).isEqualTo("topic-a");
        assertThat(vo.getPreviousReadQueueNums()).isEqualTo(4);
        assertThat(vo.getCurrentReadQueueNums()).isEqualTo(8);
        assertThat(vo.getTimestampMillis()).isEqualTo(1700000000000L);
        assertThat(vo.isHasSnapshot()).isTrue();
        assertThat(vo.getSnapshotBrokerCount()).isEqualTo(1);
        assertThat(vo.getSnapshotQueueCount()).isEqualTo(1);
    }

    @Test
    void toRouteEventVOWithoutSnapshot() {
        RouteChangeEvent event = RouteChangeEvent.newBuilder()
                .setEventType(RouteChangeEventType.BROKER_ONLINE)
                .setTopic("topic-b")
                .build();
        ProxyAdminDiagnosticsVO.RouteEvent vo = client.toRouteEventVO(event);
        assertThat(vo.isHasSnapshot()).isFalse();
        assertThat(vo.getEventType()).isEqualTo("BROKER_ONLINE");
    }

    @Test
    void listClientsRejectsBlankEndpoint() {
        assertThatThrownBy(() -> client.listClients(" ", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("endpoint");
    }

    @Test
    void popReceiptHandlesRequireGroup() {
        assertThatThrownBy(() -> client.describePopReceiptHandles("127.0.0.1:8083", " ", null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("group");
    }

    @Test
    void batchConsumeDiagnosticsRequireGroup() {
        assertThatThrownBy(() -> client.describeBatchConsumeDiagnostics("127.0.0.1:8083", "", null, null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("group");
    }

    @Test
    void collectRouteEventsRejectsBlankEndpoint() {
        assertThatThrownBy(() -> client.collectRouteEvents(null, List.of(), 3, 50))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("endpoint");
    }

    @Test
    void resourceBuilderRoundTrip() {
        Resource resource = Resource.newBuilder().setName("topic-a").build();
        assertThat(resource.getName()).isEqualTo("topic-a");
    }
}
