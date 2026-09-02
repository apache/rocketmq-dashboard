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
package org.apache.rocketmq.studio.provider.tencent;

import com.tencentcloudapi.trocket.v20230308.TrocketClient;
import com.tencentcloudapi.trocket.v20230308.models.DescribeRoleListRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeRoleListResponse;
import com.tencentcloudapi.trocket.v20230308.models.ModifyRoleRequest;
import com.tencentcloudapi.trocket.v20230308.models.RoleItem;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.instance.acl.AclRuleVO;
import org.apache.rocketmq.studio.instance.acl.AclUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TencentAclServiceTest {

    private static final String INSTANCE_ID = "instance-1";
    private static final String CLOUD_INSTANCE_ID = "rmq-abc";
    private static final Long CREDENTIAL_ID = 1L;
    private static final String REGION = "ap-guangzhou";

    @Mock
    private TencentClientFactory clientFactory;
    @Mock
    private InstanceRepository instanceRepository;
    @Mock
    private TrocketClient client;

    private TencentAclService service;

    @BeforeEach
    void setUp() {
        service = new TencentAclService(clientFactory, instanceRepository);
        when(instanceRepository.findByIdentifier(INSTANCE_ID)).thenReturn(Optional.of(InstanceVO.builder()
                .cloudInstanceId(CLOUD_INSTANCE_ID)
                .credentialId(CREDENTIAL_ID)
                .regionId(REGION)
                .build()));
        lenient().when(clientFactory.call(anyLong(), anyString(), any())).thenAnswer(invocation -> {
            TencentClientFactory.TencentCall<Object> action = invocation.getArgument(2);
            return action.execute(client);
        });
    }

    @Test
    void updateUserUsesUsernameAsRoleNameTest() throws Exception {
        AclUserVO updated = service.updateUser(INSTANCE_ID, AclUserVO.builder()
                .username("  reader-role  ")
                .permRead(false)
                .permWrite(true)
                .build());

        ArgumentCaptor<ModifyRoleRequest> requestCaptor = ArgumentCaptor.forClass(ModifyRoleRequest.class);
        verify(client).ModifyRole(requestCaptor.capture());
        ModifyRoleRequest request = requestCaptor.getValue();
        assertThat(request.getRole()).isEqualTo("reader-role");
        assertThat(request.getPermRead()).isFalse();
        assertThat(request.getPermWrite()).isTrue();
        assertThat(updated.getId()).isNull();
        assertThat(updated.getUsername()).isEqualTo("reader-role");
    }

    @Test
    void listUsersShouldSkipRolesWithoutNamesTest() throws Exception {
        RoleItem blank = new RoleItem();
        blank.setRoleName("  ");
        RoleItem valid = new RoleItem();
        valid.setRoleName("reader-role");
        DescribeRoleListResponse response = new DescribeRoleListResponse();
        response.setData(new RoleItem[]{null, blank, valid});
        when(client.DescribeRoleList(any())).thenReturn(response);

        assertThat(service.listUsers(INSTANCE_ID))
                .extracting(AclUserVO::getUsername)
                .containsExactly("reader-role");
        assertThat(service.getUserCredentials(INSTANCE_ID, "  reader-role  ").getUsername())
                .isEqualTo("reader-role");
    }

    @Test
    void listUsersShouldFetchExactlyTenThousandTencentRolesTest() throws Exception {
        when(client.DescribeRoleList(any())).thenAnswer(invocation -> {
            DescribeRoleListRequest request = invocation.getArgument(0);
            DescribeRoleListResponse response = new DescribeRoleListResponse();
            response.setTotalCount(10000L);
            response.setData(rolePage(request.getOffset(), request.getLimit(), 10000));
            return response;
        });

        assertThat(service.listUsers(INSTANCE_ID)).hasSize(10000);
        verify(client, times(100)).DescribeRoleList(any());
    }

    @Test
    void listRulesShouldFetchPastLegacyTenThousandTencentRoleCapTest() throws Exception {
        when(client.DescribeRoleList(any())).thenAnswer(invocation -> {
            DescribeRoleListRequest request = invocation.getArgument(0);
            DescribeRoleListResponse response = new DescribeRoleListResponse();
            response.setTotalCount(10001L);
            response.setData(rolePage(request.getOffset(), request.getLimit(), 10001));
            return response;
        });

        assertThat(service.listRules(INSTANCE_ID, null)).hasSize(10001);
        verify(client, times(101)).DescribeRoleList(any());
    }

    @Test
    void updateUserShouldFindRolePastLegacyTenThousandTencentRoleCapTest() throws Exception {
        when(client.DescribeRoleList(any())).thenAnswer(invocation -> {
            DescribeRoleListRequest request = invocation.getArgument(0);
            DescribeRoleListResponse response = new DescribeRoleListResponse();
            response.setTotalCount(10001L);
            response.setData(rolePage(request.getOffset(), request.getLimit(), 10001));
            return response;
        });

        AclUserVO updated = service.updateUser(INSTANCE_ID, AclUserVO.builder()
                .username("role-10000")
                .build());

        assertThat(updated.getUsername()).isEqualTo("role-10000");
        verify(client, times(101)).DescribeRoleList(any());
        verify(client).ModifyRole(any());
    }

    @Test
    void listUsersShouldStopOnShortPageWhenTencentTotalCountIsMissingTest() throws Exception {
        when(client.DescribeRoleList(any())).thenAnswer(invocation -> {
            DescribeRoleListRequest request = invocation.getArgument(0);
            DescribeRoleListResponse response = new DescribeRoleListResponse();
            response.setData(rolePage(request.getOffset(), request.getLimit(), 150));
            return response;
        });

        assertThat(service.listUsers(INSTANCE_ID)).hasSize(150);
        verify(client, times(2)).DescribeRoleList(any());
    }

    @Test
    void createUserShouldRejectNullPayloadTest() {
        assertThatThrownBy(() -> service.createUser(INSTANCE_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("ACL username is required");
    }

    @Test
    void updateUserShouldPreserveOmittedPermissionsTest() throws Exception {
        RoleItem role = new RoleItem();
        role.setRoleName("reader-role");
        role.setPermRead(false);
        role.setPermWrite(false);
        DescribeRoleListResponse response = new DescribeRoleListResponse();
        response.setData(new RoleItem[]{role});
        when(client.DescribeRoleList(any())).thenReturn(response);

        AclUserVO updated = service.updateUser(INSTANCE_ID, AclUserVO.builder()
                .username("reader-role")
                .build());

        ArgumentCaptor<ModifyRoleRequest> requestCaptor = ArgumentCaptor.forClass(ModifyRoleRequest.class);
        verify(client).ModifyRole(requestCaptor.capture());
        ModifyRoleRequest request = requestCaptor.getValue();
        assertThat(request.getPermRead()).isFalse();
        assertThat(request.getPermWrite()).isFalse();
        assertThat(updated.getPermRead()).isFalse();
        assertThat(updated.getPermWrite()).isFalse();
    }

    @Test
    void updateUserThrowsNotFoundWhenRoleMissingTest() throws Exception {
        DescribeRoleListResponse response = new DescribeRoleListResponse();
        response.setData(new RoleItem[0]);
        when(client.DescribeRoleList(any())).thenReturn(response);

        assertThatThrownBy(() -> service.updateUser(INSTANCE_ID, AclUserVO.builder()
                .username("ghost-role")
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ACL user not found: ghost-role");
    }

    @Test
    void createRuleShouldExpandAllActionToReadAndWritePermissionsTest() throws Exception {
        AclRuleVO rule = AclRuleVO.builder()
                .principal("reader-role")
                .resource("*")
                .resourceType("Cluster")
                .resourcePattern("LITERAL")
                .actions(List.of("ALL"))
                .decision("ALLOW")
                .scope("cluster")
                .build();

        AclRuleVO created = service.createRule(INSTANCE_ID, rule);

        ArgumentCaptor<ModifyRoleRequest> requestCaptor = ArgumentCaptor.forClass(ModifyRoleRequest.class);
        verify(client).ModifyRole(requestCaptor.capture());
        ModifyRoleRequest request = requestCaptor.getValue();
        assertThat(request.getRole()).isEqualTo("reader-role");
        assertThat(request.getPermRead()).isTrue();
        assertThat(request.getPermWrite()).isTrue();
        assertThat(created.getActions()).containsExactly("PUB", "SUB");
    }

    @Test
    void createRuleShouldRejectResourceScopedTencentRulesTest() {
        AclRuleVO rule = AclRuleVO.builder()
                .principal("reader-role")
                .resource("orders-*")
                .resourceType("Topic")
                .resourcePattern("PREFIX")
                .actions(List.of("SUB"))
                .decision("ALLOW")
                .scope("cluster")
                .build();

        assertThatThrownBy(() -> service.createRule(INSTANCE_ID, rule))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Tencent Cloud roles only support cluster-wide ACL rules on resource *")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));
    }

    @Test
    void createRuleShouldRejectDenyTencentRulesTest() {
        AclRuleVO rule = AclRuleVO.builder()
                .principal("reader-role")
                .resource("*")
                .resourceType("Cluster")
                .resourcePattern("LITERAL")
                .actions(List.of("SUB"))
                .decision("DENY")
                .scope("cluster")
                .build();

        assertThatThrownBy(() -> service.createRule(INSTANCE_ID, rule))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Tencent Cloud roles only support ALLOW ACL rules")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));
    }

    private static RoleItem[] rolePage(Long offset, Long limit, int total) {
        int start = offset == null ? 0 : offset.intValue();
        int size = Math.min(limit == null ? TencentAclService.PAGE_SIZE : limit.intValue(),
                Math.max(total - start, 0));
        return IntStream.range(0, size)
                .mapToObj(index -> role("role-" + (start + index)))
                .toArray(RoleItem[]::new);
    }

    private static RoleItem role(String name) {
        RoleItem role = new RoleItem();
        role.setRoleName(name);
        role.setPermRead(true);
        role.setPermWrite(false);
        return role;
    }
}
