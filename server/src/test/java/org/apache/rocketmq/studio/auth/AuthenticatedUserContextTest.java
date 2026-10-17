/*
 * Licensed to the Apache Software Foundation (ASF) under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticatedUserContextTest {

    @AfterEach
    void clearThreadLocals() {
        AuthenticatedUserContext.clear();
    }

    @Test
    void freshThreadReadsAsSystem() {
        assertThat(AuthenticatedUserContext.currentUsernameOrSystem())
                .isEqualTo(AuthenticatedUserContext.SYSTEM_ACTOR);
        assertThat(AuthenticatedUserContext.currentUserId()).isNull();
        assertThat(AuthenticatedUserContext.currentUserIsAdmin()).isFalse();
        // An unset context is system-level, which is privileged by design.
        assertThat(AuthenticatedUserContext.currentUserIsAdminOrSystem()).isTrue();
    }

    @Test
    void setUserRecordsUsernameAndAdminFlag() {
        AuthenticatedUserContext.setUser("alice", true);

        assertThat(AuthenticatedUserContext.currentUsernameOrSystem()).isEqualTo("alice");
        assertThat(AuthenticatedUserContext.currentUserIsAdmin()).isTrue();
        assertThat(AuthenticatedUserContext.currentUserIsAdminOrSystem()).isTrue();
    }

    @Test
    void setUsernameDefaultsToNonAdmin() {
        AuthenticatedUserContext.setUsername("bob");

        assertThat(AuthenticatedUserContext.currentUsernameOrSystem()).isEqualTo("bob");
        assertThat(AuthenticatedUserContext.currentUserIsAdmin()).isFalse();
        assertThat(AuthenticatedUserContext.currentUserIsAdminOrSystem()).isFalse();
    }

    @Test
    void setUserWithIdRoundTripsTheIdAsString() {
        AuthenticatedUserContext.setUser(42L, "carol", false);

        assertThat(AuthenticatedUserContext.currentUserId()).isEqualTo("42");
        assertThat(AuthenticatedUserContext.currentUsernameOrSystem()).isEqualTo("carol");
    }

    @Test
    void nullUserIdRemovesTheStoredIdButKeepsTheUsername() {
        AuthenticatedUserContext.setUser(42L, "carol", false);

        AuthenticatedUserContext.setUser(null, "carol", false);

        assertThat(AuthenticatedUserContext.currentUserId()).isNull();
        assertThat(AuthenticatedUserContext.currentUsernameOrSystem()).isEqualTo("carol");
    }

    @Test
    void blankUsernameClearsTheWholeContextIncludingTheUserId() {
        AuthenticatedUserContext.setUser(42L, "carol", true);

        AuthenticatedUserContext.setUser("   ", true);

        assertThat(AuthenticatedUserContext.currentUserId()).isNull();
        assertThat(AuthenticatedUserContext.currentUsernameOrSystem())
                .isEqualTo(AuthenticatedUserContext.SYSTEM_ACTOR);
        assertThat(AuthenticatedUserContext.currentUserIsAdmin()).isFalse();
    }

    @Test
    void clearResetsEveryField() {
        AuthenticatedUserContext.setUser(42L, "carol", true);

        AuthenticatedUserContext.clear();

        assertThat(AuthenticatedUserContext.currentUserId()).isNull();
        assertThat(AuthenticatedUserContext.currentUsernameOrSystem())
                .isEqualTo(AuthenticatedUserContext.SYSTEM_ACTOR);
        assertThat(AuthenticatedUserContext.currentUserIsAdmin()).isFalse();
        assertThat(AuthenticatedUserContext.currentUserIsAdminOrSystem()).isTrue();
    }
}
