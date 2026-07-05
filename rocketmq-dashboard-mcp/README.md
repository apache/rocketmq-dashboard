# RocketMQ Dashboard MCP Server

The RocketMQ Dashboard MCP Server implements the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) standard, enabling LLM applications (such as Claude Desktop, Cursor, and other MCP-compatible clients) to interact with RocketMQ clusters through a secure, structured interface.

## What It Does

The MCP Server exposes RocketMQ management operations as MCP **tools** and **resources**, allowing AI assistants to:

- List, describe, and monitor topics, consumer groups, clusters, brokers, and clients
- Query messages by ID or time range
- View ACL policies
- Detect cluster capabilities
- Perform controlled mutations (create, update) with dry-run previews
- Manage namespaces in RocketMQ 5.0+ Proxy clusters

All operations are classified into three **risk levels** (L1 read-only, L2 controlled mutation, L3 dangerous) with security gates that control what the AI can do.

## Architecture

```
MCP Client (Claude Desktop, etc.)
        |
        | JSON-RPC 2.0 over stdio or SSE
        |
McpServerApplication
    |-- McpProtocolHandler (JSON-RPC dispatcher)
    |       |-- tools/list, tools/call
    |       |-- resources/list, resources/read
    |
    |-- McpToolRegistry (tool execution + mock data)
    |       |-- loads tools from ToolRegistry (CLI module)
    |
    |-- SecurityGate (L1/L2/L3 access control)
    |       |-- L1: silent allow
    |       |-- L2: dry-run with preview
    |       |-- L3: blocked (requires opt-in)
    |
    |-- ResourceProvider (cluster data snapshots)
```

## Prerequisites

- **Java 17+**
- **Maven 3.8+**

## Installation

Build the MCP server module from the project root:

```bash
cd rocketmq-dashboard
mvn clean package -pl rocketmq-dashboard-mcp -am -Dmaven.test.skip=true
```

This produces `rocketmq-dashboard-mcp/target/rocketmq-dashboard-mcp-2.1.1-SNAPSHOT.jar`.

You can also build only the MCP module (it will automatically build its dependency, `rocketmq-dashboard-cli`):

```bash
mvn package -pl rocketmq-dashboard-mcp -am
```

## Running the Server

### stdio Mode (for Claude Desktop and similar MCP clients)

The default mode reads JSON-RPC messages from standard input and writes responses to standard output:

```bash
java -jar rocketmq-dashboard-mcp.jar
# equivalent to:
java -jar rocketmq-dashboard-mcp.jar --transport stdio
```

### SSE Mode (for web-based MCP clients)

SSE mode starts an HTTP server using Server-Sent Events for real-time message delivery:

```bash
java -jar rocketmq-dashboard-mcp.jar --transport sse
# Starts on port 8083 by default

# Custom port:
java -jar rocketmq-dashboard-mcp.jar --transport sse --port 9090
```

In SSE mode, the following endpoints are available:
- `GET /sse` — Subscribe to SSE event stream
- `POST /mcp/message` — Send JSON-RPC requests

### Enabling Dangerous Operations

By default, L3 (dangerous) operations like topic deletion and namespace deletion are blocked. To enable them:

```bash
java -jar rocketmq-dashboard-mcp.jar --enable-dangerous-ops
```

When enabled, L3 operations still require explicit confirmation through the dry-run mechanism.

### Help

```bash
java -jar rocketmq-dashboard-mcp.jar --help
```

## Claude Desktop Configuration

To connect the MCP server to Claude Desktop, add the following to your `claude_desktop_config.json`:

### Windows

```json
{
  "mcpServers": {
    "rocketmq": {
      "command": "java",
      "args": [
        "-jar",
        "C:\\path\\to\\rocketmq-dashboard-mcp-2.1.1-SNAPSHOT.jar",
        "--transport",
        "stdio"
      ]
    }
  }
}
```

### macOS / Linux

```json
{
  "mcpServers": {
    "rocketmq": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/rocketmq-dashboard-mcp-2.1.1-SNAPSHOT.jar",
        "--transport",
        "stdio"
      ]
    }
  }
}
```

### With Dangerous Operations Enabled

```json
{
  "mcpServers": {
    "rocketmq": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/rocketmq-dashboard-mcp-2.1.1-SNAPSHOT.jar",
        "--transport",
        "stdio",
        "--enable-dangerous-ops"
      ]
    }
  }
}
```

After updating the configuration, restart Claude Desktop. The RocketMQ tools will appear in the MCP tools list (accessible via the tools icon in the chat interface).

## SSE Mode Configuration for Web Clients

For web-based MCP clients that connect via SSE:

