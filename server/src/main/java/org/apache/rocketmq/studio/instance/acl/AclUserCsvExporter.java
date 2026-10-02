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
package org.apache.rocketmq.studio.instance.acl;

import org.apache.rocketmq.studio.common.util.CsvUtil;

import java.util.List;

/**
 * Renders the ACL account inventory as a CSV document for the export endpoint.
 *
 * <p>The rows come from the same page query the users table reads, which already masks both
 * credentials, so the document only repeats data the list view shows: the access key is written
 * exactly as it is masked there and the secret key has no column at all.
 */
public final class AclUserCsvExporter {

    private static final String EXPORT_CSV_HEADER = "User ID,Username,Access Key,Admin,Clusters,"
            + "Read Permission,Write Permission,IP Whitelist,Created\r\n";

    private AclUserCsvExporter() {
    }

    public static String render(List<AclUserVO> users) {
        StringBuilder csv = new StringBuilder("\uFEFF").append(EXPORT_CSV_HEADER);
        for (AclUserVO user : users) {
            CsvUtil.appendRow(csv, user.getId(), user.getUsername(), user.getAccessKey(), user.isAdmin(),
                    String.join(";", user.getClusters() == null ? List.of() : user.getClusters()),
                    user.getPermRead(), user.getPermWrite(), user.getWhiteRemoteAddress(),
                    user.getGmtCreate());
        }
        return csv.toString();
    }
}
