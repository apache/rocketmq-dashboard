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

package org.apache.rocketmq.studio.ops.ai;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class LlmModelsResultVO {
    public static final String SOURCE_PROVIDER = "provider";
    public static final String SOURCE_BUILTIN = "builtin";
    public static final String SOURCE_FALLBACK = "fallback";

    private int status;
    private List<LlmModelItemVO> data;
    private String source;
    private String warning;
    private String warningCode;
    private String hint;

    public LlmModelsResultVO(int status, List<LlmModelItemVO> data) {
        this(status, data, SOURCE_BUILTIN, null, null, null);
    }

    public LlmModelsResultVO(int status, List<LlmModelItemVO> data, String source,
                             String warning, String warningCode, String hint) {
        this.status = status;
        this.data = data;
        this.source = source;
        this.warning = warning;
        this.warningCode = warningCode;
        this.hint = hint;
    }
}
