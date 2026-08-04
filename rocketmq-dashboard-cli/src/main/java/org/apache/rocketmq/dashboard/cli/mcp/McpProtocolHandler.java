package org.apache.rocketmq.dashboard.cli.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.dashboard.cli.executor.ErrorModel;
import org.apache.rocketmq.dashboard.cli.executor.InvocationContext;
import org.apache.rocketmq.dashboard.cli.executor.ToolException;
import org.apache.rocketmq.dashboard.cli.executor.ToolExecutor;
import org.apache.rocketmq.dashboard.cli.schema.ToolDefinition;
import org.apache.rocketmq.dashboard.cli.schema.ToolParam;
import org.apache.rocketmq.dashboard.cli.schema.ToolRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal Model Context Protocol (MCP) JSON-RPC 2.0 handler.
 *
 * <p>Exposes the tools declared in {@link ToolRegistry} as MCP tools and routes
 * {@code tools/call} invocations through {@link ToolExecutor}. Because both the
 * MCP tool schema and the CLI argument parser are generated from the same
 * registry, they never drift (RIP-3 signal 7).</p>
 */
public class McpProtocolHandler {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Process one JSON-RPC request and return the JSON-RPC response object. */
    public Map<String, Object> handle(Map<String, Object> request) {
        Object id = request.get("id");
        String method = (String) request.get("method");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params",
                new LinkedHashMap<>());

        try {
            return switch (method) {
                case "initialize" -> ok(id, initializeResult());
                case "tools/list" -> ok(id, toolsList());
                case "tools/call" -> ok(id, toolsCall(params));
                case "ping" -> ok(id, new LinkedHashMap<>());
                default -> error(id, -32601, "Method not found: " + method);
            };
        } catch (ToolException e) {
            ErrorModel em = e.getError();
            Map<String, Object> data = em.toMap();
            return error(id, -32000, em.getMessage(), data);
        } catch (Exception e) {
            return error(id, -32603, "Internal error: " + e.getMessage());
        }
    }

    private Map<String, Object> initializeResult() {
        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", "rocketmq-dashboard-mcp");
        serverInfo.put("version", "1.0.0");

        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", new LinkedHashMap<>());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", "2024-11-05");
        result.put("serverInfo", serverInfo);
        result.put("capabilities", capabilities);
        return result;
    }

    private Map<String, Object> toolsList() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolDefinition def : ToolRegistry.getInstance().getAllTools()) {
            tools.add(toMcpTool(def));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", tools);
        return result;
    }

    private Map<String, Object> toMcpTool(ToolDefinition def) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (ToolParam p : def.getParams()) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", p.getType().name().toLowerCase());
            prop.put("description", p.getDescription());
            properties.put(p.getName(), prop);
            if (p.isRequired()) {
                required.add(p.getName());
            }
        }
        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        inputSchema.put("required", required);

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", def.getName());
        tool.put("description", def.getDescription());
        tool.put("inputSchema", inputSchema);
        return tool;
    }

    private Map<String, Object> toolsCall(Map<String, Object> params) throws ToolException {
        String name = (String) params.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments",
                new LinkedHashMap<>());

        ToolDefinition tool = ToolRegistry.getInstance().getTool(name);

        boolean dryRun = Boolean.parseBoolean(String.valueOf(arguments.getOrDefault("_dryRun", "false")));
        boolean confirmed = Boolean.parseBoolean(String.valueOf(arguments.getOrDefault("_confirmed", "false")));
        boolean dangerous = Boolean.parseBoolean(String.valueOf(arguments.getOrDefault("_dangerous", "false")));
        InvocationContext ctx = new InvocationContext(dryRun, confirmed, dangerous);

        Map<String, Object> result = ToolExecutor.execute(tool, arguments, ctx);

        Map<String, Object> contentItem = new LinkedHashMap<>();
        contentItem.put("type", "text");
        try {
            contentItem.put("text", mapper.writeValueAsString(result));
        } catch (Exception e) {
            contentItem.put("text", String.valueOf(result));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", List.of(contentItem));
        out.put("isError", false);
        return out;
    }

    private Map<String, Object> ok(Object id, Map<String, Object> result) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("result", result);
        return resp;
    }

    private Map<String, Object> error(Object id, int code, String message) {
        return error(id, code, message, null);
    }

    private Map<String, Object> error(Object id, int code, String message, Map<String, Object> data) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);
        if (data != null) {
            err.put("data", data);
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("error", err);
        return resp;
    }
}
