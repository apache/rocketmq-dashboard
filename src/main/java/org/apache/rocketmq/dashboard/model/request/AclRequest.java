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
package org.apache.rocketmq.dashboard.model.request;

import org.apache.rocketmq.common.PlainAccessConfig;

public class AclRequest {

    private PlainAccessConfig config;
    private String topicPerm;
    private String groupPerm;

    public PlainAccessConfig getConfig() {
        return config;
    }

    public void setConfig(PlainAccessConfig config) {
        this.config = config;
    }

    public String getTopicPerm() {
        return topicPerm;
    }

    public void setTopicPerm(String topicPerm) {
        this.topicPerm = topicPerm;
    }

    public String getGroupPerm() {
        return groupPerm;
    }

    public void setGroupPerm(String groupPerm) {
        this.groupPerm = groupPerm;
    }

    @Override
    public String toString() {
        return "AclRequest{" +
            "config=" + config +
            ", topicPerm='" + topicPerm + '\'' +
            ", groupPerm='" + groupPerm + '\'' +
            '}';
    }
}