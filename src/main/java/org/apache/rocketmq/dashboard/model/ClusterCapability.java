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
package org.apache.rocketmq.studio.model;

import java.util.HashSet;
import java.util.Set;

public class ClusterCapability {

    private boolean liteTopicSupported;

    private boolean popConsumeSupported;

    private boolean aclV2Supported;

    private boolean grpcClientSupported;

    private boolean delayMessageSupported;

    private boolean transactionMessageSupported;

    private boolean fifoMessageSupported;

    private String architectureVersion;

    private String rocketmqVersion;

    private Set<String> extendedCapabilities;

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

    public boolean hasCapability(String capability) {
        if (extendedCapabilities != null) {
            return extendedCapabilities.contains(capability);
        }
        return false;
    }
    public boolean isLiteTopicSupported() {
        return liteTopicSupported;
    }

    public void setLiteTopicSupported(boolean liteTopicSupported) {
        this.liteTopicSupported = liteTopicSupported;
    }

    public boolean isPopConsumeSupported() {
        return popConsumeSupported;
    }

    public void setPopConsumeSupported(boolean popConsumeSupported) {
        this.popConsumeSupported = popConsumeSupported;
    }

    public boolean isAclV2Supported() {
        return aclV2Supported;
    }

    public void setAclV2Supported(boolean aclV2Supported) {
        this.aclV2Supported = aclV2Supported;
    }

    public boolean isGrpcClientSupported() {
        return grpcClientSupported;
    }

    public void setGrpcClientSupported(boolean grpcClientSupported) {
        this.grpcClientSupported = grpcClientSupported;
    }

    public boolean isDelayMessageSupported() {
        return delayMessageSupported;
    }

    public void setDelayMessageSupported(boolean delayMessageSupported) {
        this.delayMessageSupported = delayMessageSupported;
    }

    public boolean isTransactionMessageSupported() {
        return transactionMessageSupported;
    }

    public void setTransactionMessageSupported(boolean transactionMessageSupported) {
        this.transactionMessageSupported = transactionMessageSupported;
    }

    public boolean isFifoMessageSupported() {
        return fifoMessageSupported;
    }

    public void setFifoMessageSupported(boolean fifoMessageSupported) {
        this.fifoMessageSupported = fifoMessageSupported;
    }

    public String getArchitectureVersion() {
        return architectureVersion;
    }

    public void setArchitectureVersion(String architectureVersion) {
        this.architectureVersion = architectureVersion;
    }

    public String getRocketmqVersion() {
        return rocketmqVersion;
    }

    public void setRocketmqVersion(String rocketmqVersion) {
        this.rocketmqVersion = rocketmqVersion;
    }

    public Set<String> getExtendedCapabilities() {
        return extendedCapabilities;
    }

    public void setExtendedCapabilities(Set<String> extendedCapabilities) {
        this.extendedCapabilities = extendedCapabilities;
    }

}