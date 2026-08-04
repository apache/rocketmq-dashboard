package org.apache.rocketmq.dashboard.cli.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Entry point for the MCP server. Supports two transports (RIP-3 requirement):
 * <ul>
 *   <li>{@code stdio} - JSON-RPC messages exchanged line-by-line on stdin/stdout
 *       (the default, used by Claude Desktop / Cursor / etc.).</li>
 *   <li>{@code sse} - a lightweight HTTP transport: {@code GET /sse} announces the
 *       {@code POST /messages} endpoint and keeps the stream alive, while
 *       {@code POST /messages} accepts JSON-RPC requests and returns the response
 *       directly (simplified Streamable-HTTP style).</li>
 * </ul>
 */
public class McpServerApplication {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final McpProtocolHandler HANDLER = new McpProtocolHandler();

    public static void run(String[] args) throws Exception {
        String transport = "stdio";
        int port = 8080;
        for (int i = 0; i < args.length; i++) {
            if ("--transport".equals(args[i]) && i + 1 < args.length) {
                transport = args[++i];
            } else if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            }
        }

        if ("sse".equalsIgnoreCase(transport)) {
            startHttp(port);
        } else {
            startStdio();
        }
    }

    private static void startStdio() throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> request = MAPPER.readValue(line, Map.class);
                Map<String, Object> response = HANDLER.handle(request);
                out.setLength(0);
                out.append(MAPPER.writeValueAsString(response));
                System.out.println(out);
                System.out.flush();
            } catch (Exception e) {
                Map<String, Object> err = new java.util.LinkedHashMap<>();
                err.put("jsonrpc", "2.0");
                err.put("id", null);
                Map<String, Object> e2 = new java.util.LinkedHashMap<>();
                e2.put("code", -32700);
                e2.put("message", "Parse error: " + e.getMessage());
                err.put("error", e2);
                System.out.println(MAPPER.writeValueAsString(err));
                System.out.flush();
            }
        }
    }

    private static void startHttp(int port) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/messages", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            byte[] body = exchange.getRequestBody().readAllBytes();
            String text = new String(body, StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> request = MAPPER.readValue(text, Map.class);
            Map<String, Object> response = HANDLER.handle(request);
            byte[] resp = MAPPER.writeValueAsBytes(response);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });

        server.createContext("/sse", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                String endpoint = "event: endpoint\ndata: /messages\n\n";
                os.write(endpoint.getBytes(StandardCharsets.UTF_8));
                os.flush();
                // Keep the stream alive with periodic comments.
                for (int i = 0; i < 30; i++) {
                    os.write(": keep-alive\n\n".getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    Thread.sleep(5000);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });

        server.start();
        System.out.println("MCP HTTP transport listening on http://localhost:" + port
                + " (SSE: /sse, JSON-RPC: POST /messages)");
    }
}
