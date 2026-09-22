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
package org.apache.rocketmq.studio.ops.ai.tool.contract.acl;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.rocketmq.studio.instance.acl.AclUserVO;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AclUserItem(
        String id,
        String username,
        boolean admin,
        List<String> clusters) {

    public static AclUserItem from(AclUserVO user) {
        return new AclUserItem(
                identifier(user),
                user.getUsername(),
                user.isAdmin(),
                user.getClusters());
    }

    /**
     * The tool contract requires an {@code id}. Users stored in the Studio ACL tables carry a
     * numeric primary key, but a role-backed instance (Tencent) projects its users from the
     * cloud role and has none, so the username identifies the user there — which is also what
     * {@code AclService.getUser} accepts for such an instance.
     */
    private static String identifier(AclUserVO user) {
        return user.getId() == null ? user.getUsername() : user.getId().toString();
    }
}
