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

import lombok.Data;

@Data
public class LlmConfigDTO {

    private String provider;
    private String engine;
    private String apiKey;
    private String apiBase;
    private String model;
    private int maxTokens;
    private double temperature;
    private boolean enabled;
    private String deploymentName;
    private String apiVersion;
    private String awsRegion;

    public LlmConfigVO toLlmConfigVO() {
        return LlmConfigVO.builder()
                .provider(provider)
                .engine(engine)
                .apiKey(apiKey)
                .apiBase(apiBase)
                .model(model)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .enabled(enabled)
                .deploymentName(deploymentName)
                .apiVersion(apiVersion)
                .awsRegion(awsRegion)
                .build();
    }
}
