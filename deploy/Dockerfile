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

# Multi-stage build for RocketMQ Dashboard
# Stage 1: Build with Maven
FROM eclipse-temurin:21-jdk AS builder

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

WORKDIR /build

# Copy POM files first for dependency caching
COPY pom.xml .
COPY rocketmq-dashboard-app/pom.xml rocketmq-dashboard-app/
COPY rocketmq-dashboard-cli/pom.xml rocketmq-dashboard-cli/
COPY rocketmq-dashboard-mcp/pom.xml rocketmq-dashboard-mcp/
COPY rocketmq-dashboard-llm/pom.xml rocketmq-dashboard-llm/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B || true

# Copy source code
COPY src/ src/
COPY rocketmq-dashboard-app/ rocketmq-dashboard-app/
COPY rocketmq-dashboard-cli/ rocketmq-dashboard-cli/
COPY rocketmq-dashboard-mcp/ rocketmq-dashboard-mcp/
COPY rocketmq-dashboard-llm/ rocketmq-dashboard-llm/

# Build the application (skip tests for faster build)
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre

LABEL maintainer="Apache RocketMQ <dev@rocketmq.apache.org>"
LABEL description="Apache RocketMQ Dashboard"

WORKDIR /app

# Copy the built jar from builder stage
COPY --from=builder /build/rocketmq-dashboard-app/target/rocketmq-dashboard-app-*.jar app.jar

# Create necessary directories
RUN mkdir -p /app/logs /app/data

# Expose port
EXPOSE 8082

# Environment variables
ENV JAVA_OPTS=""
ENV SERVER_PORT=8082

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT}/actuator/health || exit 1

# Entry point
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
