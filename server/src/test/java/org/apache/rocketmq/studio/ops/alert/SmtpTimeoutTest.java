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
package org.apache.rocketmq.studio.ops.alert;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The alert e-mail path hands {@code sender.send(...)} to the scheduler thread that every
 * {@code @Scheduled} job shares, so the JavaMail socket timeouts configured in
 * {@code application.yml} are what keep a slow or wedged SMTP relay from stalling the whole
 * backend. These tests pin the shipped defaults, the deployment overrides, and the behaviour of
 * a send aimed at a peer that accepts the connection and never answers.
 */
class SmtpTimeoutTest {

    private static final int SEND_GUARD_SECONDS = 25;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class));

    @Test
    void shippedMailPropertiesBoundEverySocketTimeoutTest() {
        runner.run(context -> {
            Properties properties = context.getBean(JavaMailSenderImpl.class).getJavaMailProperties();

            assertThat(properties)
                    .as("spring.mail.properties must bound every JavaMail socket timeout")
                    .containsKeys("mail.smtp.connectiontimeout", "mail.smtp.timeout", "mail.smtp.writetimeout");
            assertThat(timeoutMillis(properties, "mail.smtp.connectiontimeout")).isEqualTo(5000);
            assertThat(timeoutMillis(properties, "mail.smtp.timeout")).isEqualTo(10000);
            assertThat(timeoutMillis(properties, "mail.smtp.writetimeout")).isEqualTo(10000);
        });
    }

    @Test
    void socketTimeoutsAreDeploymentOverridableTest() {
        runner.withPropertyValues(
                        "STUDIO_ALERTING_SMTP_CONNECTION_TIMEOUT=1234",
                        "STUDIO_ALERTING_SMTP_READ_TIMEOUT=2345",
                        "STUDIO_ALERTING_SMTP_WRITE_TIMEOUT=3456")
                .run(context -> {
                    Properties properties = context.getBean(JavaMailSenderImpl.class).getJavaMailProperties();

                    assertThat(timeoutMillis(properties, "mail.smtp.connectiontimeout")).isEqualTo(1234);
                    assertThat(timeoutMillis(properties, "mail.smtp.timeout")).isEqualTo(2345);
                    assertThat(timeoutMillis(properties, "mail.smtp.writetimeout")).isEqualTo(3456);
                });
    }

    @Test
    void sendFailsFastWhenSmtpServerNeverAnswersTest() throws Exception {
        try (SilentSmtpServer server = new SilentSmtpServer()) {
            // A deployment that actually sends alerts has credentials configured, so the client
            // opens the transport and waits for the SMTP greeting instead of failing before any I/O.
            runner.withPropertyValues("spring.mail.host=127.0.0.1", "spring.mail.port=" + server.port(),
                            "spring.mail.username=probe-user", "spring.mail.password=probe-secret")
                    .run(context -> {
                        JavaMailSender sender = context.getBean(JavaMailSender.class);
                        SimpleMailMessage message = new SimpleMailMessage();
                        message.setFrom("studio-test@example.com");
                        message.setTo("recipient@example.com");
                        message.setSubject("[RocketMQ Studio] timeout probe");
                        message.setText("probe");

                        assertSendTerminates(sender, message);
                    });
        }
    }

    /**
     * Runs the send on a daemon thread so a wedged socket cannot outlive the test JVM, and fails
     * with the defect statement when the call is still blocked once the guard expires.
     */
    private void assertSendTerminates(JavaMailSender sender, SimpleMailMessage message) {
        ExecutorService executor = Executors.newSingleThreadExecutor(worker -> {
            Thread thread = new Thread(worker, "smtp-send-under-test");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<?> send = executor.submit(() -> sender.send(message));
            try {
                send.get(SEND_GUARD_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException blocked) {
                fail("sender.send() was still blocked after " + SEND_GUARD_SECONDS
                        + "s against an SMTP peer that accepted the connection and never replied: "
                        + "spring.mail.properties sets no mail.smtp.connectiontimeout / mail.smtp.timeout / "
                        + "mail.smtp.writetimeout, so a wedged relay pins its caller forever (in production "
                        + "that caller is the single @Scheduled dispatcher shared by all scheduled jobs)");
            } catch (ExecutionException rejected) {
                assertThat(rejected.getCause())
                        .as("the send must fail with a bounded mail error rather than hang")
                        .isInstanceOf(MailSendException.class)
                        .hasRootCauseInstanceOf(SocketTimeoutException.class);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail("interrupted while waiting for the SMTP send to return");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static int timeoutMillis(Properties properties, String key) {
        Object value = properties.get(key);
        assertThat(value).as("JavaMail property %s must be set", key).isNotNull();
        return Integer.parseInt(String.valueOf(value));
    }

    /**
     * Accepts SMTP connections and then stays silent: no 220 banner, no reply. JavaMail connects
     * successfully and waits for the greeting, which is exactly the read that an unbounded
     * {@code mail.smtp.timeout} never finishes.
     */
    private static final class SilentSmtpServer implements AutoCloseable {

        private final ServerSocket server;
        private final List<Socket> accepted = new ArrayList<>();
        private final Thread acceptor;

        private SilentSmtpServer() throws IOException {
            this.server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            this.acceptor = new Thread(this::acceptQuietly, "silent-smtp-acceptor");
            this.acceptor.setDaemon(true);
            this.acceptor.start();
        }

        private void acceptQuietly() {
            while (!server.isClosed()) {
                try {
                    accepted.add(server.accept());
                } catch (IOException closed) {
                    return;
                }
            }
        }

        private int port() {
            return server.getLocalPort();
        }

        @Override
        public void close() {
            try {
                server.close();
            } catch (IOException ignored) {
                // best effort: only used to unblock a hung client at the end of the test
            }
            for (Socket socket : accepted) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // best effort: closing the accepted sockets wakes the client read
                }
            }
        }
    }
}
