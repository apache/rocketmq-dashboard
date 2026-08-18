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

package org.apache.rocketmq.dashboard.config;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.Scanner;

public class ActuatorConfigurationTest {

    @Test
    public void applicationConfigurationRequiresExternalActuatorCredentialsAndLimitsExposure() throws Exception {
        Scanner scanner = new Scanner(new ClassPathResource("application.yml").getInputStream(), "UTF-8")
                .useDelimiter("\\A");
        String yaml = scanner.hasNext() ? scanner.next() : "";

        Assert.assertTrue(yaml.contains("${DASHBOARD_ACTUATOR_USERNAME}"));
        Assert.assertTrue(yaml.contains("${DASHBOARD_ACTUATOR_PASSWORD}"));
        Assert.assertTrue(yaml.contains("include: health,info"));
        Assert.assertFalse(yaml.contains("include: \"*\""));
    }
}
