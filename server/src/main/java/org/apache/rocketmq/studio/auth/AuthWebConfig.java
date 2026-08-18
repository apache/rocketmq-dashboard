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

package org.apache.rocketmq.studio.auth;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.ops.ai.tool.ToolAccessPolicy;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The interceptor is always registered: enforcement itself decides whether login is required,
 * merging the static {@code studio.auth.login-required} property with the runtime "requireLogin"
 * toggle from the settings database. A conditional registration would make the UI toggle a no-op
 * whenever the static property is false.
 */
@Configuration
@RequiredArgsConstructor
public class AuthWebConfig implements WebMvcConfigurer {

    private final ObjectProvider<AuthProperties> authPropertiesProvider;
    private final ObjectProvider<AuthService> authServiceProvider;
    private final ObjectProvider<SettingsRepository> settingsRepositoryProvider;
    private final ObjectProvider<ToolAccessPolicy> toolAccessPolicyProvider;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Slice tests and minimal contexts may not provide any of these beans; when they are
        // missing the interceptor falls back to the static login-required property (effectively
        // no enforcement), matching the old conditional-registration behaviour.
        registry.addInterceptor(new AuthInterceptor(authPropertiesProvider.getIfAvailable(),
                        authServiceProvider.getIfAvailable(),
                        settingsRepositoryProvider.getIfAvailable(),
                        toolAccessPolicyProvider.getIfAvailable()))
                .addPathPatterns("/api/**");
    }
}
