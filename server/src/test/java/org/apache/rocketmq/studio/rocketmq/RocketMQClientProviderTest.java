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
package org.apache.rocketmq.studio.rocketmq;

import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.studio.cluster.client.ClientConnectionVO;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocketMQClientProviderTest {

    @Mock
    private ObjectProvider<DefaultMQAdminExt> adminExtProvider;

    @Mock
    private DefaultMQAdminExt adminExt;

    private RocketMQClientProvider provider;

    @BeforeEach
    void setUp() {
        when(adminExtProvider.getIfAvailable()).thenReturn(adminExt);
        provider = new RocketMQClientProvider(adminExtProvider);
    }

    @Test
    void producerScanTreatsNullTopicSetAsEmpty() throws Exception {
        when(adminExt.fetchAllTopicList()).thenReturn(new TopicList());

        List<ClientConnectionVO> connections = provider.findConnections("cluster-a", "Producer");

        assertThat(connections).isEmpty();
        verify(adminExt).fetchAllTopicList();
        verify(adminExt, never()).examineProducerConnectionInfo(anyString(), anyString());
    }
}
