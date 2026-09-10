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

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BatchDeleteInstancesDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void idsShouldBeRequired() {
        BatchDeleteInstancesDTO empty = new BatchDeleteInstancesDTO();
        empty.setIds(List.of());

        BatchDeleteInstancesDTO missing = new BatchDeleteInstancesDTO();

        assertThat(validator.validate(empty)).isNotEmpty();
        assertThat(validator.validate(missing)).isNotEmpty();
    }

    @Test
    void nonEmptyIdListsShouldPassValidation() {
        BatchDeleteInstancesDTO request = new BatchDeleteInstancesDTO();
        request.setIds(List.of("instance-a", "instance-b"));

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.getIds()).containsExactly("instance-a", "instance-b");
    }
}
