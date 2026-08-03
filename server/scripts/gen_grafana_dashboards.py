#!/usr/bin/env python3
"""Generate RocketMQ Grafana dashboard JSON assets for the dashboard project.

Each dashboard is a standalone Grafana dashboard model (schemaVersion 39) that
queries Prometheus-compatible RocketMQ metrics. The JSON files are written to
server/src/main/resources/grafana/ and loaded at runtime by GrafanaDashboardService.
"""
import json
import os

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "grafana")
os.makedirs(OUT_DIR, exist_ok=True)

DS = "${DS_PROMETHEUS}"


def ts_panel(panel_id, title, expr, grid, y, legend=None, unit="short"):
    target = {
        "datasource": {"type": "prometheus", "uid": DS},
        "expr": expr,
        "legendFormat": legend or "",
        "refId": "A",
    }
    return {
        "id": panel_id,
        "title": title,
        "type": "timeseries",
        "datasource": {"type": "prometheus", "uid": DS},
        "gridPos": {"h": 8, "w": grid, "x": 0, "y": y},
        "fieldConfig": {
            "defaults": {"custom": {"drawStyle": "line", "fillOpacity": 10}, "unit": unit},
            "overrides": [],
        },
        "options": {"legend": {"displayMode": "list", "placement": "bottom"}},
        "targets": [target],
    }


def stat_panel(panel_id, title, expr, grid, y, unit="short"):
    return {
        "id": panel_id,
        "title": title,
        "type": "stat",
        "datasource": {"type": "prometheus", "uid": DS},
        "gridPos": {"h": 6, "w": grid, "x": 0, "y": y},
        "fieldConfig": {
            "defaults": {"unit": unit, "custom": {"thresholdsStyle": {"mode": "none"}}},
            "overrides": [],
        },
        "options": {"reduceOptions": {"calcs": ["lastNotNull"]}},
        "targets": [{"datasource": {"type": "prometheus", "uid": DS}, "expr": expr, "refId": "A"}],
    }


def gauge_panel(panel_id, title, expr, grid, y, max_=100):
    return {
        "id": panel_id,
        "title": title,
        "type": "gauge",
        "datasource": {"type": "prometheus", "uid": DS},
        "gridPos": {"h": 8, "w": grid, "x": 0, "y": y},
        "fieldConfig": {
            "defaults": {
                "unit": "percent",
                "max": max_,
                "custom": {"min": 0},
            },
            "overrides": [],
        },
        "options": {"reduceOptions": {"calcs": ["lastNotNull"]}},
        "targets": [{"datasource": {"type": "prometheus", "uid": DS}, "expr": expr, "refId": "A"}],
    }


def template_vars(extra=None):
    base = [
        {
            "name": "DS_PROMETHEUS",
            "type": "datasource",
            "label": "Prometheus",
            "query": "prometheus",
            "current": {},
            "hide": 0,
        },
        {
            "name": "cluster",
            "type": "query",
            "datasource": {"type": "prometheus", "uid": DS},
            "query": "label_values(rocketmq_messages_in_total, cluster)",
            "refresh": 2,
            "current": {},
            "hide": 0,
        },
        {
            "name": "broker",
            "type": "query",
            "datasource": {"type": "prometheus", "uid": DS},
            "query": "label_values(rocketmq_messages_in_total{cluster=\"$cluster\"}, broker)",
            "refresh": 2,
            "current": {},
            "hide": 0,
        },
        {
            "name": "topic",
            "type": "query",
            "datasource": {"type": "prometheus", "uid": DS},
            "query": "label_values(rocketmq_messages_in_total{cluster=\"$cluster\"}, topic)",
            "refresh": 2,
            "current": {},
            "hide": 0,
        },
    ]
    if extra:
        base.extend(extra)
    return {"list": base}


def dashboard(uid, title, description, panels, vars_=None):
    return {
        "uid": uid,
        "title": title,
        "description": description,
        "tags": ["rocketmq"],
        "schemaVersion": 39,
        "timezone": "browser",
        "editable": True,
        "templating": vars_ or template_vars(),
        "time": {"from": "now-6h", "to": "now"},
        "refresh": "30s",
        "panels": panels,
    }


def y_stack(panels):
    """Assign gridPos y offsets sequentially (2 per row of w=12)."""
    y = 0
    for p in panels:
        w = p["gridPos"]["w"]
        p["gridPos"]["x"] = 0 if (panels.index(p) % 2 == 0 or w == 24) else 12
        if w == 24:
            p["gridPos"]["x"] = 0
        p["gridPos"]["y"] = y
        y += p["gridPos"]["h"]
    return panels


specs = []

