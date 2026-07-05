# rmqctl — RocketMQ Control Plane CLI

Kubectl-style command-line tool for managing Apache RocketMQ clusters. Part of the [RocketMQ Control Plane 5.0](../RIP-Control-Plane-5.0.md) (RIP-3 Phase 1).

## Quick Start

```bash
# Build
cd rocketmq-dashboard
mvn package -pl rocketmq-dashboard-cli -DskipTests

# Run
java -jar rocketmq-dashboard-cli/target/rocketmq-dashboard-cli-2.1.1-SNAPSHOT.jar

# Or use the shaded JAR directly
java -jar rocketmq-dashboard-cli/target/rocketmq-dashboard-cli-*.jar --help
```

## Configuration

```bash
# Add a cluster
rmqctl config add-cluster prod --namesrv-addr 10.0.0.1:9876

# Set context  
rmqctl config set-context prod-ctx --cluster prod
rmqctl config use-context prod-ctx
```

Config stored at `~/.rmqctl/config.yaml`.

## Command Reference

### Global Options

| Option | Description |
|--------|-------------|
| `--cluster <name>` | Target cluster (overrides current context) |
| `--output table|json|yaml` | Output format (default: table) |
| `--dry-run` | Preview L2 changes without executing |
| `--yes` | Skip confirmation prompts |
| `--force` | Force L3 dangerous operations |
| `--help` | Show help |

### Resources

| Resource | Verbs | Risk |
|----------|-------|------|
| `cluster` | `list`, `describe` | L1 |
| `namespace` | `list`, `create`, `delete` | L1/L2/L3 |
| `topic` | `list`, `describe`, `create`, `update`, `delete` | L1/L2/L3 |
| `group` | `list`, `describe`, `create`, `update`, `reset-offset`, `delete` | L1/L2/L3 |
| `message` | `query-by-id`, `query-by-time`, `resend` | L1/L2 |
| `client` | `list`, `describe` | L1 |
| `acl` | `list`, `create`, `update`, `delete` | L1/L2/L3 |
| `broker` | `list`, `describe`, `config` | L1/L2 |
| `metrics` | `query` | L1 |
| `config` | `get-contexts`, `use-context`, `set-context`, `add-cluster` | — |

### Safety Levels

| Level | Type | Default Behavior |
|-------|------|-----------------|
| **L1** | Read-only | Auto-execute |
| **L2** | Controlled mutation | `--dry-run` preview, `--yes` to apply |
| **L3** | Dangerous | Blocked; `--yes --force` required |

### Examples

```bash
# List topics (table output)
rmqctl topic list --cluster prod

# Create topic with dry-run preview
rmqctl topic create order-pay --type FIFO --queues 16 --dry-run

# JSON output for scripting
rmqctl topic list --output json | jq '.[].topicName'

# Explain a resource
rmqctl explain topic

# Shell completion
source <(rmqctl generate-completion bash)
```

## Architecture

```
rmqctl <resource> <verb> [args]
    │
    ├── Picocli command tree
    ├── ToolRegistry (30 tools, single source of truth)
    ├── CliContext (~/.rmqctl/config.yaml)
    ├── OutputFormatter (table / json / yaml)
    └── Security (DryRunResult + AuditLogger)
```

## Build Variants

- **Shaded JAR**: `mvn package` — single fat JAR, runs anywhere with Java 17+
- **GraalVM Native Image**: `native-image` configs in `src/main/resources/META-INF/native-image/`

## Audit

All write operations logged to `~/.rmqctl/audit/audit-YYYY-MM-DD.log` with timestamp, cluster, command, result, and user.
