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

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.instance.acl.AclRuleVO;
import org.apache.rocketmq.studio.instance.acl.AclService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclRuleItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.PageOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclListInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AclListToolHandler implements ToolHandler<AclListInput, PageOutput<AclRuleItem>> {

    private final AclService aclService;

    @Override
    public String name() {
        return "rmq.acl.list";
    }

    @Override
    public Class<AclListInput> inputType() {
        return AclListInput.class;
    }

    @Override
    public PageOutput<AclRuleItem> execute(AclListInput input, ToolExecutionContext context) {
        PageResult<AclRuleVO> result = aclService.listRules(
                input.principal(), input.resource(), input.scope(),
                input.decision(), input.aclVersion(), context.cluster(),
                input.page().page(), input.page().pageSize());
        return PageOutput.from(result, AclRuleItem::from);
    }
}
