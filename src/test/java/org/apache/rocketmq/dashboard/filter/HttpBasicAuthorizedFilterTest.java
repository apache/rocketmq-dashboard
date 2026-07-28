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
package org.apache.rocketmq.dashboard.filter;

import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HttpBasicAuthorizedFilterTest {

    private final HttpBasicAuthorizedFilter filter = new HttpBasicAuthorizedFilter();

    @Test
    public void testDoFilterSetsHeadersAndContinuesChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/topic/list.query");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals("UTF-8", response.getCharacterEncoding());
        assertTrue(response.getContentType().contains("application/json"));
        assertEquals("Basic realm=\"rocketmq\"", response.getHeader("WWW-Authenticate"));
        // The chain must have been invoked with the same request
        assertSame(request, chain.getRequest());
    }

    @Test
    public void testInitAndDestroyAreNoOps() throws Exception {
        filter.init(null);
        filter.destroy();
    }
}
