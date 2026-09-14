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
package org.apache.rocketmq.studio.provider.alibaba;

import com.aliyun.sdk.service.rocketmq20220801.models.ListConsumerGroupSubscriptionsResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.ListInstancesResponseBody;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AliyunConvertersTest {

    @Test
    void toInstanceOptionShouldClampCountsOutsideTheIntegerRange() {
        ListInstancesResponseBody.List data = ListInstancesResponseBody.List.builder()
                .topicCount(Long.MAX_VALUE)
                .groupCount(Long.MIN_VALUE)
                .build();

        var result = AliyunConverters.toInstanceOptionVO(data);

        assertThat(result.getTopicCount()).isEqualTo(Integer.MAX_VALUE);
        assertThat(result.getGroupCount()).isZero();
    }

    @Test
    void toSubscriptionEntryShouldDeriveFilterModeFromTheExpressionType() {
        ListConsumerGroupSubscriptionsResponseBody.Data data =
                ListConsumerGroupSubscriptionsResponseBody.Data.builder()
                        .topicName("orders")
                        .filterExpression("orderType = 'A'")
                        .filterExpressionType("SQL92")
                        .consistency(true)
                        .build();

        SubscriptionEntryVO entry = AliyunConverters.toSubscriptionEntry(data);

        assertThat(entry.getFilterMode()).isEqualTo("SQL");
    }

    @Test
    void toSubscriptionEntryShouldKeepTagFilterMode() {
        ListConsumerGroupSubscriptionsResponseBody.Data data =
                ListConsumerGroupSubscriptionsResponseBody.Data.builder()
                        .topicName("orders")
                        .filterExpression("tag-a")
                        .filterExpressionType("TAG")
                        .build();

        SubscriptionEntryVO entry = AliyunConverters.toSubscriptionEntry(data);

        assertThat(entry.getFilterMode()).isEqualTo("TAG");
    }
}
