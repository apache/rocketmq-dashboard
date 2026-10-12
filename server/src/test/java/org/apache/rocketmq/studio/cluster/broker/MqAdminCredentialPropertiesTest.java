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
package org.apache.rocketmq.studio.cluster.broker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cluster admin credentials are deployment-specific: they bind from the environment and the
 * shipped {@code application.yml} carries only placeholders, so no key pair travels in the
 * repository. Leaving them unset is a supported state — {@link RuntimeAdminClientResolver} answers
 * "Admin credential reference is not configured" rather than authenticating with blank keys.
 */
class MqAdminCredentialPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void shippedCredentialReferenceCarriesNoLiteralKeysTest() {
        runner.run(context -> {
            MqAdminProperties.Credential credential =
                    context.getBean(MqAdminProperties.class).getCredentials().get("rmq-test");

            assertThat(credential).isNotNull();
            assertThat(credential.getAccessKey()).isNullOrEmpty();
            assertThat(credential.getSecretKey()).isNullOrEmpty();
        });
    }

    @Test
    void suppliedEnvironmentKeysAreBoundTest() {
        runner.withPropertyValues(
                        "STUDIO_CLUSTER_ADMIN_ACCESS_KEY=deployment-ak",
                        "STUDIO_CLUSTER_ADMIN_SECRET_KEY=deployment-sk")
                .run(context -> {
                    MqAdminProperties.Credential credential =
                            context.getBean(MqAdminProperties.class).getCredentials().get("rmq-test");

                    assertThat(credential.getAccessKey()).isEqualTo("deployment-ak");
                    assertThat(credential.getSecretKey()).isEqualTo("deployment-sk");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MqAdminProperties.class)
    static class TestConfiguration {
    }
}
