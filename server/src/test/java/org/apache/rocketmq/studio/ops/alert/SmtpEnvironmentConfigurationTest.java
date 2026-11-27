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
package org.apache.rocketmq.studio.ops.alert;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The alerting design doc tells operators to configure the SMTP transport with
 * {@code STUDIO_ALERTING_SMTP_AUTH} and {@code STUDIO_ALERTING_SMTP_STARTTLS}, in the same
 * sentence as the host, port, credentials and socket timeouts. Every one of those neighbours
 * carries its explicit {@code ${...}} bridge in {@code application.yml}; the two transport
 * switches were spelled as a literal {@code true}, which nothing maps the documented variables
 * onto. An operator pointing Studio at a relay that needs neither credentials nor STARTTLS
 * (implicit TLS on 465, or a trusted internal relay) therefore could not turn either off: the
 * delivery kept failing with the relay's own rejection while the variable they had configured
 * was ignored, and only the run-time error gave a hint.
 */
class SmtpEnvironmentConfigurationTest {

    private static final String AUTH = "spring.mail.properties.mail.smtp.auth";
    private static final String STARTTLS = "spring.mail.properties.mail.smtp.starttls.enable";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void smtpAuthShouldHonourTheDocumentedEnvironmentOverrideTest() {
        runner.withPropertyValues("STUDIO_ALERTING_SMTP_AUTH=false")
                .run(context -> assertThat(context.getEnvironment().getProperty(AUTH)).isEqualTo("false"));
    }

    @Test
    void smtpStartTlsShouldHonourTheDocumentedEnvironmentOverrideTest() {
        runner.withPropertyValues("STUDIO_ALERTING_SMTP_STARTTLS=false")
                .run(context -> assertThat(context.getEnvironment().getProperty(STARTTLS)).isEqualTo("false"));
    }

    @Test
    void smtpTransportShouldDefaultToAuthenticatedStartTlsTest() {
        runner.run(context -> {
            Environment environment = context.getEnvironment();
            assertThat(environment.getProperty(AUTH)).isEqualTo("true");
            assertThat(environment.getProperty(STARTTLS)).isEqualTo("true");
        });
    }
}
