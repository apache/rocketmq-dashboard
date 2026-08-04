package org.apache.rocketmq.dashboard.cli.schema;

import java.util.Collections;
import java.util.List;

/**
 * Definition of one operational tool exposed uniformly as both an MCP tool
 * ({@code rmq.<resource>.<verb>}) and a CLI command ({@code rmqctl <resource> <verb>}).
 *
 * <p>This is the single source of truth (RIP-3 signal 7): adding a tool here
 * automatically makes it available over MCP and as a CLI sub-command, with no
 * duplicated help text or schema.</p>
 */
public class ToolDefinition {

    private final String resource;
    private final String verb;
    private final String name;
    private final RiskLevel riskLevel;
    private final String description;
    private final String returnType;
    private final List<ToolParam> params;

    public ToolDefinition(String resource, String verb, RiskLevel riskLevel,
                          String description, String returnType, List<ToolParam> params) {
        this.resource = resource;
        this.verb = verb;
        this.name = "rmq." + resource + "." + verb;
        this.riskLevel = riskLevel;
        this.description = description;
        this.returnType = returnType;
        this.params = params == null ? Collections.emptyList() : params;
    }

    public static Builder def(String resource, String verb, RiskLevel riskLevel,
                              String description, String returnType, ToolParam... params) {
        return new Builder(resource, verb, riskLevel, description, returnType, params);
    }

    public String getResource() {
        return resource;
    }

    public String getVerb() {
        return verb;
    }

    public String getName() {
        return name;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public String getDescription() {
        return description;
    }

    public String getReturnType() {
        return returnType;
    }

    public List<ToolParam> getParams() {
        return params;
    }

    public ToolParam getParam(String name) {
        for (ToolParam p : params) {
            if (p.getName().equals(name)) {
                return p;
            }
        }
        return null;
    }

    /** Fluent builder that flattens the varargs parameter list. */
    public static class Builder {
        private final String resource;
        private final String verb;
        private final RiskLevel riskLevel;
        private final String description;
        private final String returnType;
        private final List<ToolParam> params;

        Builder(String resource, String verb, RiskLevel riskLevel,
                String description, String returnType, ToolParam... params) {
            this.resource = resource;
            this.verb = verb;
            this.riskLevel = riskLevel;
            this.description = description;
            this.returnType = returnType;
            this.params = List.of(params);
        }

        public ToolDefinition build() {
            return new ToolDefinition(resource, verb, riskLevel, description, returnType, params);
        }
    }
}
