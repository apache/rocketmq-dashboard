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
package org.apache.rocketmq.studio.cloud.credential;

import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import org.apache.rocketmq.studio.common.domain.DeleteRequestDTO;
import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/cloud-credentials")
public class CloudCredentialController {

    private final CloudCredentialService credentialService;

    @GetMapping
    public Result<List<CloudCredentialVO>> listCredentials() {
        return Result.ok(credentialService.listMasked());
    }

    @PostMapping("/create")
    public Result<CloudCredentialVO> createCredential(
            @Valid @RequestBody(required = false) CreateCloudCredentialDTO request) {
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "Cloud credential request is required");
        }
        return Result.ok(credentialService.create(request.toCloudCredentialVO()));
    }

    @PostMapping("/update")
    public Result<CloudCredentialVO> updateCredential(
            @Valid @RequestBody(required = false) UpdateCloudCredentialDTO request) {
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "Cloud credential request is required");
        }
        return Result.ok(credentialService.update(request));
    }

    @PostMapping("/delete")
    public Result<Void> deleteCredential(@Valid @RequestBody DeleteRequestDTO request) {
        credentialService.delete(request.getId());
        return Result.ok();
    }

    @GetMapping("/{id}/credentials")
    public Result<CloudCredentialVO> getCredentialSecrets(@PathVariable String id) {
        return Result.ok(credentialService.reveal(id));
    }
}
