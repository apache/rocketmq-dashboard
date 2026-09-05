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
package org.apache.rocketmq.studio.common.domain.enums;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the client language vocabulary shown by the client connection views.
 */
class ClientLanguageTest {

    @Test
    void exposesAllSupportedClientLanguages() {
        assertEquals(8, ClientLanguage.values().length);
        assertTrue(Arrays.asList(ClientLanguage.values()).contains(ClientLanguage.Java));
        assertTrue(Arrays.asList(ClientLanguage.values()).contains(ClientLanguage.Go));
        assertTrue(Arrays.asList(ClientLanguage.values()).contains(ClientLanguage.Python));
        assertTrue(Arrays.asList(ClientLanguage.values()).contains(ClientLanguage.Rust));
        assertTrue(Arrays.asList(ClientLanguage.values()).contains(ClientLanguage.Cpp));
        assertTrue(Arrays.asList(ClientLanguage.values()).contains(ClientLanguage.CSharp));
        assertTrue(Arrays.asList(ClientLanguage.values()).contains(ClientLanguage.NodeJS));
        assertTrue(Arrays.asList(ClientLanguage.values()).contains(ClientLanguage.PHP));
    }

    @Test
    void enumNamesPreserveOfficialCasing() {
        assertEquals("Java", ClientLanguage.Java.name());
        assertEquals("Cpp", ClientLanguage.Cpp.name());
        assertEquals("CSharp", ClientLanguage.CSharp.name());
        assertEquals("NodeJS", ClientLanguage.NodeJS.name());
        assertEquals("PHP", ClientLanguage.PHP.name());
    }

    @Test
    void namesRoundTripThroughValueOf() {
        for (ClientLanguage language : ClientLanguage.values()) {
            assertEquals(language, ClientLanguage.valueOf(language.name()));
        }
    }
}
