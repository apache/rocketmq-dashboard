/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.cluster.nameserver;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoverKubernetesNameServersDTO {

    @NotBlank(message = "namespace is required")
    @Size(max = 63, message = "namespace must not exceed 63 characters")
    @Pattern(regexp = "[a-z0-9]([-a-z0-9]*[a-z0-9])?", message = "namespace must be a valid DNS label")
    private String namespace;
}
