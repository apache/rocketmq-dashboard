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

package com.rocketmq.studio.auth;

import java.time.Clock;
import java.util.Map;
import java.util.function.LongSupplier;

import com.rocketmq.studio.auth.security.FileStudioUserRegistry;
import com.rocketmq.studio.auth.security.InMemoryStudioSessionStore;
import com.rocketmq.studio.auth.security.LoginAttemptLimiter;
import com.rocketmq.studio.auth.security.StudioSecurityProperties;
import com.rocketmq.studio.auth.security.StudioSessionStore;
import com.rocketmq.studio.auth.security.StudioUserRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties(StudioSecurityProperties.class)
public class SecurityConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock studioClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(LongSupplier.class)
    public LongSupplier studioTicker() {
        return System::nanoTime;
    }

    @Bean
    @ConditionalOnMissingBean(StudioUserRegistry.class)
    public StudioUserRegistry studioUserRegistry(
        StudioSecurityProperties properties,
        LongSupplier studioTicker
    ) {
        return new FileStudioUserRegistry(properties, studioTicker);
    }

    @Bean
    @ConditionalOnMissingBean(StudioSessionStore.class)
    public StudioSessionStore studioSessionStore(
        StudioSecurityProperties properties,
        Clock studioClock
    ) {
        return new InMemoryStudioSessionStore(properties, studioClock);
    }

    @Bean
    @ConditionalOnMissingBean(LoginAttemptLimiter.class)
    public LoginAttemptLimiter loginAttemptLimiter(
        StudioSecurityProperties properties,
        Clock studioClock
    ) {
        return new LoginAttemptLimiter(properties, studioClock);
    }

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder studioPasswordEncoder() {
        Map<String, PasswordEncoder> encoders = Map.of(
            "bcrypt",
            new BCryptPasswordEncoder(12)
        );
        return new DelegatingPasswordEncoder("bcrypt", encoders);
    }
}
