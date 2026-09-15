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
import org.apache.rocketmq.studio.instance.acl.AclService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclUserItem;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ClusterInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserListToolHandler implements ToolHandler<ClusterInput, ListOutput<AclUserItem>> {
    private final AclService aclService;

    @Override
    public String name() {
        return "rmq.user.list";
    }

    @Override
    public Class<ClusterInput> inputType() {
        return ClusterInput.class;
    }

    @Override
    public ListOutput<AclUserItem> execute(
            ClusterInput input, ToolExecutionContext context) {
        return new ListOutput<>(aclService.listUsers(context.cluster()).stream()
                .map(AclUserItem::from).toList());
    }
}
