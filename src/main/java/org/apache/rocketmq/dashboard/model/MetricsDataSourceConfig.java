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
package org.apache.rocketmq.studio.model;


import java.io.Serializable;
import java.util.Map;

/**
 * Configuration model for Prometheus-compatible metrics data sources.
 * Supports multiple data sources (N:N mapping with clusters).
 * Auth types: none, basic, bearer, sigv4.
 * <p>
 * This class carries raw configuration values. Sensitive fields such as
 * password / bearerToken should be encrypted at rest in production.
 * </p>
 */
public class MetricsDataSourceConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * User-defined name identifying this data source.
     */
    private String name;

    /**
     * Base URL of the Prometheus-compatible backend
     * (e.g., http://prometheus:9090).
     */
    private String url;

    /**
     * Authentication type: "none", "basic", "bearer", "sigv4".
     */
    private String authType;

    /**
     * Username for basic authentication.
     */
    private String username;

    /**
     * Password for basic authentication (encrypted in storage).
     */
    private String password;

    /**
     * Bearer token for bearer authentication.
     */
    private String bearerToken;

    /**
     * Prometheus-compatible backend provider type.
     * Supported: PROMETHEUS, VICTORIAMETRICS, THANOS, MIMIR, CORTEX, ARMS, CUSTOM
     */
    private String providerType = "PROMETHEUS";

    /**
     * Whether TLS / HTTPS is enabled.
     */
    private boolean tlsEnabled;

    /**
     * Default labels used for PromQL construction (e.g., cluster -> broker name).
     */
    private Map<String, String> defaultLabels;

    /**
     * Scrape interval in seconds configured on the Prometheus side.
     */
    private int scrapeInterval;

    /**
     * Whether this data source is currently activated.
     */
    private boolean enabled;
    public static final long getSerialVersionUID() {
        return serialVersionUID;
    }

    public void setSerialVersionUID(static final long serialVersionUID) {
        this.serialVersionUID = serialVersionUID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
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

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public boolean isTlsEnabled() {
        return tlsEnabled;
    }

    public void setTlsEnabled(boolean tlsEnabled) {
        this.tlsEnabled = tlsEnabled;
    }

    public Map<String, String> getDefaultLabels() {
        return defaultLabels;
    }

    public void setDefaultLabels(Map<String, String> defaultLabels) {
        this.defaultLabels = defaultLabels;
    }

    public int getScrapeInterval() {
        return scrapeInterval;
    }

    public void setScrapeInterval(int scrapeInterval) {
        this.scrapeInterval = scrapeInterval;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
