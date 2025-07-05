/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.dashboard.config;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.MixAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.ErrorPageRegistrar;
import org.springframework.boot.web.server.ErrorPageRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.apache.rocketmq.client.ClientConfig.SEND_MESSAGE_WITH_VIP_CHANNEL_PROPERTY;

@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "rocketmq.config")
public class RMQConfigure {

    private Logger logger = LoggerFactory.getLogger(RMQConfigure.class);
    //use rocketmq.namesrv.addr first,if it is empty,than use system proerty or system env
    @Getter
    private volatile String namesrvAddr = System.getProperty(MixAll.NAMESRV_ADDR_PROPERTY, System.getenv(MixAll.NAMESRV_ADDR_ENV));

    @Setter
    @Getter
    private volatile String proxyAddr;

    @Getter
    private volatile String isVIPChannel = System.getProperty(SEND_MESSAGE_WITH_VIP_CHANNEL_PROPERTY, "true");


    @Setter
    private String dataPath = "/tmp/rocketmq-console/data";

    @Getter
    private boolean enableDashBoardCollect;

    @Setter
    @Getter
    private boolean loginRequired = false;


    private String accessKey;

    @Setter
    @Getter
    private String secretKey;

    @Setter
    @Getter
    private boolean useTLS = false;

    @Setter
    @Getter
    private Long timeoutMillis;

    @Getter
    private List<String> namesrvAddrs = new ArrayList<>();

    @Getter
    private List<String> proxyAddrs = new ArrayList<>();

    @Setter
    @Getter
    private Integer clientCallbackExecutorThreads = 4;

    @Setter
    @Getter
    private String authMode = "file";

    public void setProxyAddrs(List<String> proxyAddrs) {
        this.proxyAddrs = proxyAddrs;
        if (CollectionUtils.isNotEmpty(proxyAddrs)) {
            this.setProxyAddr(proxyAddrs.get(0));
        }
    }

    public void setNamesrvAddrs(List<String> namesrvAddrs) {
        this.namesrvAddrs = namesrvAddrs;
        if (CollectionUtils.isNotEmpty(namesrvAddrs)) {
            this.setNamesrvAddr(namesrvAddrs.get(0));
        }
    }

    public void setNamesrvAddr(String namesrvAddr) {
        if (StringUtils.isNotBlank(namesrvAddr)) {
            this.namesrvAddr = namesrvAddr;
            System.setProperty(MixAll.NAMESRV_ADDR_PROPERTY, namesrvAddr);
            logger.info("setNameSrvAddrByProperty nameSrvAddr={}", namesrvAddr);
        }
    }

    public boolean isACLEnabled() {
        return !(StringUtils.isAnyBlank(this.accessKey, this.secretKey) ||
                StringUtils.isAnyEmpty(this.accessKey, this.secretKey));
    }

    public String getRocketMqDashboardDataPath() {
        return dataPath;
    }

    public String getDashboardCollectData() {
        return dataPath + File.separator + "dashboard";
    }

    public void setIsVIPChannel(String isVIPChannel) {
        if (StringUtils.isNotBlank(isVIPChannel)) {
            this.isVIPChannel = isVIPChannel;
            System.setProperty(SEND_MESSAGE_WITH_VIP_CHANNEL_PROPERTY, isVIPChannel);
            logger.info("setIsVIPChannel isVIPChannel={}", isVIPChannel);
        }
    }

    public void setEnableDashBoardCollect(String enableDashBoardCollect) {
        this.enableDashBoardCollect = Boolean.valueOf(enableDashBoardCollect);
    }

    // Error Page process logic, move to a central configure later
    @Bean
    public ErrorPageRegistrar errorPageRegistrar() {
        return new MyErrorPageRegistrar();
    }

    private static class MyErrorPageRegistrar implements ErrorPageRegistrar {

        @Override
        public void registerErrorPages(ErrorPageRegistry registry) {
            registry.addErrorPages(new ErrorPage(HttpStatus.NOT_FOUND, "/404"));
        }

    }
}