# 1. Overview
specs.append((
    "rocketmq-overview", "RocketMQ Cluster Overview",
    "Cluster-wide throughput, topic/group counts and producer footprint.",
    [
        ts_panel(1, "Messages In TPS", "sum(rate(rocketmq_messages_in_total{cluster=\"$cluster\"}[1m]))", 12, 0, "by cluster", "ops"),
        ts_panel(2, "Messages Out TPS", "sum(rate(rocketmq_messages_out_total{cluster=\"$cluster\"}[1m]))", 12, 0, "by cluster", "ops"),
        stat_panel(3, "Total Topics", "count(count by (topic) (rocketmq_messages_in_total{cluster=\"$cluster\"}))", 6, 8),
        stat_panel(4, "Total Consumer Groups", "count(count by (group) (rocketmq_messages_out_total{cluster=\"$cluster\"}))", 6, 8),
        stat_panel(5, "Producer Count", "max(rocketmq_producer_count{cluster=\"$cluster\"})", 6, 8),
        stat_panel(6, "Broker Count", "count(rocketmq_messages_in_total{cluster=\"$cluster\"})", 6, 8),
    ],
))

# 2. Broker
specs.append((
    "rocketmq-broker", "RocketMQ Broker",
    "Per-broker throughput, dispatch backlog and thread pool pressure.",
    [
        ts_panel(1, "Broker Messages In TPS", "sum by (broker) (rate(rocketmq_messages_in_total{cluster=\"$cluster\",broker=\"$broker\"}[1m]))", 12, 0, "{{broker}}", "ops"),
        ts_panel(2, "Broker Messages Out TPS", "sum by (broker) (rate(rocketmq_messages_out_total{cluster=\"$cluster\",broker=\"$broker\"}[1m]))", 12, 0, "{{broker}}", "ops"),
        ts_panel(3, "Dispatch Behind Bytes", "rocketmq_dispatch_behind_bytes{cluster=\"$cluster\",broker=\"$broker\"}", 12, 8, "{{broker}}", "bytes"),
        ts_panel(4, "Thread Pool Queue Size", "rocketmq_threadpool_queue_size{cluster=\"$cluster\",broker=\"$broker\"}", 12, 8, "{{broker}}"),
    ],
))

# 3. Producer
specs.append((
    "rocketmq-producer", "RocketMQ Producer",
    "Producer presence, send size and per-topic ingress.",
    [
        stat_panel(1, "Producer Count", "max(rocketmq_producer_count{cluster=\"$cluster\"})", 6, 0),
        ts_panel(2, "Producer Message Size", "rocketmq_producer_message_size{cluster=\"$cluster\"}", 12, 0, "{{topic}}", "bytes"),
        ts_panel(3, "Messages In by Topic", "sum by (topic) (rate(rocketmq_messages_in_total{cluster=\"$cluster\"}[1m]))", 24, 8, "{{topic}}", "ops"),
    ],
))

# 4. Consumer
specs.append((
    "rocketmq-consumer", "RocketMQ Consumer",
    "Consumer group egress, lag and client footprint.",
    [
        stat_panel(1, "Consumer Count", "max(rocketmq_consumer_count{cluster=\"$cluster\"})", 6, 0),
        ts_panel(2, "Messages Out TPS by Group", "sum by (group) (rate(rocketmq_messages_out_total{cluster=\"$cluster\",group=\"$group\"}[1m]))", 12, 0, "{{group}}", "ops"),
        ts_panel(3, "Consumer Message Size", "rocketmq_consumer_message_size{cluster=\"$cluster\"}", 12, 8, "{{group}}", "bytes"),
    ],
))

# 5. Topic
specs.append((
    "rocketmq-topic", "RocketMQ Topic",
    "Per-topic ingress/egress throughput and dispatch backlog.",
    [
        ts_panel(1, "Topic Messages In TPS", "sum by (topic) (rate(rocketmq_messages_in_total{cluster=\"$cluster\",topic=\"$topic\"}[1m]))", 12, 0, "{{topic}}", "ops"),
        ts_panel(2, "Topic Messages Out TPS", "sum by (topic) (rate(rocketmq_messages_out_total{cluster=\"$cluster\",topic=\"$topic\"}[1m]))", 12, 0, "{{topic}}", "ops"),
        ts_panel(3, "Topic Dispatch Behind Bytes", "rocketmq_dispatch_behind_bytes{cluster=\"$cluster\",topic=\"$topic\"}", 24, 8, "{{topic}}", "bytes"),
    ],
))

# 6. TPS
specs.append((
    "rocketmq-tps", "RocketMQ TPS",
    "Cluster and per-broker message throughput trends.",
    [
        ts_panel(1, "Cluster TPS In", "sum(rate(rocketmq_messages_in_total{cluster=\"$cluster\"}[1m]))", 12, 0, "", "ops"),
        ts_panel(2, "Cluster TPS Out", "sum(rate(rocketmq_messages_out_total{cluster=\"$cluster\"}[1m]))", 12, 0, "", "ops"),
        ts_panel(3, "Per-Broker TPS In", "sum by (broker) (rate(rocketmq_messages_in_total{cluster=\"$cluster\",broker=\"$broker\"}[1m]))", 24, 8, "{{broker}}", "ops"),
    ],
))

