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
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ResourceDeleteInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserDeleteToolHandlerTest {

    private AclService aclService;
    private UserDeleteToolHandler handler;

    @BeforeEach
    void setUp() {
        aclService = mock(AclService.class);
        handler = new UserDeleteToolHandler(aclService);
    }

    private static AclUserVO user() {
        return AclUserVO.builder()
                .id(42L).username("alice").admin(false)
                .clusters(List.of("cluster-a")).build();
    }

    @Test
    void executeDeletesTheUserByIdUnderTheContextInstance() {
        Void result = handler.execute(
                new ResourceDeleteInput(null, "alice"),
                new ToolExecutionContext("inst-1", null, null, null));

        assertThat(result).isNull();
        verify(aclService).deleteUser("alice", "inst-1");
    }

    @Test
    void thePreviewFetchesTheUserUnderTheContextInstanceAndShowsItAsTheBeforeDiff() {
        when(aclService.getUser("alice", "inst-1")).thenReturn(user());

        ToolPlan plan = handler.preview(
                new ResourceDeleteInput(null, "alice"),
                new ToolExecutionContext("inst-1", null, null, null));

        verify(aclService).getUser("alice", "inst-1");
        AclUserItem before = plan.before(AclUserItem.class);
        assertThat(before).isNotNull();
        assertThat(before.id()).isEqualTo("42");
        assertThat(before.username()).isEqualTo("alice");
        assertThat(before.clusters()).containsExactly("cluster-a");
        // A delete has no after state.
        assertThat(plan.after()).isEmpty();
    }

    @Test
    void theDeletePlanDescribesTheCredentialDeletion() {
        when(aclService.getUser("alice", "inst-1")).thenReturn(user());

        ToolPlan plan = handler.preview(
                new ResourceDeleteInput(null, "alice"),
                new ToolExecutionContext("inst-1", null, null, null));

        // The credential-deletion consequence lives in the plan's impact lines.
        assertThat(plan.summary()).contains("alice").contains("inst-1");
        assertThat(plan.impact()).anyMatch(impact -> impact.contains("credentials"));
    }

    @Test
    void theHandlerAdvertisesTheDeleteToolNameAndInputType() {
        assertThat(handler.name()).isEqualTo("rmq.user.delete");
        assertThat(handler.inputType()).isEqualTo(ResourceDeleteInput.class);
    }
}
