package org.apache.rocketmq.dashboard.cli.schema;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistryTest {

    private final ToolRegistry registry = ToolRegistry.getInstance();

    @Test
    void registryIsPopulated() {
        List<ToolDefinition> tools = registry.getAllTools();
        assertNotNull(tools);
        assertTrue(tools.size() >= 1, "at least the capabilities.detect tool must be registered");
    }

    @Test
    void capabilitiesDetectIsRegistered() {
        ToolDefinition def = registry.getTool("rmq.capabilities.detect");
        assertNotNull(def);
        assertEquals("capabilities", def.getResource());
        assertEquals("detect", def.getVerb());
        assertEquals(RiskLevel.L1, def.getRiskLevel());
    }

    @Test
    void toolsGroupedByResource() {
        List<ToolDefinition> caps = registry.getToolsByResource("capabilities");
        assertEquals(1, caps.size());
        assertEquals("rmq.capabilities.detect", caps.get(0).getName());
    }

    @Test
    void toolNameFollowsConvention() {
        for (ToolDefinition def : registry.getAllTools()) {
            assertTrue(def.getName().startsWith("rmq."), "tool name must be rmq.<resource>.<verb>");
        }
    }
}
