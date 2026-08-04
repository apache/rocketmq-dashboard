package org.apache.rocketmq.dashboard.cli.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for every tool exposed by RIP-3. Each registered tool
 * is automatically available both as an MCP tool ({@code rmq.<resource>.<verb>})
 * and as a CLI command ({@code rmqctl <resource> <verb>}).
 *
 * <p>New capabilities are added by implementing the backing executor in
 * {@code ToolExecutor} and registering the definition here. The CLI help text and
 * the MCP tool schema are generated from these definitions, so the two surfaces
 * can never drift apart (RIP-3 signal 7).</p>
 */
public final class ToolRegistry {

    private static final ToolRegistry INSTANCE = new ToolRegistry();

    private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();

    private ToolRegistry() {
        registerAll();
    }

    public static ToolRegistry getInstance() {
        return INSTANCE;
    }

    private void registerAll() {
        registerCapabilities();
        // Resource tools (topic/group/message/route/dlq/acl/broker/client/metrics)
        // are registered in subsequent RIP-3 PRs building on this skeleton.
    }

    private void registerCapabilities() {
        register(ToolDefinition.def("capabilities", "detect", RiskLevel.L1,
                "List every tool this server exposes, grouped by resource, with its "
                        + "risk level. Use this to discover what the server supports.",
                "LIST",
                ToolParam.p("cluster", ToolParam.Type.STRING, false,
                        "Optional cluster name or address; not required for this meta-tool.")
        ).build());
    }

    public void register(ToolDefinition def) {
        tools.put(def.getName(), def);
    }

    public ToolDefinition getTool(String name) {
        return tools.get(name);
    }

    public List<ToolDefinition> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    public List<ToolDefinition> getToolsByResource(String resource) {
        List<ToolDefinition> out = new ArrayList<>();
        for (ToolDefinition def : tools.values()) {
            if (def.getResource().equals(resource)) {
                out.add(def);
            }
        }
        return out;
    }

    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }
}
