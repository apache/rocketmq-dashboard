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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The credential-bearing models of this package. A Lombok {@code @Data} class prints every field in
 * its generated {@code toString()}, so any log line, exception message or diagnostic dump that
 * renders one of them writes the credential to disk. {@code MetricsDataSourceQueryRequest} already
 * excludes its password and bearer token, and the certificate redaction fix (#4555) named these two
 * classes as the remaining follow-up.
 */
class CredentialModelToStringRedactionTest {

    private static final String SECRET = "sensitive-secret-material";

    @Test
    void dataSourceConfigToStringShouldExcludeTheCredentialsTest() {
        MetricsDataSourceConfig config = new MetricsDataSourceConfig();
        config.setName("prometheus-prod");
        config.setUrl("http://prometheus:9090");
        config.setPassword(SECRET);
        config.setBearerToken(SECRET);

        assertThat(config.toString())
                .contains("prometheus-prod")
                .contains("http://prometheus:9090")
                .doesNotContain(SECRET);
    }

    @Test
    void acl2PolicyContextToStringShouldExcludeTheCredentialsTest() {
        Acl2PolicyContext context = new Acl2PolicyContext();
        context.setAccessKey(SECRET);
        context.setSecretKey(SECRET);
        context.setPolicyName("order-service-policy");

        assertThat(context.toString())
                .contains("order-service-policy")
                .doesNotContain(SECRET);
    }
}
