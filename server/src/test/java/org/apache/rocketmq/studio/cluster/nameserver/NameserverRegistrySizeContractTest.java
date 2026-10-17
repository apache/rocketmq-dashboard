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

/*
 * Backend side of the NS-registry form length gap: pins that
 * CreateNameserverRegistryDTO rejects names longer than 128 chars and
 * namesrvAddr longer than 512 chars, while the registry form in
 * web/src/pages/cluster/index.tsx (name / namesrvAddr / k8sNamespace / k8sId
 * Form.Items) declared no maxLength - a user could type a value the backend
 * always rejects with 400.
 */

package org.apache.rocketmq.studio.cluster.nameserver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NameserverRegistrySizeContractTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static CreateNameserverRegistryDTO dto(String name, String namesrvAddr) {
        CreateNameserverRegistryDTO dto = new CreateNameserverRegistryDTO();
        dto.setName(name);
        dto.setNamesrvAddr(namesrvAddr);
        return dto;
    }

    @Test
    void nameLongerThan128IsRejectedTest() {
        Set<ConstraintViolation<CreateNameserverRegistryDTO>> violations =
                validator.validate(dto("n".repeat(129), "10.0.0.1:9876"));
        assertFalse(violations.isEmpty(), "backend must reject a 129-char registry name");
    }

    @Test
    void nameAt128IsAcceptedTest() {
        Set<ConstraintViolation<CreateNameserverRegistryDTO>> violations =
                validator.validate(dto("n".repeat(128), "10.0.0.1:9876"));
        assertTrue(violations.isEmpty(), "backend must accept a 128-char registry name");
    }

    @Test
    void namesrvAddrLongerThan512IsRejectedTest() {
        Set<ConstraintViolation<CreateNameserverRegistryDTO>> violations =
                validator.validate(dto("registry", "a".repeat(513)));
        assertFalse(violations.isEmpty(), "backend must reject a 513-char namesrvAddr");
    }
}
