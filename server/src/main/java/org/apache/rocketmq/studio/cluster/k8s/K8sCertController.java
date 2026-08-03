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
package org.apache.rocketmq.studio.cluster.k8s;

import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/k8s-certs")
@RequiredArgsConstructor
public class K8sCertController {

    private final K8sCertService k8sCertService;

    @GetMapping
    public Result<List<K8sCertVO>> listCerts() {
        return Result.ok(k8sCertService.listCerts());
    }

    @PostMapping("/create")
    public Result<K8sCertVO> createCert(@Valid @RequestBody(required = false) CreateCertDTO command) {
        requireCommand(command);
        return Result.ok(k8sCertService.createCert(command));
    }

    @PostMapping("/update")
    public Result<K8sCertVO> updateCert(@Valid @RequestBody(required = false) UpdateCertDTO command) {
        requireCommand(command);
        return Result.ok(k8sCertService.updateCert(command));
    }

    @PostMapping("/renew")
    public Result<K8sCertVO> renewCert(@Valid @RequestBody(required = false) RenewCertDTO command) {
        requireCommand(command);
        return Result.ok(k8sCertService.renewCert(command));
    }

    @PostMapping("/delete")
    public Result<Void> deleteCert(@Valid @RequestBody(required = false) DeleteCertDTO command) {
        requireCommand(command);
        k8sCertService.deleteCert(command);
        return Result.ok();
    }

    private void requireCommand(Object command) {
        if (command == null) {
            throw new BusinessException(400, "K8s certificate request is required");
        }
    }
}
