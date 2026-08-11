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

import java.util.List;

/**
 * Store-level summary of a cluster's ACL configuration.
 *
 * <p>Returned by {@code examineBrokerClusterAclConfig}. This is a view over the dashboard's
 * MySQL store, not a live broker query: {@link #aclVersion} is the account model the store
 * manages, {@link #aclEnabled} reports whether any accounts are provisioned for the cluster,
 * and {@link #accounts} carries the stored plain access accounts scoped to the cluster with
 * their secrets masked. Plaintext secrets are only served by the explicit per-user
 * credentials endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AclClusterConfigVO {

    /** Cluster the configuration belongs to. */
    private String clusterId;

    /** Whether ACL is enabled on the cluster. */
    private boolean aclEnabled;

    /** Active ACL version, e.g. "ACL 2.0". */
    private String aclVersion;

    /** Cluster-wide IP whitelist entries. */
    private List<String> globalWhiteRemoteAddresses;

    /** Plain access accounts configured for the cluster. */
    private List<PlainAccessConfigVO> accounts;

    /** Number of accounts; kept in sync with {@link #accounts}. */
    private int accountCount;
}
