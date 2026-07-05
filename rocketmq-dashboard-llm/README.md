# RocketMQ LLM Bridge

Backend service for the Console embedded LLM chat feature. Part of the [RocketMQ Control Plane 5.0](../RIP-Control-Plane-5.0.md) (RIP-3 Phase 3).

Bridges the Web Console frontend to LLM providers (OpenAI, Azure, DeepSeek, etc.), proxying MCP tool calls with security gates and audit logging.

## Quick Start

```bash
# Build
cd rocketmq-dashboard
mvn package -pl rocketmq-dashboard-llm -DskipTests

# Run
java -jar rocketmq-dashboard-llm/target/rocketmq-dashboard-llm-2.1.1-SNAPSHOT.jar
```

Default port: **8084**.

## Configuration

LLM provider settings stored at `~/.rmqctl/llm-config.yaml` (managed via REST API or settings page):

```yaml
provider: OPENAI
apiKey: "sk-..."
apiBase: "https://api.openai.com/v1"
model: "gpt-4"
maxTokens: 4096
temperature: 0.0
enabled: true
```

## Supported Providers

| Provider | Default API Base |
|----------|-----------------|
| OpenAI | `https://api.openai.com/v1` |
| Azure OpenAI | `{custom}/openai/deployments/{model}` |
| DeepSeek | `https://api.deepseek.com/v1` |
| Tongyi (通义千问) | `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| AWS Bedrock | `https://bedrock-runtime.{region}.amazonaws.com` |
| Ollama | `http://localhost:11434` |

## REST API

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/llm/tools?cluster=xxx` | Filtered tool list for current user + cluster |
| `POST` | `/api/llm/chat` | Send chat message, returns response with tool calls |
| `POST` | `/api/llm/confirm` | Execute a dry-run confirmed action |
| `GET` | `/api/llm/config` | Get provider configuration (API key masked) |
| `POST` | `/api/llm/config` | Save provider configuration |
| `POST` | `/api/llm/config/test` | Test provider connection |

## Safety Flow

```
User Input → POST /api/llm/chat
    → ToolFilter (permission + capability filtering)
    → LlmProxyService (LLM API call with function definitions)
    → LLM returns tool_call
    → SecurityGate:
        L1: auto-execute → return result
        L2: return dry-run card → user confirms → POST /api/llm/confirm → execute
        L3: reject with hint → guide to manual detail page
    → LlmAuditLogger (source=console-llm)
```

## Architecture

```
Frontend (ChatPanel / CommandBar)
    ↓ HTTP/SSE
McpBridgeController (/api/llm/*)
    ↓
LlmProxyService → LLM Provider API
    ↓ tool calls
ToolRegistry (from CLI module) → SecurityGate → MetadataProvider
```

## Security

- API keys stored server-side only (`~/.rmqctl/llm-config.yaml`)
- `GET /api/llm/config` returns masked key (first 4 + last 4 chars)
- All LLM-triggered write ops audit-logged with `source=console-llm`
- L3 dangerous operations default-blocked regardless of LLM output
- No API keys exposed to frontend — all requests proxied through backend

## Degradation

| Scenario | Behavior |
|----------|----------|
| No LLM configured | Frontend CommandBar degrades to global search |
| LLM call fails | Returns manual operation guide link |
| Provider unreachable | Returns error with troubleshooting hints |
