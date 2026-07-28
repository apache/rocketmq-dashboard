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
package org.apache.rocketmq.studio.model.request;


/**
 * Metrics data source configuration request DTO.
 *
 * <p>Used for creating and updating Prometheus-compatible data source
 * configurations for the PromQL proxy query feature.</p>
 *
 * <h3>Supported Data Source Types</h3>
 * <ul>
 *   <li><b>PROMETHEUS:</b> Standard Prometheus HTTP API server</li>
 *   <li><b>VICTORIAMETRICS:</b> VictoriaMetrics (Prometheus-compatible)</li>
 *   <li><b>THANOS:</b> Thanos Query (Prometheus-compatible)</li>
 *   <li><b>CORTEX:</b> Cortex (Prometheus-compatible)</li>
 * </ul>
 */
public class MetricsDataSourceRequest {

    /**
     * Data source display name (required for create).
     */
    private String name;

    /**
     * Data source type (PROMETHEUS, VICTORIAMETRICS, THANOS, CORTEX).
     * Defaults to PROMETHEUS if not specified.
     */
    private String type;

    /**
     * Prometheus-compatible backend provider type.
     * Supported: PROMETHEUS, VICTORIAMETRICS, THANOS, MIMIR, CORTEX, ARMS, CUSTOM
     */
    private String providerType = "PROMETHEUS";

    /**
     * Data source URL (required for create).
     * Example: "http://prometheus:9090"
     */
    private String url;

    /**
     * Authentication username (optional).
     */
    private String username;

    /**
     * Authentication password (optional).
     */
    private String password;

    /**
     * Bearer token for authentication (optional).
     * If set, takes precedence over username/password.
     */
    private String bearerToken;

    /**
     * Whether this data source is the default for PromQL queries.
     * Only one data source can be the default at a time.
     */
    private boolean isDefault;

    /**
     * Whether this data source is read-only.
     * Read-only data sources only support query operations.
     */
    private boolean readOnly;

    /**
     * Custom HTTP headers to include in requests to this data source.
     * Format: "Header-Name: Header-Value" per line.
     */
    private String customHeaders;

    /**
     * Connection timeout in milliseconds (default: 5000).
     */
    private Integer connectionTimeoutMs;

    /**
     * Read timeout in milliseconds (default: 30000).
     */
    private Integer readTimeoutMs;

    /**
     * Additional description or notes about this data source.
     */
    private String description;
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getBearerToken() {
        return bearerToken;
    }

    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    public boolean isIsDefault() {
        return isDefault;
    }

    public void setIsDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public String getCustomHeaders() {
        return customHeaders;
    }

    public void setCustomHeaders(String customHeaders) {
        this.customHeaders = customHeaders;
    }

    public Integer getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(Integer connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    public Integer getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(Integer readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}