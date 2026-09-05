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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DuplicateInstanceNameExceptionTest {

    @Test
    void isBusinessExceptionWithBadRequestCode() {
        DuplicateInstanceNameException ex = new DuplicateInstanceNameException("prod-1");

        assertEquals(400, ex.getCode());
        assertEquals("Instance name already exists: prod-1", ex.getMessage());
    }

    @Test
    void keepsTheOffendingNameInMessage() {
        DuplicateInstanceNameException ex = new DuplicateInstanceNameException("cluster-a");

        assertEquals("Instance name already exists: cluster-a", ex.getMessage());
    }

    @Test
    void isAssignableToBusinessException() {
        DuplicateInstanceNameException ex = new DuplicateInstanceNameException("prod-1");

        BusinessException base = ex;
        assertEquals(400, base.getCode());
    }
}
