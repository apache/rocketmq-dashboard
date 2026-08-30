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
package org.apache.rocketmq.studio.cluster.nameserver;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamesrvAddrParserTest {

    @Test
    void keepsWellFormedSingleAddressTest() {
        assertThat(NamesrvAddrParser.normalize("10.0.0.1:9876")).isEqualTo("10.0.0.1:9876");
    }

    @Test
    void lowercasesHostsTest() {
        assertThat(NamesrvAddrParser.normalize("NS1.Example.COM:9876")).isEqualTo("ns1.example.com:9876");
    }

    @Test
    void lowercasesHostsIndependentlyOfTheDefaultLocaleTest() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(NamesrvAddrParser.normalize("INTERNAL.Example.COM:9876"))
                    .isEqualTo("internal.example.com:9876");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void normalizesSeparatorsAndWhitespaceTest() {
        assertThat(NamesrvAddrParser.normalize(" ns1:9876 ; ns2:9876 ,ns3:9876 "))
                .isEqualTo("ns1:9876,ns2:9876,ns3:9876");
    }

    @Test
    void supportsIpv6LiteralsTest() {
        assertThat(NamesrvAddrParser.normalize("[2001:DB8::1]:9876")).isEqualTo("[2001:db8::1]:9876");
    }

    @Test
    void rejectsBlankInputTest() {
        assertThatThrownBy(() -> NamesrvAddrParser.normalize("   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void rejectsEmptySegmentsTest() {
        assertThatThrownBy(() -> NamesrvAddrParser.normalize("ns1:9876,,ns2:9876"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("empty address segment");
    }

    @Test
    void rejectsConsecutiveSeparatorsTest() {
        assertThatThrownBy(() -> NamesrvAddrParser.normalize("ns1:9876;;ns2:9876"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("empty address segment");
    }

    @Test
    void rejectsMissingPortTest() {
        assertThatThrownBy(() -> NamesrvAddrParser.normalize("ns1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("missing host:port");
    }

    @Test
    void rejectsMissingHostTest() {
        assertThatThrownBy(() -> NamesrvAddrParser.normalize(":9876"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("missing host:port");
    }

    @Test
    void rejectsNonNumericPortTest() {
        assertThatThrownBy(() -> NamesrvAddrParser.normalize("ns1:abc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("non-numeric port");
    }

    @Test
    void rejectsPortsOutOfRangeTest() {
        assertThatThrownBy(() -> NamesrvAddrParser.normalize("ns1:0"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("out of range");
        assertThatThrownBy(() -> NamesrvAddrParser.normalize("ns1:65536"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    void rejectsUnexpectedColonInHostTest() {
        assertThatThrownBy(() -> NamesrvAddrParser.normalize("a:b:9876"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("unexpected character");
    }

    @Test
    void rejectsWhitespaceInsideHostTest() {
        assertThatThrownBy(() -> NamesrvAddrParser.normalize("ns 1:9876"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("unexpected character");
    }

    @Test
    void rejectsMalformedIpv6LiteralsTest() {
        assertThatThrownBy(() -> NamesrvAddrParser.normalize("[abc]:9876"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("malformed IPv6");
        assertThatThrownBy(() -> NamesrvAddrParser.normalize("[1:2]:9876"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("malformed IPv6");
        assertThatThrownBy(() -> NamesrvAddrParser.normalize("[::1-g]:9876"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("malformed IPv6");
    }
}
