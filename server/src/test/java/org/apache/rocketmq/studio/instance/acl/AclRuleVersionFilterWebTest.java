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
package org.apache.rocketmq.studio.instance.acl;

import org.apache.rocketmq.studio.WebMvcAuthTestSupport;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.cluster.broker.ClusterProvider;
import org.apache.rocketmq.studio.common.config.LegacyJackson2Config;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.instance.InstanceResolver;
import org.apache.rocketmq.studio.provider.tencent.TencentAclService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The console's ACL rule table offers an "ACL 1.0 / ACL 2.0" selector and sends it as
 * {@code aclVersion}; the repository has an {@code acl_version} predicate for it. This slice wires
 * the real {@link AclService} between the controller and a mocked repository, so it fails unless
 * the request parameter survives both layers and reaches the query.
 */
@WebMvcTest(AclController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({LegacyJackson2Config.class, AclService.class})
class AclRuleVersionFilterWebTest extends WebMvcAuthTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AclRepository aclRepository;

    @MockitoBean
    private OperationAuditService operationAuditService;

    @MockitoBean
    private InstanceResolver instanceResolver;

    @MockitoBean
    private TencentAclService tencentAclService;

    @MockitoBean
    private ClusterProvider clusterProvider;

    @MockitoBean
    private ApacheAclReadService apacheAclReadService;

    @Test
    void ruleListShouldApplyTheAclVersionFilterToTheQuery() throws Exception {
        when(aclRepository.findRulePage(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResult.empty(1, 20));

        mockMvc.perform(get("/api/acl/rules").param("aclVersion", "1.0"))
                .andExpect(status().isOk());

        verify(aclRepository).findRulePage(null, null, null, null, "1.0", 1, 20);
    }

    @Test
    void ruleListShouldLeaveTheVersionPredicateOpenWhenNoVersionIsRequested() throws Exception {
        when(aclRepository.findRulePage(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResult.empty(1, 20));

        mockMvc.perform(get("/api/acl/rules"))
                .andExpect(status().isOk());

        verify(aclRepository).findRulePage(null, null, null, null, null, 1, 20);
    }
}
