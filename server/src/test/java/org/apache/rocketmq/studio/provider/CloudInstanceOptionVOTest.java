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
package org.apache.rocketmq.studio.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the cloud instance option of {@link CloudInstanceOptionVO}: one cloud instance row in
 * the import picker - the identity, the display name, its status and counts.
 */
class CloudInstanceOptionVOTest {

    @Test
    void theOptionRoundTripsThroughTheBuilder() {
        CloudInstanceOptionVO option = new CloudInstanceOptionVO();
        option.setInstanceId("rmq-abc123");
        option.setInstanceName("order-cluster");
        option.setStatus("RUNNING");
        option.setRegionId("cn-hangzhou");
        option.setTopicCount(120);
        option.setGroupCount(45);
        option.setRemark("production");

        assertThat(option.getInstanceId()).isEqualTo("rmq-abc123");
        assertThat(option.getInstanceName()).isEqualTo("order-cluster");
        assertThat(option.getStatus()).isEqualTo("RUNNING");
        assertThat(option.getRegionId()).isEqualTo("cn-hangzhou");
        assertThat(option.getTopicCount()).isEqualTo(120);
        assertThat(option.getGroupCount()).isEqualTo(45);
        assertThat(option.getRemark()).isEqualTo("production");
    }

    @Test
    void aFreshOptionCountsNothing() {
        CloudInstanceOptionVO option = new CloudInstanceOptionVO();
        assertThat(option.getInstanceId()).isNull();
        assertThat(option.getTopicCount()).isNull();
        assertThat(option.getGroupCount()).isNull();
    }
}
