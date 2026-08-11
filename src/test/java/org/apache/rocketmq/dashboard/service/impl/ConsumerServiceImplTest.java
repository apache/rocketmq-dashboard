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
package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ConsumerServiceImplTest {

    @InjectMocks
    private ConsumerServiceImpl consumerService;

    @Mock
    private MQAdminExt mqAdminExt;

    @Test
    public void testQueryConsumeStatsWithoutAddress() throws Exception {
        ConsumeStats consumeStats = new ConsumeStats();
        when(mqAdminExt.examineConsumeStats("group")).thenReturn(consumeStats);

        Assert.assertTrue(consumerService.queryConsumeStatsListByGroupName("group", null).isEmpty());

        verify(mqAdminExt).examineConsumeStats("group");
        verify(mqAdminExt, never()).examineConsumeStats(null, "group", null, 3000);
    }

    @Test
    public void testGetConsumerConnectionWithoutAddress() throws Exception {
        ConsumerConnection expected = new ConsumerConnection();
        when(mqAdminExt.examineConsumerConnectionInfo("group")).thenReturn(expected);

        Assert.assertSame(expected, consumerService.getConsumerConnection("group", " "));

        verify(mqAdminExt).examineConsumerConnectionInfo("group");
        verify(mqAdminExt, never()).examineConsumerConnectionInfo("group", " ");
    }
}
