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
package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.model.Acl2PolicyContext;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.NamespaceInfo;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class Acl2ServiceImplTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @InjectMocks
    private Acl2ServiceImpl acl2Service;

    @Mock
    private RMQConfigure rmqConfigure;

    @Mock
    private ClusterProvider clusterProvider;

    @Mock
    private MetadataProvider metadataProvider;

    @Before
    public void setUp() {
        when(rmqConfigure.getRocketMqDashboardDataPath())
            .thenReturn(tempFolder.getRoot().getAbsolutePath());
    }

    private ClusterCapability capabilityV2() {
        ClusterCapability capability = new ClusterCapability();
        capability.setAclV2Supported(true);
        capability.setArchitectureVersion("5.0");
        capability.setRocketmqVersion("5.3.3");
        return capability;
    }

    private Acl2PolicyContext buildContext(String accessKey, String policyName) {
        Acl2PolicyContext context = new Acl2PolicyContext();
        context.setAccessKey(accessKey);
        context.setPolicyName(policyName);
        context.setSecretKey("secret");
        context.setBoundType("USER");
        context.setBoundEntityId("alice");
        Acl2PolicyContext.AuthorizationRule rule =
            Acl2PolicyContext.AuthorizationRule.defaultAllowRule("Topic:test*");
        context.setRules(Collections.singletonList(rule));
        return context;
    }

    private File policyFile() {
        return new File(tempFolder.getRoot(), "acl" + File.separator + "acl2_policy_conf.yaml");
    }

    // ==================== version detection ====================

    @Test
    public void testDetectAclVersionV2() throws Exception {
        when(clusterProvider.getClusterCapability()).thenReturn(capabilityV2());
        assertEquals("V2", acl2Service.detectAclVersion());
        // second call returns cached value
        assertEquals("V2", acl2Service.detectAclVersion());
    }

    @Test
    public void testDetectAclVersionV1ForLegacyArchitecture() throws Exception {
        ClusterCapability capability = new ClusterCapability();
        capability.setArchitectureVersion("4.0");
        when(clusterProvider.getClusterCapability()).thenReturn(capability);
        assertEquals("V1", acl2Service.detectAclVersion());
    }

    @Test
    public void testDetectAclVersionMixedMode() throws Exception {
        ClusterCapability capability = capabilityV2();
        capability.setArchitectureVersion("4.0"); // supports both -> MIXED -> V2
        when(clusterProvider.getClusterCapability()).thenReturn(capability);
        assertEquals("V2", acl2Service.detectAclVersion());
    }

    @Test
    public void testDetectAclVersionNoSupportDefaultsToV1() throws Exception {
        when(clusterProvider.getClusterCapability()).thenReturn(new ClusterCapability());
        assertEquals("V1", acl2Service.detectAclVersion());
    }

    @Test
    public void testDetectAclVersionFailureDefaultsToV1() throws Exception {
        when(clusterProvider.getClusterCapability()).thenThrow(new RuntimeException("down"));
        assertEquals("V1", acl2Service.detectAclVersion());
    }

    @Test
    public void testDetectAndReportV2() throws Exception {
        when(clusterProvider.getClusterCapability()).thenReturn(capabilityV2());

        Map<String, Object> report = acl2Service.detectAndReport();
        assertEquals("ACL_2_0", report.get("rawDetection"));
        assertEquals("V2", report.get("effectiveVersion"));
        assertEquals(Boolean.TRUE, report.get("aclV2Supported"));
        assertEquals("5.0", report.get("architectureVersion"));
        assertEquals("FULL_ACL_2_0", report.get("migrationStatus"));
        assertNotNull(report.get("migrationDescription"));
    }

    @Test
    public void testDetectAndReportNone() throws Exception {
        when(clusterProvider.getClusterCapability()).thenReturn(new ClusterCapability());

        Map<String, Object> report = acl2Service.detectAndReport();
        assertEquals("NONE", report.get("rawDetection"));
        assertEquals("NONE", report.get("effectiveVersion"));
        assertEquals("NO_ACL_SUPPORT", report.get("migrationStatus"));
    }

    @Test
    public void testDetectAndReportFailure() throws Exception {
        when(clusterProvider.getClusterCapability()).thenThrow(new RuntimeException("cluster down"));

        Map<String, Object> report = acl2Service.detectAndReport();
        assertEquals("UNKNOWN", report.get("effectiveVersion"));
        assertEquals("cluster down", report.get("error"));
    }

    // ==================== policy CRUD ====================

    @Test
    @SuppressWarnings("unchecked")
    public void testCreatePolicySuccess() throws Exception {
        when(clusterProvider.getClusterCapability()).thenReturn(capabilityV2());

        Map<String, Object> response = (Map<String, Object>) acl2Service.createPolicy(
            buildContext("ak-1", "policy-1"));

        assertEquals(Boolean.TRUE, response.get("success"));
        assertEquals("ak-1", response.get("accessKey"));
        assertEquals("policy-1", response.get("policyName"));
        assertTrue(policyFile().exists());

        Map<String, Object> listResponse = (Map<String, Object>) acl2Service.listPolicies(null);
        assertEquals(1, listResponse.get("total"));
        assertEquals("V2", listResponse.get("aclVersion"));
    }

    @Test
    public void testCreatePolicyDuplicate() {
        acl2Service.createPolicy(buildContext("ak-dup", "policy-1"));
        try {
            acl2Service.createPolicy(buildContext("ak-dup", "policy-2"));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("already exists"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreatePolicyMissingAccessKey() {
        acl2Service.createPolicy(buildContext(" ", "policy-1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreatePolicyMissingPolicyName() {
        acl2Service.createPolicy(buildContext("ak-2", null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreatePolicyEmptyRules() {
        Acl2PolicyContext context = buildContext("ak-3", "policy-3");
        context.setRules(Collections.emptyList());
        acl2Service.createPolicy(context);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreatePolicyInvalidEffect() {
        Acl2PolicyContext context = buildContext("ak-4", "policy-4");
        context.getRules().get(0).setEffect("Maybe");
        acl2Service.createPolicy(context);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreatePolicyInvalidBoundType() {
        Acl2PolicyContext context = buildContext("ak-5", "policy-5");
        context.setBoundType("ROBOT");
        acl2Service.createPolicy(context);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUpdatePolicySuccess() {
        acl2Service.createPolicy(buildContext("ak-6", "policy-6"));

        Acl2PolicyContext update = buildContext("ak-6", "policy-6-renamed");
        Map<String, Object> response = (Map<String, Object>) acl2Service.updatePolicy("ak-6", update);

        assertEquals(Boolean.TRUE, response.get("success"));
        assertEquals("policy-6-renamed", response.get("policyName"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUpdatePolicyBlankAccessKey() {
        acl2Service.updatePolicy("  ", buildContext("ak-7", "policy-7"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUpdatePolicyNotFound() {
        acl2Service.updatePolicy("nonexistent", buildContext("nonexistent", "policy"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDeletePolicySuccess() {
        acl2Service.createPolicy(buildContext("ak-8", "policy-8"));

        Map<String, Object> response = (Map<String, Object>) acl2Service.deletePolicy("ak-8");
        assertEquals(Boolean.TRUE, response.get("success"));
        assertEquals("ak-8", response.get("accessKey"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeletePolicyBlankAccessKey() {
        acl2Service.deletePolicy(" ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeletePolicyNotFound() {
        acl2Service.deletePolicy("nonexistent");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testListPoliciesWithNamespaceFilter() throws Exception {
        when(clusterProvider.getClusterCapability()).thenReturn(capabilityV2());

        // global policy without namespace scope
        acl2Service.createPolicy(buildContext("ak-global", "global-policy"));
        // policy scoped to ns1
        Acl2PolicyContext scoped = buildContext("ak-scoped", "scoped-policy");
        scoped.setNamespaceScopes(Collections.singletonList("ns1"));
        acl2Service.createPolicy(scoped);

        Map<String, Object> allResult = (Map<String, Object>) acl2Service.listPolicies(null);
        assertEquals(2, allResult.get("total"));

        Map<String, Object> ns1Result = (Map<String, Object>) acl2Service.listPolicies("ns1");
        assertEquals(2, ns1Result.get("total"));

        Map<String, Object> ns2Result = (Map<String, Object>) acl2Service.listPolicies("ns2");
        assertEquals(1, ns2Result.get("total"));
        List<Map<String, Object>> policies = (List<Map<String, Object>>) ns2Result.get("policies");
        assertEquals("ak-global", policies.get(0).get("accessKey"));
    }

    // ==================== namespaces ====================

    @Test
    @SuppressWarnings("unchecked")
    public void testListNamespaces() throws Exception {
        NamespaceInfo ns = new NamespaceInfo();
        ns.setNamespaceName("ns1");
        ns.setDisplayName("Namespace One");
        when(metadataProvider.listNamespaces()).thenReturn(Collections.singletonList(ns));

        Map<String, Object> response = (Map<String, Object>) acl2Service.listNamespaces();
        assertEquals(1, response.get("total"));
        List<Map<String, Object>> namespaces = (List<Map<String, Object>>) response.get("namespaces");
        assertEquals("ns1", namespaces.get(0).get("namespace"));
        assertEquals("Namespace One", namespaces.get(0).get("displayName"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testListNamespacesNullList() throws Exception {
        when(metadataProvider.listNamespaces()).thenReturn(null);

        Map<String, Object> response = (Map<String, Object>) acl2Service.listNamespaces();
        assertEquals(0, response.get("total"));
    }

    @Test(expected = RuntimeException.class)
    public void testListNamespacesFailure() throws Exception {
        when(metadataProvider.listNamespaces()).thenThrow(new RuntimeException("unavailable"));
        acl2Service.listNamespaces();
    }

    // ==================== rotation & file loading ====================

    @Test
    public void testReloadPoliciesIfChangedAndRotationStatus() {
        acl2Service.createPolicy(buildContext("ak-rotate", "rotate-policy"));

        int size = acl2Service.reloadPoliciesIfChanged();
        assertEquals(1, size);

        Map<String, Object> status = acl2Service.getRotationStatus();
        assertEquals(Boolean.TRUE, status.get("enabled"));
        assertEquals(5000L, status.get("intervalMs"));
        assertTrue((Integer) status.get("rotationCount") >= 1);
        assertEquals(1, status.get("cachedPolicyCount"));
        assertNotNull(status.get("nextRotationInMs"));
    }

    @Test
    public void testRotateCredentialsSwallowsErrors() {
        // no file present, should not throw
        acl2Service.rotateCredentials();
        Map<String, Object> status = acl2Service.getRotationStatus();
        assertTrue((Integer) status.get("rotationCount") >= 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInitLoadsPoliciesFromExistingFile() throws Exception {
        File aclDir = tempFolder.newFolder("acl");
        File file = new File(aclDir, "acl2_policy_conf.yaml");
        String yaml = "version: '2.0'\n"
            + "policies:\n"
            + "  - accessKey: file-ak\n"
            + "    policyName: file-policy\n"
            + "    enabled: true\n";
        Files.write(file.toPath(), yaml.getBytes(StandardCharsets.UTF_8));

        acl2Service.init();

        Map<String, Object> listResponse = (Map<String, Object>) acl2Service.listPolicies(null);
        assertEquals(1, listResponse.get("total"));
        List<Map<String, Object>> policies = (List<Map<String, Object>>) listResponse.get("policies");
        assertEquals("file-ak", policies.get(0).get("accessKey"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInitWithCorruptedFileIsGraceful() throws Exception {
        File aclDir = tempFolder.newFolder("acl");
        File file = new File(aclDir, "acl2_policy_conf.yaml");
        Files.write(file.toPath(), "{invalid yaml: [".getBytes(StandardCharsets.UTF_8));

        acl2Service.init();

        Map<String, Object> listResponse = (Map<String, Object>) acl2Service.listPolicies(null);
        assertEquals(0, listResponse.get("total"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInitWithInvalidStructureIsGraceful() throws Exception {
        File aclDir = tempFolder.newFolder("acl");
        File file = new File(aclDir, "acl2_policy_conf.yaml");
        Files.write(file.toPath(), "policies: not-a-list\n".getBytes(StandardCharsets.UTF_8));

        acl2Service.init();

        Map<String, Object> listResponse = (Map<String, Object>) acl2Service.listPolicies(null);
        assertEquals(0, listResponse.get("total"));
    }
}
