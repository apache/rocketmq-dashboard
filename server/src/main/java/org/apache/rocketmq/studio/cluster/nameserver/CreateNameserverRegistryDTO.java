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
package org.apache.rocketmq.studio.cluster.nameserver;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNameserverRegistryDTO {
    @NotBlank(message = "name is required")
    @Size(max = 128, message = "name must not exceed 128 characters")
    private String name;

    @NotBlank(message = "namesrvAddr is required")
    @Size(max = 512, message = "namesrvAddr must not exceed 512 characters")
    private String namesrvAddr;

    @Size(max = 128, message = "k8sNamespace must not exceed 128 characters")
    private String k8sNamespace;

    @Size(max = 128, message = "k8sId must not exceed 128 characters")
    private String k8sId;

    private String description;
}
