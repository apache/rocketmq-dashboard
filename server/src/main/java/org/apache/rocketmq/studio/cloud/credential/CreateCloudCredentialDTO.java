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
package org.apache.rocketmq.studio.cloud.credential;

import jakarta.validation.constraints.NotBlank;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;

public class CreateCloudCredentialDTO {

    @NotBlank(message = "credential name is required")
    private String name;

    @NotBlank(message = "credential vendor is required")
    private String vendor;

    @NotBlank(message = "credential accessKey is required")
    private String accessKey;

    @NotBlank(message = "credential secretKey is required")
    private String secretKey;

    private String remark;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public CloudCredentialVO toCloudCredentialVO() {
        CloudCredentialVO vo = new CloudCredentialVO();
        vo.setName(name);
        vo.setVendor(parseVendor(vendor));
        vo.setAccessKey(accessKey);
        vo.setSecretKey(secretKey);
        vo.setRemark(remark);
        return vo;
    }

    static InstanceVendor parseVendor(String vendor) {
        if (vendor == null) {
            return null;
        }
        try {
            return InstanceVendor.valueOf(vendor.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
