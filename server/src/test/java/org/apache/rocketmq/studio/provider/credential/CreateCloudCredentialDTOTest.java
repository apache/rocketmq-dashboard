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
import jakarta.validation.ValidatorFactory;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CreateCloudCredentialDTOTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

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
    void parseVendorShouldTrimAndIgnoreCase() {
        assertThat(CreateCloudCredentialDTO.parseVendor(" apache ")).isEqualTo(InstanceVendor.APACHE);
        assertThat(CreateCloudCredentialDTO.parseVendor("TENCENT")).isEqualTo(InstanceVendor.TENCENT);
    }

    @Test
    void parseVendorShouldReturnNullForBlankAndUnknownValues() {
        assertThat(CreateCloudCredentialDTO.parseVendor(null)).isNull();
        assertThat(CreateCloudCredentialDTO.parseVendor(" ")).isNull();
        assertThat(CreateCloudCredentialDTO.parseVendor("bare-metal")).isNull();
    }

    @Test
    void shouldRejectMissingRequiredCredentialFields() {
        CreateCloudCredentialDTO request = new CreateCloudCredentialDTO();

        Set<String> messages = validator.validate(request).stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.toSet());

        assertThat(messages).contains(
                "credential name is required",
                "credential vendor is required",
                "credential accessKey is required",
                "credential secretKey is required");
    }

    @Test
    void toCloudCredentialVoShouldCarryTheStoredCredentials() {
        CreateCloudCredentialDTO request = new CreateCloudCredentialDTO();
        request.setName("aliyun-test");
        request.setVendor("aliyun");
        request.setAccessKey("LTAI00000001");
        request.setSecretKey("secret-0001");
        request.setRemark("test account");

        CloudCredentialVO vo = request.toCloudCredentialVO();

        assertThat(vo.getName()).isEqualTo("aliyun-test");
        assertThat(vo.getVendor()).isEqualTo(InstanceVendor.ALIYUN);
        assertThat(vo.getAccessKey()).isEqualTo("LTAI00000001");
        assertThat(vo.getSecretKey()).isEqualTo("secret-0001");
        assertThat(vo.getRemark()).isEqualTo("test account");
    }

    @Test
    void toStringShouldNeverExposeCredentials() {
        CreateCloudCredentialDTO request = new CreateCloudCredentialDTO();
        request.setName("aliyun-test");
        request.setVendor("aliyun");
        request.setAccessKey("LTAI00000001");
        request.setSecretKey("secret-0001");

        String value = request.toString();

        assertThat(value).contains("name=aliyun-test");
        assertThat(value).doesNotContain("LTAI00000001");
        assertThat(value).doesNotContain("secret-0001");
    }
}
