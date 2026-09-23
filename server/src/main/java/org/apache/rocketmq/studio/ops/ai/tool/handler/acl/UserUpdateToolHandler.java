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
package org.apache.rocketmq.studio.ops.ai.tool.handler.acl;

import org.apache.rocketmq.studio.instance.acl.AclService;
import org.apache.rocketmq.studio.instance.acl.AclUserVO;
import org.apache.rocketmq.studio.instance.acl.UpdateAclUserDTO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclUserItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.UserUpdateInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.PlanDescription;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserUpdateToolHandler extends MutationToolHandler<UserUpdateInput, AclUserItem> {

    private static final PlanDescription PLAN_DESCRIPTION = new PlanDescription(
            "update user '%s' in instance '%s'.",
            List.of("Updates the selected ACL identity without exposing credentials."),
            List.of());

    private final AclService aclService;

    public UserUpdateToolHandler(AclService aclService) {
        super(UserUpdateInput.class);
        this.aclService = aclService;
    }

    @Override
    public String name() {
        return "rmq.user.update";
    }

    @Override
    public ToolPlan preview(UserUpdateInput input, ToolExecutionContext context) {
        AclUserItem before = AclUserItem.from(aclService.getUser(input.id(), context.instanceId()));
        AclUserItem after = new AclUserItem(
                before.id(),
                input.username() == null ? before.username() : input.username(),
                input.admin() == null ? before.admin() : input.admin(),
                input.clusters() == null ? before.clusters() : input.clusters());
        return PLAN_DESCRIPTION.builder(input.id(), context.instanceId())
                .before(before)
                .after(after)
                .warningIf(before.equals(after),
                        "The requested ACL user already matches the current state.")
                .build();
    }

    @Override
    public AclUserItem execute(UserUpdateInput input, ToolExecutionContext context) {
        UpdateAclUserDTO update = new UpdateAclUserDTO();
        update.setId(input.id());
        update.setUsername(input.username());
        update.setAdmin(input.admin());
        update.setClusters(input.clusters());
        update.setInstanceId(context.instanceId());
        AclUserVO updated = aclService.updateUser(update, context.instanceId());
        return AclUserItem.from(updated);
    }
}
