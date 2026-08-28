# Studio Native Alerting Design

## Status

The native alerting execution path is implemented. Native Apache collection (including NameServer, Broker, and Proxy availability), snapshots, scheduling, evaluation, event lifecycle,
acknowledgement, multi-replica collection lease, rule test-runs, label-scoped silence management, and
the DingTalk, SMS webhook, and SMTP email outbox delivery channels are in place. Provider capability catalogs are available for Apache,
Aliyun, and Tencent; cloud consumer-lag and managed-instance lifecycle collection are available. This design does
not require Prometheus or Alertmanager for rule evaluation or event creation.

### Implemented and Deferred Scope

| Area | Current implementation | Explicitly deferred |
| --- | --- | --- |
| Notification routing | Per-rule channel selection for DingTalk, configured SMS webhook, and SMTP email. Delivery uses the durable outbox. | Independent channel and notification-policy CRUD, generic per-rule Webhook endpoints. |
| Notification frequency | `FIRING`, periodic `REMINDER` (per rule `reminderInterval`), and `RESOLVED` deliveries; retries have bounded exponential backoff. | A separate `cooldownSeconds` policy. |
| Collector health | Per-instance bounded collection, timeout, database lease, and delivery audit history. | Producer failure-rate, thread-pool rejection, and cloud Broker-level health metrics. |
| Validation | Unit and controller tests plus `server/scripts/native-alert-e2e.sh` for a real RocketMQ, MySQL, SMTP, and webhook receiver environment. | A CI-owned shared RocketMQ/SMTP/webhook environment; production SLO dashboards for collector or delivery failure rate. |

## Goals

- Keep the current three user-facing menus: Business Alerts, Cluster Alerts, and Alert Events.
- Collect RocketMQ operational and business metrics directly from Studio instance providers.
- Reuse one rule evaluator, event lifecycle, silence model, notification pipeline, authorization model, and audit trail.
- Support Apache RocketMQ first, with explicit capability discovery for Proxy and cloud instances.
- Keep Prometheus rule YAML export as optional interoperability, not as the execution path.

## Non-Goals

- Replace a dedicated time-series database for long-term metric retention or arbitrary analytics.
- Implement general PromQL or a generic monitoring DSL.
- Assume every provider can expose every metric.
- Automatically enable notifications when upgrading existing installations.

## Product Model

The UI remains split by audience and resource type while all alerts share one event stream.

| Menu | Route | Rule domain | Typical resources |
| --- | --- | --- | --- |
| Business Alerts | `/ops/business-alerts` | `BUSINESS` | Topic, Consumer Group, queue, DLQ, producer flow |
| Cluster Alerts | `/ops/cluster-alerts` | `CLUSTER` | NameServer, Broker, Proxy, instance connectivity |
| Alert Events | `/ops/system-alerts` | Both | Active, recovered, acknowledged alert events |

Business Alerts own message-flow risk: consumer lag, consumption delay, DLQ growth, and topic backlog. Cluster Alerts own runtime health: NameServer or Broker availability, disk pressure, JVM pressure, Broker send-queue pressure, Proxy availability, and collector connectivity. Producer failure rate and thread-pool rejection are planned metrics, not current capabilities.

Alert Events are not a third rule domain. They are the shared lifecycle record emitted by either rule domain.

## Architecture

```text
Apache Admin / Proxy API / Cloud API
                |
                v
ClusterMetricsCollector      BusinessMetricsCollector
                |                     |
                +----- MetricSample --+
                              |
                              v
                    AlertRuleEvaluator
                              |
                              v
                     AlertEventService
                       |            |
                       v            v
                 Event history   NotificationOutbox
                                      |
                                      v
                     DingTalk / SMS webhook / Email
```

The collectors know only how to obtain metrics. The evaluator knows only typed metric samples and structured rules. Notification senders know only event payloads and channel configuration.

### Package Boundaries

```text
server/.../cluster/metrics/
  ClusterMetricsCollector
  BusinessMetricsCollector
  CollectorScheduler
  MetricCatalogService
  MetricSnapshotRepository
  collectors/
    ApacheRocketMqClusterMetricsCollector
    ApacheRocketMqBusinessMetricsCollector
    ProxyMetricsCollector
    AliyunMetricsCollector
    TencentMetricsCollector

server/.../ops/alert/
  AlertRuleService
  AlertRuleEvaluator
  AlertStateMachine
  AlertEventService
  AlertSilenceService
  AlertNotificationService
  NotificationOutboxWorker
  notification/
    WebhookNotificationSender
    DingTalkNotificationSender
    EmailNotificationSender
```

`ClusterMetricsCollector` is a neutral collection name. It does not imply that every sample is a cluster-health sample; it is the Studio component responsible for collecting metrics from a managed cluster instance. `BusinessMetricsCollector` is separate because its target resources and collection costs differ.

