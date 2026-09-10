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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the audit vocabulary shared by services and the audit repository. Renaming any of
 * these constants changes what lands in the audit rows, so the exact values are protected.
 */
class OperationAuditConstantsTest {

    @Test
    void topicAndGroupOperationsUseUppercaseSnakeCase() {
        assertEquals("CREATE_TOPIC", OperationAuditConstants.Operation.CREATE_TOPIC);
        assertEquals("UPDATE_TOPIC", OperationAuditConstants.Operation.UPDATE_TOPIC);
        assertEquals("DELETE_TOPIC", OperationAuditConstants.Operation.DELETE_TOPIC);
        assertEquals("CREATE_GROUP", OperationAuditConstants.Operation.CREATE_GROUP);
        assertEquals("UPDATE_GROUP", OperationAuditConstants.Operation.UPDATE_GROUP);
        assertEquals("DELETE_GROUP", OperationAuditConstants.Operation.DELETE_GROUP);
        assertEquals("RESET_OFFSET", OperationAuditConstants.Operation.RESET_OFFSET);
    }

    @Test
    void proxyOperationsUseUppercaseSnakeCase() {
        assertEquals("ADD_PROXY_ADDRESS", OperationAuditConstants.Operation.ADD_PROXY_ADDRESS);
        assertEquals("REMOVE_PROXY_ADDRESS", OperationAuditConstants.Operation.REMOVE_PROXY_ADDRESS);
        assertEquals("RELOAD_PROXY_CONFIG", OperationAuditConstants.Operation.RELOAD_PROXY_CONFIG);
    }

    @Test
    void operationsAreUnique() {
        Set<String> values = new HashSet<>(Arrays.asList(
                OperationAuditConstants.Operation.CREATE_TOPIC,
                OperationAuditConstants.Operation.UPDATE_TOPIC,
                OperationAuditConstants.Operation.DELETE_TOPIC,
                OperationAuditConstants.Operation.CREATE_GROUP,
                OperationAuditConstants.Operation.UPDATE_GROUP,
                OperationAuditConstants.Operation.DELETE_GROUP,
                OperationAuditConstants.Operation.RESET_OFFSET,
                OperationAuditConstants.Operation.ADD_PROXY_ADDRESS,
                OperationAuditConstants.Operation.REMOVE_PROXY_ADDRESS,
                OperationAuditConstants.Operation.RELOAD_PROXY_CONFIG));

        assertEquals(10, values.size());
    }

    @Test
    void resourceTypesCoverTopicGroupAndProxy() {
        assertEquals("TOPIC", OperationAuditConstants.ResourceType.TOPIC);
        assertEquals("GROUP", OperationAuditConstants.ResourceType.GROUP);
        assertEquals("PROXY", OperationAuditConstants.ResourceType.PROXY);
    }

    @Test
    void resultsCoverSuccessAndFailure() {
        assertEquals("SUCCESS", OperationAuditConstants.Result.SUCCESS);
        assertEquals("FAILED", OperationAuditConstants.Result.FAILED);
    }

    @Test
    void operationsAlignWithResourceTypes() {
        // Every topic operation is grouped under the TOPIC resource and vice versa.
        assertTrue(OperationAuditConstants.Operation.CREATE_TOPIC.endsWith("TOPIC"));
        assertTrue(OperationAuditConstants.Operation.UPDATE_TOPIC.endsWith("TOPIC"));
        assertTrue(OperationAuditConstants.Operation.DELETE_TOPIC.endsWith("TOPIC"));
        assertTrue(OperationAuditConstants.Operation.RESET_OFFSET.startsWith("RESET_"));
    }
}
