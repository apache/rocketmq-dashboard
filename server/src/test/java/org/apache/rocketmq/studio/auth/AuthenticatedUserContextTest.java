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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AuthenticatedUserContext}: the per-thread principal exposed to
 * services/controllers — including the default "system" identity when nothing is set and
 * the way an admin flag is carried through the various setter overloads.
 */
class AuthenticatedUserContextTest {

    @BeforeEach
    void clearContext() {
        AuthenticatedUserContext.clear();
    }

    @Test
    void defaultsToTheSystemActorWithoutAContext() {
        assertThat(AuthenticatedUserContext.currentUsernameOrSystem())
                .isEqualTo(AuthenticatedUserContext.SYSTEM_ACTOR);
        assertThat(AuthenticatedUserContext.currentUserId()).isNull();
        assertThat(AuthenticatedUserContext.currentUserIsAdmin()).isFalse();
        // An unset admin flag is treated as system, which is allowed everywhere.
        assertThat(AuthenticatedUserContext.currentUserIsAdminOrSystem()).isTrue();
    }

    @Test
    void carriesTheAdminFlagFromTheTwoArgumentSetter() {
        AuthenticatedUserContext.setUser("admin", true);

        assertThat(AuthenticatedUserContext.currentUsernameOrSystem()).isEqualTo("admin");
        assertThat(AuthenticatedUserContext.currentUserIsAdmin()).isTrue();
        assertThat(AuthenticatedUserContext.currentUserIsAdminOrSystem()).isTrue();
    }

    @Test
    void recordsNonAdminPrincipalsWithoutSystemPrivileges() {
        AuthenticatedUserContext.setUser("reader", false);

        assertThat(AuthenticatedUserContext.currentUsernameOrSystem()).isEqualTo("reader");
        assertThat(AuthenticatedUserContext.currentUserIsAdmin()).isFalse();
        assertThat(AuthenticatedUserContext.currentUserIsAdminOrSystem()).isFalse();
    }

    @Test
    void usernameOnlySetterDefaultsToNonAdmin() {
        AuthenticatedUserContext.setUsername("editor");

        assertThat(AuthenticatedUserContext.currentUsernameOrSystem()).isEqualTo("editor");
        assertThat(AuthenticatedUserContext.currentUserIsAdmin()).isFalse();
    }

    @Test
    void numericSettersRecordTheUserId() {
        AuthenticatedUserContext.setUser(42L, "operator");
        assertThat(AuthenticatedUserContext.currentUserId()).isEqualTo("42");
        assertThat(AuthenticatedUserContext.currentUserIsAdmin()).isFalse();

        AuthenticatedUserContext.setUser(7L, "boss", true);
        assertThat(AuthenticatedUserContext.currentUserId()).isEqualTo("7");
        assertThat(AuthenticatedUserContext.currentUserIsAdmin()).isTrue();
    }

    @Test
    void blankUsernameOrClearResetsToTheSystemActor() {
        AuthenticatedUserContext.setUser("temp", true);
        AuthenticatedUserContext.setUser(null, false);

        assertThat(AuthenticatedUserContext.currentUsernameOrSystem())
                .isEqualTo(AuthenticatedUserContext.SYSTEM_ACTOR);
        assertThat(AuthenticatedUserContext.currentUserIsAdmin()).isFalse();
        assertThat(AuthenticatedUserContext.currentUserIsAdminOrSystem()).isTrue();

        AuthenticatedUserContext.setUser("temp", true);
        AuthenticatedUserContext.clear();
        assertThat(AuthenticatedUserContext.currentUsernameOrSystem())
                .isEqualTo(AuthenticatedUserContext.SYSTEM_ACTOR);
    }
}
