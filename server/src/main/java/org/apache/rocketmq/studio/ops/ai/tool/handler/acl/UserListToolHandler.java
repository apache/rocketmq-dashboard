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

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.instance.acl.AclService;
import org.apache.rocketmq.studio.instance.acl.AclUserVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclUserItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.UserListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.PageOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserListToolHandler implements ToolHandler<UserListInput, PageOutput<AclUserItem>> {
    private final AclService aclService;

    @Override
    public String name() {
        return "rmq.user.list";
    }

    @Override
    public Class<UserListInput> inputType() {
        return UserListInput.class;
    }

    @Override
    public PageOutput<AclUserItem> execute(
            UserListInput input, ToolExecutionContext context) {
        PageResult<AclUserVO> result = aclService.pageUsers(
                context.instanceId(), input.page().page(), input.page().pageSize(), input.keyword());
        return PageOutput.from(result, AclUserItem::from);
    }
}
