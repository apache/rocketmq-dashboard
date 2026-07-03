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
package org.apache.rocketmq.dashboard.model.request;

/**
 * Request body for architecture switch operation.
 *
 * <p>Used by {@code POST /api/architecture/switch} to dynamically switch
 * the dashboard between different cluster architecture types.</p>
 *
 * <p>For V5 architectures (V5_PROXY_LOCAL, V5_PROXY_CLUSTER), the
 * {@code proxyAddresses} and {@code nameSrvAddress} fields are required.
 * For V4 architecture, only {@code accessType} is needed.</p>
 */
public class ArchitectureSwitchRequest {

    /**
     * Target access type name (e.g., "V4_NAMESRV", "V5_PROXY_LOCAL", "V5_PROXY_CLUSTER").
     * Must match a value in {@link org.apache.rocketmq.dashboard.architecture.ClusterAccessType}.
     */
    private String accessType;

    /**
     * Proxy node addresses for V5 architecture.
     * Required when accessType is V5_PROXY_LOCAL or V5_PROXY_CLUSTER.
     */
    private String[] proxyAddresses;

    /**
     * NameServer address for Remoting fallback.
     * Required when accessType is V5_PROXY_LOCAL or V5_PROXY_CLUSTER.
     */
    private String nameSrvAddress;

    /**
     * Optional namespace scope for V5 architecture.
     */
    private String namespace;

    public String getAccessType() {
        return accessType;
    }

    public void setAccessType(String accessType) {
        this.accessType = accessType;
    }

    public String[] getProxyAddresses() {
        return proxyAddresses;
    }

    public void setProxyAddresses(String[] proxyAddresses) {
        this.proxyAddresses = proxyAddresses;
    }

    public String getNameSrvAddress() {
        return nameSrvAddress;
    }

    public void setNameSrvAddress(String nameSrvAddress) {
        this.nameSrvAddress = nameSrvAddress;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}