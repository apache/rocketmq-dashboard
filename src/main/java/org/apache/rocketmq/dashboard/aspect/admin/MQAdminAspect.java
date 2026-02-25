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
package org.apache.rocketmq.dashboard.aspect.admin;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.rocketmq.dashboard.admin.UserMQAdminPoolManager;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.service.client.MQAdminInstance;
import org.apache.rocketmq.dashboard.util.UserInfoContext;
import org.apache.rocketmq.dashboard.util.WebUtil;
import org.apache.rocketmq.remoting.protocol.body.UserInfo;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Aspect
@Component
@Slf4j
public class MQAdminAspect {

    @Autowired
    private UserMQAdminPoolManager userMQAdminPoolManager;

    @Autowired
    private GenericObjectPool<MQAdminExt> mqAdminExtPool;

    @Autowired
    private RMQConfigure rmqConfigure;

    private static final Set<String> METHODS_TO_CHECK = new HashSet<>();

    static {
        METHODS_TO_CHECK.add("getUser");
        METHODS_TO_CHECK.add("examineBrokerClusterInfo");
        METHODS_TO_CHECK.add("examineConsumerConnectionInfo");
        METHODS_TO_CHECK.add("examineConsumeStats");
        METHODS_TO_CHECK.add("examineProducerConnectionInfo");
        METHODS_TO_CHECK.add("fetchBrokerRuntimeStats");
        METHODS_TO_CHECK.add("fetchAllTopicList");
        METHODS_TO_CHECK.add("examineTopicRouteInfo");
        METHODS_TO_CHECK.add("queryTopicConsumeByWho");
    }

    // Pointcut remains the same, targeting methods in MQAdminExtImpl
    @Pointcut("execution(* org.apache.rocketmq.dashboard.service.client.MQAdminExtImpl..*(..))")
    public void mQAdminMethodPointCut() {
    }

    @Pointcut("execution(* org.apache.rocketmq.dashboard.service.client.ProxyAdminImpl..*(..))")
    public void proxyAdminMethodPointCut() {
    }

    @Around(value = "mQAdminMethodPointCut()||proxyAdminMethodPointCut()")
    public Object aroundMQAdminMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        MQAdminExt mqAdminExt = null; // The MQAdminExt instance borrowed from the pool
        UserInfo currentUserInfo = null;     // The user initiating the operation
        String methodName = joinPoint.getSignature().getName();

        try {
            if (isPoolConfigIsolatedByUser(rmqConfigure.isLoginRequired(), rmqConfigure.getAuthMode(), methodName)) {
                currentUserInfo = (UserInfo) UserInfoContext.get(WebUtil.USER_NAME);
                // 2. Borrow the user-specific MQAdminExt instance.
                //    currentUser.getName() is assumed to be the AccessKey, and currentUser.getPassword() is SecretKey.
                mqAdminExt = userMQAdminPoolManager.borrowMQAdminExt(currentUserInfo.getUsername(), currentUserInfo.getPassword());

                // 3. Set the borrowed MQAdminExt instance into the ThreadLocal for MQAdminInstance.
                //    This makes it available to MQAdminExtImpl methods.
                MQAdminInstance.setCurrentMQAdminExt(mqAdminExt);
                log.debug("MQAdminExt borrowed for user {} and set in ThreadLocal.", currentUserInfo.getUsername());
            } else {
                mqAdminExt = mqAdminExtPool.borrowObject(); // Fallback to a default MQAdminExt if no user is provided
                MQAdminInstance.setCurrentMQAdminExt(mqAdminExt);
            }
            // 4. Proceed with the original method execution.
            return joinPoint.proceed();

        } finally {

            if (currentUserInfo != null) {
                if (mqAdminExt != null) {
                    userMQAdminPoolManager.returnMQAdminExt(currentUserInfo.getUsername(), mqAdminExt);
                    MQAdminInstance.clearCurrentMQAdminExt();
                    log.debug("MQAdminExt returned for user {} and cleared from ThreadLocal.", currentUserInfo.getUsername());
                }
                log.debug("Operation {} for user {} cost {}ms",
                        methodName,
                        currentUserInfo.getUsername(),
                        System.currentTimeMillis() - start);
            } else {
                if (mqAdminExt != null) {
                    mqAdminExtPool.returnObject(mqAdminExt);
                    MQAdminInstance.clearCurrentMQAdminExt();
                    log.debug("MQAdminExt returned to default pool and cleared from ThreadLocal.");
                }
                log.debug("Operation {} cost {}ms",
                        methodName,
                        System.currentTimeMillis() - start);
            }

        }
    }

    private boolean isPoolConfigIsolatedByUser(boolean loginRequired, String authMode, String methodName) {
        if (!loginRequired || authMode.equals("file")) {
            return false;
        } else {
            return !METHODS_TO_CHECK.contains(methodName);
        }
    }


}
