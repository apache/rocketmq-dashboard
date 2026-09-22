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
package org.apache.rocketmq.studio.ops.ai.conversation.agent;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.cluster.broker.MqAdminProperties;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceResolver;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.provider.credential.CloudCredentialRepository;
import org.apache.rocketmq.studio.provider.credential.CloudCredentialVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resolves the access-key/secret-key pair that identifies a Studio instance, whatever vendor it
 * belongs to. One implementation, two callers:
 *
 * <ul>
 *   <li>{@code McpAuthenticator} — the pair a request must have been signed with, so an incoming
 *       HMAC signature can be verified. It resolves the instance itself (an unknown instance is an
 *       <em>authentication</em> failure, not a 404) and calls {@link #resolve(InstanceVO)}.</li>
 *   <li>{@code RmqctlWorkspace} — the pair to hand to a hosted agent's {@code rmqctl} child process,
 *       which signs its callback into {@code /api/mcp} with it. It calls {@link #resolveByName(String)}
 *       once per run, so an operator editing {@code studio.cluster.admin.credentials.*} takes effect
 *       on the next message rather than on the next restart.</li>
 * </ul>
 *
 * <p>Both callers must agree on <em>which</em> credential belongs to an instance id, otherwise the
 * agent signs with a key the server will not accept. That is why {@link #resolveByName(String)} goes
 * through {@link InstanceResolver#findByName(String)} — the same lookup {@code McpAuthenticator}
 * performs for the {@code x-rmq-instance-id} header — and not through {@code findByIdentifier}, which
 * also matches numeric primary keys and cloud instance ids that the MCP entry point would reject.
 *
 * <h2>Vendor rules</h2>
 * {@code APACHE} (and a null vendor, which the registered-instance form treats as open source) reads
 * {@code studio.cluster.admin.credentials.<adminCredentialRef>} through
 * {@link RuntimeAdminClientResolver#resolveCredential(InstanceVO)}. Every cloud vendor reads the
 * {@code rmq_cloud_credential} row named by {@code instance.credentialId}. A cloud instance without a
 * credential reference never falls back to the configured admin credential: a tenant's cloud key and
 * the operator's admin key are different identities and mixing them would authenticate one instance's
 * traffic as another's.
 *
 * <h2>Failure mode and secrets</h2>
 * Every "this instance has no usable credential" case is a {@link BusinessException}, so a REST caller
 * can answer 4xx and {@code McpAuthenticator} can collapse it into a single opaque 401 (leaking which
 * part of the credential chain is missing would be an oracle). Failures that are <em>not</em>
 * configuration problems — a database outage inside the repository, for instance — propagate
 * untouched and must stay distinguishable from a bad credential.
 *
 * <p>The returned {@link InstanceCredential} is the only place these values are allowed to live: it
 * masks itself in {@code toString()} so it cannot reach a log line, and callers hand it to a child
 * process environment rather than to argv, a file or a database column.
 */
@Component
@RequiredArgsConstructor
public class InstanceCredentialResolver {

    private final RuntimeAdminClientResolver adminClientResolver;
    private final CloudCredentialRepository cloudCredentialRepository;
    private final InstanceResolver instanceResolver;

    /**
     * Resolves an instance by its Studio identifier (the {@code rmq_instance.name} that also travels
     * as {@code x-rmq-instance-id}) and returns its credential.
     *
     * @throws BusinessException 400 when the identifier is blank, 404 when no instance carries it,
     *     422 when the instance exists but has no usable credential
     */
    public InstanceCredential resolveByName(String instanceId) {
        if (!StringUtils.hasText(instanceId)) {
            throw new BusinessException(400, "instanceId is required");
        }
        String name = instanceId.trim();
        InstanceVO instance = instanceResolver.findByName(name)
                .orElseThrow(() -> new BusinessException(404, "Instance not found: " + name));
        return resolve(instance);
    }

    /**
     * Returns the credential of an already resolved instance.
     *
     * @throws BusinessException 422 when the instance has no usable credential
     */
    public InstanceCredential resolve(InstanceVO instance) {
        if (instance == null) {
            throw new BusinessException(422, "Instance is required to resolve a credential");
        }
        if (instance.getVendor() == null || instance.getVendor() == InstanceVendor.APACHE) {
            return apacheCredential(instance);
        }
        return cloudCredential(instance);
    }

    private InstanceCredential apacheCredential(InstanceVO instance) {
        // BusinessException from the resolver means "the referenced admin credential is not
        // configured"; it is a configuration problem, so it stays a BusinessException and the
        // caller decides how loud to be about it.
        MqAdminProperties.Credential credential = adminClientResolver.resolveCredential(instance);
        return requirePresent(credential == null ? null : credential.getAccessKey(),
                credential == null ? null : credential.getSecretKey(),
                "Admin credential is not configured for instance: " + instance.getName());
    }

    private InstanceCredential cloudCredential(InstanceVO instance) {
        if (instance.getCredentialId() == null) {
            throw new BusinessException(422,
                    "Instance has no cloud credential reference: " + instance.getName());
        }
        CloudCredentialVO credential = cloudCredentialRepository.findById(instance.getCredentialId())
                .orElseThrow(() -> new BusinessException(422,
                        "Cloud credential is not available for instance: " + instance.getName()));
        return requirePresent(credential.getAccessKey(), credential.getSecretKey(),
                "Cloud credential is incomplete for instance: " + instance.getName());
    }

    private static InstanceCredential requirePresent(String accessKey, String secretKey, String message) {
        if (!StringUtils.hasText(accessKey) || !StringUtils.hasText(secretKey)) {
            throw new BusinessException(422, message);
        }
        return new InstanceCredential(accessKey.trim(), secretKey.trim());
    }

    /**
     * A resolved credential pair. Values are already trimmed and never blank.
     *
     * <p>{@code toString()} is overridden rather than annotated with Lombok's
     * {@code @ToString.Exclude}: a record's canonical {@code toString()} prints every component, and
     * the only reliable way to keep a secret out of a log line that happens to interpolate this
     * object is to not produce it in the first place.
     */
    public record InstanceCredential(String accessKey, String secretKey) {

        @Override
        public String toString() {
            return "InstanceCredential[accessKey=" + mask(accessKey) + ", secretKey=***]";
        }

        /**
         * Keeps enough of the access key to tell two identities apart in a diagnostic without
         * reproducing it: an access key is an identifier, not a secret, but it is still half a
         * credential pair.
         */
        private static String mask(String accessKey) {
            if (accessKey == null || accessKey.length() <= 4) {
                return "***";
            }
            return accessKey.substring(0, 4) + "***";
        }
    }
}
