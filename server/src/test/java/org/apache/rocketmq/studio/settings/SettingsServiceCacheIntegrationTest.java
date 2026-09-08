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
package org.apache.rocketmq.studio.settings;

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"studio.auth.login-required=false", "spring.cache.type=simple", "spring.cache.cache-names=data-sources"})
class SettingsServiceCacheIntegrationTest {

    @Autowired
    private SettingsService settingsService;

    @MockBean
    private SettingsRepository settingsRepository;

    @Test
    void pagedDataSourceCacheUsesDistinctKeysForNullAndLiteralNullTest() {
        PageResult<DataSourceVO> unfilteredPage = PageResult.empty(1, 20);
        PageResult<DataSourceVO> literalNullPage = PageResult.of(List.of(), 5, 1, 20);
        when(settingsRepository.findDataSources(null, null, 1, 20))
                .thenReturn(unfilteredPage);
        when(settingsRepository.findDataSources("null", null, 1, 20))
                .thenReturn(literalNullPage);

        assertThat(settingsService.listDataSources(null, null, 1, 20))
                .isSameAs(unfilteredPage);
        assertThat(settingsService.listDataSources("null", null, 1, 20))
                .isSameAs(literalNullPage);

        verify(settingsRepository, times(1)).findDataSources(null, null, 1, 20);
        verify(settingsRepository, times(1)).findDataSources("null", null, 1, 20);
    }
}
