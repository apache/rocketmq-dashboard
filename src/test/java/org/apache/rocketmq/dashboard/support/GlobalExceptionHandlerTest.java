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
package org.apache.rocketmq.dashboard.support;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.rocketmq.dashboard.exception.ServiceException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.Silent.class)
public class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Mock
    private HttpServletRequest request;

    @Test
    public void testHandleServiceException() throws Exception {
        JsonResult<Object> result = handler.jsonErrorHandler(request, new ServiceException(1001, "service error"));
        Assert.assertNotNull(result);
        Assert.assertEquals(1001, result.getStatus());
        Assert.assertEquals("service error", result.getErrMsg());
    }

    @Test
    public void testHandleGenericException() throws Exception {
        JsonResult<Object> result = handler.jsonErrorHandler(request, new RuntimeException("generic error"));
        Assert.assertNotNull(result);
        Assert.assertEquals(-1, result.getStatus());
        Assert.assertEquals("generic error", result.getErrMsg());
    }

    @Test
    public void testHandleExceptionWithNullMessage() throws Exception {
        RuntimeException ex = new RuntimeException();
        JsonResult<Object> result = handler.jsonErrorHandler(request, ex);
        Assert.assertNotNull(result);
        Assert.assertEquals(-1, result.getStatus());
        Assert.assertEquals(ex.toString(), result.getErrMsg());
    }

    @Test
    public void testHandleNullException() throws Exception {
        JsonResult<Object> result = handler.jsonErrorHandler(request, null);
        Assert.assertNull(result);
    }
}
