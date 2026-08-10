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
package org.apache.rocketmq.studio.provider.tencent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TencentClientFactoryTest {

    @Test
    void endpointForShouldUseNearbyAccessForNonFinancialRegionsTest() {
        assertThat(TencentClientFactory.endpointFor("ap-seoul"))
                .isEqualTo(TencentClientFactory.ENDPOINT);
        assertThat(TencentClientFactory.endpointFor("ap-shanghai-adc"))
                .isEqualTo(TencentClientFactory.ENDPOINT);
    }

    @Test
    void endpointForShouldUseNearbyAccessWhenRegionIsMissingTest() {
        assertThat(TencentClientFactory.endpointFor(null))
                .isEqualTo(TencentClientFactory.ENDPOINT);
        assertThat(TencentClientFactory.endpointFor(""))
                .isEqualTo(TencentClientFactory.ENDPOINT);
    }

    @Test
    void endpointForShouldUseRegionalEndpointForFinancialRegionsTest() {
        assertThat(TencentClientFactory.endpointFor("ap-shenzhen-fsi"))
                .isEqualTo("trocket.ap-shenzhen-fsi.tencentcloudapi.com");
        assertThat(TencentClientFactory.endpointFor("ap-shanghai-fsi"))
                .isEqualTo("trocket.ap-shanghai-fsi.tencentcloudapi.com");
    }
}
