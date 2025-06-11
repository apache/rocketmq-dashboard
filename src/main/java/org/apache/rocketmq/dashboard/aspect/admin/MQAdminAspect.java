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
import org.apache.rocketmq.dashboard.service.client.MQAdminInstance;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Service;

@Aspect
@Service
@Slf4j
public class MQAdminAspect {

    private final GenericObjectPool<MQAdminExt> mqAdminExtPool;

    public MQAdminAspect(GenericObjectPool<MQAdminExt> mqAdminExtPool) {
        this.mqAdminExtPool = mqAdminExtPool;
    }

    @Pointcut("execution(* org.apache.rocketmq.dashboard.service.client.MQAdminExtImpl..*(..))")
    public void mQAdminMethodPointCut() {

    }

    @Around(value = "mQAdminMethodPointCut()")
    public Object aroundMQAdminMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        Object obj;
        try {
            MQAdminInstance.createMQAdmin(mqAdminExtPool);
            obj = joinPoint.proceed();
            return obj;
        } finally {
            MQAdminInstance.returnMQAdmin(mqAdminExtPool);
            log.debug("op=look method={} cost={}", joinPoint.getSignature().getName(), System.currentTimeMillis() - start);
        }
    }
}
