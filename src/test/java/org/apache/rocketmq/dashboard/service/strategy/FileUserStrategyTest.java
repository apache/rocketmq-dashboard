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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.exception.ServiceException;
import org.apache.rocketmq.dashboard.model.User;
import org.apache.rocketmq.remoting.protocol.body.UserInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class FileUserStrategyTest {

    @Mock
    private RMQConfigure configure;

    private FileUserStrategy strategy;

    @Before
    public void setUp() {
        strategy = new FileUserStrategy();
        ReflectionTestUtils.setField(strategy, "configure", configure);
        // Point to a non-existent directory so the store falls back to the
        // classpath users.properties bundled with the test resources.
        when(configure.getRocketMqDashboardDataPath()).thenReturn("/tmp/no-such-dashboard-data-dir");
    }

    @Test
    public void testAfterPropertiesSetSkipsStoreWhenLoginNotRequired() throws Exception {
        when(configure.isLoginRequired()).thenReturn(false);
        strategy.afterPropertiesSet();
        assertNull(ReflectionTestUtils.getField(strategy, "fileBasedUserInfoStore"));
    }

    @Test
    public void testLoadsUsersFromClasspathWhenFileMissing() throws Exception {
        when(configure.isLoginRequired()).thenReturn(true);
        strategy.afterPropertiesSet();

        UserInfo admin = strategy.getUserInfoByUsername("admin");
        assertNotNull(admin);
        assertEquals("admin", admin.getUsername());
        assertEquals("admin", admin.getPassword());
        // users.properties has no role suffix, so type defaults to normal
        assertEquals("normal", admin.getUserType());

        assertNull(strategy.getUserInfoByUsername("ghost"));
    }

    @Test
    public void testSuperRoleMapping() throws Exception {
        when(configure.isLoginRequired()).thenReturn(true);
        strategy.afterPropertiesSet();

        FileUserStrategy.FileBasedUserInfoStore store =
            (FileUserStrategy.FileBasedUserInfoStore) ReflectionTestUtils.getField(strategy, "fileBasedUserInfoStore");
        assertNotNull(store);
        String content = "root=secret,1\nplain=pwd";
        store.load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        UserInfo root = strategy.getUserInfoByUsername("root");
        assertNotNull(root);
        assertEquals("secret", root.getPassword());
        assertEquals("super", root.getUserType());

        UserInfo plain = strategy.getUserInfoByUsername("plain");
        assertNotNull(plain);
        assertEquals("normal", plain.getUserType());
    }

    @Test
    public void testQueryReturnsClonedUser() throws Exception {
        when(configure.isLoginRequired()).thenReturn(true);
        strategy.afterPropertiesSet();

        FileUserStrategy.FileBasedUserInfoStore store =
            (FileUserStrategy.FileBasedUserInfoStore) ReflectionTestUtils.getField(strategy, "fileBasedUserInfoStore");
        store.load(new ByteArrayInputStream("admin=admin".getBytes(StandardCharsets.UTF_8)));

        User original = store.queryByName("admin");
        User cloned = store.queryByUsernameAndPassword("admin");
        assertNotNull(cloned);
        assertEquals(original.getName(), cloned.getName());
        assertEquals(original.getPassword(), cloned.getPassword());
    }

    @Test(expected = ServiceException.class)
    public void testLoadFailureThrowsServiceException() throws Exception {
        when(configure.isLoginRequired()).thenReturn(true);
        strategy.afterPropertiesSet();

        FileUserStrategy.FileBasedUserInfoStore store =
            (FileUserStrategy.FileBasedUserInfoStore) ReflectionTestUtils.getField(strategy, "fileBasedUserInfoStore");
        // null stream forces a FileReader on the non-existent file path
        store.load(null);
    }
}
