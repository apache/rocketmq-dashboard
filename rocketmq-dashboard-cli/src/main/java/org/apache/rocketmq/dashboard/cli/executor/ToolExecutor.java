package org.apache.rocketmq.dashboard.cli.executor;

import org.apache.rocketmq.dashboard.cli.schema.ToolDefinition;
import org.apache.rocketmq.dashboard.cli.schema.ToolParam;
import org.apache.rocketmq.dashboard.cli.schema.ToolRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatches a tool invocation to its backing logic and enforces the safety
 * model. Every operational tool is implemented here and keyed by its
 * {@link ToolDefinition#getName()} (e.g. {@code rmq.capabilities.detect}).
 *
 * <p>Resource tools (cluster/topic/group/message/route/dlq/acl/broker/client/
 * metrics) are added in later RIP-3 PRs that build on this skeleton.</p>
 */
public final class ToolExecutor {

    private ToolExecutor() {
    }

    public static Map<String, Object> execute(ToolDefinition tool, Map<String, Object> arguments,
                                              InvocationContext ctx) throws ToolException {
        if (tool == null) {
            throw new ToolException(ErrorModel.of(ErrorModel.Code.UNKNOWN_TOOL,
                    "No such tool.", "Run `rmqctl explain` to list available tools."));
        }

        Map<String, Object> args = arguments == null ? new LinkedHashMap<>() : arguments;

        // Static enumerations / meta-tools need no live cluster connection.
        if ("rmq.capabilities.detect".equals(tool.getName())) {
            return detectCapabilities();
        }

        // Fail fast on missing required arguments before paying the connection cost.
        validateRequiredArguments(tool, args);

        SecurityGate.Decision decision = SecurityGate.evaluate(
                tool.getRiskLevel(), ctx.dryRun(), ctx.dangerousOpsEnabled(), ctx.confirmed());
        switch (decision) {
            case DENIED:
                throw new ToolException(ErrorModel.of(ErrorModel.Code.SECURITY_DENIED,
                        "Operation denied by the security gate.", SecurityGate.denialHint(tool.getRiskLevel())));
            case REQUIRES_CONFIRMATION:
                throw new ToolException(ErrorModel.of(ErrorModel.Code.CONFIRMATION_REQUIRED,
                        "Confirmation required before applying this operation.",
                        SecurityGate.denialHint(tool.getRiskLevel())));
            case DRY_RUN:
                return dryRun(tool, args);
            case APPLY:
            default:
                return apply(tool, args);
        }
    }

    private static Map<String, Object> detectCapabilities() {
        Map<String, List<Map<String, Object>>> byResource = new LinkedHashMap<>();
        for (ToolDefinition def : ToolRegistry.getInstance().getAllTools()) {
            byResource.computeIfAbsent(def.getResource(), k -> new ArrayList<>())
                    .add(Map.of(
                            "name", def.getName(),
                            "risk", def.getRiskLevel().name(),
                            "description", def.getDescription(),
                            "returnType", def.getReturnType()));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", byResource);
        result.put("count", ToolRegistry.getInstance().getAllTools().size());
        return result;
    }

    private static void validateRequiredArguments(ToolDefinition tool, Map<String, Object> args)
            throws ToolException {
        for (ToolParam p : tool.getParams()) {
            if (p.isRequired() && (args.get(p.getName()) == null
                    || (args.get(p.getName()) instanceof String s && s.isBlank()))) {
                throw new ToolException(ErrorModel.of(ErrorModel.Code.MISSING_ARGUMENT,
                        "Missing required argument: " + p.getName(),
                        p.getDescription()));
            }
        }
    }

    private static Map<String, Object> dryRun(ToolDefinition tool, Map<String, Object> args) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("dryRun", true);
        preview.put("tool", tool.getName());
        preview.put("risk", tool.getRiskLevel().name());
        preview.put("arguments", args);
        preview.put("wouldApply", "This operation would be applied to the live cluster once confirmed.");
        return preview;
    }

    private static Map<String, Object> apply(ToolDefinition tool, Map<String, Object> args)
            throws ToolException {
        // Resource tools are implemented in subsequent RIP-3 PRs.
        throw new ToolException(ErrorModel.of(ErrorModel.Code.INTERNAL_ERROR,
                "Tool not yet implemented: " + tool.getName(),
                "Implemented by a later RIP-3 capability PR."));
    }
}
