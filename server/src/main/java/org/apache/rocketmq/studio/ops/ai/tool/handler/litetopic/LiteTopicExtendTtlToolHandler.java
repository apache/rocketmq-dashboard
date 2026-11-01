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
package org.apache.rocketmq.studio.ops.ai.tool.handler.litetopic;

import org.apache.rocketmq.studio.instance.topic.LiteTopicService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.litetopic.LiteTopicExtendTtlInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.litetopic.LiteTopicExtendTtlOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.PlanDescription;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * L2 mutation tool extending the LiteTopic TTL of a registered Lite parent topic through
 * {@link LiteTopicService#extendTTL}, which enforces the instance ownership guard. The
 * preview is derived from the request alone — no broker state is read — so the confirmed
 * plan never drifts between preview and apply; current pattern state belongs to the
 * read-only {@code rmq.litetopic.list} / {@code rmq.litetopic.quota} tools.
 */
@Component
public class LiteTopicExtendTtlToolHandler
        extends MutationToolHandler<LiteTopicExtendTtlInput, LiteTopicExtendTtlOutput> {

    private static final PlanDescription PLAN_DESCRIPTION = new PlanDescription(
            "extend the LiteTopic TTL under parent topic '%s' in instance '%s'.",
            List.of("Rewrites the lite.topic.expiration attribute of the parent topic on every "
                    + "broker master; lite topics under the pattern inherit the new TTL."),
            List.of());

    private static final long MILLIS_PER_MINUTE = TimeUnit.MINUTES.toMillis(1);
    private static final long MAX_TTL_MILLIS = TimeUnit.DAYS.toMillis(30);

    private final LiteTopicService liteTopicService;

    public LiteTopicExtendTtlToolHandler(LiteTopicService liteTopicService) {
        super(LiteTopicExtendTtlInput.class);
        this.liteTopicService = liteTopicService;
    }

    @Override
    public String name() {
        return "rmq.litetopic.extend_ttl";
    }

    @Override
    public ToolPlan preview(LiteTopicExtendTtlInput input, ToolExecutionContext context) {
        long requested = input.newTtlMillis();
        return PLAN_DESCRIPTION.builder(input.topicPattern(), context.instanceId())
                .after(Map.of(
                        "topicPattern", input.topicPattern(),
                        "newTtlMillis", requested))
                .warningIf(requested % MILLIS_PER_MINUTE != 0,
                        "Broker TTL granularity is one minute; the requested millisecond value "
                                + "will be applied rounded to whole minutes.")
                .warningIf(requested > MAX_TTL_MILLIS,
                        "Broker caps the LiteTopic TTL at 30 days; the requested value exceeds "
                                + "the cap and will be clamped.")
                .build();
    }

    @Override
    public LiteTopicExtendTtlOutput execute(LiteTopicExtendTtlInput input, ToolExecutionContext context) {
        liteTopicService.extendTTL(context.instanceId(), input.topicPattern(), input.newTtlMillis());
        return new LiteTopicExtendTtlOutput(input.topicPattern(), input.newTtlMillis());
    }
}
