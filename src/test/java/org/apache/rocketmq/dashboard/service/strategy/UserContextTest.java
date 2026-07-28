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
package org.apache.rocketmq.dashboard.service.strategy;

import java.util.HashMap;
import java.util.Map;
import org.apache.rocketmq.remoting.protocol.body.UserInfo;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class UserContextTest {

    private UserContext userContext;
    private UserStrategy aclStrategy;
    private UserStrategy fileStrategy;
    private Map<String, UserStrategy> strategies;

    private UserStrategy namedStrategy(String username) {
        return name -> UserInfo.of(username, "pwd", "normal");
    }

    @Before
    public void setUp() {
        userContext = new UserContext();
        aclStrategy = namedStrategy("from-acl");
        fileStrategy = namedStrategy("from-file");
        strategies = new HashMap<>();
        strategies.put("aclUserStrategy", aclStrategy);
        strategies.put("fileUserStrategy", fileStrategy);
        ReflectionTestUtils.setField(userContext, "userStrategies", strategies);
    }

    private UserStrategy selectedStrategy() {
        return (UserStrategy) ReflectionTestUtils.getField(userContext, "userStrategy");
    }

    @Test
    public void testInitSelectsAclStrategy() {
        ReflectionTestUtils.setField(userContext, "authMode", "acl");
        userContext.init();
        assertSame(aclStrategy, selectedStrategy());
        assertEquals("from-acl", userContext.queryByUsername("any").getUsername());
    }

    @Test
    public void testInitSelectsFileStrategy() {
        ReflectionTestUtils.setField(userContext, "authMode", "file");
        userContext.init();
        assertSame(fileStrategy, selectedStrategy());
        assertEquals("from-file", userContext.queryByUsername("any").getUsername());
    }

    @Test
    public void testInitDefaultsToFileStrategyForUnknownMode() {
        ReflectionTestUtils.setField(userContext, "authMode", "whatever");
        userContext.init();
        assertSame(fileStrategy, selectedStrategy());
    }

    @Test
    public void testInitIsCaseInsensitive() {
        ReflectionTestUtils.setField(userContext, "authMode", "ACL");
        userContext.init();
        assertSame(aclStrategy, selectedStrategy());
    }
}
