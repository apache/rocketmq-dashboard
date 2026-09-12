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

import org.apache.rocketmq.studio.instance.acl.AclRuleVO;
import org.apache.rocketmq.studio.instance.acl.AclService;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolError;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclMutationInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.PlanDescription;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AclUpdateToolHandler extends MutationToolHandler<AclMutationInput, AclRuleVO> {

    private static final PlanDescription PLAN_DESCRIPTION = new PlanDescription(
            "update ACL rule '%s' in cluster '%s'.",
            List.of("Replaces the selected ACL rule."),
            List.of("The updated ACL rule changes permissions for subsequent broker requests."));

    private final AclService aclService;

    public AclUpdateToolHandler(AclService aclService) {
        super(AclMutationInput.class);
        this.aclService = aclService;
    }

    @Override
    public String name() {
        return "rmq.acl.update";
    }

    @Override
    public ToolPlan preview(AclMutationInput input, ToolExecutionContext context) {
        String instanceId = context.cluster();
        AclMutationInput before = AclMutationInput.from(aclService.getRule(input.id(), instanceId));
        AclMutationInput after = AclMutationInput.from(input.toRule(parseId(input.id())));
        return PLAN_DESCRIPTION.builder(input.id(), context.cluster())
                .before(before)
                .after(after)
                .warningIf(before.equals(after),
                        "The requested ACL rule already matches the current state.")
                .build();
    }

    @Override
    public AclRuleVO execute(AclMutationInput input, ToolExecutionContext context) {
        return aclService.updateRule(input.toRule(parseId(input.id())), context.cluster());
    }

    private static Long parseId(String id) {
        if (id == null || id.isBlank()) {
            throw ToolError.ACL_ID_INVALID.exception(id);
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw ToolError.ACL_ID_INVALID.exception(id);
        }
    }
}
