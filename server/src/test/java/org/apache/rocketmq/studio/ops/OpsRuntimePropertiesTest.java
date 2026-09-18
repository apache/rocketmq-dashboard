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

package org.apache.rocketmq.studio.ops;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class OpsRuntimePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(OpsRuntimeProperties.class);

    @Test
    void explicitOpsNamesrvAddrOverridesExternalDefaults() {
        runner.withPropertyValues(
                "studio.ops.runtime.namesrv-addr=ops:9876",
                "studio.cluster.admin.namesrv-addr=admin:9876",
                "studio.rocketmq.namesrv-addr=rocketmq:9876"
        ).run(context -> assertThat(context.getBean(OpsRuntimeProperties.class).getNamesrvAddr())
                .isEqualTo("ops:9876"));
    }

    @Test
    void clusterAdminNamesrvAddrIsPreferredWhenOpsOverrideIsAbsent() {
        runner.withPropertyValues(
                "studio.cluster.admin.namesrv-addr=admin:9876",
                "studio.rocketmq.namesrv-addr=rocketmq:9876"
        ).run(context -> assertThat(context.getBean(OpsRuntimeProperties.class).getNamesrvAddr())
                .isEqualTo("admin:9876"));
    }

    @Test
    void rocketmqNamesrvAddrSeedsOpsRuntimeWhenNoAdminDefaultExists() {
        runner.withPropertyValues("studio.rocketmq.namesrv-addr=rocketmq:9876")
                .run(context -> assertThat(context.getBean(OpsRuntimeProperties.class).getNamesrvAddr())
                        .isEqualTo("rocketmq:9876"));
    }
}
