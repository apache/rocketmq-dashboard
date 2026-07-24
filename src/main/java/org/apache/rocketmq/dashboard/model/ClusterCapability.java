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
package org.apache.rocketmq.dashboard.model;

import lombok.Data;
import java.util.HashSet;
import java.util.Set;

/**
 *
 *
 */
@Data
public class ClusterCapability {

    /**
 *
     */
    private boolean liteTopicSupported;

    /**
 *
     */
    private boolean popConsumeSupported;

    /**
 *
     */
    private boolean aclV2Supported;

    /**
 *
     */
    private boolean grpcClientSupported;

    /**
 *
     */
    private boolean delayMessageSupported;

    /**
 *
     */
    private boolean transactionMessageSupported;

    /**
 *
     */
    private boolean fifoMessageSupported;

    /**
 *
     */
    private String architectureVersion;

    /**
 *
     */
    private String rocketmqVersion;

    /**
 *
     */
    private Set<String> extendedCapabilities;

    /**
 *
     */
    public Set<TopicType> getSupportedTopicTypes() {
        Set<TopicType> supported = new HashSet<>();
        supported.add(TopicType.NORMAL);

        if (fifoMessageSupported) {
            supported.add(TopicType.FIFO);
        }
        if (delayMessageSupported) {
            supported.add(TopicType.DELAY);
        }
        if (transactionMessageSupported) {
            supported.add(TopicType.TRANSACTION);
        }
        if (liteTopicSupported) {
            supported.add(TopicType.LITE);
        }

        return supported;
    }

    /**
 *
     */
    public boolean hasCapability(String capability) {
        if (extendedCapabilities != null) {
            return extendedCapabilities.contains(capability);
        }
        return false;
    }
}