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
package org.apache.rocketmq.studio.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the request-thread principal of {@link AuthenticatedUserContext}: a blank username clears
 * instead of installing garbage, the user id round-trips as a string, a username-only login is
 * non-admin, an unset context reads as the system actor, and the context never leaks across
 * threads.
 */
class AuthenticatedUserContextTest {

    @AfterEach
    void tearDown() {
        AuthenticatedUserContext.clear();
    }

    @Test
    void aBlankUsernameClearsTheContextInsteadOfInstallingGarbage() {
        AuthenticatedUserContext.setUser("alice", true);
        AuthenticatedUserContext.setUser("  ", true);
        assertThat(AuthenticatedUserContext.currentUsernameOrSystem()).isEqualTo(AuthenticatedUserContext.SYSTEM_ACTOR);

        AuthenticatedUserContext.setUser("alice", true);
        AuthenticatedUserContext.setUser(null, true);
        assertThat(AuthenticatedUserContext.currentUsernameOrSystem()).isEqualTo(AuthenticatedUserContext.SYSTEM_ACTOR);
        assertThat(AuthenticatedUserContext.currentUserIsAdmin()).isFalse();
    }

    @Test
    void aUserWithAnIdRoundTripsAsAString() {
        AuthenticatedUserContext.setUser(42L, "alice", true);
        assertThat(AuthenticatedUserContext.currentUserId()).isEqualTo("42");
        assertThat(AuthenticatedUserContext.currentUsernameOrSystem()).isEqualTo("alice");
        assertThat(AuthenticatedUserContext.currentUserIsAdmin()).isTrue();
        assertThat(AuthenticatedUserContext.currentUserIsAdminOrSystem()).isTrue();
    }

    @Test
    void aNullUserIdRemovesThePreviousOne() {
        AuthenticatedUserContext.setUser(42L, "alice", false);
        AuthenticatedUserContext.setUser(null, "bob", false);
        assertThat(AuthenticatedUserContext.currentUserId()).isNull();
        assertThat(AuthenticatedUserContext.currentUsernameOrSystem()).isEqualTo("bob");
    }

    @Test
    void aUsernameOnlyLoginDefaultsToNonAdmin() {
        AuthenticatedUserContext.setUsername("bob");
        assertThat(AuthenticatedUserContext.currentUserIsAdmin()).isFalse();
        assertThat(AuthenticatedUserContext.currentUserIsAdminOrSystem()).isFalse();
    }

    @Test
    void anUnsetContextReadsAsTheSystemActor() {
        AuthenticatedUserContext.clear();
        assertThat(AuthenticatedUserContext.currentUsernameOrSystem()).isEqualTo("system");
        assertThat(AuthenticatedUserContext.currentUserIsAdmin()).isFalse();
        // the admin-or-system check intentionally treats an unset context as privileged: it runs
        // on background threads where no request principal exists
        assertThat(AuthenticatedUserContext.currentUserIsAdminOrSystem()).isTrue();
    }

    @Test
    void theContextDoesNotLeakAcrossThreads() throws InterruptedException {
        AuthenticatedUserContext.setUser(7L, "alice", true);
        CountDownLatch done = new CountDownLatch(1);
        String[] seenByOtherThread = new String[1];
        Thread other = new Thread(() -> {
            seenByOtherThread[0] = AuthenticatedUserContext.currentUsernameOrSystem();
            done.countDown();
        });
        other.start();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        other.join(5000);
        assertThat(seenByOtherThread[0]).isEqualTo("system");
        assertThat(AuthenticatedUserContext.currentUsernameOrSystem()).isEqualTo("alice");
    }
}
