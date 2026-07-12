/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the License.  You may obtain a
 * copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.rocketmq.studio.ops.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Real LLM gateway that proxies chat completions to an OpenAI-compatible provider.
 *
 * <p>Configuration is resolved from {@link com.rocketmq.studio.settings.GeneralSettingsVO}
 * (provider / apiKey / model / baseUrl) and streamed back to the client as Server-Sent
 * Events. Supports OpenAI, Azure OpenAI, DeepSeek, Tongyi/Qwen (DashScope), Ollama and
 * any custom OpenAI-compatible base URL. Function/tool calling against the registered
 * MCP tools is forwarded to the frontend for display.
 */
@Slf4j
@Component
@Primary
public class LlmGatewayImpl implements LlmGateway {

    private static final String SYSTEM_PROMPT = String.join("\n",
            "You are RocketMQ Studio AI Assistant, an expert operations assistant for Apache RocketMQ clusters.",
            "You help SREs and platform engineers with: cluster diagnostics, topic/consumer-group management,",
            "message queries and trace analysis, DLQ handling, ACL/permission questions, and capacity planning.",
            "Answer concisely and in the user's language (Chinese if they write Chinese).",
            "When you suggest a management action, describe the exact operation and the resources it affects.",
            "If you lack live cluster data, say so clearly and recommend the relevant Studio page or dry-run action.");

    private static final boolean ENABLE_TOOLS = true;

    private final LlmSettingsResolver settingsResolver;
    private final McpServerRegistry mcpServerRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public LlmGatewayImpl(LlmSettingsResolver settingsResolver, McpServerRegistry mcpServerRegistry) {
        this.settingsResolver = settingsResolver;
        this.mcpServerRegistry = mcpServerRegistry;
    }

    // ─────────────────────────────────────────────────────────────
    // Chat (streaming)
    // ─────────────────────────────────────────────────────────────

    @Override
    public SseEmitter chat(ChatDTO request) {
        LlmConfig cfg = settingsResolver.resolve(null);
        SseEmitter emitter = new SseEmitter(300_000L);

        if (!cfg.configured) {
            streamDegraded(emitter,
                    "AI assistant is not configured yet. Go to Settings -> General -> AI Configuration to add an API Key and model, then try again.");
            return emitter;
        }

        executor.execute(() -> {
            try {
                streamChat(request, cfg, emitter);
            } catch (Exception e) {
                log.error("LLM chat stream failed", e);
                emitError(emitter, "AI request failed: " + e.getMessage());
                emitter.complete();
            }
        });
        return emitter;
    }