# 7. Storage
specs.append((
    "rocketmq-storage", "RocketMQ Storage",
    "Broker disk usage and JVM heap footprint.",
    [
        gauge_panel(1, "Disk Use Ratio", "rocketmq_disk_use_ratio{cluster=\"$cluster\",broker=\"$broker\"}", 12, 0),
        ts_panel(2, "JVM Heap Used", "jvm_memory_used_bytes{area=\"heap\",cluster=\"$cluster\",broker=\"$broker\"}", 12, 0, "{{broker}}", "bytes"),
        ts_panel(3, "Dispatch Behind Bytes", "rocketmq_dispatch_behind_bytes{cluster=\"$cluster\",broker=\"$broker\"}", 24, 8, "{{broker}}", "bytes"),
    ],
))

# 8. JVM
specs.append((
    "rocketmq-jvm", "RocketMQ Broker JVM",
    "JVM memory, threads and garbage collection for brokers.",
    [
        ts_panel(1, "JVM Heap", "jvm_memory_used_bytes{area=\"heap\",cluster=\"$cluster\",broker=\"$broker\"}", 12, 0, "{{broker}}", "bytes"),
        ts_panel(2, "JVM Non-Heap", "jvm_memory_used_bytes{area=\"nonheap\",cluster=\"$cluster\",broker=\"$broker\"}", 12, 0, "{{broker}}", "bytes"),
        ts_panel(3, "Live Threads", "jvm_threads_live_threads{cluster=\"$cluster\",broker=\"$broker\"}", 12, 8, "{{broker}}"),
        ts_panel(4, "GC Pause (1m rate)", "rate(jvm_gc_pause_seconds_sum{cluster=\"$cluster\",broker=\"$broker\"}[1m])", 12, 8, "{{broker}}", "s"),
    ],
))

# 9. Thread pool
specs.append((
    "rocketmq-threadpool", "RocketMQ Thread Pool",
    "Broker thread pool queue depth, capacity and rejections.",
    [
        ts_panel(1, "Queue Size", "rocketmq_threadpool_queue_size{cluster=\"$cluster\",broker=\"$broker\"}", 12, 0, "{{broker}}"),
        ts_panel(2, "Queue Capacity", "rocketmq_threadpool_queue_capacity{cluster=\"$cluster\",broker=\"$broker\"}", 12, 0, "{{broker}}"),
        ts_panel(3, "Reject Count (1m)", "increase(rocketmq_threadpool_reject_count{cluster=\"$cluster\",broker=\"$broker\"}[1m])", 24, 8, "{{broker}}"),
    ],
))

# 10. DLQ
specs.append((
    "rocketmq-dlq", "RocketMQ DLQ & Retry",
    "Dead-letter queue resend volume and latency.",
    [
        ts_panel(1, "DLQ Resend Count (1m)", "rate(rocketmq_dlq_resend_count{cluster=\"$cluster\"}[1m])", 12, 0, "{{topic}}"),
        ts_panel(2, "DLQ Resend Latency", "rocketmq_dlq_resend_latency{cluster=\"$cluster\"}", 12, 0, "{{topic}}", "s"),
    ],
))

# 11. Latency
specs.append((
    "rocketmq-latency", "RocketMQ Latency",
    "Dispatch and client push latency percentiles.",
    [
        ts_panel(1, "Dispatch Latency p99", "histogram_quantile(0.99, sum by (le) (rate(rocketmq_dispatch_latency_bucket{cluster=\"$cluster\"}[5m])))", 12, 0, "", "s"),
        ts_panel(2, "Send To Client Latency p99", "histogram_quantile(0.99, sum by (le) (rate(rocketmq_send_to_client_latency_bucket{cluster=\"$cluster\"}[5m])))", 12, 0, "", "s"),
    ],
))

# 12. Network
specs.append((
    "rocketmq-network", "RocketMQ Network & Connections",
    "Client connections and produced/consumed connection counts.",
    [
        stat_panel(1, "Client Connections", "rocketmq_producer_count{cluster=\"$cluster\"} + rocketmq_consumer_count{cluster=\"$cluster\"}", 6, 0),
        ts_panel(2, "Connections by Broker", "rocketmq_connection_count{cluster=\"$cluster\",broker=\"$broker\"}", 12, 0, "{{broker}}"),
        ts_panel(3, "Producer Connections", "rocketmq_producer_count{cluster=\"$cluster\"}", 12, 8, "{{broker}}"),
    ],
))

for uid, title, desc, panels in specs:
    panels = y_stack(panels)
    doc = dashboard(uid, title, desc, panels)
    path = os.path.join(OUT_DIR, f"{uid}.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(doc, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"wrote {path} ({len(panels)} panels)")

print(f"TOTAL dashboards: {len(specs)}")
