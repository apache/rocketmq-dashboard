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
package org.apache.rocketmq.dashboard.model;

import lombok.Data;

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
@Data
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
}
