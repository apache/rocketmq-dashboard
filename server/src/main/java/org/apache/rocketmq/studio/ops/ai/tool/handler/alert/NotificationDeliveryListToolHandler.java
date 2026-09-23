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
package org.apache.rocketmq.studio.ops.ai.tool.handler.alert;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.ops.ai.tool.contract.alert.NotificationDeliveryItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.alert.NotificationDeliveryListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.PageOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.alert.NotificationDeliveryPageVO;
import org.apache.rocketmq.studio.ops.alert.NotificationOutboxService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationDeliveryListToolHandler
        implements ToolHandler<NotificationDeliveryListInput, PageOutput<NotificationDeliveryItem>> {

    private final NotificationOutboxService notificationOutboxService;

    @Override
    public String name() {
        return "rmq.alert.delivery.list";
    }

    @Override
    public Class<NotificationDeliveryListInput> inputType() {
        return NotificationDeliveryListInput.class;
    }

    @Override
    public PageOutput<NotificationDeliveryItem> execute(
            NotificationDeliveryListInput input, ToolExecutionContext context) {
        PageResult<NotificationDeliveryPageVO> result = notificationOutboxService.listDeliveries(
                input.channel(), input.status(), input.instanceId(),
                input.page().page(), input.page().pageSize());
        return PageOutput.from(result, NotificationDeliveryItem::from);
    }
}
