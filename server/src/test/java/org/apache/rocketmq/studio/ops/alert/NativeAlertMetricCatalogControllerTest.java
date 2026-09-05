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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NativeAlertMetricCatalogController}: the native metric catalog
 * endpoint delegates to the catalog service with the instance and domain intact.
 */
@ExtendWith(MockitoExtension.class)
class NativeAlertMetricCatalogControllerTest {

    @Mock
    private NativeAlertMetricCatalogService catalogService;

    @InjectMocks
    private NativeAlertMetricCatalogController controller;

    @Test
    void listsMetricsForTheInstanceAndDomain() {
        NativeAlertMetricInfo metric =
                new NativeAlertMetricInfo("broker.availability", "Broker availability", "", false);
        when(catalogService.list("apache", AlertDomain.CLUSTER)).thenReturn(List.of(metric));

        assertThat(controller.list("apache", AlertDomain.CLUSTER).getData())
                .extracting(NativeAlertMetricInfo::key)
                .containsExactly("broker.availability");
        verify(catalogService).list("apache", AlertDomain.CLUSTER);
    }

    @Test
    void passesTheInstanceIdThroughWithoutTrimming() {
        when(catalogService.list(" apache ", AlertDomain.BUSINESS)).thenReturn(List.of());

        assertThat(controller.list(" apache ", AlertDomain.BUSINESS).getData()).isEmpty();
        verify(catalogService).list(" apache ", AlertDomain.BUSINESS);
    }
}
