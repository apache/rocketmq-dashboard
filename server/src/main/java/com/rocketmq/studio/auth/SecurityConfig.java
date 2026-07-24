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
import com.rocketmq.studio.auth.security.StudioAuthorizationPolicy;
import com.rocketmq.studio.auth.security.StudioBearerAuthenticationFilter;
import com.rocketmq.studio.auth.security.StudioSecurityProperties;
import com.rocketmq.studio.auth.security.StudioSecurityResponses;
import com.rocketmq.studio.auth.security.StudioSessionStore;
import com.rocketmq.studio.auth.security.StudioUserRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

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

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain studioSecurityFilterChain(
        HttpSecurity http,
        StudioAuthorizationPolicy policy,
        StudioSecurityResponses responses,
        StudioSessionStore sessions,
        StudioUserRegistry registry
    ) throws Exception {
        StudioBearerAuthenticationFilter bearerFilter =
            new StudioBearerAuthenticationFilter(policy, sessions, registry, responses);
        http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(responses)
                .accessDeniedHandler(responses))
            .authorizeHttpRequests(authorization -> authorization
                .anyRequest().access(policy.authorizationManager()))
            .addFilterBefore(bearerFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
