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
package org.apache.rocketmq.studio.ops.ai.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolError;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public class McpAuthenticationFilter extends OncePerRequestFilter {

    private final McpAuthenticator authenticator;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        final McpAuthentication authentication;
        try {
            authentication = authenticator.authenticate(request);
        } catch (McpAuthenticationException exception) {
            log.warn("MCP authentication failed: path={}, remoteAddress={}, status={}, message={}",
                    request.getRequestURI(), request.getRemoteAddr(),
                    HttpStatus.UNAUTHORIZED.value(), exception.getMessage());
            writeError(response, ToolError.MCP_AUTHENTICATION_FAILED, exception.getMessage());
            return;
        } catch (RuntimeException exception) {
            log.error("MCP authentication failed unexpectedly: path={}, remoteAddress={}",
                    request.getRequestURI(), request.getRemoteAddr(), exception);
            writeError(response, ToolError.MCP_AUTHENTICATION_INTERNAL_ERROR);
            return;
        }
        request.setAttribute(McpAuthentication.ATTRIBUTE, authentication);
        try {
            AuthenticatedUserContext.setUser(null, authentication.principal(), false);
            filterChain.doFilter(request, response);
        } finally {
            AuthenticatedUserContext.clear();
        }
    }

    private void writeError(HttpServletResponse response, ToolError error, Object... arguments)
            throws IOException {
        response.setStatus(error.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (error.httpStatus() == HttpStatus.UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, McpAuthenticator.ALGORITHM);
        }
        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "code", error.code(),
                "message", error.message(arguments),
                "hint", error.hint()));
    }
}
