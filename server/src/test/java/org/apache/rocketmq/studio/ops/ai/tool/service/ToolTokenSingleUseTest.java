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
package org.apache.rocketmq.studio.ops.ai.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.MutationOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionException;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolInvocation;
import org.apache.rocketmq.studio.ops.ai.tool.filter.ToolFilterChain;
import org.apache.rocketmq.studio.ops.ai.tool.filter.ToolMutationFilter;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Acceptance for single-use confirmation tokens: the same token must admit at most one
 * non-idempotent mutation, replayed and concurrent applies must be rejected with an
 * identifiable result, and independent previews must not interfere with each other.
 */
class ToolTokenSingleUseTest {

    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-11T02:00:00Z"), ZoneOffset.UTC);
    private static final Map<String, Object> INPUT = Map.of("instanceId", "instance-dev", "topic", "orders");

    private final ToolCatalog catalog = new ToolCatalog(new DefaultResourceLoader());
    private final ToolTokenService tokens = new ToolTokenService(new ObjectMapper(), CLOCK, SECRET);
    private final CountingHandler handler = new CountingHandler();
    private final ToolFilterChain chain = new ToolFilterChain(List.of(new ToolMutationFilter(tokens, true)));

    @Test
    void replayedTokenIsRejectedAndDoesNotExecuteTwice() {
        String token = preview();

        apply(token);
        assertThat(handler.executions.get()).isEqualTo(1);

        assertThatThrownBy(() -> apply(token)).isInstanceOfSatisfying(ToolExecutionException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo("CONFLICT"));
        assertThat(handler.executions.get()).isEqualTo(1);
    }

    @Test
    @Timeout(30)
    void concurrentAppliesExecuteTheMutationExactlyOnce() throws Exception {
        String token = preview();
        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            CyclicBarrier rendezvous = new CyclicBarrier(callers);
            List<Future<String>> outcomes = new ArrayList<>();
            for (int caller = 0; caller < callers; caller++) {
                outcomes.add(pool.submit(applyAfterBarrier(rendezvous, token)));
            }
            long executed = 0;
            for (Future<String> outcome : outcomes) {
                assertThat(outcome.get(10, TimeUnit.SECONDS)).isIn("EXECUTED", "CONFLICT");
                if ("EXECUTED".equals(outcome.get())) {
                    executed++;
                }
            }
            assertThat(executed).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(handler.executions.get()).isEqualTo(1);
    }

    @Test
    void distinctPreviewsIssueIndependentTokens() {
        String first = preview();
        String second = preview();
        assertThat(first).isNotEqualTo(second);

        apply(first);
        apply(second);
        assertThat(handler.executions.get()).isEqualTo(2);

        assertThatThrownBy(() -> apply(first)).isInstanceOfSatisfying(ToolExecutionException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo("CONFLICT"));
        assertThatThrownBy(() -> apply(second)).isInstanceOfSatisfying(ToolExecutionException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo("CONFLICT"));
        assertThat(handler.executions.get()).isEqualTo(2);
    }

    private Callable<String> applyAfterBarrier(CyclicBarrier rendezvous, String token) {
        return () -> {
            rendezvous.await(10, TimeUnit.SECONDS);
            try {
                apply(token);
                return "EXECUTED";
            } catch (ToolExecutionException failure) {
                return failure.getErrorCode();
            }
        };
    }

    private String preview() {
        Map<String, Object> input = new LinkedHashMap<>(INPUT);
        input.put("dry_run", true);
        MutationOutput<?> output = (MutationOutput<?>) chain.execute(
                new ToolInvocation(context(input), handler));
        assertThat(output.status()).isEqualTo(MutationOutput.Status.PLANNED);
        assertThat(output.confirmToken()).isNotBlank();
        return output.confirmToken();
    }

    private void apply(String token) {
        Map<String, Object> input = new LinkedHashMap<>(INPUT);
        input.put("confirm_token", token);
        MutationOutput<?> output = (MutationOutput<?>) chain.execute(
                new ToolInvocation(context(input), handler));
        assertThat(output.status()).isEqualTo(MutationOutput.Status.EXECUTED);
    }

    private ToolExecutionContext context(Map<String, Object> input) {
        return ToolExecutionContext.of("instance-dev", catalog.getDefinition("rmq.topic.update"), input, "alice");
    }

    private static class CountingHandler extends MutationToolHandler<Map<String, Object>, Object> {
        private final AtomicInteger executions = new AtomicInteger();

        @SuppressWarnings("unchecked")
        private CountingHandler() {
            super((Class<Map<String, Object>>) (Class<?>) Map.class);
        }

        @Override
        public String name() {
            return "rmq.topic.update";
        }

        @Override
        public ToolPlan preview(Map<String, Object> input, ToolExecutionContext context) {
            return ToolPlan.builder("Create topic").build();
        }

        @Override
        public Object execute(Map<String, Object> input, ToolExecutionContext context) {
            executions.incrementAndGet();
            return new LinkedHashMap<>(input);
        }
    }
}
