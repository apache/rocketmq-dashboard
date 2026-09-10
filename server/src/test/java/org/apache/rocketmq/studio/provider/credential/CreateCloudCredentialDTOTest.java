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
package org.apache.rocketmq.studio.provider.credential;

import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class CreateCloudCredentialDTOTest {

    @Test
    void parseVendorShouldBeIndependentOfDefaultLocale() {
        Locale originalLocale = Locale.getDefault();

        InstanceVendor vendor;
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            vendor = CreateCloudCredentialDTO.parseVendor("aliyun");
        } finally {
            Locale.setDefault(originalLocale);
        }

        assertThat(vendor).isEqualTo(InstanceVendor.ALIYUN);
    }

    @Test
    void parseVendorShouldTrimAndNormalizeCase() {
        assertThat(CreateCloudCredentialDTO.parseVendor(" ALIYUN ")).isEqualTo(InstanceVendor.ALIYUN);
        assertThat(CreateCloudCredentialDTO.parseVendor("tencent")).isEqualTo(InstanceVendor.TENCENT);
        assertThat(CreateCloudCredentialDTO.parseVendor("APACHE")).isEqualTo(InstanceVendor.APACHE);
    }

    @Test
    void parseVendorShouldReturnNullForBlankAndUnknownValues() {
        assertThat(CreateCloudCredentialDTO.parseVendor(null)).isNull();
        assertThat(CreateCloudCredentialDTO.parseVendor("  ")).isNull();
        assertThat(CreateCloudCredentialDTO.parseVendor("alibaba")).isNull();
    }

    @Test
    void toCloudCredentialVOShouldCarryEveryInputField() {
        CreateCloudCredentialDTO request = new CreateCloudCredentialDTO();
        request.setName("production");
        request.setVendor(" aliyun ");
        request.setAccessKey("ak-1");
        request.setSecretKey("sk-1");
        request.setRemark("primary account");

        CloudCredentialVO vo = request.toCloudCredentialVO();

        assertThat(vo.getName()).isEqualTo("production");
        assertThat(vo.getVendor()).isEqualTo(InstanceVendor.ALIYUN);
        assertThat(vo.getAccessKey()).isEqualTo("ak-1");
        assertThat(vo.getSecretKey()).isEqualTo("sk-1");
        assertThat(vo.getRemark()).isEqualTo("primary account");
    }
}
