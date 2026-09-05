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
package org.apache.rocketmq.studio.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RegionNamesTest {

    private RegionNames loaded() {
        RegionNames regionNames = new RegionNames();
        regionNames.load();
        return regionNames;
    }

    @Test
    void resolvesKnownRegionToDisplayName() {
        RegionNames regionNames = loaded();

        String resolved = regionNames.resolve("cn-hangzhou");

        assertEquals("\u534e\u4e1c1\uff08\u676d\u5dde\uff09", resolved);
    }

    @Test
    void fallsBackToRawIdForUnknownRegion() {
        RegionNames regionNames = loaded();

        assertEquals("eu-north-9", regionNames.resolve("eu-north-9"));
    }

    @Test
    void trimsInputBeforeLookup() {
        RegionNames regionNames = loaded();

        assertEquals("\u534e\u4e1c1\uff08\u676d\u5dde\uff09", regionNames.resolve("  cn-hangzhou  "));
    }

    @Test
    void nullRegionStaysNull() {
        RegionNames regionNames = loaded();

        assertNull(regionNames.resolve(null));
    }

    @Test
    void blankRegionPassesThrough() {
        RegionNames regionNames = loaded();

        assertEquals("", regionNames.resolve(""));
        assertEquals("  ", regionNames.resolve("  "));
    }

    @Test
    void displayNameRoundTripsWhenPassedAgain() {
        RegionNames regionNames = loaded();

        String display = regionNames.resolve("cn-hangzhou");
        // A display name is not a key in the map, so re-resolving keeps it as-is.
        assertEquals(display, regionNames.resolve(display));
    }
}
