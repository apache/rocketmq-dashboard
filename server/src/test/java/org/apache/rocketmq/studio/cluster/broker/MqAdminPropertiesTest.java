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

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the admin connection defaults of {@link MqAdminProperties}: a blank namesrvAddr
 * disables discovery (the interactive test-connection endpoint takes over), the credentials
 * map starts empty, and externalized credentials flow through the credential map with their
 * access and secret keys.
 */
class MqAdminPropertiesTest {

    @Test
    void aFreshConfigHasNoAddressAndNoCredentials() {
        MqAdminProperties properties = new MqAdminProperties();
        assertThat(properties.getNamesrvAddr()).isNull();
        assertThat(properties.getCredentials()).isEmpty();
    }

    @Test
    void theCredentialsMapStartsAsAMutableLinkedMap() {
        MqAdminProperties properties = new MqAdminProperties();
        MqAdminProperties.Credential credential = new MqAdminProperties.Credential();
        credential.setAccessKey("ak-1");
        credential.setSecretKey("sk-1");
        properties.getCredentials().put("prod", credential);

        assertThat(properties.getCredentials()).containsKey("prod");
        assertThat(properties.getCredentials().get("prod").getAccessKey()).isEqualTo("ak-1");
        assertThat(properties.getCredentials().get("prod").getSecretKey()).isEqualTo("sk-1");
        assertThat(properties.getCredentials()).isInstanceOf(LinkedHashMap.class);
    }

    @Test
    void theAddressRoundTrips() {
        MqAdminProperties properties = new MqAdminProperties();
        properties.setNamesrvAddr("10.0.0.1:9876;10.0.0.2:9876");
        assertThat(properties.getNamesrvAddr()).isEqualTo("10.0.0.1:9876;10.0.0.2:9876");
    }
}
