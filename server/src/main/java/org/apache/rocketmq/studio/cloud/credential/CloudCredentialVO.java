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

import org.apache.rocketmq.studio.common.domain.BaseEntity;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;

import java.util.Objects;

public class CloudCredentialVO extends BaseEntity {

    private String name;
    private InstanceVendor vendor;
    private String accessKey;
    private String secretKey;
    private String remark;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public InstanceVendor getVendor() {
        return vendor;
    }

    public void setVendor(InstanceVendor vendor) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CloudCredentialVO)) {
            return false;
        }
        CloudCredentialVO that = (CloudCredentialVO) o;
        return Objects.equals(getId(), that.getId())
                && Objects.equals(name, that.name)
                && vendor == that.vendor
                && Objects.equals(accessKey, that.accessKey)
                && Objects.equals(secretKey, that.secretKey)
                && Objects.equals(remark, that.remark)
                && Objects.equals(getCreatedAt(), that.getCreatedAt())
                && Objects.equals(getUpdatedAt(), that.getUpdatedAt());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), name, vendor, accessKey, secretKey, remark,
                getCreatedAt(), getUpdatedAt());
    }

    @Override
    public String toString() {
        return "CloudCredentialVO{id=" + getId() + ", name=" + name + ", vendor=" + vendor
                + ", accessKey=" + accessKey + ", secretKey=****}";
    }
}