1. Start the server in SSE mode:
   ```bash
   java -jar rocketmq-dashboard-mcp.jar --transport sse --port 8083
   ```

2. Configure your web client to connect to:
   - SSE endpoint: `http://localhost:8083/sse`
   - Message endpoint: `http://localhost:8083/mcp/message`

The SSE endpoint sends an initial `endpoint` event with the message POST URL, following the MCP SSE transport specification.

## Tool List

The MCP server exposes **30 tools** across 10 resource domains. All tool schemas are defined in the CLI module's `ToolRegistry` as the single source of truth.

### Cluster (2 tools)

| Tool | Risk | Description |
|------|------|-------------|
| `rmq.cluster.list` | L1 | List all clusters in the RocketMQ deployment |
| `rmq.cluster.describe` | L1 | Get detailed information about a specific cluster |

### Namespace (3 tools)

| Tool | Risk | Description |
|------|------|-------------|
| `rmq.namespace.list` | L1 | List all namespaces |
| `rmq.namespace.create` | L2 | Create a new namespace |
| `rmq.namespace.delete` | L3 | Delete an existing namespace |

### Topic (5 tools)

| Tool | Risk | Description |
|------|------|-------------|
| `rmq.topic.list` | L1 | List all topics, optionally filtered by namespace or topic type |
| `rmq.topic.describe` | L1 | Get detailed information about a specific topic |
| `rmq.topic.create` | L2 | Create a new topic with specified configuration |
| `rmq.topic.update` | L2 | Update configuration of an existing topic |
| `rmq.topic.delete` | L3 | Delete a topic and all its messages |

### Consumer Group (6 tools)

| Tool | Risk | Description |
|------|------|-------------|
| `rmq.group.list` | L1 | List all consumer groups, optionally filtered |
| `rmq.group.describe` | L1 | Get detailed information about a specific consumer group |
| `rmq.group.create` | L2 | Create a new consumer group |
| `rmq.group.update` | L2 | Update configuration of an existing consumer group |
| `rmq.group.reset_offset` | L2 | Reset consumer offset for a group-topic pair |
| `rmq.group.delete` | L3 | Delete a consumer group |

### Message (3 tools)

| Tool | Risk | Description |
|------|------|-------------|
| `rmq.message.query_by_id` | L1 | Query a message by its message ID |
| `rmq.message.query_by_time` | L1 | Query messages within a time range |
| `rmq.message.resend` | L2 | Resend a message to a consumer group |

### Client (2 tools)

| Tool | Risk | Description |
|------|------|-------------|
| `rmq.client.list` | L1 | List all connected clients, optionally filtered |
| `rmq.client.describe` | L1 | Get detailed information about a specific client |

### ACL (4 tools)

| Tool | Risk | Description |
|------|------|-------------|
| `rmq.acl.list` | L1 | List all ACL policies, optionally filtered by namespace |
| `rmq.acl.create` | L2 | Create a new ACL policy |
| `rmq.acl.update` | L2 | Update an existing ACL policy |
| `rmq.acl.delete` | L3 | Delete an ACL policy |

### Broker (3 tools)

| Tool | Risk | Description |
|------|------|-------------|
| `rmq.broker.list` | L1 | List all brokers in the cluster |
| `rmq.broker.describe` | L1 | Get detailed information about a specific broker |
| `rmq.broker.config` | L2 | Get or update broker configuration |

### Metrics (1 tool)

| Tool | Risk | Description |
|------|------|-------------|
| `rmq.metrics.query` | L1 | Query metrics for the specified target |

### Capabilities (1 tool)

| Tool | Risk | Description |
|------|------|-------------|
| `rmq.capabilities.detect` | L1 | Detect capabilities and features of the connected cluster |

## Risk Levels and Security

The MCP server enforces a three-tier risk classification for all operations:

### L1 — Read-only (Safe)

- **Default behavior:** Allowed silently. The AI can call these tools without any confirmation.
- **Examples:** `rmq.topic.list`, `rmq.cluster.describe`, `rmq.client.list`
- **Count:** 16 tools

### L2 — Controlled Mutation

- **Default behavior:** Dry-run with preview. The AI receives a preview of what would happen (`willExecute: false`, `confirmationRequired: true`) but no actual changes are made. The user must explicitly confirm before changes are applied.
- **Examples:** `rmq.topic.create`, `rmq.group.update`, `rmq.acl.create`
- **Count:** 10 tools

### L3 — Dangerous Operations

- **Default behavior:** Blocked. These operations are completely refused unless the MCP server was started with `--enable-dangerous-ops`.
- **Examples:** `rmq.topic.delete`, `rmq.group.delete`, `rmq.namespace.delete`, `rmq.acl.delete`
- **Count:** 4 tools

