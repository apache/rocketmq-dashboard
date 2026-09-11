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
package org.apache.rocketmq.studio.instance.acl;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PlainAclRemoteAddressValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "*",
        "*.*.*.*",
        "127.0.0.1",
        "192.168.*.*",
        "192.168.1-100.*",
        "192.168.1.{1,2,3}",
        "192.168.1.10,192.168.1.11",
        "*:*:*:*:*:*:*:*",
        "2001:db8::1",
        "1050::0005:0600:300c:{1,2,3}",
        "1050::0005:0600:300c:1-200",
        "1050::0005:0600:300c:1-20:*"
    })
    void shouldAcceptRocketMqPlainAclExpressions(String expression) {
        assertThat(PlainAclRemoteAddressValidator.isValid(expression)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "999.999.999.999",
        "10.0.0.0/8",
        "192.168.300.*",
        "192.168.100-1.*",
        "192.168.1.{}",
        "192.168.1.{1,}",
        "192.168.{1,2}.1",
        "192.168.1.10,",
        "192.168.1.10, 192.168.1.11",
        "2001:db8::gggg",
        "1050::0005:0600:300c:200-1",
        "1050::0005:*:300c:1",
        "::*",
        "::1-20",
        "::1-20:*",
        "not-an-address"
    })
    void shouldRejectExpressionsThePlainAclParserCannotUse(String expression) {
        assertThat(PlainAclRemoteAddressValidator.isValid(expression)).isFalse();
    }
}
