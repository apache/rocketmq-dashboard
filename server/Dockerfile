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

# ==============================================================================
# Build context: REPOSITORY ROOT (not server/).
# Callers: docker build -f server/Dockerfile .  /  compose context: ..
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: rmqctl CLI + MCP server (static Go binary)
# ------------------------------------------------------------------------------
# golang:1.27.1 pinned to match rmqctl/go.mod; GOTOOLCHAIN=local prevents
# silent downloads. CGO_ENABLED=0 produces a static binary portable across
# Debian (this stage) and Anolis (runtime stage).
FROM golang:1.27.1 AS rmqctl-build
WORKDIR /src/rmqctl

ENV GOPROXY=https://goproxy.cn,direct \
    GOTOOLCHAIN=local \
    CGO_ENABLED=0

# Cache module downloads separately from source changes.
COPY rmqctl/go.mod rmqctl/go.sum ./
RUN go mod download

COPY rmqctl/ ./
COPY LICENSE NOTICE /src/

RUN mkdir -p /out \
    && go build -trimpath -ldflags "-s -w" -o /out/rmqctl main.go \
    && go run scripts/license-binary.go -root /src -binary /out/rmqctl -output /out/legal \
    && go run scripts/license-binary.go -root /src -binary /out/rmqctl -output /out/legal -check

# ------------------------------------------------------------------------------
# Stage 2: runtime-base — shared runtime layer (JDK + Node.js + rmqctl)
# ------------------------------------------------------------------------------
# Rolling :21 tag until a pinned dragonwell patch tag is reachable through
# deployment registry mirrors.
FROM alibabadragonwell/dragonwell:21 AS runtime-base
WORKDIR /app

# Python3 interpreter for the license-check script (standard library only).
RUN if ! command -v python3 >/dev/null 2>&1; then \
      if command -v yum >/dev/null 2>&1; then \
        yum install -y python3 && yum clean all; \
      else \
        apt-get update && apt-get install -y --no-install-recommends python3; \
      fi; \
    fi

COPY server/scripts/legal.py /opt/studio-legal/scripts/legal.py
COPY server/LICENSE server/NOTICE /opt/studio-legal/
COPY server/LICENSE server/NOTICE /usr/share/licenses/rocketmq-studio/

# Node.js + AI agent CLIs (claude-code, qodercli).
# These packages are obtained from npm at build time by the user; they are NOT
# distributed as part of the ASF release artifact (only this Dockerfile is).
RUN curl -fsSL https://npmmirror.com/mirrors/node/v20.19.2/node-v20.19.2-linux-x64.tar.gz \
      | tar xz -C /opt \
    && ln -s /opt/node-v20.19.2-linux-x64/bin/node  /usr/local/bin/node \
    && ln -s /opt/node-v20.19.2-linux-x64/bin/npm   /usr/local/bin/npm \
    && ln -s /opt/node-v20.19.2-linux-x64/bin/npx   /usr/local/bin/npx \
    && npm config set registry https://registry.npmmirror.com \
    && npm install -g @anthropic-ai/claude-code @qoder-ai/qodercli \
    && ln -s /opt/node-v20.19.2-linux-x64/bin/claude   /usr/local/bin/claude \
    && ln -s /opt/node-v20.19.2-linux-x64/bin/qodercli /usr/local/bin/qodercli

# rmqctl binary + legal materials from stage 1.
COPY --from=rmqctl-build /out/rmqctl /usr/local/bin/rmqctl
COPY --from=rmqctl-build /out/legal/LICENSE /out/legal/NOTICE /usr/share/licenses/rmqctl/
COPY --from=rmqctl-build /out/legal /usr/share/licenses/rmqctl/legal
RUN chmod 0755 /usr/local/bin/rmqctl

# MySQL Connector/J (GPL-2.0 + Universal FOSS Exception, ASF Category X).
# Not bundled in the fat JAR (pom.xml scope=provided); downloaded here at image
# build time so the container has a working MySQL driver out of the box.
# ASF distributes only this Dockerfile text, not the connector binary.
RUN mkdir -p /app/lib \
    && curl -fsSL -o /app/lib/mysql-connector-j-9.7.0.jar \
       https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar
ENV LOADER_PATH=/app/lib

EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]

# ------------------------------------------------------------------------------
# Stage 3a: runtime-prebuilt — deploy.sh target (JAR built on host)
# ------------------------------------------------------------------------------
# Expects server/target/*.jar to exist before docker build.
FROM runtime-base AS runtime-prebuilt
COPY server/target/*.jar app.jar
RUN python3 /opt/studio-legal/scripts/legal.py check-jar /app/app.jar \
      --output /usr/share/licenses/rocketmq-studio

# ------------------------------------------------------------------------------
# Stage 3b: build — Maven compile inside Docker (CI / docker compose default)
# ------------------------------------------------------------------------------
# mysql-connector-j is scope=provided, so `mvn package` produces a fat JAR
# WITHOUT the GPL connector. The connector is downloaded separately in the
# runtime-base stage above and loaded via LOADER_PATH at startup.
FROM alibabadragonwell/dragonwell:21 AS build
WORKDIR /app

RUN curl -fsSL https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz \
      | tar xz -C /opt \
    && ln -s /opt/apache-maven-3.9.9/bin/mvn /usr/local/bin/mvn

RUN if ! command -v python3 >/dev/null 2>&1; then \
      if command -v yum >/dev/null 2>&1; then \
        yum install -y python3 && yum clean all; \
      else \
        apt-get update && apt-get install -y --no-install-recommends python3; \
      fi; \
    fi

# Cache dependencies separately from source changes.
COPY server/pom.xml .
RUN mvn dependency:go-offline

COPY server/src ./src
COPY server/style ./style
COPY server/scripts/legal.py ./scripts/legal.py
COPY server/LICENSE server/NOTICE ./

RUN mvn package -DskipTests

# ------------------------------------------------------------------------------
# Stage 4: runtime — default target (JAR built inside Docker)
# ------------------------------------------------------------------------------
FROM runtime-base AS runtime
COPY --from=build /app/target/*.jar app.jar
RUN python3 /opt/studio-legal/scripts/legal.py check-jar /app/app.jar \
      --output /usr/share/licenses/rocketmq-studio
