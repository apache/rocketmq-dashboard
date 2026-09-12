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
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclMutationInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.PlanDescription;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AclCreateToolHandler extends MutationToolHandler<AclMutationInput, AclRuleVO> {

    private static final PlanDescription PLAN_DESCRIPTION = new PlanDescription(
            "create ACL rule '%s:%s' in cluster '%s'.",
            List.of("Adds a rule to the ACL policy evaluated for broker requests."),
            List.of("The new ACL rule changes permissions for subsequent broker requests."));

    private final AclService aclService;

    public AclCreateToolHandler(AclService aclService) {
        super(AclMutationInput.class);
        this.aclService = aclService;
    }

    @Override
    public String name() {
        return "rmq.acl.create";
    }

    @Override
    public ToolPlan preview(AclMutationInput input, ToolExecutionContext context) {
        return PLAN_DESCRIPTION.builder(input.principal(), input.resource(), context.cluster())
                .after(AclMutationInput.from(input.toRule(null)))
                .build();
    }

    @Override
    public AclRuleVO execute(AclMutationInput input, ToolExecutionContext context) {
        return aclService.createRule(input.toRule(null), context.cluster());
    }

}
