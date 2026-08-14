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
package org.apache.rocketmq.studio.auth;

import lombok.Builder;
import lombok.Data;
import org.apache.rocketmq.studio.persistence.entity.RmqStudioUser;

import java.time.LocalDateTime;

@Data
@Builder
public class StudioUserVO {
    private String id;
    private String username;
    private boolean admin;
    private boolean enabled;
    private LocalDateTime passwordChangedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StudioUserVO from(RmqStudioUser user) {
        return StudioUserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .admin(Boolean.TRUE.equals(user.getAdmin()))
                .enabled(Boolean.TRUE.equals(user.getEnabled()))
                .passwordChangedAt(user.getPasswordChangedAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
