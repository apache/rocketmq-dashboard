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

import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.MqAdminProperties;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RocketMQClusterResolverTest {
    private final RocketMQProperties properties = new RocketMQProperties();
    private final MqAdminProperties credentials = new MqAdminProperties();
    private final MqAdminExtFactory factory = mock(MqAdminExtFactory.class);
    private final RocketMQDefaultClusterResolver service = new RocketMQDefaultClusterResolver(properties, credentials, factory);

    @Test
    void disabledConfigurationDoesNotContactNameServer() {
        assertThat(service.find("DefaultCluster")).isEmpty();
        assertThat(service.names()).isEmpty();
        verifyNoInteractions(factory);
    }

    @Test
    void discoversEveryPhysicalNameAndNeverSelectsTheFirstForAnUnknownName() throws Exception {
        properties.setNamesrvAddr(" configured:9876 ");
        MQAdminExt admin = mock(MQAdminExt.class);
        ClusterInfo info = new ClusterInfo();
        HashMap<String, Set<String>> clusters = new HashMap<>();
        clusters.put("FirstCluster", Set.of("broker-a"));
        clusters.put("SecondCluster", Set.of("broker-b"));
        info.setClusterAddrTable(clusters);
        when(admin.examineBrokerClusterInfo()).thenReturn(info);
        when(factory.execute(eq("configured:9876"), eq(null), any()))
                .thenAnswer(call -> call.<MqAdminExtFactory.AdminAction<Object>>getArgument(2).apply(admin));
        assertThat(service.find("SecondCluster")).get().satisfies(instance -> {
            assertThat(instance.getId()).isNull();
            assertThat(instance.getName()).isEqualTo("SecondCluster");
            assertThat(instance.getEndpoint()).isEqualTo("configured:9876");
        });
        assertThat(service.find("unknown")).isEmpty();
    }

    @Test
    void credentialReferenceProtectsTheConfiguredAdminConnection() {
        properties.setNamesrvAddr("configured:9876");
        MqAdminProperties.Credential credential = new MqAdminProperties.Credential();
        credential.setAccessKey("ak");
        credential.setSecretKey("sk");
        credentials.getCredentials().put("admin", credential);
        service.execute(admin -> null);
        verify(factory).execute(eq("configured:9876"), any(AclClientRPCHook.class), eq("admin"), any());
        credentials.getCredentials().clear();
        assertThatThrownBy(() -> service.execute(admin -> null)).isInstanceOf(BusinessException.class);
    }
}
