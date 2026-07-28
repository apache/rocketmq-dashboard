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
package org.apache.rocketmq.dashboard.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AclVersionDetectorTest {

    private AclVersionDetector detector;

    @Before
    public void setUp() {
        detector = new AclVersionDetector();
    }

    private ClusterCapability capabilityWith(boolean aclV2, Set<String> extended, String archVersion) {
        ClusterCapability capability = new ClusterCapability();
        capability.setAclV2Supported(aclV2);
        capability.setExtendedCapabilities(extended);
        capability.setArchitectureVersion(archVersion);
        return capability;
    }

    @Test
    public void testDetectAclVersionNullCapability() {
        assertEquals("NONE", detector.detectAclVersion(null));
    }

    @Test
    public void testDetectAclVersionNone() {
        ClusterCapability capability = capabilityWith(false, Collections.emptySet(), "5.0");
        assertEquals("NONE", detector.detectAclVersion(capability));
    }

    @Test
    public void testDetectAclVersion2() {
        ClusterCapability capability = capabilityWith(true, Collections.emptySet(), "5.0");
        assertEquals("ACL_2_0", detector.detectAclVersion(capability));
    }

    @Test
    public void testDetectAclVersion1ViaExtendedCapability() {
        ClusterCapability capability = capabilityWith(false,
            new HashSet<>(Collections.singletonList("ACL_1_0")), "5.0");
        assertEquals("ACL_1_0", detector.detectAclVersion(capability));
    }

    @Test
    public void testDetectAclVersion1ViaArchitectureVersion() {
        ClusterCapability capability = capabilityWith(false, null, "4.0");
        assertEquals("ACL_1_0", detector.detectAclVersion(capability));
    }

    @Test
    public void testDetectAclVersionMixed() {
        ClusterCapability capability = capabilityWith(true,
            new HashSet<>(Collections.singletonList("ACL_1_0")), "5.0");
        assertEquals("ACL_MIXED", detector.detectAclVersion(capability));
    }

    @Test
    public void testSupportsAcl1() {
        assertFalse(detector.supportsAcl1(null));
        assertTrue(detector.supportsAcl1(capabilityWith(false, null, "4.0")));
        assertTrue(detector.supportsAcl1(capabilityWith(false,
            new HashSet<>(Collections.singletonList("ACL_1_0")), "5.0")));
        assertFalse(detector.supportsAcl1(capabilityWith(false, Collections.emptySet(), "5.0")));
    }

    @Test
    public void testSupportsAcl2() {
        assertFalse(detector.supportsAcl2(null));
        assertTrue(detector.supportsAcl2(capabilityWith(true, null, "5.0")));
        assertFalse(detector.supportsAcl2(capabilityWith(false, null, "5.0")));
    }

    @Test
    public void testIsMixedMode() {
        assertFalse(detector.isMixedMode(null));
        assertTrue(detector.isMixedMode(capabilityWith(false,
            new HashSet<>(Collections.singletonList("ACL_MIXED")), "5.0")));
        assertFalse(detector.isMixedMode(capabilityWith(false, null, "5.0")));
    }

    @Test
    public void testGetMigrationInfoFullAcl2() {
        AclVersionDetector.AclMigrationInfo info =
            detector.getMigrationInfo(capabilityWith(true, Collections.emptySet(), "5.0"));
        assertEquals("ACL_2_0", info.getCurrentVersion());
        assertEquals("FULL_ACL_2_0", info.getStatus());
        assertTrue(info.getDescription().contains("2.0"));
    }

    @Test
    public void testGetMigrationInfoLegacyAcl1() {
        AclVersionDetector.AclMigrationInfo info =
            detector.getMigrationInfo(capabilityWith(false, null, "4.0"));
        assertEquals("ACL_1_0", info.getCurrentVersion());
        assertEquals("LEGACY_ACL_1_0", info.getStatus());
        assertTrue(info.getDescription().contains("1.0"));
    }

    @Test
    public void testGetMigrationInfoMixed() {
        AclVersionDetector.AclMigrationInfo info = detector.getMigrationInfo(capabilityWith(true,
            new HashSet<>(Collections.singletonList("ACL_1_0")), "5.0"));
        assertEquals("ACL_MIXED", info.getCurrentVersion());
        assertEquals("MIGRATION_IN_PROGRESS", info.getStatus());
    }

    @Test
    public void testGetMigrationInfoNoSupport() {
        AclVersionDetector.AclMigrationInfo info = detector.getMigrationInfo(null);
        assertEquals("NONE", info.getCurrentVersion());
        assertEquals("NO_ACL_SUPPORT", info.getStatus());
    }

    @Test
    public void testAclMigrationInfoAccessorsAndToString() {
        AclVersionDetector.AclMigrationInfo info = new AclVersionDetector.AclMigrationInfo();
        info.setCurrentVersion("ACL_2_0");
        info.setStatus("FULL_ACL_2_0");
        info.setDescription("desc");
        assertEquals("ACL_2_0", info.getCurrentVersion());
        assertEquals("FULL_ACL_2_0", info.getStatus());
        assertEquals("desc", info.getDescription());
        assertTrue(info.toString().contains("FULL_ACL_2_0"));
    }
}
