FROM openjdk:8-jdk-slim-bullseye
VOLUME /tmp
# LABEL MAINTAINER liudian@szgenjoy.com
ADD ./target/rocketmq-dashboard-1.0.1-SNAPSHOT.jar rocketmq-dashboard.jar
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Xms512m -Xmx1024m -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom -Duser.timezone=Asia/Shanghai -jar /rocketmq-dashboard.jar" ]