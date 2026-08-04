#!/usr/bin/env python3
"""Generate RocketMQ Prometheus alert rule YAML assets.

Each asset is a standalone Prometheus rule file (one alert per file) covering
broker, consumer, producer, topic, client, proxy and error conditions. The
dashboard loads these as the default alert rule set.
"""
import os

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "alerts")
os.makedirs(OUT_DIR, exist_ok=True)

# (file_slug, alert, group, expr, for_, severity, team, summary, description)
RULES = [
    ("rocketmq-broker-down", "RocketMQBrokerDown", "rocketmq-broker.rules",
     'up{job=~".*rocketmq.*broker.*"} == 0', "1m", "critical", "broker",
     "RocketMQ broker is down", "A RocketMQ broker scrape target has been unavailable for more than 1 minute."),
    ("rocketmq-broker-cpu-high", "RocketMQBrokerCPUHigh", "rocketmq-broker.rules",
     '100 * (1 - avg by(instance)(rate(node_cpu_seconds_total{mode="idle"}[5m]))) > 85', "5m", "warning", "broker",
     "Broker CPU usage high", "Broker CPU usage has stayed above 85% for 5 minutes."),
    ("rocketmq-broker-memory-high", "RocketMQBrokerMemoryHigh", "rocketmq-broker.rules",
     'jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100 > 85', "5m", "warning", "broker",
     "Broker JVM heap high", "Broker JVM heap usage is above 85%."),
    ("rocketmq-broker-disk-high", "RocketMQBrokerDiskHigh", "rocketmq-broker.rules",
     'rocketmq_disk_use_ratio > 85', "5m", "critical", "broker",
     "Broker disk usage high", "Broker disk usage ratio is above 85%."),
    ("rocketmq-broker-replication-lag", "RocketMQBrokerReplicationLag", "rocketmq-broker.rules",
     'rocketmq_broker_replication_fall_behind_bytes > 1073741824', "5m", "warning", "broker",
     "Replication lag high", "Master-slave replication fall-behind is above 1GiB."),
    ("rocketmq-consumer-lag-high", "RocketMQConsumerLagHigh", "rocketmq-consumer.rules",
     'rocketmq_consumer_lag_messages > 100000', "5m", "warning", "consumer",
     "Consumer lag high", "A consumer group lag is above 100000 messages."),
    ("rocketmq-consumer-lag-critical", "RocketMQConsumerLagCritical", "rocketmq-consumer.rules",
     'rocketmq_consumer_lag_messages > 1000000', "5m", "critical", "consumer",
     "Consumer lag critical", "A consumer group lag is above 1 million messages."),
    ("rocketmq-consumer-rebalance", "RocketMQConsumerRebalance", "rocketmq-consumer.rules",
     'increase(rocketmq_consumer_rebalance_times[5m]) > 10', "5m", "warning", "consumer",
     "Frequent consumer rebalances", "More than 10 consumer rebalances happened in 5 minutes."),
    ("rocketmq-consumer-group-empty", "RocketMQConsumerGroupEmpty", "rocketmq-consumer.rules",
     'absent(rocketmq_consumer_lag_messages) == 1', "10m", "info", "consumer",
     "Consumer group missing", "No consumer lag metrics observed for a consumer group."),
    ("rocketmq-producer-latency-high", "RocketMQProducerSendLatencyHigh", "rocketmq-client.rules",
     'rocketmq_producer_send_to_back_rt > 1000', "5m", "warning", "client",
     "Producer send latency high", "Producer send-to-broker latency is above 1000 ms."),
    ("rocketmq-producer-failure", "RocketMQProducerSendFailure", "rocketmq-client.rules",
     'rate(rocketmq_producer_send_failure_count[5m]) > 0', "5m", "critical", "client",
     "Producer send failures", "Producer message send failures have been observed."),
    ("rocketmq-producer-tps-drop", "RocketMQProducerTPSDrop", "rocketmq-client.rules",
     'rate(rocketmq_messages_in_total[5m]) < 0.1 * rate(rocketmq_messages_in_total[1h] offset 1h)', "10m", "warning", "client",
     "Producer TPS dropped", "Ingress TPS dropped by more than 90% compared with one hour ago."),
    ("rocketmq-topic-in-drop", "RocketMQTopicMessageInDrop", "rocketmq-topic.rules",
     'rate(rocketmq_messages_in_total[10m]) == 0', "10m", "info", "topic",
     "No incoming messages", "No incoming messages have been observed for 10 minutes."),
    ("rocketmq-topic-accumulation", "RocketMQTopicAccumulation", "rocketmq-topic.rules",
     'rocketmq_dispatch_behind_bytes > 1073741824', "5m", "warning", "topic",
     "Topic dispatch backlog high", "Topic dispatch behind bytes is above 1GiB."),
    ("rocketmq-topic-dispatch-latency", "RocketMQTopicDispatchLatency", "rocketmq-topic.rules",
     'histogram_quantile(0.99, rate(rocketmq_dispatch_latency_bucket[5m])) > 1', "5m", "warning", "topic",
     "Dispatch latency high", "99th percentile dispatch latency is above 1 second."),
    ("rocketmq-client-connection-drop", "RocketMQClientConnectionDrop", "rocketmq-client.rules",
     'changes(rocketmq_producer_count[5m]) < -5', "5m", "warning", "client",
     "Client connections dropped", "More than 5 producer connections dropped in 5 minutes."),
    ("rocketmq-client-timeout", "RocketMQClientTimeout", "rocketmq-client.rules",
     'rocketmq_send_to_client_latency > 3000', "5m", "warning", "client",
     "Client push latency high", "Push-to-client latency is above 3000 ms."),
    ("rocketmq-proxy-down", "RocketMQProxyDown", "rocketmq-proxy.rules",
     'up{job=~".*rocketmq.*proxy.*"} == 0', "1m", "critical", "proxy",
     "RocketMQ proxy is down", "A RocketMQ 5.x proxy target has been down for more than 1 minute."),
    ("rocketmq-proxy-latency-high", "RocketMQProxyLatencyHigh", "rocketmq-proxy.rules",
     'histogram_quantile(0.99, rate(rocketmq_proxy_process_time_bucket[5m])) > 1', "5m", "warning", "proxy",
     "Proxy latency high", "99th percentile proxy process time is above 1 second."),
    ("rocketmq-exception-rate", "RocketMQBrokerExceptions", "rocketmq-errors.rules",
     'rate(rocketmq_broker_exception_count[5m]) > 0', "5m", "critical", "broker",
     "Broker exceptions", "Broker runtime exceptions have been observed."),
    ("rocketmq-dlq-resend-high", "RocketMQDLQResendHigh", "rocketmq-errors.rules",
     'rate(rocketmq_dlq_resend_count[5m]) > 10', "5m", "warning", "consumer",
     "DLQ resends high", "More than 10 dead-letter queue resends occurred in 5 minutes."),
    ("rocketmq-threadpool-reject", "RocketMQThreadPoolReject", "rocketmq-broker.rules",
     'increase(rocketmq_threadpool_reject_count[5m]) > 0', "5m", "critical", "broker",
     "Thread pool rejections", "The broker thread pool rejected tasks, indicating saturation."),
    ("rocketmq-jvm-gc-cpu-high", "RocketMQJVMCpuHigh", "rocketmq-broker.rules",
     'rate(jvm_gc_pause_seconds_count[5m]) * avg(rate(jvm_gc_pause_seconds_sum[5m])) > 0.3', "5m", "warning", "broker",
     "JVM GC CPU high", "The broker spends more than 30% of CPU time in GC pauses."),
]

TEMPLATE = """\
# ============================================================================
# RocketMQ 5.x Monitoring - Alert Rule
# Compatible with Prometheus / VictoriaMetrics / Thanos / Cortex / Mimir
# ============================================================================
groups:
  - name: {group}
    rules:
      - alert: {alert}
        expr: {expr}
        for: {for_}
        labels:
          severity: {severity}
          team: {team}
        annotations:
          summary: "{summary}"
          description: "{description}"
"""

for slug, alert, group, expr, for_, severity, team, summary, description in RULES:
    content = TEMPLATE.format(
        group=group, alert=alert, expr=expr, for_=for_,
        severity=severity, team=team, summary=summary, description=description,
    )
    path = os.path.join(OUT_DIR, f"{slug}.yaml")
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"wrote {path}")

print(f"TOTAL alert rule assets: {len(RULES)}")
