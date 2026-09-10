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

import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Aliyun converter enum mappers: Aliyun string message types and message
 * models map case-insensitively onto the Studio topic/consume enums, with null and unknown
 * values degrading to null.
 */
class AliyunConvertersEnumsTest {

    @Test
    void mapsAliyunMessageTypesOntoTopicTypes() {
        assertThat(AliyunConverters.toTopicType("NORMAL")).isEqualTo(TopicType.NORMAL);
        assertThat(AliyunConverters.toTopicType("fifo")).isEqualTo(TopicType.FIFO);
        assertThat(AliyunConverters.toTopicType("Delay")).isEqualTo(TopicType.DELAY);
        assertThat(AliyunConverters.toTopicType("TRANSACTION")).isEqualTo(TopicType.TRANSACTION);
    }

    @Test
    void unknownOrMissingMessageTypesDegradeToNull() {
        assertThat(AliyunConverters.toTopicType(null)).isNull();
        assertThat(AliyunConverters.toTopicType("LITE")).isNull();
        assertThat(AliyunConverters.toTopicType("custom-type")).isNull();
    }

    @Test
    void mapsAliyunMessageModelsOntoConsumeTypes() {
        assertThat(AliyunConverters.toConsumeType("Clustering")).isEqualTo(ConsumeType.CLUSTERING);
        assertThat(AliyunConverters.toConsumeType("BROADCASTING")).isEqualTo(ConsumeType.BROADCASTING);
        assertThat(AliyunConverters.toConsumeType("clustering")).isEqualTo(ConsumeType.CLUSTERING);
    }

    @Test
    void unknownOrMissingMessageModelsDegradeToNull() {
        assertThat(AliyunConverters.toConsumeType(null)).isNull();
        assertThat(AliyunConverters.toConsumeType("PUSH")).isNull();
    }
}
