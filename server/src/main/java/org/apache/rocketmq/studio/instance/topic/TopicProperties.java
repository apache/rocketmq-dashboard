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
package org.apache.rocketmq.studio.instance.topic;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Topic-level feature switches.
 */
@Data
@Component
@ConfigurationProperties(prefix = "studio.topic")
public class TopicProperties {
    /**
     * Whether the console surfaces topics whose message type is {@code MIXED}. A broker only
     * reports that type when it runs with {@code enableMixedMessageType=true}, so deployments on
     * older clusters keep the historical behaviour of treating an unrecognised {@code MIXED}
     * value as {@code NORMAL}. Disabled by default.
     */
    private boolean mixedTypeEnabled;
}
