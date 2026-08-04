package org.apache.rocketmq.dashboard.cli.executor;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured error returned by every tool invocation (RIP-3 signal 6).
 * Carries a stable {@code code}, a human {@code message} and an actionable
 * {@code hint} so the caller (CLI, MCP client or LLM bridge) can recover.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorModel {

    public enum Code {
        BAD_REQUEST,
        MISSING_ARGUMENT,
        UNKNOWN_TOOL,
        CLUSTER_UNREACHABLE,
        SECURITY_DENIED,
        CONFIRMATION_REQUIRED,
        INTERNAL_ERROR
    }

    private final String code;
    private final String message;
    private final String hint;

    public ErrorModel(Code code, String message, String hint) {
        this.code = code.name();
        this.message = message;
        this.hint = hint;
    }

    public static ErrorModel of(Code code, String message, String hint) {
        return new ErrorModel(code, message, hint);
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getHint() {
        return hint;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("message", message);
        if (hint != null) {
            m.put("hint", hint);
        }
        return m;
    }
}
