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
package org.apache.rocketmq.dashboard.permission;

import com.google.common.collect.Lists;
import com.google.common.io.Files;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.rocketmq.dashboard.BaseTest;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.model.User;
import org.apache.rocketmq.dashboard.model.UserInfo;
import org.apache.rocketmq.dashboard.permisssion.PermissionAspect;
import org.apache.rocketmq.dashboard.service.impl.PermissionServiceImpl;
import org.apache.rocketmq.dashboard.util.JsonUtil;
import org.apache.rocketmq.dashboard.util.WebUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PermissionAspectTest extends BaseTest {

    @InjectMocks
    private PermissionAspect permissionAspect;

    @Mock
    private RMQConfigure configure;

    @Spy
    private PermissionServiceImpl permissionService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        autoInjection();
        when(configure.isLoginRequired()).thenReturn(true);
        when(configure.getDashboardCollectData()).thenReturn("/tmp/rocketmq-console/test/data");
    }

    @Test
    public void testCheckPermission() throws Throwable {
        ReflectionTestUtils.setField(permissionAspect, "configure", configure);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        permissionService.afterPropertiesSet();
        ReflectionTestUtils.setField(permissionAspect, "permissionService", permissionService);

        // user not login
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            permissionAspect.checkPermission(joinPoint);
        } catch (Throwable throwable) {
            Assert.assertEquals(throwable.getMessage(), "user not login");
        }
        // userRole is admin
        UserInfo info = new UserInfo();
        User adminUser = new User("admin", "admin", 1);
        info.setUser(adminUser);
        request.getSession().setAttribute(WebUtil.USER_INFO, info);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        permissionAspect.checkPermission(joinPoint);

        // userRole is ordinary
        User ordinaryUser = new User("user1", "user1", 0);
        info.setUser(ordinaryUser);
        request = new MockHttpServletRequest("", "/topic/deleteTopic.do");
        request.getSession().setAttribute(WebUtil.USER_INFO, info);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            permissionAspect.checkPermission(joinPoint);
        } catch (Throwable throwable) {
            Assert.assertEquals(throwable.getMessage(), "no permission");
        }

        // no permission
        request = new MockHttpServletRequest("", "/topic/route.query");
        request.getSession().setAttribute(WebUtil.USER_INFO, info);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        permissionAspect.checkPermission(joinPoint);
    }

    @Test
    public void testFileBasedPermissionStoreWatch() throws Exception {
        when(configure.getRocketMqDashboardDataPath()).thenReturn("/tmp/rocketmq-console/test/data");
        Map<String, Map<String, List<String>>> rolePermsMap = new HashMap<>();
        Map<String, List<String>> rolePerms = new HashMap<>();
        List<String> accessUrls = Lists.asList("/topic/route.query", new String[] {"/topic/stats.query"});
        rolePerms.put("admin", accessUrls);
        rolePermsMap.put("rolePerms", rolePerms);
        File file = createTestFile(rolePermsMap);
        new PermissionServiceImpl.PermissionFileStore(configure);
        rolePerms.put("ordinary", accessUrls);
        // Update file and flush to yaml file
        Files.write(JsonUtil.obj2String(rolePerms).getBytes(), file);
        Thread.sleep(1000);
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    private File createTestFile(Map map) throws Exception {
        String fileName = "/tmp/rocketmq-console/test/data/role-permission.yml";
        File file = new File(fileName);
        file.delete();
        file.createNewFile();
        Files.write(JsonUtil.obj2String(map).getBytes(), file);
        return file;
    }
}