## Metric Contract

Every collector produces the same typed sample:

```java
record MetricSample(
    String metricKey,
    AlertDomain domain,
    String instanceId,
    String clusterId,
    Map<String, String> labels,
    Double value,
    MetricAvailability availability,
    Instant collectedAt
) {}
```

`MetricAvailability` is one of `AVAILABLE`, `UNAVAILABLE`, `UNSUPPORTED`, or `STALE`.

- `UNAVAILABLE` means a collection attempt failed or timed out. It must never be converted to zero.
- `UNSUPPORTED` means the selected provider cannot expose this metric.
- `STALE` means the last sample is older than the configured freshness limit.
- Only an explicit availability rule, such as Broker unavailable, may alert on a non-`AVAILABLE` value.

### Initial Metric Catalog

| Domain | Metric key | Scope | Source |
| --- | --- | --- | --- |
| Cluster | `broker.availability` | Broker | Admin client route/runtime query |
| Cluster | `nameserver.availability` | NameServer | NameServer connection check |
| Cluster | `broker.disk.usage_ratio` | Broker | Broker runtime/config data |
| Cluster | `broker.jvm.heap.usage_ratio` | Broker | Broker runtime data |
| Cluster | `broker.send_queue.usage_ratio` | Broker | Broker runtime send queue size/capacity |
| Cluster | `proxy.availability` | Proxy | TCP probe of the gRPC listener discovered for the selected instance |
| Cluster | `cloud.instance.availability` | Managed cloud instance | Aliyun/Tencent control-plane instance status |
| Business | `consumer.lag.total` | Consumer Group / Topic | Consumer progress (Apache, Aliyun, Tencent) |
| Business | `consumer.lag.max_queue` | Consumer Group / Queue | Consumer progress (Apache, Aliyun, Tencent) |
| Business | `consumer.delay.seconds` | Consumer Group | Apache broker consume stats; emitted only when a consumption timestamp is available |
| Business | `topic.backlog.total` | Topic / Consumer Group | Topic and consumer offsets; each sample is scoped to one Topic and consumer group to avoid cross-group double counting |
| Business | `dlq.message.count` | Consumer Group / DLQ | DLQ provider |

The metric catalog is capability-aware. The rule editor must query the selected instance capability and hide unsupported metrics instead of allowing rules that can never run.

## Rule Model

Rules are structured. Studio does not parse or execute free-form PromQL.

```text
StudioAlertRule
  id
  domain: BUSINESS | CLUSTER
  name
  enabled
  scopeType: INSTANCE | CLUSTER | BROKER | PROXY | TOPIC | CONSUMER_GROUP | QUEUE | DLQ
  metricKey
  selectorJson
  aggregation: LAST | MAX | MIN | AVG | SUM
  windowSeconds
  operator: GT | GTE | LT | LTE | EQ | NE | UNAVAILABLE
  threshold
  consecutiveSamples
  severity: INFO | WARNING | CRITICAL
```

`cooldownSeconds` and `notificationPolicyId` are future rule-model fields. Current notification routing uses the enabled channels configured in General Settings.

`selectorJson` contains the resource boundary, for example `instanceId`, `clusterName`, `brokerName`, `topic`, `consumerGroup`, and `queueId`. A rule must always be constrained to an instance. Wildcards are only valid for resource labels beneath that instance.

### Example Rules

```text
Business: order-consumer lag high
metricKey=consumer.lag.total
selector={instanceId=local, consumerGroup=order-consumer}
aggregation=MAX, windowSeconds=300, operator=GT, threshold=100000
consecutiveSamples=3, severity=WARNING

Cluster: broker disk pressure
metricKey=broker.disk.usage_ratio
selector={instanceId=local, brokerName=broker-a}
aggregation=LAST, operator=GTE, threshold=0.85
consecutiveSamples=2, severity=CRITICAL
```

## Evaluation and Event Lifecycle

The evaluator runs after a collector writes a new sample. It computes an aggregation over the bounded local snapshot window, then advances an alert state keyed by a stable fingerprint.

```text
OK -- condition met --> PENDING -- consecutive samples met --> FIRING
FIRING -- condition clear --> RESOLVED
FIRING -- user acknowledges --> ACKED
PENDING/FIRING -- silence matches --> state unchanged, notification suppressed
```

The fingerprint is `sha256(ruleId + instanceId + sorted(labels))`. A single rule therefore creates independent events for different Brokers, Topics, queues, or consumer groups.

Current delivery emits on `FIRING`, periodic `REMINDER` transitions controlled by the rule's `reminderInterval`, and `RESOLVED`. A separate `cooldownSeconds` policy remains future work. A value recovery always emits a `RESOLVED` event.

## Persistence

