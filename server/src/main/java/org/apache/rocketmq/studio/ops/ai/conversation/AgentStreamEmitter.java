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
package org.apache.rocketmq.studio.ops.ai.conversation;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;

/** Bounded, ordered socket writes: slow observers never execute on run or scheduler threads. */
final class AgentStreamEmitter extends SseEmitter {
    static final int MAX_PENDING_FRAMES = 256;
    static final int MAX_PENDING_BYTES = 1024 * 1024;
    private static final int MAX_SENDERS = 128;
    private final ExecutorService executor;
    private final Object queueLock = new Object();
    private final Deque<Frame> queue = new ArrayDeque<>();
    private long pendingBytes;
    private boolean draining;
    private boolean closed;
    private Throwable failure;
    private Thread writer;

    private AgentStreamEmitter(long timeout, ExecutorService executor) {
        super(timeout);
        this.executor = executor;
        onCompletion(this::cancel);
        onTimeout(this::cancel);
        onError(ignored -> cancel());
    }

    @Override
    public void send(SseEventBuilder event) throws IOException {
        Set<DataWithMediaType> parts = new LinkedHashSet<>(event.build());
        long bytes = 0;
        for (DataWithMediaType part : parts) {
            if (!(part.getData() instanceof String text)) {
                throw new IOException("Agent stream frames must be serialized text");
            }
            bytes += 2L * text.length();
        }
        synchronized (queueLock) {
            if (closed) throw new IOException("Agent stream observer is closed");
            if (queue.size() >= MAX_PENDING_FRAMES || bytes + pendingBytes > MAX_PENDING_BYTES) {
                fail(new IOException("Agent stream observer exceeded its pending output budget"));
                throw new IOException("Agent stream observer exceeded its pending output budget");
            }
            queue.addLast(new Frame(parts, bytes));
            pendingBytes += bytes;
            if (!startDrain()) throw new IOException("Agent stream sender capacity exhausted");
        }
    }

    @Override
    public void complete() {
        synchronized (queueLock) {
            if (closed) return;
            closed = true;
            // One reserved control slot: done must follow the frames already admitted.
            queue.addLast(new Frame(null, 0));
            startDrain();
        }
    }

    @Override
    public void completeWithError(Throwable throwable) {
        synchronized (queueLock) {
            if (closed) return;
            fail(throwable);
        }
    }

    private void fail(Throwable throwable) {
        closed = true;
        failure = throwable;
        queue.clear();
        pendingBytes = 0;
        if (writer != null) writer.interrupt();
        startDrain();
    }

    /** Called with queueLock, never with a socket write lock. No caller-runs fallback. */
    private boolean startDrain() {
        if (draining) return true;
        draining = true;
        try {
            executor.execute(this::drain);
            return true;
        } catch (RejectedExecutionException rejected) {
            draining = false;
            closed = true;
            queue.clear();
            pendingBytes = 0;
            // No writer exists for this observer, so completion cannot contend with its socket send.
            super.completeWithError(new IOException("Agent stream sender capacity exhausted"));
            return false;
        }
    }

    private void drain() {
        Thread current = Thread.currentThread();
        synchronized (queueLock) {
            writer = current;
        }
        try {
            while (true) {
                Frame frame;
                Throwable failed;
                synchronized (queueLock) {
                    failed = failure;
                    frame = failed == null ? queue.pollFirst() : null;
                    if (frame != null) pendingBytes -= frame.bytes();
                    if (failed == null && frame == null) {
                        draining = false;
                        writer = null;
                        return;
                    }
                }
                if (failed != null) {
                    super.completeWithError(failed);
                    return;
                }
                if (frame.parts() == null) {
                    super.complete();
                    return;
                }
                super.send(frame.parts());
            }
        } catch (IOException | RuntimeException exception) {
            synchronized (queueLock) {
                closed = true;
                queue.clear();
                pendingBytes = 0;
            }
            super.completeWithError(exception);
        } finally {
            synchronized (queueLock) {
                if (writer == current) writer = null;
            }
            // Overflow cancellation must not leak the interrupt into the next observer's task.
            Thread.interrupted();
        }
    }

    private void cancel() {
        synchronized (queueLock) {
            closed = true;
            queue.clear();
            pendingBytes = 0;
            if (writer != null && writer != Thread.currentThread()) writer.interrupt();
        }
    }

    private record Frame(Set<DataWithMediaType> parts, long bytes) {
    }

    static final class Factory implements LongFunction<SseEmitter>, AutoCloseable {
        private final ExecutorService senders = new ThreadPoolExecutor(0, MAX_SENDERS,
                30, TimeUnit.SECONDS, new SynchronousQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable, "ai-stream-sender");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());

        @Override
        public SseEmitter apply(long timeout) {
            return new AgentStreamEmitter(timeout, senders);
        }

        @Override
        public void close() {
            senders.shutdownNow();
        }
    }
}
