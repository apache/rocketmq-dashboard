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

import org.apache.rocketmq.dashboard.service.ClusterInfoService;
import org.apache.rocketmq.remoting.protocol.body.UserInfo;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AclServiceImplTest {

    @InjectMocks
    @Spy
    private AclServiceImpl aclService;

    @Mock
    private MQAdminExt mqAdminExt;

    @Mock
    private ClusterInfoService clusterInfoService;

    @Test
    public void testListUsersIsEmptyWhenAnyBrokerHasNoUsers() throws Exception {
        String clusterName = "cluster-a";
        String brokerName = "broker-a";
        String firstAddress = "127.0.0.1:10911";
        String secondAddress = "127.0.0.1:10912";
        UserInfo user = new UserInfo();
        user.setUsername("user-a");

        doReturn(Arrays.asList(firstAddress, secondAddress))
            .when(aclService).getBrokerAddressList(clusterName, brokerName);
        when(mqAdminExt.listUser(firstAddress, "")).thenReturn(Collections.singletonList(user));
        when(mqAdminExt.listUser(secondAddress, "")).thenReturn(Collections.emptyList());

        Assert.assertTrue(aclService.listUsers(clusterName, brokerName).isEmpty());
        verify(mqAdminExt).listUser(firstAddress, "");
        verify(mqAdminExt).listUser(secondAddress, "");
    }
}
