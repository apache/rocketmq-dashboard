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
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]

# deploy.sh target: package with the JAR prebuilt by a Maven container that
# mounts the host ~/.m2 cache, avoiding dependency downloads on every build.
# Keep this before the build stage so Docker's legacy builder can stop here.
FROM runtime-base AS runtime-prebuilt
COPY target/*.jar app.jar

# Maven build stage used by the default CI / docker compose target.
FROM alibabadragonwell/dragonwell:21 AS build
RUN curl -fsSL https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz | tar xz -C /opt \
    && ln -s /opt/apache-maven-3.9.9/bin/mvn /usr/local/bin/mvn
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
COPY style ./style
RUN mvn package -DskipTests

# Default (last) target: build the JAR inside Docker for CI / docker compose.
FROM runtime-base AS runtime
COPY --from=build /app/target/*.jar app.jar
