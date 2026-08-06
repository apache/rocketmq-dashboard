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
package org.apache.rocketmq.studio.provider;

import java.util.List;

public class CloudInstanceDetailVO {

    private String instanceId;
    private String instanceName;
    private String status;
    private String regionId;
    private String remark;
    private List<CloudEndpoint> endpoints;

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public List<CloudEndpoint> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<CloudEndpoint> endpoints) {
        this.endpoints = endpoints;
    }

    public static class CloudEndpoint {
        private String endpointType;
        private String endpointUrl;

        public CloudEndpoint() {
        }

        public CloudEndpoint(String endpointType, String endpointUrl) {
            this.endpointType = endpointType;
            this.endpointUrl = endpointUrl;
        }

        public String getEndpointType() {
            return endpointType;
        }

        public void setEndpointType(String endpointType) {
            this.endpointType = endpointType;
        }

        public String getEndpointUrl() {
            return endpointUrl;
        }

        public void setEndpointUrl(String endpointUrl) {
            this.endpointUrl = endpointUrl;
        }
    }
}
