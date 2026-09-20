# NOTE: the build context for this Dockerfile is the REPOSITORY ROOT, not server/.
# It was widened so the rmqctl Go stage below can `COPY rmqctl/ ...` and ship
# /usr/local/bin/rmqctl in the server image (the AI backend spawns the agent CLI
# with an MCP config that runs `rmqctl mcp stdio`). Every COPY/ADD path in this
# file is therefore rooted at the repository. Callers must pass the root as the
# context: `docker build -f server/Dockerfile .` / compose `context: ..`.
# A root-level .dockerignore keeps that wider context lean.

# rmqctl CLI + MCP server. Built here rather than shipped as a host artifact so
# the image is self-contained and reproducible.
#   - golang:1.27.1 EXACTLY: rmqctl/go.mod declares `go 1.27.1`, and an older
#     toolchain refuses to build it unless GOTOOLCHAIN may download a newer one,
#     which the China network makes unreliable. GOTOOLCHAIN=local pins the
#     in-image toolchain so a version mismatch fails loudly instead of hanging.
#   - GOPROXY=goproxy.cn mirrors the other China-network choices in this file
#     (npmmirror for the Node.js tarball, registry.npmmirror.com for npm).
#   - CGO_ENABLED=0: this stage is Debian-based while the runtime is
#     dragonwell:21 (Anolis), so a static binary runs regardless of glibc.
#   - go.mod/go.sum are copied BEFORE the sources so the module download layer
#     stays cached across source-only changes.
FROM golang:1.27.1 AS rmqctl-build
WORKDIR /src
ENV GOPROXY=https://goproxy.cn,direct \
    GOTOOLCHAIN=local \
    CGO_ENABLED=0
COPY rmqctl/go.mod rmqctl/go.sum ./
RUN go mod download
COPY rmqctl/ ./
RUN mkdir -p /out && go build -trimpath -ldflags "-s -w" -o /out/rmqctl main.go

# Shared runtime dependencies
# Note: a pinned dragonwell patch tag (e.g. 21.0.10-anolis) would be preferable for
# reproducibility, but such tags are not reachable through the deployment registry
# mirrors in use, so keep the rolling :21 tag until a verifiable pin is available.
FROM alibabadragonwell/dragonwell:21 AS runtime-base
WORKDIR /app
# Node.js + agent CLIs for the claude-code / qoder agent providers.
RUN curl -fsSL https://npmmirror.com/mirrors/node/v20.19.2/node-v20.19.2-linux-x64.tar.gz | tar xz -C /opt \
    && ln -s /opt/node-v20.19.2-linux-x64/bin/node /usr/local/bin/node \
    && ln -s /opt/node-v20.19.2-linux-x64/bin/npm /usr/local/bin/npm \
    && ln -s /opt/node-v20.19.2-linux-x64/bin/npx /usr/local/bin/npx \
    && npm config set registry https://registry.npmmirror.com \
    && npm install -g @anthropic-ai/claude-code @qoder-ai/qodercli \
    && ln -s /opt/node-v20.19.2-linux-x64/bin/claude /usr/local/bin/claude \
    && ln -s /opt/node-v20.19.2-linux-x64/bin/qodercli /usr/local/bin/qodercli
# rmqctl: RocketMQ MCP server / CLI used by the agent providers.
COPY --from=rmqctl-build /out/rmqctl /usr/local/bin/rmqctl
RUN chmod 0755 /usr/local/bin/rmqctl
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]

# deploy.sh target: package with the JAR prebuilt by a Maven container that
# mounts the host ~/.m2 cache, avoiding dependency downloads on every build.
# Keep this before the build stage so Docker's legacy builder can stop here.
FROM runtime-base AS runtime-prebuilt
COPY server/target/*.jar app.jar

# Maven build stage used by the default CI / docker compose target.
FROM alibabadragonwell/dragonwell:21 AS build
RUN curl -fsSL https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz | tar xz -C /opt \
    && ln -s /opt/apache-maven-3.9.9/bin/mvn /usr/local/bin/mvn
WORKDIR /app
COPY server/pom.xml .
RUN mvn dependency:go-offline
COPY server/src ./src
COPY server/style ./style
RUN mvn package -DskipTests

# Default (last) target: build the JAR inside Docker for CI / docker compose.
FROM runtime-base AS runtime
COPY --from=build /app/target/*.jar app.jar
