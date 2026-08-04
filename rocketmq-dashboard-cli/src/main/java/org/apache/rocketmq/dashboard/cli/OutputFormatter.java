package org.apache.rocketmq.dashboard.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.rocketmq.dashboard.cli.executor.ErrorModel;

import java.util.Map;

/**
 * Renders tool results in the requested output format (RIP-3: every command
 * supports {@code --output json}). Errors are emitted as the same structured
 * {@link ErrorModel} (signal 6).
 */
public final class OutputFormatter {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private OutputFormatter() {
    }

    public static void print(Map<String, Object> result, String format) {
        try {
            String rendered = switch (normalize(format)) {
                case "yaml" -> YAML.writeValueAsString(result);
                case "json" -> JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result);
                default -> toTable(result, 0);
            };
            System.out.println(rendered);
        } catch (Exception e) {
            System.out.println(String.valueOf(result));
        }
    }

    public static void printError(ErrorModel em, String format) {
        Map<String, Object> m = em.toMap();
        try {
            if ("json".equals(normalize(format))) {
                System.err.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(m));
            } else if ("yaml".equals(normalize(format))) {
                System.err.println(YAML.writeValueAsString(m));
            } else {
                System.err.println("Error [" + em.getCode() + "]: " + em.getMessage());
                if (em.getHint() != null) {
                    System.err.println("Hint: " + em.getHint());
                }
            }
        } catch (Exception e) {
            System.err.println("Error [" + em.getCode() + "]: " + em.getMessage());
        }
    }

    private static String toTable(Map<String, Object> map, int indent) {
        StringBuilder sb = new StringBuilder();
        String pad = "  ".repeat(indent);
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map<?, ?> child) {
                sb.append(pad).append(e.getKey()).append(":\n");
                sb.append(toTable(asMap(child), indent + 1));
            } else if (v instanceof java.util.Collection<?> list) {
                sb.append(pad).append(e.getKey()).append(":\n");
                int i = 0;
                for (Object item : list) {
                    if (item instanceof Map<?, ?> child) {
                        sb.append(pad).append("  [").append(i++).append("]:\n");
                        sb.append(toTable(asMap(child), indent + 2));
                    } else {
                        sb.append(pad).append("  - ").append(item).append("\n");
                    }
                }
            } else {
                sb.append(pad).append(e.getKey()).append(": ").append(v).append("\n");
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (Map<String, Object>) o;
    }

    private static String normalize(String format) {
        return format == null ? "table" : format.toLowerCase();
    }
}
