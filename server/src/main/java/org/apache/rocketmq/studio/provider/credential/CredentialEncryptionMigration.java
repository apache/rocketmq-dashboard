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
package org.apache.rocketmq.studio.provider.credential;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.common.util.CredentialCipher;
import org.apache.rocketmq.studio.common.util.CredentialUtils;
import org.apache.rocketmq.studio.persistence.entity.RmqCloudCredential;
import org.apache.rocketmq.studio.persistence.mapper.RmqCloudCredentialMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Re-seals cloud credential secrets that were persisted before AES-GCM was introduced.
 *
 * <p>Earlier Studio builds only base64-encoded {@code rmq_cloud_credential.secret_key}. Those rows stay
 * readable through {@link CredentialCipher#decrypt(String)}, but they keep no confidentiality until they
 * are rewritten. This one-shot startup step rewrites every legacy row once, so an upgraded deployment ends
 * up fully sealed without an operator manually editing each credential. It is idempotent: rows already
 * carrying the {@code enc:v1:} prefix are skipped, and a second boot finds nothing to do.</p>
 *
 * <p>Failures are logged rather than thrown. The migration is a hardening step for existing data, and
 * refusing to boot because one row could not be read would take the whole dashboard down; the affected row
 * keeps working through the legacy read path until it is next written.</p>
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class CredentialEncryptionMigration implements ApplicationRunner {

    private final RmqCloudCredentialMapper credentialMapper;
    private final CredentialCipher credentialCipher;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<RmqCloudCredential> legacyRows = credentialMapper.selectList(null).stream()
                    .filter(row -> row.getSecretKey() != null)
                    .filter(row -> !CredentialCipher.isEncrypted(row.getSecretKey()))
                    .toList();
            if (legacyRows.isEmpty()) {
                return;
            }
            int resealed = 0;
            for (RmqCloudCredential row : legacyRows) {
                RmqCloudCredential update = new RmqCloudCredential();
                update.setId(row.getId());
                update.setSecretKey(credentialCipher.encrypt(CredentialUtils.decodeBase64(row.getSecretKey())));
                if (credentialMapper.updateById(update) > 0) {
                    resealed++;
                }
            }
            log.info("Re-sealed {} cloud credential secret(s) with AES-GCM", resealed);
        } catch (Exception failure) {
            log.warn("Skipping cloud credential encryption migration: {}", failure.getMessage());
            log.debug("Cloud credential encryption migration failure detail", failure);
        }
    }
}
