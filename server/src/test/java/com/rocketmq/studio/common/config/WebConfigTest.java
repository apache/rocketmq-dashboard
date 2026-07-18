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
package com.rocketmq.studio.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.junit.jupiter.api.Assertions.*;

class WebConfigTest {

    @Test
    void shouldHaveConfigurationAnnotation() {
        Configuration annotation = WebConfig.class.getAnnotation(Configuration.class);
        assertNotNull(annotation, "WebConfig should be annotated with @Configuration");
    }

    @Test
    void shouldImplementWebMvcConfigurer() {
        assertTrue(WebMvcConfigurer.class.isAssignableFrom(WebConfig.class),
                "WebConfig should implement WebMvcConfigurer");
    }

    @Test
    void shouldBeInstantiable() {
        assertDoesNotThrow(() -> new WebConfig(), "WebConfig should be instantiable");
    }
}