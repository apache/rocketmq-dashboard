#!/bin/sh
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0.

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CONFIG=${1:-"$SCRIPT_DIR/nginx.conf"}

fail() {
    echo "nginx contract test failed: $*" >&2
    exit 1
}

location_block() {
    awk -v header="$1" '
        index($0, header) { inside = 1 }
        inside {
            print
            opened += gsub(/\{/, "{")
            closed += gsub(/\}/, "}")
            if (opened > 0 && opened == closed) exit
        }
    ' "$CONFIG"
}

require_directive() {
    block=$(location_block "$1")
    [ -n "$block" ] || fail "missing block: $1"
    printf '%s\n' "$block" | grep -F "$2" >/dev/null \
        || fail "$1 is missing: $2"
}

require_directive "location = /api-docs {" 'proxy_pass $backend_api_docs;'
require_directive "location ^~ /api-docs/ {" 'proxy_pass $backend_api_docs_assets;'
require_directive "location = /swagger-ui.html {" 'proxy_pass $backend_swagger_redirect;'
require_directive "location ^~ /swagger-ui/ {" 'proxy_pass $backend_swagger_assets;'
require_directive "location = /api/ai/chat {" 'proxy_buffering off;'
require_directive "location = /api/ai/chat {" 'proxy_read_timeout 360s;'

api_block=$(location_block "location /api/ {")
printf '%s\n' "$api_block" | grep -F 'proxy_buffering off;' >/dev/null \
    && fail "generic /api/ block must keep default buffering"
printf '%s\n' "$api_block" | grep -F 'proxy_read_timeout' >/dev/null \
    && fail "generic /api/ block must keep the default read timeout"

[ "$(grep -F -c 'proxy_buffering off;' "$CONFIG")" -eq 1 ] \
    || fail "proxy buffering must be disabled only for the SSE endpoint"
[ "$(grep -F -c 'proxy_read_timeout 360s;' "$CONFIG")" -eq 1 ] \
    || fail "extended read timeout must be scoped to the SSE endpoint"

echo "nginx edge contract: PASS"
