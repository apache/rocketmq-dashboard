package org.apache.rocketmq.dashboard.cli.executor;

import org.apache.rocketmq.dashboard.cli.schema.RiskLevel;
import org.apache.rocketmq.dashboard.cli.schema.ToolDefinition;
import org.apache.rocketmq.dashboard.cli.schema.ToolParam;
import org.apache.rocketmq.dashboard.cli.schema.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutorTest {

    @Test
    void capabilitiesDetectReturnsToolCatalog() throws ToolException {
        ToolDefinition def = ToolRegistry.getInstance().getTool("rmq.capabilities.detect");
        Map<String, Object> result = ToolExecutor.execute(def, new LinkedHashMap<>(),
                new InvocationContext(false, false, false));
        assertNotNull(result.get("tools"));
        assertTrue((Integer) result.get("count") >= 1);
    }

    @Test
    void unknownToolThrowsStructuredError() {
        ToolException ex = assertThrows(ToolException.class,
                () -> ToolExecutor.execute(null, new LinkedHashMap<>(),
                        new InvocationContext(false, false, false)));
        assertEquals(ErrorModel.Code.UNKNOWN_TOOL.name(), ex.getError().getCode());
    }

    @Test
    void missingRequiredArgumentIsRejected() {
        ToolDefinition def = new ToolDefinition("sample", "act", RiskLevel.L1, "x", "VOID",
                java.util.List.of(ToolParam.p("cluster", ToolParam.Type.STRING, true, "c")));
        ToolException ex = assertThrows(ToolException.class,
                () -> ToolExecutor.execute(def, new LinkedHashMap<>(),
                        new InvocationContext(false, false, false)));
        assertEquals(ErrorModel.Code.MISSING_ARGUMENT.name(), ex.getError().getCode());
    }
}
