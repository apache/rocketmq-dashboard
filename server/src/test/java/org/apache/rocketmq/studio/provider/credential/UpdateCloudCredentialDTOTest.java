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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateCloudCredentialDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();


    @Test
    void idShouldBeRequired() {
        UpdateCloudCredentialDTO request = new UpdateCloudCredentialDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("credential id is required");
    }

    @Test
    void toStringShouldNeverExposeTheSecretKey() {
        UpdateCloudCredentialDTO request = new UpdateCloudCredentialDTO();
        request.setId(1L);
        request.setName("aliyun-test");
        request.setSecretKey("secret-0001");

        String value = request.toString();

        assertThat(value).contains("name=aliyun-test");
        assertThat(value).doesNotContain("secret-0001");
    }

    @Test
    void validUpdateShouldPassValidation() {
        UpdateCloudCredentialDTO request = new UpdateCloudCredentialDTO();
        request.setId(1L);
        request.setName("renamed");
        request.setRemark("test account");

        assertThat(validator.validate(request)).isEmpty();
    }
}
