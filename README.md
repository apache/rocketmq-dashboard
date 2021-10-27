## [Apache RocketMQ](https://github.com/apache/rocketmq) Dashboard [![Build Status](https://api.travis-ci.com/apache/rocketmq-dashboard.svg?branch=master)](https://travis-ci.com/github/apache/rocketmq-dashboard) [![Coverage Status](https://coveralls.io/repos/github/apache/rocketmq-dashboard/badge.svg?branch=master)](https://coveralls.io/github/apache/rocketmq-dashboard?branch=master)
[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![Average time to resolve an issue](http://isitmaintained.com/badge/resolution/apache/rocketmq-dashboard.svg)](http://isitmaintained.com/project/apache/rocketmq-dashboard "Average time to resolve an issue")
[![Percentage of issues still open](http://isitmaintained.com/badge/open/apache/rocketmq-dashboard.svg)](http://isitmaintained.com/project/apache/rocketmq-dashboard "Percentage of issues still open")
[![Twitter Follow](https://img.shields.io/twitter/follow/ApacheRocketMQ?style=social)](https://twitter.com/intent/follow?screen_name=ApacheRocketMQ)
## Quick Start

### Run with docker

#### Pull from [docker hub(rocketmq-dashboard)](https://hub.docker.com/r/apacherocketmq/rocketmq-dashboard/tags)

```shell
docker pull apacherocketmq/rocketmq-dashboard:latest
```

#### Run it (use your own `rocketmq.namesrv.addr` and `port`)

```shell
docker run -d --name rocketmq-dashboard -e "JAVA_OPTS=-Drocketmq.namesrv.addr=127.0.0.1:9876" -p 8080:8080 -t apacherocketmq/rocketmq-dashboard:latest
```

### Run with source code


#### Prerequisite
1. 64bit OS, Linux/Unix/Mac is recommended;
2. 64bit JDK 1.8+;
3. Maven 3.2.x;

#### Maven spring-boot run

```shell
mvn spring-boot:run
```
or

#### Maven build and run

```shell
mvn clean package -Dmaven.test.skip=true
java -jar target/rocketmq-dashboard-1.0.1-SNAPSHOT.jar
```

#### Tips
* If you download the package slowly, you can change maven's mirror(maven's settings.xml)

  ```
  <mirrors>
      <mirror>
            <id>alimaven</id>
            <name>aliyun maven</name>
            <url>http://maven.aliyun.com/nexus/content/groups/public/</url>
            <mirrorOf>central</mirrorOf>        
      </mirror>
  </mirrors>
  ```

* Change the rocketmq.config.namesrvAddr in resource/application.properties.(or you can change it in ops page)

## UserGuide

[English](https://github.com/apache/rocketmq-dashboard/blob/master/docs/1_0_0/UserGuide_EN.md)

[中文](https://github.com/apache/rocketmq-dashboard/blob/master/docs/1_0_0/UserGuide_CN.md)

## Contributing

We are always very happy to have contributions, whether for trivial cleanups or big new features. Please see the RocketMQ main website to read the [details](http://rocketmq.apache.org/docs/how-to-contribute/).

## License
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html) Copyright (C) Apache Software Foundation
