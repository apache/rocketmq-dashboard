#!/usr/bin/env bash
#
# Validates native alerting against real dependencies. It requires an isolated, already
# initialized MySQL database, a RocketMQ NameServer, an SMTP endpoint, and a webhook receiver
# whose assertion endpoint returns captured JSON payloads.
#
# Required environment:
#   E2E_DB_JDBC_URL       JDBC URL for an isolated initialized MySQL database
#   E2E_MYSQL_DATABASE    Database name in E2E_DB_JDBC_URL
#   E2E_ADMIN_USERNAME    Studio administrator already present in that database
#   E2E_ADMIN_PASSWORD    Password for E2E_ADMIN_USERNAME
#   E2E_NAMESRV_ADDR      Reachable NameServer address, for example 127.0.0.1:9876
#   E2E_WEBHOOK_URL       Non-loopback receiver URL saved as the SMS webhook setting
#   E2E_WEBHOOK_ASSERT_URL GET endpoint returning captured webhook payloads
#   E2E_EMAIL_RECIPIENT   Recipient accepted by the SMTP sink
#   E2E_SMTP_HOST         SMTP host
#
# Optional environment:
#   E2E_DB_USERNAME=root E2E_DB_PASSWORD=studio123 E2E_MYSQL_HOST=127.0.0.1
#   E2E_MYSQL_PORT=3306 E2E_SMTP_PORT=1025 E2E_PORT=18083 E2E_SILENCE_SECONDS=10
#   E2E_STUDIO_JAR=.../server/target/rocketmq-studio-1.0.0.jar
#   E2E_KEEP_ARTIFACTS=true keeps the cookie file and Studio log for debugging
#
# The script retains its e2e-native-alert-* records as database evidence. It does not
# use an operator's development database and it stops the temporary Studio process.

set -euo pipefail

required=(E2E_DB_JDBC_URL E2E_MYSQL_DATABASE E2E_ADMIN_USERNAME E2E_ADMIN_PASSWORD \
  E2E_NAMESRV_ADDR E2E_WEBHOOK_URL E2E_WEBHOOK_ASSERT_URL E2E_EMAIL_RECIPIENT E2E_SMTP_HOST)
for name in "${required[@]}"; do
  [[ -n "${!name:-}" ]] || { echo "Missing required environment variable: $name" >&2; exit 2; }
done
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
SERVER_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
STUDIO_JAR=${E2E_STUDIO_JAR:-"$SERVER_DIR/target/rocketmq-studio-1.0.0.jar"}
PORT=${E2E_PORT:-18083}
SMTP_PORT=${E2E_SMTP_PORT:-1025}
SILENCE_SECONDS=${E2E_SILENCE_SECONDS:-10}
MYSQL_HOST=${E2E_MYSQL_HOST:-127.0.0.1}
MYSQL_PORT=${E2E_MYSQL_PORT:-3306}
DB_USERNAME=${E2E_DB_USERNAME:-root}
DB_PASSWORD=${E2E_DB_PASSWORD:-studio123}
KEEP_ARTIFACTS=${E2E_KEEP_ARTIFACTS:-false}
RUN_ID="e2e-native-alert-$(date -u +%Y%m%d%H%M%S)-$$"
INSTANCE_ID="$RUN_ID-instance"
RULE_NAME="$RUN_ID-rule"
COOKIE=
RUN_DIR=
APP_LOG=
APP_PID=

require_port() {
  local name=$1
  local value=$2
  [[ "$value" =~ ^[0-9]+$ && "$value" -ge 1 && "$value" -le 65535 ]] \
    || { echo "$name must be an integer between 1 and 65535" >&2; exit 2; }
}

for command in curl jq mysql; do
  command -v "$command" >/dev/null || { echo "Required command is unavailable: $command" >&2; exit 2; }
done
command -v "$JAVA_BIN" >/dev/null || { echo "Required Java command is unavailable: $JAVA_BIN" >&2; exit 2; }
require_port E2E_PORT "$PORT"
require_port E2E_SMTP_PORT "$SMTP_PORT"
require_port E2E_MYSQL_PORT "$MYSQL_PORT"
[[ "$SILENCE_SECONDS" =~ ^[0-9]+$ && "$SILENCE_SECONDS" -ge 5 ]] \
  || { echo 'E2E_SILENCE_SECONDS must be an integer of at least 5' >&2; exit 2; }
[[ "$KEEP_ARTIFACTS" == true || "$KEEP_ARTIFACTS" == false ]] \
  || { echo 'E2E_KEEP_ARTIFACTS must be true or false' >&2; exit 2; }