    @SuppressWarnings("unchecked")
    private void streamChat(ChatDTO request, LlmConfig cfg, SseEmitter emitter) throws Exception {
        boolean ollama = "ollama".equalsIgnoreCase(cfg.provider);
        String url = buildUrl(cfg);
        String body = buildRequestBody(request, cfg, ollama);

        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        applyAuth(rb, cfg);

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(rb.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to reach LLM provider: " + e.getMessage(), e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String err = readError(response.body());
            emitError(emitter, "LLM provider error (HTTP " + response.statusCode() + "): " + err);
            emitter.complete();
            return;
        }

        Map<Integer, ToolCallAcc> toolAcc = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String payload = line.startsWith("data: ") ? line.substring(6).trim() : line.trim();
                if (payload.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(payload)) {
                    break;
                }
                Map<String, Object> parsed;
                try {
                    parsed = objectMapper.readValue(payload, Map.class);
                } catch (IOException ex) {
                    continue;
                }
                if (ollama) {
                    handleOllamaChunk(parsed, emitter, toolAcc);
                } else {
                    handleOpenAiChunk(parsed, emitter, toolAcc);
                }
            }
        }

        // Flush accumulated tool calls as display events.
        if (!toolAcc.isEmpty()) {
            for (ToolCallAcc acc : toolAcc.values()) {
                Map<String, Object> tc = new LinkedHashMap<>();
                tc.put("name", acc.name);
                try {
                    tc.put("arguments", objectMapper.readValue(acc.arguments.toString(), Map.class));
                } catch (Exception ex) {
                    tc.put("arguments", acc.arguments.toString());
                }
                Map<String, Object> evt = new LinkedHashMap<>();
                evt.put("tool_call", tc);
                emitJson(emitter, "tool", evt);
            }
        }

        emitRaw(emitter, "done", "[DONE]");
        emitter.complete();
    }

    @SuppressWarnings("unchecked")
    private void handleOpenAiChunk(Map<String, Object> parsed, SseEmitter emitter,
                                   Map<Integer, ToolCallAcc> toolAcc) {
        Object choicesObj = parsed.get("choices");
        if (!(choicesObj instanceof List)) {
            return;
        }
        List<Map<String, Object>> choices = (List<Map<String, Object>>) choicesObj;
        if (choices.isEmpty()) {
            return;
        }
        Map<String, Object> choice = choices.get(0);
        Map<String, Object> delta = (Map<String, Object>) choice.get("delta");
        if (delta != null) {
            Object content = delta.get("content");
            if (content != null && !content.toString().isEmpty()) {
                emitContent(emitter, content.toString());
            }
            Object toolCallsObj = delta.get("tool_calls");
            if (toolCallsObj instanceof List) {
                for (Map<String, Object> tc : (List<Map<String, Object>>) toolCallsObj) {
                    Integer index = tc.get("index") instanceof Integer
                            ? (Integer) tc.get("index") : 0;
                    ToolCallAcc acc = toolAcc.computeIfAbsent(index, k -> new ToolCallAcc());
                    Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                    if (fn != null) {
                        if (fn.get("name") != null) {
                            acc.name = fn.get("name").toString();
                        }
                        if (fn.get("arguments") != null) {
                            acc.arguments.append(fn.get("arguments").toString());
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleOllamaChunk(Map<String, Object> parsed, SseEmitter emitter,
                                   Map<Integer, ToolCallAcc> toolAcc) {
        Map<String, Object> message = (Map<String, Object>) parsed.get("message");
        if (message != null) {
            Object content = message.get("content");
            if (content != null && !content.toString().isEmpty()) {
                emitContent(emitter, content.toString());
            }
            Object toolCallsObj = message.get("tool_calls");
            if (toolCallsObj instanceof List) {
                List<Map<String, Object>> calls = (List<Map<String, Object>>) toolCallsObj;
                for (int i = 0; i < calls.size(); i++) {
                    ToolCallAcc acc = toolAcc.computeIfAbsent(i, k -> new ToolCallAcc());
                    Map<String, Object> fn = (Map<String, Object>) calls.get(i).get("function");
                    if (fn != null) {
                        if (fn.get("name") != null) {
                            acc.name = fn.get("name").toString();
                        }
                        if (fn.get("arguments") != null) {
                            acc.arguments.append(fn.get("arguments").toString());
                        }
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Execute (one-shot, non-streaming)
    // ─────────────────────────────────────────────────────────────

    @Override
    public String execute(AiCommandDTO command) {
        LlmConfig cfg = settingsResolver.resolve(null);
        if (!cfg.configured) {
            return "AI is not configured. Please configure a model in Settings first.";
        }
        try {
            String url = buildUrl(cfg);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", cfg.model);
            body.put("stream", false);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content",
                            command.getCommand() != null ? command.getCommand()
                                    : command.getPrompt() != null ? command.getPrompt() : "")
            ));
            String json = objectMapper.writeValueAsString(body);
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .timeout(Duration.ofMinutes(2))
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            applyAuth(rb, cfg);
            HttpResponse<String> resp = httpClient.send(rb.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return "Execution failed (HTTP " + resp.statusCode() + "): " + resp.body();
            }
            return extractContent(resp.body());
        } catch (Exception e) {
            log.error("AI execute failed", e);
            return "Execution failed: " + e.getMessage();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Connection test
    // ─────────────────────────────────────────────────────────────

    @Override
    public Map<String, Object> testConnection(AiLlmConfigDTO config) {
        LlmConfig cfg = settingsResolver.resolve(config);
        Map<String, Object> result = new LinkedHashMap<>();
        if (!cfg.configured) {
            result.put("success", false);
            result.put("message", "Missing API Key, cannot test connection.");
            return result;
        }
        try {
            String url = buildUrl(cfg);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", cfg.model);
            body.put("stream", false);
            body.put("messages", List.of(
                    Map.of("role", "user", "content", "ping"),
                    Map.of("role", "system", "content", "Reply with the single word: pong")
            ));
            String json = objectMapper.writeValueAsString(body);
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            applyAuth(rb, cfg);
            HttpResponse<String> resp = httpClient.send(rb.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                result.put("success", true);
                result.put("message", "Connection successful (" + cfg.provider + " / " + cfg.model + ")");
            } else {
                result.put("success", false);
                result.put("message", "Connection failed (HTTP " + resp.statusCode() + "): "
                        + truncate(resp.body()));
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Connection failed: " + e.getMessage());
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private String buildUrl(LlmConfig cfg) {
        String provider = cfg.provider == null ? "openai" : cfg.provider.toLowerCase();
        String base = cfg.baseUrl == null ? "" : cfg.baseUrl.trim().replaceAll("/+$", "");
        switch (provider) {
            case "azure":
                String az = base.isEmpty() ? "" : base;
                return az + "/openai/deployments/" + cfg.model + "/chat/completions?api-version=2024-02-15-preview";
            case "deepseek":
                return base.isEmpty() ? "https://api.deepseek.com/v1/chat/completions" : base + "/chat/completions";
            case "qwen":
            case "dashscope":
            case "tongyi":
                return base.isEmpty() ? "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
                        : base + "/chat/completions";
            case "ollama":
                return base.isEmpty() ? "http://localhost:11434/api/chat" : base + "/api/chat";
            case "openai":
            default:
                return base.isEmpty() ? "https://api.openai.com/v1/chat/completions" : base + "/chat/completions";
        }
    }

    private void applyAuth(HttpRequest.Builder rb, LlmConfig cfg) {
        String provider = cfg.provider == null ? "openai" : cfg.provider.toLowerCase();
        if ("azure".equals(provider)) {
            rb.header("api-key", cfg.apiKey);
        } else if ("ollama".equals(provider)) {
            if (cfg.apiKey != null && !cfg.apiKey.isEmpty()) {
                rb.header("Authorization", "Bearer " + cfg.apiKey);
            }
        } else {
            rb.header("Authorization", "Bearer " + cfg.apiKey);
        }
    }

    private String buildRequestBody(ChatDTO request, LlmConfig cfg, boolean ollama) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.model);
        body.put("stream", true);
        body.put("temperature", 0.4);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        if (request.getHistory() != null) {
            for (Map<String, String> h : request.getHistory()) {
                String role = h.get("role");
                String text = h.get("content");
                if (role != null && text != null) {
                    messages.add(Map.of("role", role, "content", text));
                }
            }
        }
        messages.add(Map.of("role", "user", "content",
                request.getMessage() == null ? "" : request.getMessage()));
        body.put("messages", messages);

        if (ENABLE_TOOLS && !ollama) {
            List<AiToolVO> tools = mcpServerRegistry.listTools();
            if (tools != null && !tools.isEmpty()) {
                List<Map<String, Object>> toolDefs = tools.stream()
                        .map(t -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("type", "function");
                            Map<String, Object> fn = new LinkedHashMap<>();
                            fn.put("name", t.getName());
                            fn.put("description", t.getDescription());
                            Object params = t.getParameters();
                            fn.put("parameters", params != null ? params
                                    : Map.of("type", "object", "properties", Map.of()));
                            m.put("function", fn);
                            return m;
                        })
                        .collect(Collectors.toList());
                body.put("tools", toolDefs);
                body.put("tool_choice", "auto");
            }
        }
        return objectMapper.writeValueAsString(body);
    }

    @SuppressWarnings("unchecked")
    private String extractContent(String responseBody) {
        try {
            Map<String, Object> resp = objectMapper.readValue(responseBody, Map.class);
            Object choicesObj = resp.get("choices");
            if (choicesObj instanceof List && !((List<?>) choicesObj).isEmpty()) {
                Map<String, Object> choice = (Map<String, Object>) ((List<?>) choicesObj).get(0);
                Map<String, Object> message = (Map<String, Object>) choice.get("message");
                if (message != null && message.get("content") != null) {
                    return message.get("content").toString();
                }
            }
            return responseBody;
        } catch (IOException e) {
            return responseBody;
        }
    }

    private String readError(InputStream body) {
        try {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<unreadable>";
        }
    }

    private String truncate(String s) {
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    // ── SSE emit helpers ──

    private void streamDegraded(SseEmitter emitter, String text) {
        executor.execute(() -> {
            try {
                for (String token : text.split("(?<=\\n)| ")) {
                    emitContent(emitter, token);
                }
                emitRaw(emitter, "done", "[DONE]");
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
    }

    private void emitContent(SseEmitter emitter, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("content", text);
        emitJson(emitter, "message", m);
    }

    private void emitError(SseEmitter emitter, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        emitJson(emitter, "error", m);
    }

    private void emitJson(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(objectMapper.writeValueAsString(data)));
        } catch (IOException e) {
            log.debug("Client disconnected during stream: {}", e.getMessage());
        }
    }

    private void emitRaw(SseEmitter emitter, String name, String raw) {
        try {
            emitter.send(SseEmitter.event().name(name).data(raw));
        } catch (IOException e) {
            log.debug("Client disconnected during stream: {}", e.getMessage());
        }
    }

    // ── Internal types ──

    private static final class ToolCallAcc {
        private String name = "";
        private final StringBuilder arguments = new StringBuilder();
    }
}
