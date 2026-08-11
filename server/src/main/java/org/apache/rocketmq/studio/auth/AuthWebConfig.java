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

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        AuthService authService = authServiceProvider.getIfAvailable();
        // MVC slice tests and other minimal contexts do not necessarily provide authentication
        // infrastructure. Registering an interceptor without its service turns every request into
        // a 500 response instead of leaving authentication outside the scope of that context.
        if (authService == null) {
            return;
        }
        registry.addInterceptor(new AuthInterceptor(authPropertiesProvider.getIfAvailable(),
                        authService, settingsRepositoryProvider.getIfAvailable()))
                .addPathPatterns("/api/**");
    }
}
