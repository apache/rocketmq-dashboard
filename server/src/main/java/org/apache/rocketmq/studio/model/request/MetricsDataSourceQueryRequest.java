/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * additional information regarding copyright ownership.
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

import org.apache.rocketmq.studio.cluster.metrics.MetricQueryDTO;

/**
 * Request to run a PromQL range query against a configured data source.
 *
 * <p>The data source is identified by its persisted key (carrying the backend
 * {@code type} and {@code url}); credentials are supplied per request and are
 * never persisted, mirroring the existing {@code /api/settings/datasources/test}
 * flow.</p>
 */
public class MetricsDataSourceQueryRequest {

    private MetricQueryDTO query;

    private String username;

    private String password;

    private String bearerToken;

    public MetricQueryDTO getQuery() {
        return query;
    }

    public void setQuery(MetricQueryDTO query) {
        this.query = query;
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
}
