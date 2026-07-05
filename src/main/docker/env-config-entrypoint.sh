#!/bin/sh
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# If API_BASE_URL is set, extract env-config.js from the jar and update it
if [ -n "$API_BASE_URL" ]; then
    TMP_DIR=$(mktemp -d)
    cd "$TMP_DIR"
    jar xf /rocketmq-dashboard.jar BOOT-INF/classes/static/env-config.js
    cat > BOOT-INF/classes/static/env-config.js << EOF
window.__ENV__ = window.__ENV__ || {};
window.__ENV__.API_BASE_URL = "$API_BASE_URL";
EOF
    jar uf /rocketmq-dashboard.jar BOOT-INF/classes/static/env-config.js
    cd /
    rm -rf "$TMP_DIR"
fi

exec java $JAVA_OPTS -jar /rocketmq-dashboard.jar
