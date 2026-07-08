# RocketMQ Prometheus Alert Rules

This directory contains production-ready [Prometheus](https://prometheus.io/) alerting rule
definitions for Apache RocketMQ 5.x.

## Files

| File | Description |
|---|---|
| `rocketmq-alerts.yaml` | 22 alert rules covering broker availability, consumer lag, throughput anomalies, connection issues, resource saturation, transaction health, and message size. |

## Metric Source

All metric names and labels are sourced from the official OpenTelemetry-based exporter
embedded in RocketMQ broker and proxy since version 5.x:

- **Broker metrics**: `broker/src/main/java/org/apache/rocketmq/broker/metrics/BrokerMetricsConstant.java`
- **Proxy metrics**: `proxy/src/main/java/org/apache/rocketmq/proxy/metrics/ProxyMetricsConstant.java`

To enable the built-in Prometheus endpoint on the broker, set the following in `broker.conf`:

```properties
metricsExporterType=PROM
```

This exposes metrics at `http://<broker-host>:5557/metrics`.

For the proxy, set in `rmq-proxy.json`:

```json
{
  "metricsExporterType": "PROM"
}
```

## Alert Categories

| Category | # Rules | Key Metrics |
|---|---|---|
| Broker Availability | 2 | `rocketmq_broker_permission`, `rocketmq_processor_watermark` |
| Proxy Availability | 2 | `rocketmq_proxy_up` |
| Consumer Lag | 5 | `rocketmq_consumer_lag_messages`, `rocketmq_consumer_lag_latency`, `rocketmq_consumer_inflight_messages`, `rocketmq_consumer_queueing_latency` |
| Throughput Anomalies | 3 | `rocketmq_messages_in_total`, `rocketmq_messages_out_total`, `rocketmq_send_to_dlq_messages_total` |
| Connection Anomalies | 2 | `rocketmq_producer_connections`, `rocketmq_consumer_connections` |
| Resource Saturation | 2 | `rocketmq_topic_number`, `rocketmq_consumer_group_number` |
| Transaction Health | 2 | `rocketmq_half_messages`, `rocketmq_rollback_messages_total` |
| Message Size | 1 | `rocketmq_message_size` |
| Throughput Bandwidth | 2 | `rocketmq_throughput_in_total`, `rocketmq_throughput_out_total` |
| Transaction Latency | 1 | `rocketmq_finish_message_latency` |

## Loading Alert Rules

### Option 1: Via `prometheus.yml`

```yaml
rule_files:
  - /path/to/rocketmq-alerts.yaml
```

Restart or reload Prometheus:

```bash
curl -X POST http://localhost:9090/-/reload
```

### Option 2: Via Docker

Mount the rules file and reference it in `prometheus.yml`:

```yaml
# docker-compose.yml
services:
  prometheus:
    image: prom/prometheus:latest
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - ./rocketmq-alerts.yaml:/etc/prometheus/rocketmq-alerts.yaml
    ports:
      - "9090:9090"
```

### Option 3: Via Helm (kube-prometheus-stack)

```bash
kubectl create configmap rocketmq-alerts --from-file=rocketmq-alerts.yaml -n monitoring
kubectl annotate configmap rocketmq-alerts prometheus.io/rules=true -n monitoring
```

Or add to your `values.yaml`:

```yaml
prometheusSpec:
  ruleFiles:
    rocketmq-alerts: |-
      <contents of rocketmq-alerts.yaml>
```

## Customizing Thresholds

All thresholds are defined as generic defaults. Adjust them according to your
specific workload and SLA requirements:

| Alert | Default Threshold | Label for Customization |
|---|---|---|
| `RocketMQConsumerLagMessagesHigh` | > 10 000 | Filter by `topic` or `consumer_group` |
| `RocketMQConsumerLagMessagesCritical` | > 100 000 | Filter by `topic` or `consumer_group` |
| `RocketMQConsumerLagLatencyHigh` | > 10 min (600 000 ms) | Filter by `topic` or `consumer_group` |
| `RocketMQDLQRateHigh` | > 10 msgs/s | Filter by `consumer_group` |
| `RocketMQInboundBandwidthHigh` | > 100 MB/s | Filter by `node_id` |
| `RocketMQTopicCountHigh` | > 10 000 | Per broker |

Example — only alert on a specific topic:

```yaml
expr: rocketmq_consumer_lag_messages{topic="my-important-topic"} > 5000
```

## Severity Levels

- **critical**: Immediate action required — broker/proxy down, severe backlog,
  permission degraded.
- **warning**: Investigate soon — performance degradation, connection loss,
  accumulating resources.

## Integration with Alertmanager

Recommended `alertmanager.yml` route:

```yaml
route:
  group_by: ['alertname', 'cluster', 'node_id']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  receiver: 'default'

receivers:
  - name: 'default'
    # Configure your notification channel (Slack, PagerDuty, email, etc.)
```
