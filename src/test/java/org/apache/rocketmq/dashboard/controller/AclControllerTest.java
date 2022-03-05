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

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import java.util.List;
import org.apache.rocketmq.common.AclConfig;
import org.apache.rocketmq.common.PlainAccessConfig;
import org.apache.rocketmq.common.protocol.body.ClusterInfo;
import org.apache.rocketmq.dashboard.model.request.AclRequest;
import org.apache.rocketmq.dashboard.service.impl.AclServiceImpl;
import org.apache.rocketmq.dashboard.util.MockObjectUtil;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AclControllerTest extends BaseControllerTest {

    @InjectMocks
    private AclController aclController;

    @Spy
    private AclServiceImpl aclService;

    @Before
    public void init() throws Exception {
        AclConfig aclConfig = MockObjectUtil.createAclConfig();
        when(mqAdminExt.examineBrokerClusterAclConfig(anyString())).thenReturn(aclConfig);
        ClusterInfo clusterInfo = MockObjectUtil.createClusterInfo();
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        doNothing().when(mqAdminExt).createAndUpdatePlainAccessConfig(anyString(), any(PlainAccessConfig.class));
        doNothing().when(mqAdminExt).deletePlainAccessConfig(anyString(), anyString());
        doNothing().when(mqAdminExt).updateGlobalWhiteAddrConfig(anyString(), anyString());
    }

    @Test
    public void testIsEnableAcl() throws Exception {
        final String url = "/acl/enable.query";
        // 1. disable acl.
        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(false));

        // 2.enable acl.
        super.mockRmqConfigure();
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    public void testGetAclConfig() throws Exception {
        final String url = "/acl/config.query";

        // 1. broker addr table is not empty.
        ClusterInfo clusterInfo = MockObjectUtil.createClusterInfo();
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isMap())
            .andExpect(jsonPath("$.data.globalWhiteAddrs").isNotEmpty())
            .andExpect(jsonPath("$.data.plainAccessConfigs").isNotEmpty())
            .andExpect(jsonPath("$.data.plainAccessConfigs[0].secretKey").isNotEmpty());

        // 2. broker addr table is empty.
        clusterInfo.getBrokerAddrTable().clear();
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isMap())
            .andExpect(jsonPath("$.data.globalWhiteAddrs").isEmpty())
            .andExpect(jsonPath("$.data.plainAccessConfigs").isEmpty());

        // 3. login required and user info is null.
        when(configure.isLoginRequired()).thenReturn(true);
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(MockObjectUtil.createClusterInfo());
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isMap())
            .andExpect(jsonPath("$.data.globalWhiteAddrs").isNotEmpty())
            .andExpect(jsonPath("$.data.plainAccessConfigs").isNotEmpty())
            .andExpect(jsonPath("$.data.plainAccessConfigs[0].secretKey").isEmpty());
        // 4. login required, but user is not admin. emmmm, Mockito may can not mock static method.
    }

    @Test
    public void testAddAclConfig() throws Exception {
        final String url = "/acl/add.do";
        PlainAccessConfig accessConfig = new PlainAccessConfig();
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON_UTF8);

        // 1. access key is null.
        requestBuilder.content(JSON.toJSONString(accessConfig));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(-1))
            .andExpect(jsonPath("$.errMsg").exists());

        // 2. secret key is null.
        accessConfig.setAccessKey("test-access-key");
        requestBuilder.content(JSON.toJSONString(accessConfig));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(-1))
            .andExpect(jsonPath("$.errMsg").exists());

        ClusterInfo clusterInfo = MockObjectUtil.createClusterInfo();
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);

        // 3. add if the access key not exist.
        accessConfig.setSecretKey("12345678");
        requestBuilder.content(JSON.toJSONString(accessConfig));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));

        // 4. add failed if the access key is existed.
        accessConfig.setAccessKey("rocketmq2");
        requestBuilder.content(JSON.toJSONString(accessConfig));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(-1))
            .andExpect(jsonPath("$.errMsg").exists());

        // 5.  add failed if there is no alive broker.
        clusterInfo.getBrokerAddrTable().clear();
        accessConfig.setAccessKey("test-access-key");
        requestBuilder.content(JSON.toJSONString(accessConfig));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(-1))
            .andExpect(jsonPath("$.errMsg").exists());
    }

    @Test
    public void testDeleteAclConfig() throws Exception {
        final String url = "/acl/delete.do";
        PlainAccessConfig accessConfig = new PlainAccessConfig();
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON_UTF8);

        // 1. access key is null.
        requestBuilder.content(JSON.toJSONString(accessConfig));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(-1))
            .andExpect(jsonPath("$.errMsg").exists());

        // 2. access key is not null.
        accessConfig.setAccessKey("rocketmq");
        requestBuilder.content(JSON.toJSONString(accessConfig));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    public void testUpdateAclConfig() throws Exception {
        final String url = "/acl/update.do";
        PlainAccessConfig accessConfig = new PlainAccessConfig();
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON_UTF8);

        // 1. secret key is null.
        accessConfig.setAccessKey("rocketmq");
        requestBuilder.content(JSON.toJSONString(accessConfig));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(-1))
            .andExpect(jsonPath("$.errMsg").exists());

        // 2. update.
        accessConfig.setSecretKey("abcdefghjkl");
        requestBuilder.content(JSON.toJSONString(accessConfig));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    public void testAddAclTopicConfig() throws Exception {
        final String url = "/acl/topic/add.do";
        AclRequest request = new AclRequest();
        request.setConfig(createDefaultPlainAccessConfig());

        // 1. if not exist.
        request.setTopicPerm("test_topic=PUB");
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON_UTF8);
        requestBuilder.content(JSON.toJSONString(request));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));

        // 2. if exist.
        request.setTopicPerm("topicA=PUB");
        requestBuilder.content(JSON.toJSONString(request));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));

        // 3. if access key not exist.
        request.getConfig().setAccessKey("test_access_key123");
        requestBuilder.content(JSON.toJSONString(request));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    public void testAddAclGroupConfig() throws Exception {
        final String url = "/acl/group/add.do";
        AclRequest request = new AclRequest();
        request.setConfig(createDefaultPlainAccessConfig());

        // 1. if not exist.
        request.setGroupPerm("test_consumer=PUB|SUB");
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON_UTF8);
        requestBuilder.content(JSON.toJSONString(request));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));

        // 2. if exist.
        request.setGroupPerm("groupA=PUB|SUB");
        requestBuilder.content(JSON.toJSONString(request));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));

        // 3. if access key not exist.
        request.getConfig().setAccessKey("test_access_key123");
        requestBuilder.content(JSON.toJSONString(request));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    public void testDeletePermConfig() throws Exception {
        final String url = "/acl/perm/delete.do";
        AclRequest request = new AclRequest();
        request.setConfig(createDefaultPlainAccessConfig());
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON_UTF8);
        requestBuilder.content(JSON.toJSONString(request));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));

        // if access key not exist.
        request.getConfig().setAccessKey("test_access_key123");
        requestBuilder.content(JSON.toJSONString(request));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    public void testSyncConfig() throws Exception {
        final String url = "/acl/sync.do";
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON_UTF8);
        requestBuilder.content(JSON.toJSONString(createDefaultPlainAccessConfig()));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    public void testAddWhiteList() throws Exception {
        final String url = "/acl/white/list/add.do";
        List<String> whiteList = Lists.newArrayList("192.168.0.1");

        // 1. if global white list is not null.
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON_UTF8);
        requestBuilder.content(JSON.toJSONString(whiteList));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));

        // 2. if global white list is null.
        AclConfig aclConfig = MockObjectUtil.createAclConfig();
        aclConfig.setGlobalWhiteAddrs(null);
        when(mqAdminExt.examineBrokerClusterAclConfig(anyString())).thenReturn(aclConfig);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    public void testDeleteWhiteAddr() throws Exception {
        final String url = "/acl/white/list/delete.do";
        requestBuilder = MockMvcRequestBuilders.delete(url);
        requestBuilder.param("request", "localhost");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    public void testSynchronizeWhiteList() throws Exception {
        final String url = "/acl/white/list/sync.do";
        List<String> whiteList = Lists.newArrayList();

        // 1. if white list for syncing is empty.
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON_UTF8);
        requestBuilder.content(JSON.toJSONString(whiteList));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(-1))
            .andExpect(jsonPath("$.errMsg").exists());

        // 2. if white list for syncing is not empty.
        whiteList.add("localhost");
        requestBuilder.content(JSON.toJSONString(whiteList));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Override protected Object getTestController() {
        return aclController;
    }

    private PlainAccessConfig createDefaultPlainAccessConfig() {
        PlainAccessConfig config = new PlainAccessConfig();
        config.setAdmin(false);
        config.setAccessKey("rocketmq");
        config.setSecretKey("123456789");
        config.setDefaultGroupPerm("SUB");
        config.setDefaultTopicPerm("DENY");
        config.setTopicPerms(Lists.newArrayList("topicA=DENY", "topicB=PUB|SUB"));
        config.setGroupPerms(Lists.newArrayList("groupA=DENY", "groupB=PUB|SUB"));

        return config;
    }
}