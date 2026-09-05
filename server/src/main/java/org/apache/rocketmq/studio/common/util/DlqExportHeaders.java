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

package org.apache.rocketmq.studio.common.util;

import java.util.List;

/**
 * Names of the response headers the DLQ export endpoints attach to describe scan
 * completeness. The web UI reads them to warn when an export is truncated or some
 * queues could not be scanned, so they must also be declared in the CORS
 * {@code exposedHeaders} list — browsers only let cross-origin JavaScript read
 * response headers that appear in {@code Access-Control-Expose-Headers}.
 */
public final class DlqExportHeaders {

    public static final String TRUNCATED = "X-DLQ-Export-Truncated";
    public static final String FAILED_QUEUES = "X-DLQ-Export-FailedQueues";
    public static final String LIMIT = "X-DLQ-Export-Limit";

    public static final List<String> ALL = List.of(TRUNCATED, FAILED_QUEUES, LIMIT);

    private DlqExportHeaders() {
    }
}
