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
package org.apache.rocketmq.dashboard.service.client;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.impl.MQClientAPIImpl;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.dashboard.util.MockObjectUtil;
import org.apache.rocketmq.remoting.RemotingClient;
import org.apache.rocketmq.remoting.exception.RemotingTimeoutException;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExtImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProxyAdminImpl}.
 *
 * <p>The RemotingClient is reached through MQAdminInstance's jOOR reflection chain,
 * so the mocked DefaultMQAdminExt has its internal fields populated via
 * ReflectionTestUtils just like MQAdminExtImplTest does.</p>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ProxyAdminImplTest {

    private static final String BROKER_ADDR = "127.0.0.1:10911";

    @InjectMocks
    private ProxyAdminImpl proxyAdmin;

    @Mock
    private DefaultMQAdminExt defaultMQAdminExt;

    @Mock
    private DefaultMQAdminExtImpl defaultMQAdminExtImpl;

    @Mock
    private MQClientInstance mqClientInstance;

    @Mock
    private MQClientAPIImpl mQClientAPIImpl;

    @Mock
    private RemotingClient remotingClient;

    @Before
    public void setUp() {
        MQAdminInstance.setCurrentMQAdminExt(defaultMQAdminExt);
        // Wire the jOOR reflection chain used by MQAdminInstance.threadLocalRemotingClient()
        ReflectionTestUtils.setField(defaultMQAdminExt, "defaultMQAdminExtImpl", defaultMQAdminExtImpl);
        ReflectionTestUtils.setField(defaultMQAdminExtImpl, "mqClientInstance", mqClientInstance);
        ReflectionTestUtils.setField(mqClientInstance, "mQClientAPIImpl", mQClientAPIImpl);
        ReflectionTestUtils.setField(mQClientAPIImpl, "remotingClient", remotingClient);
    }

    @After
    public void tearDown() {
        MQAdminInstance.clearCurrentMQAdminExt();
    }

    @Test
    public void testExamineConsumerConnectionInfoSuccess() throws Exception {
        ConsumerConnection expected = MockObjectUtil.createConsumerConnection();
        RemotingCommand response = RemotingCommand.createResponseCommand(null);
        response.setCode(0);
        response.setBody(expected.encode());
        when(remotingClient.invokeSync(anyString(), any(RemotingCommand.class), anyLong())).thenReturn(response);

        ConsumerConnection actual = proxyAdmin.examineConsumerConnectionInfo(BROKER_ADDR, "groupA");
        assertNotNull(actual);
        assertEquals(1, actual.getConnectionSet().size());
        assertEquals(expected.getConsumeType(), actual.getConsumeType());
        assertEquals(expected.getMessageModel(), actual.getMessageModel());
    }

    @Test
    public void testExamineConsumerConnectionInfoBrokerError() throws Exception {
        RemotingCommand response = RemotingCommand.createResponseCommand(null);
        response.setCode(206);
        response.setRemark("consumer offline");
        when(remotingClient.invokeSync(anyString(), any(RemotingCommand.class), anyLong())).thenReturn(response);

        try {
            proxyAdmin.examineConsumerConnectionInfo(BROKER_ADDR, "groupA");
            fail("Expected MQBrokerException");
        } catch (MQBrokerException e) {
            assertEquals(206, e.getResponseCode());
            assertEquals("consumer offline", e.getErrorMessage());
        }
    }

    @Test(expected = RemotingTimeoutException.class)
    public void testExamineConsumerConnectionInfoTimeoutRethrown() throws Exception {
        when(remotingClient.invokeSync(anyString(), any(RemotingCommand.class), anyLong()))
            .thenThrow(new RemotingTimeoutException("timeout"));

        proxyAdmin.examineConsumerConnectionInfo(BROKER_ADDR, "groupA");
    }

    @Test(expected = IllegalStateException.class)
    public void testExamineConsumerConnectionInfoWithoutThreadLocalAdmin() throws Exception {
        MQAdminInstance.clearCurrentMQAdminExt();

        proxyAdmin.examineConsumerConnectionInfo(BROKER_ADDR, "groupA");
    }
}
