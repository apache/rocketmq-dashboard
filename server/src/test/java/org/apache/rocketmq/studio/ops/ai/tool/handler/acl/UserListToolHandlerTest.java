/*
 * Licensed to the Apache Software Foundation (ASF) under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.studio.ops.ai.tool.handler.acl;

import org.apache.rocketmq.studio.instance.acl.AclService;
import org.apache.rocketmq.studio.instance.acl.AclUserVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclUserItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.InstanceInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserListToolHandlerTest {

    private AclService aclService;
    private UserListToolHandler handler;

    @BeforeEach
    void setUp() {
        aclService = mock(AclService.class);
        handler = new UserListToolHandler(aclService);
    }

    private static AclUserVO user(String username, boolean admin) {
        return AclUserVO.builder()
                .id(7L).username(username).admin(admin)
                .clusters(List.of("cluster-a", "cluster-b")).build();
    }

    @Test
    void theUsersAreListedUnderTheContextInstance() {
        when(aclService.listUsers("inst-1")).thenReturn(List.of(user("alice", false)));

        ListOutput<AclUserItem> output = handler.execute(
                new InstanceInput(null),
                new ToolExecutionContext("inst-1", null, null, null));

        verify(aclService).listUsers("inst-1");
        assertThat(output.items()).hasSize(1);
    }

    @Test
    void everyUserFieldRendersThroughTheItemMapping() {
        when(aclService.listUsers("inst-1")).thenReturn(List.of(user("alice", true)));

        AclUserItem item = handler.execute(
                new InstanceInput(null),
                new ToolExecutionContext("inst-1", null, null, null)).items().get(0);

        assertThat(item.id()).isEqualTo("7");
        assertThat(item.username()).isEqualTo("alice");
        assertThat(item.admin()).isTrue();
        assertThat(item.clusters()).containsExactly("cluster-a", "cluster-b");
    }

    @Test
    void aUserWithoutClustersRendersAnEmptyClusterList() {
        AclUserVO clusterless = AclUserVO.builder()
                .id(1L).username("bob").admin(false).clusters(null).build();
        when(aclService.listUsers("inst-1")).thenReturn(List.of(clusterless));

        AclUserItem item = handler.execute(
                new InstanceInput(null),
                new ToolExecutionContext("inst-1", null, null, null)).items().get(0);

        // A null clusters list from the service is NOT defaulted here - the item passes
        // it through verbatim, so the current mapping contract is null, and the renderer
        // omits the block. Pin the passthrough rather than assume a default.
        assertThat(item.clusters()).isNull();
    }

    @Test
    void anEmptyUserListProducesAnEmptyOutputNeverNull() {
        when(aclService.listUsers("inst-1")).thenReturn(List.of());

        ListOutput<AclUserItem> output = handler.execute(
                new InstanceInput(null),
                new ToolExecutionContext("inst-1", null, null, null));

        assertThat(output.items()).isNotNull().isEmpty();
    }

    @Test
    void theHandlerAdvertisesTheListToolNameAndInputType() {
        assertThat(handler.name()).isEqualTo("rmq.user.list");
        assertThat(handler.inputType()).isEqualTo(InstanceInput.class);
    }
}
