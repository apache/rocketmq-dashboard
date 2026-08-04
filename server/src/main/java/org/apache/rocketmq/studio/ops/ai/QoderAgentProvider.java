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
package org.apache.rocketmq.studio.ops.ai;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Qoder CLI provider ({@code qodercli -p}). qodercli authenticates through its
 * own login state in the runtime image, so no credentials are injected here.
 */
@Component
public class QoderAgentProvider extends CliAgentProvider {

    public static final String ENGINE = "qoder";

    @Override
    public String engine() {
        return ENGINE;
    }

    @Override
    protected String binaryName() {
        return "qodercli";
    }

    @Override
    protected List<String> buildCommand(LlmConfigVO config, String prompt, String modelOverride) {
        List<String> command = new ArrayList<>(List.of("qodercli", "-p", prompt == null ? "" : prompt));
        if (StringUtils.hasText(modelOverride)) {
            command.add("-m");
            command.add(modelOverride.trim());
        }
        return command;
    }

    @Override
    protected Map<String, String> childEnv(LlmConfigVO config) {
        return Collections.emptyMap();
    }
}
