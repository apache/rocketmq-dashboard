# 从源码构建 Apache RocketMQ 开源镜像（默认 develop 分支）
# 用法: ./build.sh（自动判断地理位置，国内走阿里源）
#       或 docker compose build，可用 --build-arg ROCKETMQ_BRANCH=rocketmq-all-5.5.0 指定分支/tag

FROM maven:3.9-eclipse-temurin-17 AS builder

ARG ROCKETMQ_REPO=https://github.com/apache/rocketmq.git
ARG ROCKETMQ_BRANCH=develop
# true 时 apt / Maven 走阿里镜像源（国内网络加速），由 build.sh 自动探测
ARG USE_CN_MIRROR=false

RUN if [ "$USE_CN_MIRROR" = "true" ]; then \
        sed -i 's|http://archive.ubuntu.com|https://mirrors.aliyun.com|g; s|http://security.ubuntu.com|https://mirrors.aliyun.com|g' \
            /etc/apt/sources.list.d/ubuntu.sources /etc/apt/sources.list 2>/dev/null || true; \
        mkdir -p /root/.m2 && printf '%s\n' \
            '<settings><mirrors><mirror>' \
            '<id>aliyun</id><mirrorOf>central</mirrorOf>' \
            '<url>https://maven.aliyun.com/repository/public</url>' \
            '</mirror></mirrors></settings>' > /root/.m2/settings.xml; \
    fi

RUN apt-get update && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*

RUN git clone --depth 1 -b ${ROCKETMQ_BRANCH} ${ROCKETMQ_REPO} /build/rocketmq

WORKDIR /build/rocketmq
RUN mvn -Prelease-all -DskipTests -Dspotbugs.skip=true -Dcheckstyle.skip=true \
        -Dmaven.javadoc.skip=true -T 2C clean install \
    && mkdir /dist \
    && cp -r distribution/target/rocketmq-*/rocketmq-*/* /dist/

FROM alibabadragonwell/dragonwell:17-anolis

# RocketMQ 启动脚本依赖 JAVA_HOME，构建期断言基础镜像已正确设置
RUN test -x "$JAVA_HOME/bin/java" && test -x "$JAVA_HOME/bin/javac" \
    && useradd -m rocketmq
COPY --from=builder --chown=rocketmq:rocketmq /dist /home/rocketmq
# 预创建 store/logs 并赋属主，命名卷首次初始化会继承镜像内目录的属主
RUN mkdir -p /home/rocketmq/store /home/rocketmq/logs \
    && chown -R rocketmq:rocketmq /home/rocketmq/store /home/rocketmq/logs

USER rocketmq
WORKDIR /home/rocketmq
ENV ROCKETMQ_HOME=/home/rocketmq

# 9876 namesrv | 10909/10911/10912 broker | 8080 proxy remoting | 8081 proxy grpc
EXPOSE 9876 10909 10911 10912 8080 8081
