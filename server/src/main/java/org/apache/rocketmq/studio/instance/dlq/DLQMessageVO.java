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
package org.apache.rocketmq.studio.instance.dlq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A dead-letter message as exported by the DLQ export endpoint. {@code body} carries the
 * UTF-8-decoded payload (best effort) while {@code bodyBase64} preserves the exact bytes
 * so binary messages can be exported losslessly.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DLQMessageVO {

    private String msgId;
    private String topic;
    private int queueId;
    private long offset;
    private long storeTime;
    private String keys;
    private String body;
    private String bodyBase64;
}
