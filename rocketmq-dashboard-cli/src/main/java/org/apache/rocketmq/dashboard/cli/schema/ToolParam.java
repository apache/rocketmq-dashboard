package org.apache.rocketmq.dashboard.cli.schema;

/**
 * A single parameter declared by a tool. The same declaration drives both the
 * CLI argument parser and the MCP tool input schema, guaranteeing that the two
 * surfaces never drift (RIP-3 signal 7: single source of truth).
 */
public class ToolParam {

    public enum Type {
        STRING, INT, BOOLEAN, OBJECT, LIST
    }

    private final String name;
    private final Type type;
    private final boolean required;
    private final String description;

    public ToolParam(String name, Type type, boolean required, String description) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.description = description;
    }

    public static ToolParam p(String name, Type type, boolean required, String description) {
        return new ToolParam(name, type, required, description);
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    public String getDescription() {
        return description;
    }
}
