package org.apache.rocketmq.dashboard.cli;

import org.apache.rocketmq.dashboard.cli.schema.ToolDefinition;
import org.apache.rocketmq.dashboard.cli.schema.ToolParam;
import org.apache.rocketmq.dashboard.cli.schema.ToolRegistry;

import java.util.List;

/**
 * {@code rmqctl explain <resource>} - prints the tools available for a resource
 * together with their parameters. The text is generated from {@link ToolRegistry}
 * (the single source of truth), so it is always in sync with the MCP surface.
 */
public final class ExplainCommand {

    private ExplainCommand() {
    }

    public static void run(String resource) {
        ToolRegistry registry = ToolRegistry.getInstance();
        if (resource == null || resource.isBlank()) {
            System.out.println("Available resources: "
                    + String.join(", ", registry.getAllTools().stream()
                    .map(ToolDefinition::getResource).distinct().sorted().toList()));
            System.out.println("Run `rmqctl explain <resource>` for details.");
            return;
        }
        List<ToolDefinition> tools = registry.getToolsByResource(resource);
        if (tools.isEmpty()) {
            System.out.println("No tools found for resource '" + resource + "'.");
            System.out.println("Available resources: "
                    + String.join(", ", registry.getAllTools().stream()
                    .map(ToolDefinition::getResource).distinct().sorted().toList()));
            return;
        }
        System.out.println("Resource: " + resource);
        for (ToolDefinition def : tools) {
            System.out.println();
            System.out.println("  " + def.getName() + "  [" + def.getRiskLevel() + "]");
            System.out.println("    " + def.getDescription());
            if (!def.getParams().isEmpty()) {
                System.out.println("    Parameters:");
                for (ToolParam p : def.getParams()) {
                    System.out.println("      --" + p.getName()
                            + (p.isRequired() ? " (required)" : " (optional)")
                            + ": " + p.getDescription());
                }
            }
        }
    }
}
