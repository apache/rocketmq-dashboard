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
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclUserItem;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.UserCreateInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.PlanDescription;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserCreateToolHandler extends MutationToolHandler<UserCreateInput, AclUserItem> {

    private static final PlanDescription PLAN_DESCRIPTION = new PlanDescription(
            "create user '%s' in cluster '%s'.",
            List.of("Creates an ACL identity; credentials are generated only during apply."),
            List.of());

    private final AclService aclService;

    public UserCreateToolHandler(AclService aclService) {
        super(UserCreateInput.class);
        this.aclService = aclService;
    }

    @Override
    public String name() {
        return "rmq.user.create";
    }

    @Override
    public ToolPlan preview(UserCreateInput input, ToolExecutionContext context) {
        String instanceId = context.cluster();
        AclUserItem before = aclService.listUsers(instanceId).stream()
                .filter(user -> input.username().equals(user.getUsername()))
                .findFirst().map(AclUserItem::from).orElse(null);
        AclUserItem after = new AclUserItem(
                null, input.username(), Boolean.TRUE.equals(input.admin()), input.clusters());
        return PLAN_DESCRIPTION.builder(input.username(), context.cluster())
                .before(before)
                .after(after)
                .build();
    }

    @Override
    public AclUserItem execute(UserCreateInput input, ToolExecutionContext context) {
        AclUserVO user = AclUserVO.builder()
                .username(input.username())
                .admin(Boolean.TRUE.equals(input.admin()))
                .clusters(input.clusters())
                .build();
        return AclUserItem.from(aclService.createUser(user, context.cluster()));
    }
}