The existing `rmq_alert_rule` and `rmq_system_alert` tables can be evolved, but the event table must represent a lifecycle rather than a standalone local message.

```text
rmq_alert_rule
  domain, scope_type, metric_key, selector_json,
  aggregation, window_seconds, consecutive_samples,
  operator, threshold, severity

rmq_metric_snapshot
  instance_id, metric_key, labels_hash, labels_json,
  value, availability, collected_at

rmq_alert_state
  rule_id, fingerprint, status, consecutive_hits,
  current_value, labels_json, first_pending_at, fired_at, resolved_at, version

rmq_system_alert
  rule_id, fingerprint, transition, domain, severity,
  title, description, current_value, threshold, labels_json,
  occurred_at, acknowledged_by, acknowledged_at

rmq_alert_silence
  selector_json, starts_at, ends_at, created_by, reason

rmq_notification_outbox
  event_id, channel, state, attempt_count,
  next_attempt_at, last_error, delivered_at
```

Snapshots have short retention, initially 24 hours. Studio is not a replacement for a time-series database.

## Collection and Scheduling

`CollectorScheduler` creates per-instance jobs at a default 30-second interval. Each job has a bounded timeout and the scheduler uses bounded concurrency, so one slow remote instance does not indefinitely delay every other instance. Collection failure records an unavailable sample and health diagnostic; it does not block other instances.

For multi-replica Studio deployment, the scheduler uses a database lease:

```text
rmq_metric_collector_lease
  collector_name, owner_id, expires_at
```

Only the active lease holder collects and evaluates. Notification outbox rows use claimant-bound state transitions so a stale worker cannot overwrite a newer claimant. A delivery worker renews its claim while a webhook or SMTP call is in flight; the renewal is conditional on the claim token, and a worker that loses the lease stops updating that row. The delivery state is committed before its audit entry, so an audit-store failure cannot turn a completed external send into another retry. Delivery is still at-least-once: receivers should use the event and channel identity to deduplicate a request that times out after reaching the remote service.

## Notifications and Silences

The supported notification channels are DingTalk, the SMS webhook configured in General Settings, and Email. A real event creates outbox rows for enabled supported channels. Independent notification-channel and notification-policy CRUD are future work.

The dispatcher claims a row for one minute by default and renews the claim every 20 seconds while the external
delivery is running. Deployments with slower receivers can tune `studio.alerting.notification-claim-timeout` and
`studio.alerting.notification-claim-renewal-interval`; the renewal interval must remain shorter than the claim timeout.
`studio.alerting.notification-heartbeat-threads` bounds the daemon workers used for these renewals.

Email delivery uses Spring's standard SMTP configuration. Configure `STUDIO_ALERTING_SMTP_HOST`,
`STUDIO_ALERTING_SMTP_PORT`, `STUDIO_ALERTING_SMTP_USERNAME`, `STUDIO_ALERTING_SMTP_PASSWORD`,
`STUDIO_ALERTING_SMTP_AUTH`, and `STUDIO_ALERTING_SMTP_STARTTLS`; recipient addresses are configured in
General Settings. Missing SMTP configuration leaves the outbox row retryable and records the delivery failure.

```text
PENDING -> SENDING -> DELIVERED
                  -> RETRY_WAIT -> FAILED
```

Retries use bounded exponential backoff. Channel configuration is encrypted at rest and only write-only secrets are returned by APIs. A test-send action uses the same sender implementation but does not create an alert event.

Terminal delivery rows are retained for `studio.alerting.notification-retention` (`P30D` by default). The scheduled cleanup only removes `DELIVERED` and `FAILED` rows older than the retention cutoff, and it runs with bounded batches using `studio.alerting.notification-cleanup-batch-size` and `studio.alerting.notification-cleanup-max-batches`.

Silences match `domain`, rule ID, instance ID, and optional resource labels. They suppress delivery but do not hide active state from the Alert Events page.

## APIs

The two rule menus keep separate endpoints so their domain cannot be accidentally changed by a client. Both delegate to the same `AlertRuleService`.

```text
GET/POST/PUT /api/business-alert-rules
GET/POST/PUT /api/cluster-alert-rules
POST         /api/business-alert-rules/{id}/test
POST         /api/cluster-alert-rules/{id}/test

GET          /api/system-alerts?domain=&status=&severity=&instanceId=&page=
POST         /api/system-alerts/{id}/acknowledge

GET/POST     /api/alert-silences
DELETE       /api/alert-silences/{id}
GET          /api/alert-collector-status
GET          /api/alert-metric-catalog?instanceId=&domain=
```

Existing `/api/alert-rules/export` remains as a compatibility endpoint for users who deliberately export rules to Prometheus. It must not be used by the native evaluator.

## Authorization and Audit

