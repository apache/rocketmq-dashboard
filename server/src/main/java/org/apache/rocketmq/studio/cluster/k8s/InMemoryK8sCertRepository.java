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

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class InMemoryK8sCertRepository implements K8sCertRepository {

    private final Map<String, K8sCertVO> store = new ConcurrentHashMap<>();

    @Override
    public List<K8sCertVO> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Optional<K8sCertVO> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public K8sCertVO save(K8sCertVO cert) {
        store.put(cert.getId(), cert);
        log.info("Saved certificate: {} (id={})", cert.getName(), cert.getId());
        return cert;
    }

    @Override
    public void deleteById(String id) {
        store.remove(id);
        log.info("Deleted certificate: {}", id);
    }
}
