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

import jakarta.validation.Validation;
import jakarta.validation.Validator;
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
    void parseVendorShouldNormalizeAndReturnNullForUnknown() {
        assertThat(CreateCloudCredentialDTO.parseVendor(" tencent ")).isEqualTo(InstanceVendor.TENCENT);
        assertThat(CreateCloudCredentialDTO.parseVendor("aliyun")).isEqualTo(InstanceVendor.ALIYUN);
        assertThat(CreateCloudCredentialDTO.parseVendor("aws")).isNull();
        assertThat(CreateCloudCredentialDTO.parseVendor(null)).isNull();
        assertThat(CreateCloudCredentialDTO.parseVendor("  ")).isNull();
    }

    @Test
    void toStringShouldNotExposeCredentialSecrets() {
        CreateCloudCredentialDTO request = new CreateCloudCredentialDTO();
        request.setName("prod-aliyun");
        request.setVendor("ALIYUN");
        request.setAccessKey("access-key-secret");
        request.setSecretKey("secret-key-secret");

        String value = request.toString();

        assertThat(value).contains("name=prod-aliyun");
        assertThat(value).doesNotContain("access-key-secret");
        assertThat(value).doesNotContain("secret-key-secret");
    }

    @Test
    void validationShouldRequireAllCredentialFields() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        CreateCloudCredentialDTO request = new CreateCloudCredentialDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("credential name is required",
                        "credential vendor is required",
                        "credential accessKey is required",
                        "credential secretKey is required");
    }

    @Test
    void toCloudCredentialVOShouldCopyFieldsAndParseVendor() {
        CreateCloudCredentialDTO request = new CreateCloudCredentialDTO();
        request.setName("prod-tencent");
        request.setVendor(" tencent ");
        request.setAccessKey("ak-123");
        request.setSecretKey("sk-456");
        request.setRemark("production read-only");

        CloudCredentialVO vo = request.toCloudCredentialVO();

        assertThat(vo.getName()).isEqualTo("prod-tencent");
        assertThat(vo.getVendor()).isEqualTo(InstanceVendor.TENCENT);
        assertThat(vo.getAccessKey()).isEqualTo("ak-123");
        assertThat(vo.getSecretKey()).isEqualTo("sk-456");
        assertThat(vo.getRemark()).isEqualTo("production read-only");
    }
}
