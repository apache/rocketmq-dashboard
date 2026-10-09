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

import org.apache.rocketmq.studio.common.domain.Result;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the native metric catalog endpoint: it delegates to the catalog service with the instance
 * and domain verbatim and wraps the answer in a success {@link Result}.
 */
class NativeAlertMetricCatalogControllerTest {

    private final NativeAlertMetricCatalogService catalogService = mock(NativeAlertMetricCatalogService.class);
    private final NativeAlertMetricCatalogController controller = new NativeAlertMetricCatalogController(catalogService);

    @Test
    void listDelegatesAndWrapsTheAnswer() {
        NativeAlertMetricInfo metric =
                new NativeAlertMetricInfo("consumer_lag_messages", "Consumer Lag", "messages", true);
        when(catalogService.list("instance-1", AlertDomain.CLUSTER)).thenReturn(List.of(metric));

        Result<List<NativeAlertMetricInfo>> result = controller.list("instance-1", AlertDomain.CLUSTER);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMessage()).isEqualTo("success");
        assertThat(result.getData()).containsExactly(metric);
    }

    @Test
    void anInstanceWithoutNativeMetricsYieldsAnEmptyCatalog() {
        when(catalogService.list("instance-2", AlertDomain.BUSINESS)).thenReturn(List.of());

        Result<List<NativeAlertMetricInfo>> result = controller.list("instance-2", AlertDomain.BUSINESS);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isEmpty();
    }
}
