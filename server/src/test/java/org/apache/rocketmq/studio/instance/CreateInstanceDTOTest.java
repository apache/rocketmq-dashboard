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
package org.apache.rocketmq.studio.instance;

import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the create contract of {@link CreateInstanceDTO}: {@code toInstanceVO()} carries every
 * field of the registration form, including the cloud routing fields (vendor, cloud instance id,
 * credential reference and region).
 */
class CreateInstanceDTOTest {

    @Test
    void toInstanceVOCarriesEveryField() {
        CreateInstanceDTO request = new CreateInstanceDTO();
        request.setName("order-cluster");
        request.setType(InstanceType.CLOUD);
        request.setEndpoint("rmq-abc123.ap-southeast.rmq.aliyuncs.com:8080");
        request.setRemark("production");
        request.setVendor(InstanceVendor.ALIYUN);
        request.setCloudInstanceId("rmq-abc123");
        request.setCredentialId(11L);
        request.setAdminCredentialRef("cred-ref-1");
        request.setRegionId("ap-southeast-1");

        InstanceVO vo = request.toInstanceVO();

        assertThat(vo.getName()).isEqualTo("order-cluster");
        assertThat(vo.getType()).isEqualTo(InstanceType.CLOUD);
        assertThat(vo.getEndpoint()).isEqualTo("rmq-abc123.ap-southeast.rmq.aliyuncs.com:8080");
        assertThat(vo.getRemark()).isEqualTo("production");
        assertThat(vo.getVendor()).isEqualTo(InstanceVendor.ALIYUN);
        assertThat(vo.getCloudInstanceId()).isEqualTo("rmq-abc123");
        assertThat(vo.getCredentialId()).isEqualTo(11L);
        assertThat(vo.getAdminCredentialRef()).isEqualTo("cred-ref-1");
        assertThat(vo.getRegionId()).isEqualTo("ap-southeast-1");
    }

    @Test
    void aMinimalCreateOnlyCarriesTheCoreFields() {
        CreateInstanceDTO request = new CreateInstanceDTO();
        request.setName("local");
        request.setType(InstanceType.PROXY_LOCAL);
        request.setEndpoint("10.0.0.1:8080");

        InstanceVO vo = request.toInstanceVO();

        assertThat(vo.getName()).isEqualTo("local");
        assertThat(vo.getType()).isEqualTo(InstanceType.PROXY_LOCAL);
        assertThat(vo.getEndpoint()).isEqualTo("10.0.0.1:8080");
        assertThat(vo.getVendor()).isNull();
        assertThat(vo.getCloudInstanceId()).isNull();
        assertThat(vo.getCredentialId()).isNull();
        assertThat(vo.getRegionId()).isNull();
    }
}
