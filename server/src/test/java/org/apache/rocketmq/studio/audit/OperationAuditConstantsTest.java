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
package org.apache.rocketmq.studio.audit;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the audit vocabulary of {@link OperationAuditConstants}: every constant equals its own
 * name (the catalog is its own wire format), and the three vocabularies - operations, resource
 * types, results - contain no duplicates. The audit trail is filtered and grouped on these
 * exact strings, so a renamed constant silently orphans history.
 */
class OperationAuditConstantsTest {

    private static List<String> publicConstants(Class<?> holder) {
        List<String> names = new ArrayList<>();
        for (Field field : holder.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers())) {
                names.add(field.getName());
            }
        }
        return names;
    }

    @Test
    void everyOperationConstantEqualsItsOwnName() {
        for (String name : publicConstants(OperationAuditConstants.Operation.class)) {
            String value;
            try {
                value = (String) OperationAuditConstants.Operation.class.getField(name).get(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            assertThat(value).as("operation %s", name).isEqualTo(name);
        }
    }

    @Test
    void everyResourceTypeConstantEqualsItsOwnName() {
        for (String name : publicConstants(OperationAuditConstants.ResourceType.class)) {
            String value;
            try {
                value = (String) OperationAuditConstants.ResourceType.class.getField(name).get(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            assertThat(value).as("resource type %s", name).isEqualTo(name);
        }
    }

    @Test
    void theResultVocabularyIsExactlySuccessAndFailed() {
        assertThat(OperationAuditConstants.Result.SUCCESS).isEqualTo("SUCCESS");
        assertThat(OperationAuditConstants.Result.FAILED).isEqualTo("FAILED");
        assertThat(publicConstants(OperationAuditConstants.Result.class))
                .containsExactlyInAnyOrder("SUCCESS", "FAILED");
    }
}
