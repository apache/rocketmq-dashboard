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

/*
 * Contract test: the consumer-group create form (web/src/pages/instance/consumer.tsx,
 * the "order type" select under a FIFO subscription type) sends deliveryOrderType =
 * PARTITON_ORDER / MESSAGES_ORDER, and the CSV import accepts PARTITION_ORDER
 * (web/src/utils/resourceCsvImport.ts GROUP_DELIVERY_ORDER_TYPES).
 * CreateConsumerGroupDTO passes the string through untouched.
 *
 * AliyunInstanceProvider.createConsumerGroup normalizes it via
 * normalizeDeliveryOrderType(), whose javadoc promises to "tolerate FIFO/ordered
 * spellings from the UI" - but it only recognises "FIFO"/"ORDERLY" spellings and maps
 * everything else to "Concurrently". So creating an ordered group on an Aliyun
 * instance from the console ALWAYS produces a Concurrently group with
 * DefaultRetryPolicy - the operator's order-type selection is silently dropped.
 *
 * Tencent is unaffected (isOrderly() matches *ORDER*), and the Apache provider treats
 * the field as metadata only.
 */
package org.apache.rocketmq.studio.provider.alibaba;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AliyunDeliveryOrderTypeContractTest {

    @Test
    void uiOrderlySelectionsAreMappedToOrderlyTest() {
        assertEquals("Orderly",
                AliyunInstanceProvider.normalizeDeliveryOrderType("PARTITON_ORDER"));
        assertEquals("Orderly",
                AliyunInstanceProvider.normalizeDeliveryOrderType("MESSAGES_ORDER"));
        // CSV import accepts the correctly spelled variant as well
        // (web/src/utils/resourceCsvImport.ts GROUP_DELIVERY_ORDER_TYPES).
        assertEquals("Orderly",
                AliyunInstanceProvider.normalizeDeliveryOrderType("PARTITION_ORDER"));
    }
}