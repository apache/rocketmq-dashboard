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

package org.apache.rocketmq.studio.instance.dlq;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DLQProviderStubTest {

    private final DLQProviderStub provider = new DLQProviderStub();

    @Test
    void listDLQGroupsShouldFailAsNotImplemented() {
        assertThatThrownBy(() -> provider.listDLQGroups(null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED.value()));
    }

    @Test
    void listDLQGroupsShouldFailAsNotImplementedForAnyCluster() {
        assertThatThrownBy(() -> provider.listDLQGroups("rmq-cn-v5-prod-01"))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED.value()));
    }

    @Test
    void resendMessagesShouldFailAsNotImplemented() {
        assertThatThrownBy(() -> provider.resendMessages("cg-order-payment", null, null, "target-topic"))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED.value()));
    }
}
