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

/**
 * Holds the authenticated Studio principal for the current request thread.
 */
public final class AuthenticatedUserContext {

    public static final String SYSTEM_ACTOR = "system";

    private static final ThreadLocal<String> CURRENT_USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> CURRENT_ADMIN = new ThreadLocal<>();

    private AuthenticatedUserContext() {
    }

    public static void setUser(String username, boolean admin) {
        if (username == null || username.isBlank()) {
            clear();
            return;
        }
        CURRENT_USERNAME.set(username);
        CURRENT_ADMIN.set(admin);
    }

    public static void setUsername(String username) {
        setUser(username, false);
    }

    public static void setUser(Long userId, String username) {
        setUser(userId, username, false);
    }

    public static void setUser(Long userId, String username, boolean admin) {
        setUser(username, admin);
        if (userId == null) {
            CURRENT_USER_ID.remove();
        } else {
            CURRENT_USER_ID.set(String.valueOf(userId));
        }
    }

    public static String currentUserId() {
        return CURRENT_USER_ID.get();
    }

    public static String currentUsernameOrSystem() {
        String username = CURRENT_USERNAME.get();
        return username == null ? SYSTEM_ACTOR : username;
    }

    /**
     * Returns whether the current request is an administrator or a system-initiated operation.
     * A missing context represents background/system work, which must retain access to persisted
     * configuration rather than being treated as a reader request.
     */
    public static boolean currentUserIsAdminOrSystem() {
        Boolean admin = CURRENT_ADMIN.get();
        return admin == null || admin;
    }

    public static void clear() {
        CURRENT_USERNAME.remove();
        CURRENT_USER_ID.remove();
        CURRENT_ADMIN.remove();
    }
}
