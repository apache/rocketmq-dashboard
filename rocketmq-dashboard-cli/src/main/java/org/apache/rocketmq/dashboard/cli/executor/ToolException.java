package org.apache.rocketmq.dashboard.cli.executor;

/**
 * Carries a structured {@link ErrorModel} out of the executor so the MCP / CLI
 * layers can render a consistent, machine-readable error (RIP-3 signal 6).
 */
public class ToolException extends Exception {

    private final ErrorModel error;

    public ToolException(ErrorModel error) {
        super(error.getMessage());
        this.error = error;
    }

    public ErrorModel getError() {
        return error;
    }
}
