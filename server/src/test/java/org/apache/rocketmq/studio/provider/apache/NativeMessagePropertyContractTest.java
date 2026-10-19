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
package org.apache.rocketmq.studio.provider.apache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NativeMessagePropertyContractTest {
    @Test
    void frontendWireContractMatchesTheNativeSdkTest() throws Exception {
        JsonNode contract = new ObjectMapper().readTree(
                Path.of("..", "web", "src", "utils", "nativeMessagePropertyContract.json").toFile());
        assertThat(contract.path("nameValueSeparator").asText())
                .isEqualTo(String.valueOf(MessageDecoder.NAME_VALUE_SEPARATOR));
        assertThat(contract.path("propertySeparator").asText())
                .isEqualTo(String.valueOf(MessageDecoder.PROPERTY_SEPARATOR));
        List<String> reserved = new ArrayList<>();
        contract.path("reservedProperties").forEach(value -> reserved.add(value.asText()));
        assertThat(reserved).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(MessageConst.STRING_HASH_SET);
    }
}