- Readers can list rules, events, collector status, and notification delivery results with secrets redacted.
- Administrators can mutate rules, silences, and acknowledgements. Independent channel and policy administration is future work.
- Every mutation and every notification result is written to operation audit history.
- Event acknowledgement captures the user and timestamp.

## UI Behavior

Business Alerts and Cluster Alerts use separate forms and metric catalogs. The Alert Events page combines both sources and exposes filters for domain, status, severity, instance, resource labels, and time range.

An event row displays its domain badge, current value, threshold, resource identity, state transition timeline, acknowledgement, silence state, and notification delivery results.

## Migration

1. Add new nullable fields and backfill existing rule records to `BUSINESS` or `CLUSTER` where the metric is unambiguous.
2. Retain rules that cannot be structurally mapped as export-only Prometheus compatibility rules.
3. Migrate existing `rmq_system_alert` rows into `rmq_alert_event` as historical events without inventing a firing state.
4. Do not automatically enable notification channels after migration.
5. Keep legacy read APIs for one release cycle, then redirect callers to the domain-specific rule APIs and unified event API.

## Delivery Plan

1. Introduce the metric contract, catalog, Apache collectors, snapshots, and single-node scheduler.
2. Implement rule evaluation, state transitions, Alert Events lifecycle, acknowledgement, and three initial rules: Broker unavailable, Broker disk pressure, Consumer lag.
3. Implement DingTalk, SMS webhook, and Email through the outbox worker, plus delivery history and rule-level `reminderInterval`. Generic per-rule Webhook delivery and a separate cooldown policy are future work.
4. Add silences, multi-replica lease handling, rule test-run, and capability-aware forms. These are complete,
   including label-scoped silences and instance-specific metric capability forms.
5. Add Proxy and cloud collectors, additional business rules, and optional Prometheus YAML export compatibility.
   Proxy availability, Apache consumer delay, Aliyun/Tencent consumer-lag and topic-backlog, and managed cloud-instance lifecycle collection are complete;
   broker-level cloud health metrics remain future work. The existing Prometheus YAML export is compatibility-only; richer mapping remains future work.

## Real Environment Verification

`server/scripts/native-alert-e2e.sh` verifies the native lifecycle against an isolated, already-initialized MySQL database and a real RocketMQ NameServer. It creates a temporary unavailable instance, applies a label-free silence, verifies that the `FIRING` event is persisted while both notification rows remain pending, waits for the silence to expire, then changes the same instance to a reachable NameServer. The script requires the following evidence before succeeding:

1. `FIRING` and `RESOLVED` events for the same rule and instance.
2. Delivered Email and SMS-webhook outbox rows for both lifecycle transitions.
3. A webhook capture response containing both `FIRING` and `RESOLVED`.

The Email assertions stop at the durable Outbox's `DELIVERED` state, which confirms successful handoff to the configured SMTP endpoint. The script intentionally does not require a Mailpit-specific message-inspection API or assert final mailbox storage.

The script intentionally requires an isolated MySQL database with `rmq_studio_user` and `rmq_settings` already initialized, plus an administrator account supplied through environment variables. It never uses an operator's development database or deletes any existing records. Its required environment, receiver contract, and invocation guidance are documented in the script header.

For example, after building the server jar, run it against a dedicated validation database:

```bash
export E2E_DB_JDBC_URL='jdbc:mysql://127.0.0.1:3306/rocketmq_alert_e2e?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export E2E_MYSQL_DATABASE=rocketmq_alert_e2e
export E2E_ADMIN_USERNAME=e2e-admin
export E2E_ADMIN_PASSWORD='***'
export E2E_NAMESRV_ADDR=127.0.0.1:9876
export E2E_SMTP_HOST=127.0.0.1
export E2E_EMAIL_RECIPIENT=native-alert-e2e@example.test
# Use a routable private address, not 127.0.0.1: notification webhooks reject loopback
# targets to prevent SSRF. Replace this address with the host address of the receiver.
export E2E_WEBHOOK_URL='http://192.168.1.10:18090/alerts'
export E2E_WEBHOOK_ASSERT_URL='http://192.168.1.10:18090/received'
server/scripts/native-alert-e2e.sh
```

The webhook receiver must accept the configured `POST` endpoint and expose captured request bodies from the assertion URL. This keeps the production delivery client unchanged while allowing the script to verify both lifecycle payloads.

## Acceptance Criteria

- Studio can fire and recover a Broker unavailable or Consumer lag alert with no Prometheus service configured.
- A rule never treats failed collection as zero.
- A business rule and a cluster rule share the same event, acknowledgement, silence, notification, and audit pipeline.
- The three existing menus remain distinct and usable.
- Notifications are claimant-safe, retryable, auditable, and do not expose secrets; external delivery is at-least-once.
- Unsupported provider metrics cannot be selected in the corresponding rule editor.
