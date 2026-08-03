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
package org.apache.rocketmq.studio.instance.topic;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NameSrvAdminClientTest {

    private final NameSrvAdminClient adminClient = new NameSrvAdminClient();

    @Test
    void consumerGroupOperationsShouldReturnAdminUnavailable() {
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("cg-order");

        assertUnavailable(() -> adminClient.getConsumerGroup("cg-order"));
        assertUnavailable(() -> adminClient.createConsumerGroup(group));
        assertUnavailable(() -> adminClient.deleteConsumerGroup("cg-order"));
        assertUnavailable(() -> adminClient.resetOffset("cg-order", 1784246400000L, "order-topic"));
    }

    private void assertUnavailable(ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .hasMessage("Consumer group admin client is not configured")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(501));
    }
}
