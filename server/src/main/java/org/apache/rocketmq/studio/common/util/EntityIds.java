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
package org.apache.rocketmq.studio.common.util;

import org.apache.rocketmq.studio.common.exception.BusinessException;

/**
 * Parses external string identifiers into the numeric auto-increment primary keys used by
 * the persistence layer. All database entity ids are {@code bigint unsigned AUTO_INCREMENT};
 * API inputs arriving as strings are converted here with a uniform 400 error on failure.
 */
public final class EntityIds {

    private EntityIds() {
    }

    /**
     * Parses a required external id into a database primary key.
     *
     * @throws BusinessException with HTTP 400 when the value is blank or not numeric
     */
    public static Long parseId(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, "id is required");
        }
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) {
                throw new BusinessException(400, "id must be a positive numeric value: " + value);
            }
            return id;
        } catch (NumberFormatException ex) {
            throw new BusinessException(400, "id must be a numeric value: " + value);
        }
    }
}
