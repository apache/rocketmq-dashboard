# Stage 1: Build
FROM alibabadragonwell/dragonwell:21 AS build
RUN curl -fsSL https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz | tar xz -C /opt \
    && ln -s /opt/apache-maven-3.9.9/bin/mvn /usr/local/bin/mvn
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
COPY style ./style
RUN mvn package -DskipTests

# Stage 2: Runtime
FROM alibabadragonwell/dragonwell:21
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
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]