COOKIE=$(mktemp "${TMPDIR:-/tmp}/rocketmq-studio-e2e.cookie.XXXXXX")
RUN_DIR=$(mktemp -d "${TMPDIR:-/tmp}/rocketmq-studio-e2e.XXXXXX")
APP_LOG="$RUN_DIR/studio.log"

cleanup() {
  if [[ -n "$APP_PID" ]]; then
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
  fi
  if [[ "$KEEP_ARTIFACTS" != true ]]; then
    [[ -z "$COOKIE" || ! -e "$COOKIE" ]] || unlink "$COOKIE"
    [[ -z "$APP_LOG" || ! -e "$APP_LOG" ]] || unlink "$APP_LOG"
    [[ -z "$RUN_DIR" || ! -d "$RUN_DIR" ]] || rmdir "$RUN_DIR" 2>/dev/null || true
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

[[ -f "$STUDIO_JAR" ]] || { echo "Studio jar not found: $STUDIO_JAR" >&2; exit 2; }

mysql_query() {
  mysql -N -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$DB_USERNAME" -p"$DB_PASSWORD" \
    "$E2E_MYSQL_DATABASE" -e "$1" | tr -d '[:space:]'
}

wait_for() {
  local description=$1
  local attempts=$2
  local predicate=$3
  for _ in $(seq 1 "$attempts"); do
    "$predicate" && return 0
    sleep 2
  done
  echo "Timed out waiting for $description" >&2
  return 1
}

utc_after_seconds() {
  local seconds=$1
  if date -u -d "+${seconds} seconds" +%Y-%m-%dT%H:%M:%SZ >/dev/null 2>&1; then
    date -u -d "+${seconds} seconds" +%Y-%m-%dT%H:%M:%SZ
  else
    date -u -v+"${seconds}"S +%Y-%m-%dT%H:%M:%SZ
  fi
}

api_post() {
  curl -fsS -b "$COOKIE" -H 'Content-Type: application/json' -d "$2" \
    "http://127.0.0.1:${PORT}$1"
}

studio_started() {
  [[ $(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${PORT}/api/auth/status") == 200 ]]
}

firing_exists() {
  [[ $(mysql_query "SELECT COUNT(*) FROM rmq_system_alert WHERE instance_id='${INSTANCE_ID}' AND transition='FIRING'") -ge 1 ]]
}

silenced_rows_pending() {
  [[ $(mysql_query "SELECT COUNT(*) FROM rmq_alert_notification_outbox WHERE alert_id IN (SELECT id FROM rmq_system_alert WHERE instance_id='${INSTANCE_ID}' AND transition='FIRING') AND status='PENDING'") -eq 2 ]]
}

firing_rows_delivered() {
  [[ $(mysql_query "SELECT COUNT(*) FROM rmq_alert_notification_outbox WHERE alert_id IN (SELECT id FROM rmq_system_alert WHERE instance_id='${INSTANCE_ID}' AND transition='FIRING') AND status='DELIVERED'") -eq 2 ]]
}

resolved_exists() {
  [[ $(mysql_query "SELECT COUNT(*) FROM rmq_system_alert WHERE instance_id='${INSTANCE_ID}' AND transition='RESOLVED'") -ge 1 ]]
}

resolved_rows_delivered() {
  [[ $(mysql_query "SELECT COUNT(*) FROM rmq_alert_notification_outbox WHERE alert_id IN (SELECT id FROM rmq_system_alert WHERE instance_id='${INSTANCE_ID}' AND transition='RESOLVED') AND status='DELIVERED'") -eq 2 ]]
}

echo "Starting Studio from $STUDIO_JAR; logs: $APP_LOG"
(
  cd "$RUN_DIR"
  SPRING_PROFILES_ACTIVE=default \
  SPRING_DATASOURCE_URL="$E2E_DB_JDBC_URL" \
  SPRING_DATASOURCE_USERNAME="$DB_USERNAME" \
  SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
  STUDIO_AUTH_SESSION_COOKIE_SECURE=false \
  STUDIO_ALERTING_COLLECTION_INTERVAL=PT2S \
  STUDIO_ALERTING_COLLECTION_TIMEOUT=PT1S \
  STUDIO_ALERTING_COLLECTION_PARALLELISM=4 \
  STUDIO_ALERTING_NOTIFICATION_DISPATCH_INTERVAL=PT1S \
  STUDIO_ALERTING_SMTP_HOST="$E2E_SMTP_HOST" \
  STUDIO_ALERTING_SMTP_PORT="$SMTP_PORT" \
  STUDIO_ALERTING_SMTP_AUTH=false \
  STUDIO_ALERTING_SMTP_STARTTLS=false \
  exec "$JAVA_BIN" -jar "$STUDIO_JAR" --server.port="$PORT" >"$APP_LOG" 2>&1
) &
APP_PID=$!

wait_for "Studio startup" 45 studio_started
curl -fsS -c "$COOKIE" -H 'Content-Type: application/json' \
  -d "$(jq -n --arg username "$E2E_ADMIN_USERNAME" --arg password "$E2E_ADMIN_PASSWORD" '{username:$username,password:$password}')" \
  "http://127.0.0.1:${PORT}/api/auth/login" >/dev/null

# Configure both real delivery paths through the public settings API. LLM is outside this
# scenario, so leave its base URL blank rather than depending on external DNS/network access.
api_post /api/settings/general/save "$(jq -n --arg webhook "$E2E_WEBHOOK_URL" --arg recipient "$E2E_EMAIL_RECIPIENT" \
  '{theme:"light",compact:false,desktopNotify:false,notifySound:false,sessionTimeout:30,requireLogin:true,
    llmProvider:"openai",model:"gpt-4o-mini",baseUrl:"",emailRecipients:$recipient,smsWebhook:$webhook}')" >/dev/null
api_post /api/instances/create "$(jq -n --arg name "$INSTANCE_ID" \
  '{name:$name,type:"DIRECT",endpoint:"127.0.0.1:19879",vendor:"APACHE"}')" >/dev/null
RULE=$(api_post /api/cluster-alert-rules/create "$(jq -n --arg name "$RULE_NAME" --arg instance "$INSTANCE_ID" \
  '{name:$name,metric:"nameserver.availability",operator:"UNAVAILABLE",threshold:0,duration:"1s",aggregation:"LAST",
    channels:["email","sms"],enabled:true,severity:"critical",instanceId:$instance,consecutiveSamples:1}')")
RULE_ID=$(jq -er '.data.id' <<<"$RULE")

# The silence must initially defer both channel rows, then its normal expiry releases them.
SILENCE=$(api_post /api/alert-silences "$(jq -n --arg instance "$INSTANCE_ID" --argjson ruleId "$RULE_ID" \
  --arg start "$(utc_after_seconds 0)" --arg end "$(utc_after_seconds "$SILENCE_SECONDS")" \
  '{domain:"CLUSTER",ruleId:$ruleId,instanceId:$instance,startsAt:$start,endsAt:$end,reason:"native alert e2e"}')")
SILENCE_ID=$(jq -er '.data.id' <<<"$SILENCE")

wait_for "FIRING event" 30 firing_exists
wait_for "silenced notification rows" 8 silenced_rows_pending
echo "Silence $SILENCE_ID deferred FIRING delivery."
wait_for "silence expiry and FIRING delivery" 30 firing_rows_delivered

api_post /api/instances/update "$(jq -n --arg id "$INSTANCE_ID" --arg endpoint "$E2E_NAMESRV_ADDR" \
  '{instanceId:$id,name:$id,type:"DIRECT",endpoint:$endpoint}')" >/dev/null
wait_for "RESOLVED event" 30 resolved_exists
wait_for "RESOLVED delivery" 30 resolved_rows_delivered

# DELIVERED confirms the SMTP sender handed both lifecycle messages to its configured endpoint.
# The script deliberately does not depend on a Mailpit-specific message-inspection API.
WEBHOOK_PAYLOADS=$(curl -fsS "$E2E_WEBHOOK_ASSERT_URL")
grep -q "$RULE_NAME" <<<"$WEBHOOK_PAYLOADS" || { echo 'Webhook capture has no payload for this run' >&2; exit 1; }
grep -q 'FIRING' <<<"$WEBHOOK_PAYLOADS" || { echo 'Webhook capture has no FIRING payload' >&2; exit 1; }
grep -q 'RESOLVED' <<<"$WEBHOOK_PAYLOADS" || { echo 'Webhook capture has no RESOLVED payload' >&2; exit 1; }

echo "PASS: silence suppression, FIRING/RESOLVED state transitions, SMTP handoff, and webhook delivery verified."
if [[ "$KEEP_ARTIFACTS" == true ]]; then
  echo "Evidence is retained in $E2E_MYSQL_DATABASE for instance $INSTANCE_ID; Studio log: $APP_LOG"
else
  echo "Database evidence is retained in $E2E_MYSQL_DATABASE for instance $INSTANCE_ID."
fi
