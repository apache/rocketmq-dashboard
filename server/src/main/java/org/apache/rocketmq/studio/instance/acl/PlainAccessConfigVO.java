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
package org.apache.rocketmq.studio.instance.acl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Plain access configuration (ACL 1.0 / plain_acl.yml account model).
 *
 * <p>Each instance represents one access identity with its access key, optional
 * secret key, IP whitelist and the default / per-resource permissions it carries.
 * This mirrors the {@code PlainAccessData} model used by the broker's plain ACL
 * provider and by ACL 2.0 account export.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlainAccessConfigVO {

    /** Unique access key identifying the account. */
    private String accessKey;

    /**
     * Secret key. Written base64-encoded to the store; only echoed back by the write endpoint
     * when it was just provided. Read-back views return a masked value, and the plaintext is
     * available solely through the explicit per-user credentials endpoint.
     */
    private String secretKey;

    /** IP whitelist pattern for this account; persisted, empty/null means no restriction. */
    private String whiteRemoteAddress;

    /** Whether this account has admin privileges. */
    private boolean admin;

    /** Default permission applied to topics, e.g. DENY / PUB / SUB / ALL. */
    private String defaultTopicPerm;

    /** Default permission applied to groups, e.g. DENY / PUB / SUB / ALL. */
    private String defaultGroupPerm;

    /** Per-topic permission entries, e.g. "order-*=PUB". */
    private List<String> topicPerms;

    /** Per-group permission entries, e.g. "cg-order-*=SUB". */
    private List<String> groupPerms;

    private LocalDateTime gmtCreate;
}