### Enabling L3 Operations

```bash
java -jar rocketmq-dashboard-mcp.jar --enable-dangerous-ops
```

When L3 is enabled, dangerous operations still run in dry-run mode with an explicit warning message. The user must confirm each operation.

## MCP Resources

The server also exposes three read-only data resources that provide cluster snapshots:

| URI | Name | Description |
|-----|------|-------------|
| `rmq://topics` | RocketMQ Topics | Snapshot of all topics and their configurations |
| `rmq://groups` | RocketMQ Consumer Groups | Snapshot of all consumer groups and their status |
| `rmq://clients` | RocketMQ Clients | Snapshot of all connected clients and their metadata |

Resources are accessed via the MCP `resources/list` and `resources/read` methods.

## Security Considerations

### Production Deployment

1. **Start without `--enable-dangerous-ops`** unless you have a specific need for destructive operations through AI tools.
2. **Use SSE mode** for production deployments behind a reverse proxy with TLS termination.
3. **Never expose the MCP server** to the public internet without authentication. The MCP protocol itself does not include authentication — use a reverse proxy (nginx, Caddy) with basic auth or mTLS.
4. **Audit logging:** All L2 and L3 operations produce structured log entries with the tool name, arguments, and security decision.

### Multi-Cluster Access

The MCP server itself does not connect to a live RocketMQ cluster by default. It returns mock/simulated data for demonstration and testing purposes. For production use with live clusters, the server should be configured with valid RocketMQ NameServer or Proxy addresses via the shared application configuration.

## Troubleshooting

### The JAR file won't start

**Symptom:** `Error: Unable to access jarfile` or `Could not find or load main class`

**Solution:** Ensure you built the MCP module with its dependencies:
```bash
mvn clean package -pl rocketmq-dashboard-mcp -am
```
The `-am` flag (also-make) ensures the `rocketmq-dashboard-cli` dependency is built first.

### Java version error

**Symptom:** `Unsupported class file major version` errors

**Solution:** The MCP server requires **Java 17+**. Verify your Java version:
```bash
java -version
```
Expected output should show version 17 or higher.

### Claude Desktop cannot find the tools

**Symptom:** The tools do not appear in Claude Desktop's MCP tools list.

**Solutions:**
1. Verify the `claude_desktop_config.json` path is correct and uses absolute paths.
2. Check Claude Desktop logs for MCP connection errors.
3. Ensure the JAR file path in the config is an **absolute path**, not relative.
4. Try running the server manually from a terminal to verify it starts:
   ```bash
   java -jar /path/to/rocketmq-dashboard-mcp.jar --transport stdio
   ```
   Then type `{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}` and press Enter. You should receive a JSON-RPC response.

### SSE connections fail

**Symptom:** Cannot connect to SSE endpoint or messages are not delivered.

**Solutions:**
1. Verify the port is not in use: `netstat -an | grep 8083`
2. Check that `--transport sse` was specified (not the default stdio mode).
3. For CORS issues in browser-based clients, you may need to configure a reverse proxy.

### Tool not found error

**Symptom:** MCP client reports "Tool not found: rmq.some.tool"

**Solutions:**
1. Check the exact tool name spelling. Tool names use dots as separators: `rmq.topic.list`.
2. Tools with multi-word verbs use underscores in MCP: use `rmq.message.query_by_id` not `rmq.message.query-by-id`.
3. Both underscore and hyphen forms are accepted — the server normalizes underscores to hyphens before lookup.

### L3 operations return "blocked"

**Symptom:** Calling a delete tool returns `"status": "blocked"` with a hint message.

**Solution:** This is by design. L3 operations (delete topic, delete group, delete namespace, delete ACL) require the `--enable-dangerous-ops` flag when starting the server. Restart with:
```bash
java -jar rocketmq-dashboard-mcp.jar --enable-dangerous-ops
```

### Build fails with missing dependency

**Symptom:** Maven build fails with `Could not find artifact org.apache.rocketmq:rocketmq-dashboard-cli`

**Solution:** Build with the `-am` flag to include required modules:
```bash
mvn clean package -pl rocketmq-dashboard-mcp -am
```

## MCP Protocol Details

- **Protocol version:** MCP 2024-11-05
- **Transport:** JSON-RPC 2.0
- **Server name:** `rocketmq-dashboard-mcp`
- **Server version:** `2.1.1-SNAPSHOT`
- **Capabilities:** Tools (listChanged: false), Resources (listChanged: false, subscribe: false)

## License

Apache License, Version 2.0
