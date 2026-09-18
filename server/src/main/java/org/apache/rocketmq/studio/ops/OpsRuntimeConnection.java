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

package org.apache.rocketmq.studio.ops;

import org.apache.rocketmq.remoting.netty.TlsSystemConfig;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;

@Slf4j
@Service
public class OpsRuntimeConnection {

    private static final String RUNTIME_SETTINGS_UNAVAILABLE =
            "Ops runtime settings are disabled";

    private final OpsConnectionRepository repository;
    private final OpsRuntimeProperties properties;
    private final BooleanSupplier secureTlsAvailable;

    @Autowired
    public OpsRuntimeConnection(OpsConnectionRepository repository, OpsRuntimeProperties properties) {
        this(repository, properties, OpsRuntimeConnection::isSecureTlsConfigured);
    }

    OpsRuntimeConnection(OpsConnectionRepository repository, OpsRuntimeProperties properties,
                         BooleanSupplier secureTlsAvailable) {
        this.repository = repository;
        this.properties = properties;
        this.secureTlsAvailable = secureTlsAvailable;
    }

    public OpsConnectionSettings current() {
        if (!properties.isEnabled()) {
            return defaultSettings();
        }
        return loadManagedSettings();
    }

    public OpsConnectionSettings update(UnaryOperator<OpsConnectionSettings> updater) {
        if (!properties.isEnabled()) {
            throw new BusinessException(501, RUNTIME_SETTINGS_UNAVAILABLE);
        }
        try {
            return repository.update(updater, this::defaultSettings, this::validate);
        } catch (BusinessException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            log.warn("Failed to persist Ops runtime settings: {}", exception.getMessage());
            throw new BusinessException(503, "Ops runtime settings storage is unavailable");
        } catch (RuntimeException exception) {
            log.warn("Failed to persist Ops runtime settings", exception);
            throw new BusinessException(503, "Ops runtime settings storage is unavailable");
        }
    }

    private OpsConnectionSettings loadManagedSettings() {
        try {
            OpsConnectionSettings settings = repository.load()
                    .orElseGet(this::defaultSettings);
            validate(settings);
            return settings;
        } catch (BusinessException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            log.warn("Failed to load Ops runtime settings: {}", exception.getMessage());
            throw new BusinessException(503, "Ops runtime settings storage is unavailable");
        } catch (RuntimeException exception) {
            log.warn("Failed to load Ops runtime settings", exception);
            throw new BusinessException(503, "Ops runtime settings storage is unavailable");
        }
    }

    private OpsConnectionSettings defaultSettings() {
        return OpsConnectionSettings.fromNamesrvAddr(properties.getNamesrvAddr());
    }

    private void validate(OpsConnectionSettings settings) {
        if (settings.addresses().size() > properties.getMaxNameServerAddresses()) {
            throw new BusinessException(400, "namesrvAddr list exceeds the limit of "
                    + properties.getMaxNameServerAddresses());
        }
        if (settings.useTLS() && !secureTlsAvailable.getAsBoolean()) {
            throw new BusinessException(400,
                    "TLS requires tls.test.mode.enable=false, tls.client.authServer=true, "
                            + "and tls.client.trustCertPath");
        }
    }

    private static boolean isSecureTlsConfigured() {
        return !TlsSystemConfig.tlsTestModeEnable
                && TlsSystemConfig.tlsClientAuthServer
                && isReadableTrustCert(TlsSystemConfig.tlsClientTrustCertPath);
    }

    private static boolean isReadableTrustCert(String trustCertPath) {
        if (trustCertPath == null || trustCertPath.isBlank()) {
            return false;
        }
        try {
            Path path = Path.of(trustCertPath);
            return Files.exists(path) && Files.isRegularFile(path) && Files.isReadable(path);
        } catch (InvalidPathException | SecurityException exception) {
            return false;
        }
    }
}
