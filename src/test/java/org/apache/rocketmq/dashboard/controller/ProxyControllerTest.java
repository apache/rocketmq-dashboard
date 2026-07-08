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

package org.apache.rocketmq.dashboard.controller;

import com.google.common.collect.Lists;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.service.impl.ProxyServiceImpl;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ProxyControllerTest extends BaseControllerTest {

    @InjectMocks
    private ProxyController proxyController;

    @Spy
    private ProxyServiceImpl proxyService;

    @Mock
    private RMQConfigure configure;

    @Override
    protected Object getTestController() {
        return proxyController;
    }

    @Before
    public void init() {
        super.mockRmqConfigure();
    }

    @Test
    public void testHomePage() throws Exception {
        final String url = "/proxy/homePage.query";
        when(configure.getProxyAddr()).thenReturn("127.0.0.1:8080");
        when(configure.getProxyAddrs()).thenReturn(Lists.newArrayList("127.0.0.1:8080", "127.0.0.2:8080"));

        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.data.currentProxyAddr").value("127.0.0.1:8080"))
                .andExpect(jsonPath("$.data.proxyAddrList").isArray());
    }

    @Test
    public void testAddProxyAddr() throws Exception {
        final String url = "/proxy/addProxyAddr.do";
        {
            when(configure.getProxyAddrs()).thenReturn(Lists.newArrayList("127.0.0.1:8080"));
            doNothing().when(configure).setProxyAddrs(any());
        }
        requestBuilder = MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newProxyAddr\":\"127.0.0.2:8080\"}");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    public void testUpdateProxyAddr() throws Exception {
        final String url = "/proxy/updateProxyAddr.do";
        {
            doNothing().when(configure).setProxyAddr(anyString());
        }
        requestBuilder = MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"proxyAddr\":\"127.0.0.2:8080\"}");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }
}
