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
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ResourceDeleteInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclMutationInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.PlanDescription;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AclDeleteToolHandler extends MutationToolHandler<ResourceDeleteInput, Void> {

    private static final PlanDescription PLAN_DESCRIPTION = new PlanDescription(
            "delete ACL rule '%s' in cluster '%s'.",
            List.of("Permanently removes the selected ACL rule."),
            List.of("Removing this rule changes permissions for subsequent broker requests."));

    private final AclService aclService;

    public AclDeleteToolHandler(AclService aclService) {
        super(ResourceDeleteInput.class);
        this.aclService = aclService;
    }

    @Override
    public String name() {
        return "rmq.acl.delete";
    }

    @Override
    public ToolPlan preview(ResourceDeleteInput input, ToolExecutionContext context) {
        AclMutationInput before = AclMutationInput.from(
                aclService.getRule(input.id(), context.cluster()));
        return PLAN_DESCRIPTION.builder(input.id(), context.cluster())
                .before(before)
                .build();
    }

    @Override
    public Void execute(ResourceDeleteInput input, ToolExecutionContext context) {
        aclService.deleteRule(input.id(), context.cluster());
        return null;
    }
}
